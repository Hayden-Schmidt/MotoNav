package com.motonav.app.ride

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.libraries.navigation.ListenableResultFuture
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.Waypoint
import com.motonav.app.MainActivity
import com.motonav.app.location.CompassTracker
import com.motonav.app.location.RideSensors
import com.motonav.app.location.RideSensorsStateHolder
import com.motonav.app.navsdk.NavSdkStateHolder
import com.motonav.app.navsdk.NavSdkTurnByTurnService
import com.motonav.app.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

// Owns the whole ride session: the Navigator (this file), location + sensors (stage 3), and the
// BT-gated wake lock / foreground state / auto-launch that already lived here. Nav-start detection
// now observes navsdk.NavSdkStateHolder (real turn-by-turn data), not the dead notification
// package's NavStateHolder — see MotoNav_TASK7_STAGED_PLAN.md §0.3 point 2.
//
// Started directly by MainActivity once location permission is granted and Navigation SDK terms
// are accepted (MainActivity's job, not this service's — see §0.4). No longer depends on
// NavNotificationListenerService.onListenerConnected() to be alive.
class RideSessionService : Service() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var btConnected = false
    private var guidanceActive = false
    private var scope: CoroutineScope? = null
    private var navigator: Navigator? = null

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
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        createNotificationChannel()
        startSensors()

        val job = Job()
        scope = CoroutineScope(Dispatchers.Default + job).also { s ->
            NavSdkStateHolder.state
                .map { it != null }
                .distinctUntilChanged()
                .drop(1) // skip the initial value — only react to transitions
                .onEach { isActive ->
                    guidanceActive = isActive
                    updateForegroundState()
                    if (isActive) onEvent(TriggerEvent.NAV_START)
                }
                .launchIn(s)
        }

        initializeNavigator()
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

    private fun initializeNavigator() {
        // Application overload (not the Activity one) — MainActivity already forced terms
        // acceptance before starting this service, so no Activity context is needed here.
        // Verified via javap against navigation-7.9.0.aar: both overloads exist on NavigationApi.
        NavigationApi.getNavigator(
            application,
            object : NavigationApi.NavigatorListener {
                override fun onNavigatorReady(readyNavigator: Navigator) {
                    navigator = readyNavigator
                    NavigatorStateHolder.update(NavigatorState.Ready)
                    startGuidanceToTestDestination(readyNavigator)
                }

                override fun onError(errorCode: Int) {
                    NavigatorStateHolder.update(NavigatorState.Error(describeErrorCode(errorCode)))
                }
            },
        )
    }

    private fun startGuidanceToTestDestination(navigator: Navigator) {
        val waypoint = Waypoint.Builder().setLatLng(TEST_DESTINATION_LAT, TEST_DESTINATION_LNG).build()
        navigator.setDestination(waypoint).setOnResultListener(
            object : ListenableResultFuture.OnResultListener<Navigator.RouteStatus> {
                override fun onResult(status: Navigator.RouteStatus) {
                    if (status != Navigator.RouteStatus.OK) {
                        // Per stage-1 instructions: report the exact value, don't work around it.
                        NavigatorStateHolder.update(NavigatorState.Error("RouteStatus: $status"))
                        return
                    }
                    // Int.MAX_VALUE = "send all remaining steps" — confirmed against Google's
                    // official navigation-sample (NavForwardingManager.kt), not guessed.
                    navigator.registerServiceForNavUpdates(
                        packageName,
                        NavSdkTurnByTurnService::class.java.name,
                        Int.MAX_VALUE,
                    )
                    navigator.startGuidance()
                }
            },
        )
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
        navigator?.let {
            it.stopGuidance()
            it.unregisterServiceForNavUpdates()
        }
        navigator = null
        NavSdkStateHolder.update(null)
        stopSensors()
        stopForegroundSession()
        scope?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_BT_EVENT = "bt_event"
        const val BT_EVENT_CONNECTED = "connected"
        const val BT_EVENT_DISCONNECTED = "disconnected"
        private const val CHANNEL_ID = "ride_session"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_INTERVAL_MS = 1_500L

        // STAGE-1 SCAFFOLDING — remove in stage 6 (destination search replaces this).
        // Westfield Albany, Auckland — approximate, checked in Google Maps before use.
        private const val TEST_DESTINATION_LAT = -36.7290
        private const val TEST_DESTINATION_LNG = 174.7000
    }
}

private fun describeErrorCode(errorCode: Int): String = when (errorCode) {
    NavigationApi.ErrorCode.NOT_AUTHORIZED -> "NOT_AUTHORIZED (bad/unauthorized API key)"
    NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> "TERMS_NOT_ACCEPTED"
    NavigationApi.ErrorCode.NETWORK_ERROR -> "NETWORK_ERROR"
    NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING -> "LOCATION_PERMISSION_MISSING"
    else -> "UNKNOWN ($errorCode)"
}
