package com.motonav.app.ride

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.motonav.app.MainActivity
import com.motonav.app.location.CompassTracker
import com.motonav.app.location.RideSensors
import com.motonav.app.location.RideSensorsStateHolder
import com.motonav.app.settings.SettingsStore
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider
import com.stadiamaps.ferrostar.core.service.ForegroundServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.Instant
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.Speed
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.WellKnownRouteProvider
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

// Owns the whole ride session: the FerrostarCore (this file), location + sensors (stage 3), and
// the BT-gated wake lock / foreground state / auto-launch that already lived here. Nav-start
// detection now observes ride.RideStateHolder (fed from Ferrostar's NavigationState), not the dead
// notification package's NavStateHolder or the dead navsdk package's NavSdkStateHolder — see
// docs/MotoNav_REBUILD_PLAN_OSM.md Phase A.
//
// Started directly by MainActivity once location permission is granted. No Navigation SDK terms
// dialog to gate on any more — Ferrostar/Valhalla has none.
class RideSessionService : Service() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var btConnected = false
    private var guidanceActive = false
    private var scope: CoroutineScope? = null
    private var ferrostarCore: FerrostarCore? = null
    private val rideLocationProvider = RideLocationProvider()
    // Phase C — only set (and only closed in onDestroy) when routerBackend == LOCAL.
    private var localRouteProvider: LocalValhallaRouteProvider? = null
    // Phase D — streams the same RideState (+ RideSensors) to the ESP32 puck over BLE.
    private val bleLink = BleLink(this)

    // Stage 3: live speed + heading. Both feed RideSensorsStateHolder for the dial and must keep
    // running with the screen off — hence hosted here, not in a composable (plan §0.4 / stage 3).
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var compassTracker: CompassTracker? = null
    private var currentSpeedKmh: Float? = null
    private var currentHeadingDegrees: Float? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            currentSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else null
            RideSensorsStateHolder.update(RideSensors(currentSpeedKmh, currentHeadingDegrees))
            compassTracker?.onLocationChanged(location)
            rideLocationProvider.update(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        createNotificationChannel()
        startSensors()
        bleLink.start()

        val job = Job()
        scope = CoroutineScope(Dispatchers.Default + job).also { s ->
            RideStateHolder.state
                .map { it != null }
                .distinctUntilChanged()
                .drop(1) // skip the initial value — only react to transitions
                .onEach { isActive ->
                    guidanceActive = isActive
                    updateForegroundState()
                    if (isActive) onEvent(TriggerEvent.NAV_START)
                }
                .launchIn(s)

            // Phase D — same RideState the dial reads, packed and pushed to the puck. Sensors are
            // a separate flow (speed/heading come from GPS/compass, not Ferrostar), so combine.
            combine(RideStateHolder.state, RideSensorsStateHolder.state) { rideState, sensors ->
                packRideStatePacket(rideState, sensors)
            }.onEach { bleLink.update(it) }.launchIn(s)

            initializeFerrostarCore(s)
        }
    }

    // Location permission is guaranteed by the time MainActivity starts this service (it gates on
    // it), so the runtime check Android otherwise wants here is already satisfied — see startup
    // order in MainActivity.proceedPastLocationPermission()/startRideSessionService().
    @SuppressLint("MissingPermission")
    private fun startSensors() {
        compassTracker = CompassTracker(this) { heading ->
            currentHeadingDegrees = heading
            RideSensorsStateHolder.update(RideSensors(currentSpeedKmh, currentHeadingDegrees))
        }.also { it.start() }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopSensors() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        compassTracker?.stop()
        compassTracker = null
        RideSensorsStateHolder.update(RideSensors(null, null))
    }

    // FOSSGIS (valhalla1.openstreetmap.de) explicitly asks published apps to identify themselves —
    // see plan §5.3. Stadia Maps needs no such header; sending it there is harmless but pointless,
    // so it's scoped to FOSSGIS only.
    private fun remoteRouterHttpClient(backend: RouterBackend): OkHttpClient =
        if (backend == RouterBackend.FOSSGIS) {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("X-Client-Id", CLIENT_ID).build())
                }
                .build()
        } else {
            OkHttpClient()
        }

    private fun initializeFerrostarCore(scope: CoroutineScope) {
        val backend = settingsStore.routerBackend
        // Phase C — LOCAL swaps in the on-device Valhalla engine as Ferrostar's CustomRouteProvider;
        // remote (Phase A) stays the default and stays user-selectable. See
        // docs/MotoNav_REBUILD_PLAN_OSM.md Phase C item 3.
        val core = if (backend == RouterBackend.LOCAL) {
            val provider = LocalValhallaRouteProvider(applicationContext, VALHALLA_PROFILE)
            localRouteProvider = provider
            FerrostarCore(
                provider,
                OkHttpClientProvider(OkHttpClient()),
                rideLocationProvider,
                defaultNavigationControllerConfig(),
                NoopForegroundServiceManager,
            )
        } else {
            FerrostarCore(
                WellKnownRouteProvider.Valhalla(
                    endpointUrl = backend.endpointUrl(settingsStore.routerApiKey),
                    profile = VALHALLA_PROFILE,
                    optionsJson = "{}", // motorcycle costing, defaults only — see plan Phase A item 5
                ),
                OkHttpClientProvider(remoteRouterHttpClient(backend)),
                rideLocationProvider,
                defaultNavigationControllerConfig(),
                NoopForegroundServiceManager,
            )
        }
        ferrostarCore = core

        core.state
            .map { it.toRideState() }
            .distinctUntilChanged()
            .onEach { RideStateHolder.update(it) }
            .launchIn(scope)

        // Phase E — no auto-started guidance any more. A destination chosen through the
        // destination-search/route-map screens (MainActivity) arrives via EXTRA_DEST_LAT/LNG on a
        // later onStartCommand; if one was already saved from a previous run, resume it now.
        val savedLat = settingsStore.destinationLat
        val savedLng = settingsStore.destinationLng
        if (savedLat != null && savedLng != null) {
            scope.launch { startGuidanceToDestination(core, savedLat.toDouble(), savedLng.toDouble()) }
        }
    }

    private suspend fun startGuidanceToDestination(core: FerrostarCore, lat: Double, lng: Double) {
        try {
            val userLocation = rideLocationProvider.lastLocation().toUserLocation()
            val destination = Waypoint(
                coordinate = GeographicCoordinate(lat, lng),
                kind = WaypointKind.BREAK,
                properties = null,
            )
            val route = core.getRoutes(userLocation, listOf(destination)).firstOrNull()
            if (route == null) {
                Log.e(TAG, "No route returned to destination")
                RideErrorHolder.report("No route found to that destination")
                return
            }
            core.startNavigation(route)
        } catch (e: Exception) {
            // ponytail: no retry/backoff — one attempt, surfaced to the user via RideErrorHolder,
            // dial stays idle. Add retry if a real ride shows transient router failures matter.
            Log.e(TAG, "Failed to start guidance", e)
            RideErrorHolder.report("Could not get a route: ${e.message ?: e::class.simpleName}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(EXTRA_BT_EVENT)) {
            BT_EVENT_CONNECTED -> {
                btConnected = true
                updateForegroundState()
                onEvent(TriggerEvent.BT_CONNECTED)
            }
            BT_EVENT_DISCONNECTED -> {
                btConnected = false
                updateForegroundState()
            }
        }
        // Phase E — a destination picked in the search/route-map screens. Persisted so a service
        // restart (or the "resume on create" branch above) doesn't lose it.
        if (intent?.hasExtra(EXTRA_DEST_LAT) == true && intent.hasExtra(EXTRA_DEST_LNG) == true) {
            val lat = intent.getFloatExtra(EXTRA_DEST_LAT, 0f)
            val lng = intent.getFloatExtra(EXTRA_DEST_LNG, 0f)
            settingsStore.destinationLat = lat
            settingsStore.destinationLng = lng
            val core = ferrostarCore
            val s = scope
            if (core != null && s != null) {
                s.launch { startGuidanceToDestination(core, lat.toDouble(), lng.toDouble()) }
            }
        }
        return START_STICKY
    }

    private fun onEvent(event: TriggerEvent) {
        if (shouldLaunch(settingsStore.autoLaunchMode, event, btConnected)) {
            val launchIntent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }
    }

    // Foreground/wake-lock state is now the OR of two independent gates — BT connected (existing
    // PRD auto-launch feature) and guidance active (this stage). Losing BT mid-ride must not tear
    // down the session that's keeping guidance alive; losing guidance while still BT-connected
    // must not either. Only tear down when both are false.
    //
    // The active foregroundServiceType set must match reality on every transition, not just the
    // first call: Android 14+ rejects startForeground() with a type whose runtime permission isn't
    // held (e.g. connectedDevice needs BLUETOOTH_CONNECT, which we don't request in this stage) —
    // confirmed on-device (SecurityException, targetSDK=36). Guidance-only sessions must declare
    // only `location`, never `connectedDevice`.
    private fun updateForegroundState() {
        if (btConnected || guidanceActive) startForegroundSession() else stopForegroundSession()
    }

    private fun startForegroundSession() {
        if (wakeLock?.isHeld != true) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MotoNav:ride").apply { acquire() }
        }
        var type = 0
        if (btConnected) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (guidanceActive) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun stopForegroundSession() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MotoNav ride active")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Ride session", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        ferrostarCore?.stopNavigation()
        ferrostarCore = null
        localRouteProvider?.close()
        localRouteProvider = null
        RideStateHolder.update(null)
        stopSensors()
        bleLink.stop()
        stopForegroundSession()
        scope?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_BT_EVENT = "bt_event"
        const val BT_EVENT_CONNECTED = "connected"
        const val BT_EVENT_DISCONNECTED = "disconnected"
        // Phase E — set by MainActivity once a destination is confirmed on the route-selection map.
        const val EXTRA_DEST_LAT = "dest_lat"
        const val EXTRA_DEST_LNG = "dest_lng"
        private const val CHANNEL_ID = "ride_session"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_INTERVAL_MS = 1_500L
        private const val TAG = "RideSessionService"

        // Valhalla's dedicated two-wheeled costing model — see
        // docs/MotoNav_REBUILD_PLAN_OSM.md Phase A item 5. Defaults only (optionsJson = "{}"
        // above); no custom costing options exposed yet.
        private const val VALHALLA_PROFILE = "motorcycle"

        // Sent as X-Client-Id to FOSSGIS — see remoteRouterHttpClient() and plan §5.3.
        private const val CLIENT_ID = "com.motonav.app/0.1.0"
    }
}

// Ferrostar's own ForegroundServiceManager would start a second, competing Android foreground
// service; RideSessionService already owns that lifecycle end-to-end (wake lock, notification,
// service type) per docs/MotoNav_REBUILD_PLAN_OSM.md §1.1, so this is a no-op.
private object NoopForegroundServiceManager : ForegroundServiceManager {
    override fun startService(stopNavigation: () -> Unit) = Unit
    override fun stopService() = Unit
    override fun onNavigationStateUpdated(state: NavigationState) = Unit
}

// Defaults only, per docs/MotoNav_REBUILD_PLAN_OSM.md Phase A item 5 — nothing here is exposed as
// a setting yet. Values are Ferrostar's own suggested starting points (32m GPS-accuracy floor,
// ~30m step-advance trigger, tighter 10m for the final arrival step, 25m/10m off-route threshold).
private fun defaultNavigationControllerConfig(): NavigationControllerConfig = NavigationControllerConfig(
    waypointAdvance = WaypointAdvanceMode.WaypointWithinRange(100.0),
    stepAdvanceCondition = stepAdvanceDistanceToEndOfStep(32u, 30u),
    arrivalStepAdvanceCondition = stepAdvanceDistanceToEndOfStep(32u, 10u),
    routeDeviationTracking = RouteDeviationTracking.StaticThreshold(25u, 10.0),
    snappedLocationCourseFiltering = CourseFiltering.SNAP_TO_ROUTE,
)

private fun Location.toUserLocation(): UserLocation = UserLocation(
    coordinates = GeographicCoordinate(latitude, longitude),
    horizontalAccuracy = accuracy.toDouble(),
    courseOverGround = null,
    timestamp = Instant.ofEpochMilli(time),
    speed = if (hasSpeed()) Speed(speed.toDouble(), null) else null,
)
