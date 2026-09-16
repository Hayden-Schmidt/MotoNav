package com.motonav.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.motonav.app.notification.NavState
import com.motonav.app.notification.NavStateHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MotoNavTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navState by NavStateHolder.state.collectAsState()
                    val state = navState
                    if (state == null) IdleScreen() else NavScreen(state)
                }
            }
        }
    }
}

@Composable
fun MotoNavTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
fun IdleScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Waiting for navigation…")
    }
}

@Composable
fun NavScreen(state: NavState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(state.maneuverText ?: "—", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(formatDistance(state.distanceToTurnMeters), fontSize = 72.sp, fontWeight = FontWeight.Bold)
        Text(state.streetName ?: "", fontSize = 28.sp)
        Text(state.etaText ?: "", fontSize = 24.sp)
        Text("${formatDistance(state.remainingDistanceMeters)} remaining", fontSize = 24.sp)
    }
}

private fun formatDistance(meters: Double?): String =
    if (meters == null) "—" else "${meters.toInt()} m"
