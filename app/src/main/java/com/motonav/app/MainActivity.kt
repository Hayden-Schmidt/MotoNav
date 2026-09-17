package com.motonav.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.compose.material.icons.filled.Place
import com.motonav.app.location.RideSensorsStateHolder
import com.motonav.app.map.DestinationSearchScreen
import com.motonav.app.map.RouteSelectionMapScreen
import com.motonav.app.nav.GeocodeResult
import com.motonav.app.ride.RideErrorHolder
import com.motonav.app.ride.RideSessionService
import com.motonav.app.ride.RideStateHolder
import com.motonav.app.ride.shouldKeepScreenOn
import com.motonav.app.settings.SettingsScreen
import com.motonav.app.settings.SettingsStore
import com.motonav.app.ui.dial.NavDial
import com.motonav.app.ui.dial.deriveDialState
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore
    private var locationPermissionGranted by mutableStateOf(false)
    private var locationPermissionPermanentlyDenied by mutableStateOf(false)

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
            locationPermissionGranted = granted
            if (granted) {
                startRideSessionService()
            } else {
                locationPermissionPermanentlyDenied =
                    !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(this)
        locationPermissionGranted = hasLocationPermission()

        setContent {
            MotoNavTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showSettings by remember { mutableStateOf(false) }
                    // Phase E — destination search → route-selection map → guidance. Two screens
                    // chained through one nullable "picked but not yet confirmed" result, not a
                    // navigation-library back stack — there are only two steps.
                    var showDestinationSearch by remember { mutableStateOf(false) }
                    var pendingDestination by remember { mutableStateOf<GeocodeResult?>(null) }
                    // Recomputed whenever settings closes, so toggles made there take effect
                    // immediately (SettingsStore is plain SharedPreferences, not itself a State).
                    val dialConfig = remember(showSettings) { settingsStore.navDialConfig() }
                    val rideState by RideStateHolder.state.collectAsState()
                    val rideSensors by RideSensorsStateHolder.state.collectAsState()
                    val rideError by RideErrorHolder.error.collectAsState()
                    LaunchedEffect(rideError) {
                        rideError?.let {
                            Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                            RideErrorHolder.clear()
                        }
                    }
                    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(500)
                            nowMs = System.currentTimeMillis()
                        }
                    }

                    LaunchedEffect(rideState, settingsStore.screenOnMode) {
                        val externalDisplayConnected =
                            (getSystemService(DisplayManager::class.java)).displays.size > 1
                        val keepOn = shouldKeepScreenOn(settingsStore.screenOnMode, externalDisplayConnected)
                        if (keepOn) {
                            window.addFlags(FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    when {
                        !locationPermissionGranted && locationPermissionPermanentlyDenied ->
                            LocationPermissionDeniedScreen()
                        !locationPermissionGranted -> LocationPermissionRationaleScreen(
                            onContinue = { requestLocationPermission.launch(permissionsToRequest()) },
                        )
                        showSettings -> SettingsScreen(settingsStore)
                        pendingDestination != null -> RouteSelectionMapScreen(
                            destination = pendingDestination!!,
                            onBack = { pendingDestination = null },
                            onConfirm = {
                                startGuidanceTo(pendingDestination!!)
                                pendingDestination = null
                            },
                        )
                        showDestinationSearch -> DestinationSearchScreen(
                            geocoderBaseUrl = settingsStore.geocoderBaseUrl,
                            onBack = { showDestinationSearch = false },
                            onResultSelected = {
                                pendingDestination = it
                                showDestinationSearch = false
                            },
                        )
                        else -> NavPageScreen(
                            dialState = deriveDialState(rideState, nowMs),
                            dialConfig = dialConfig,
                            compassBearingDegrees = rideSensors.headingDegrees,
                            speedKmh = rideSensors.speedKmh,
                            routeAheadMeters = rideState?.routeAheadMeters ?: emptyList(),
                            onSettingsClick = { showSettings = true },
                            onDestinationClick = { showDestinationSearch = true },
                        )
                    }
                }
            }
        }

        if (locationPermissionGranted) {
            startRideSessionService()
        } else {
            requestLocationPermission.launch(permissionsToRequest())
        }
    }

    // Location is what gates the ride session (unchanged); the Phase D BLE permissions ride along
    // in the same prompt since there's no separate moment that needs them — BleLink just no-ops
    // if they end up denied (see BleLink.hasPermissions()). API 31+ only: below that, advertising
    // is covered by the legacy BLUETOOTH_ADMIN normal permission, already in the manifest.
    private fun permissionsToRequest(): Array<String> {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_ADVERTISE
            perms += Manifest.permission.BLUETOOTH_CONNECT
        }
        return perms.toTypedArray()
    }

    private fun startRideSessionService() {
        startService(Intent(this, RideSessionService::class.java))
        requestBatteryOptimizationExemptionOnce()
    }

    // Phase E — re-delivers to the already-running RideSessionService (startService on a running
    // service just calls onStartCommand again; no bind/messenger needed for one lat/lng pair).
    private fun startGuidanceTo(destination: GeocodeResult) {
        val intent = Intent(this, RideSessionService::class.java)
            .putExtra(RideSessionService.EXTRA_DEST_LAT, destination.lat.toFloat())
            .putExtra(RideSessionService.EXTRA_DEST_LNG, destination.lng.toFloat())
        startService(intent)
    }

    private var batteryExemptionPromptShown = false

    private fun requestBatteryOptimizationExemptionOnce() {
        if (batteryExemptionPromptShown || isIgnoringBatteryOptimizations()) return
        batteryExemptionPromptShown = true
        requestBatteryOptimizationExemption()
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationExemption() {
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"),
            ),
        )
    }
}

private val MotoNavColorScheme: ColorScheme = darkColorScheme(
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    primary = Color(0xFFFFD400),
)

@Composable
fun MotoNavTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MotoNavColorScheme, content = content)
}

@Composable
fun LocationPermissionRationaleScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "MotoNav needs your location to guide your ride. It's used only for turn-by-turn navigation.",
            fontSize = 20.sp,
        )
        Button(onClick = onContinue) { Text("Continue") }
    }
}

@Composable
fun LocationPermissionDeniedScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Location access was permanently denied. Open app settings to grant it.",
            fontSize = 20.sp,
        )
        Button(onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }) {
            Text("Open settings")
        }
    }
}

// The dial as MainActivity's default screen (MotoNav_TASK7_STAGED_PLAN.md stage 2 §3.2(d)),
// replacing the stage-1 debug screen.
@Composable
fun NavPageScreen(
    dialState: com.motonav.app.ui.dial.DialState,
    dialConfig: com.motonav.app.ui.dial.NavDialConfig,
    compassBearingDegrees: Float?,
    speedKmh: Float?,
    routeAheadMeters: List<com.motonav.app.nav.LocalOffsetMeters> = emptyList(),
    onSettingsClick: () -> Unit,
    onDestinationClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavDial(
            dialState = dialState,
            config = dialConfig,
            compassBearingDegrees = compassBearingDegrees,
            speedKmh = speedKmh,
            routeAheadMeters = routeAheadMeters,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        )
        Row(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
        ) {
            // Phase E — destination search entry point, next to the existing settings gear.
            IconButton(onClick = onDestinationClick) {
                Icon(Icons.Filled.Place, contentDescription = "Destination", tint = Color.White)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }
}
