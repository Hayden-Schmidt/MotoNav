package com.motonav.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.motonav.app.location.RideSensorsStateHolder
import com.motonav.app.navsdk.NavSdkStateHolder
import com.motonav.app.ride.RideSessionService
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
    private var navApiErrorReason by mutableStateOf<String?>(null)

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
            locationPermissionGranted = granted
            if (granted) {
                navApiErrorReason = null
                proceedPastLocationPermission()
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
                    // Recomputed whenever settings closes, so toggles made there take effect
                    // immediately (SettingsStore is plain SharedPreferences, not itself a State).
                    val dialConfig = remember(showSettings) { settingsStore.navDialConfig() }
                    val navSdkUiState by NavSdkStateHolder.state.collectAsState()
                    val rideSensors by RideSensorsStateHolder.state.collectAsState()
                    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(500)
                            nowMs = System.currentTimeMillis()
                        }
                    }

                    LaunchedEffect(navSdkUiState, settingsStore.screenOnMode) {
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
                            onContinue = {
                                requestLocationPermission.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            },
                        )
                        navApiErrorReason != null -> NavApiErrorScreen(
                            reason = navApiErrorReason!!,
                            onRetry = { proceedPastLocationPermission() },
                        )
                        showSettings -> SettingsScreen(settingsStore)
                        else -> NavPageScreen(
                            dialState = deriveDialState(navSdkUiState, nowMs),
                            dialConfig = dialConfig,
                            compassBearingDegrees = rideSensors.headingDegrees,
                            speedKmh = rideSensors.speedKmh,
                            onSettingsClick = { showSettings = true },
                        )
                    }
                }
            }
        }

        if (locationPermissionGranted) {
            proceedPastLocationPermission()
        } else {
            requestLocationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    // Per MotoNav_TASK7_STAGED_PLAN.md §0.4: this is the one Activity-context Navigation SDK call
    // that should ever happen. It exists purely to force terms-of-use acceptance (the SDK shows
    // its dialog automatically here if not yet accepted) — the resulting Navigator reference is
    // discarded. RideSessionService acquires its own Navigator via the Application-context
    // overload once terms are already accepted.
    private fun proceedPastLocationPermission() {
        if (NavigationApi.areTermsAccepted(application)) {
            startRideSessionService()
            return
        }
        NavigationApi.getNavigator(
            this,
            object : NavigationApi.NavigatorListener {
                override fun onNavigatorReady(navigator: Navigator) {
                    navApiErrorReason = null
                    startRideSessionService()
                }

                override fun onError(errorCode: Int) {
                    navApiErrorReason = when (errorCode) {
                        NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> "You declined the navigation terms of use."
                        NavigationApi.ErrorCode.NOT_AUTHORIZED -> "API key is invalid or not authorized."
                        NavigationApi.ErrorCode.NETWORK_ERROR -> "Network error contacting Google Navigation."
                        NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING -> "Location permission missing."
                        else -> "Unknown Navigation SDK error ($errorCode)."
                    }
                }
            },
        )
    }

    private fun startRideSessionService() {
        startService(Intent(this, RideSessionService::class.java))
        requestBatteryOptimizationExemptionOnce()
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

@Composable
fun NavApiErrorScreen(reason: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Navigation SDK error", fontSize = 24.sp)
        Text(reason, fontSize = 18.sp)
        Button(onClick = onRetry) { Text("Retry") }
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
    onSettingsClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavDial(
            dialState = dialState,
            config = dialConfig,
            compassBearingDegrees = compassBearingDegrees,
            speedKmh = speedKmh,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        )
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }
}
