package com.motonav.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder entry point. Real UI (arrow, distance, street name, ETA/speed —
 * see PRD "Display UI" requirements) replaces this once NavNotificationListenerService
 * is wired up to publish state the UI can observe.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MotoNavTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    IdleScreen()
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
