package com.motonav.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motonav.app.ride.AutoLaunchMode
import com.motonav.app.ride.ScreenOnMode

@Composable
fun SettingsScreen(store: SettingsStore) {
    var autoLaunchMode by remember { mutableStateOf(store.autoLaunchMode) }
    var screenOnMode by remember { mutableStateOf(store.screenOnMode) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Auto-launch", fontSize = 22.sp)
        AutoLaunchMode.entries.forEach { mode ->
            OptionRow(mode.name, selected = mode == autoLaunchMode) {
                autoLaunchMode = mode
                store.autoLaunchMode = mode
            }
        }

        Text("Screen on", fontSize = 22.sp)
        ScreenOnMode.entries.forEach { mode ->
            OptionRow(mode.name, selected = mode == screenOnMode) {
                screenOnMode = mode
                store.screenOnMode = mode
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, fontSize = 18.sp)
    }
}
