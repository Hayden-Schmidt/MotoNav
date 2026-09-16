package com.motonav.app

import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import com.motonav.app.notification.NavState
import com.motonav.app.notification.NavStateHolder
import com.motonav.app.notification.icon
import com.motonav.app.ride.shouldKeepScreenOn
import com.motonav.app.settings.SettingsScreen
import com.motonav.app.settings.SettingsStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsStore = SettingsStore(this)
        setContent {
            MotoNavTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showSettings by remember { mutableStateOf(false) }
                    val navState by NavStateHolder.state.collectAsState()

                    LaunchedEffect(navState, settingsStore.screenOnMode) {
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
                        !isNotificationAccessGranted() -> PermissionRequestScreen()
                        showSettings -> SettingsScreen(settingsStore)
                        navState == null -> IdleScreen(onSettingsClick = { showSettings = true })
                        else -> NavScreen(navState!!, onSettingsClick = { showSettings = true })
                    }
                }
            }
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return enabledListeners.contains(packageName)
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
fun PermissionRequestScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "MotoNav needs notification access to read turn-by-turn directions from Google Maps.",
            fontSize = 20.sp,
        )
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) {
            Text("Open settings")
        }
    }
}

@Composable
fun IdleScreen(onSettingsClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Waiting for navigation…", fontSize = 28.sp)
        }
        IconButton(onClick = onSettingsClick, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
fun NavScreen(state: NavState, onSettingsClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                state.maneuverType.icon(),
                contentDescription = state.maneuverText,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                formatDistance(state.distanceToTurnMeters),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(state.streetName ?: "", fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(state.etaText ?: "", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "${formatDistance(state.remainingDistanceMeters)} remaining",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = onSettingsClick, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }
}

private fun formatDistance(meters: Double?): String =
    if (meters == null) "—" else "${meters.toInt()} m"
