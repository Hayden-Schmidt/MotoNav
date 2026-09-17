package com.motonav.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motonav.app.ui.dial.CompassStyle
import com.motonav.app.ride.AutoLaunchMode
import com.motonav.app.ride.LocalTileFiles
import com.motonav.app.ride.OfflineTileDownloader
import com.motonav.app.ride.RouterBackend
import com.motonav.app.ride.ScreenOnMode
import com.motonav.app.ride.TileDownloadProgress
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(store: SettingsStore) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var tileDownloadProgress by remember { mutableStateOf<TileDownloadProgress?>(null) }
    var tilesReady by remember {
        mutableStateOf(context.getExternalFilesDir(null)?.let { LocalTileFiles.tilesReady(it) } ?: false)
    }
    var autoLaunchMode by remember { mutableStateOf(store.autoLaunchMode) }
    var screenOnMode by remember { mutableStateOf(store.screenOnMode) }
    var showCompass by remember { mutableStateOf(store.navShowCompass) }
    var compassStyle by remember { mutableStateOf(store.navCompassStyle) }
    var showSpeed by remember { mutableStateOf(store.navShowSpeed) }
    var showSpeedLimit by remember { mutableStateOf(store.navShowSpeedLimit) }
    var showEta by remember { mutableStateOf(store.navShowEta) }
    var showDistanceRemaining by remember { mutableStateOf(store.navShowDistanceRemaining) }
    var showStreetName by remember { mutableStateOf(store.navShowStreetName) }
    var showRouteLine by remember { mutableStateOf(store.navShowRouteLine) }
    var routerBackend by remember { mutableStateOf(store.routerBackend) }
    var routerApiKey by remember { mutableStateOf(store.routerApiKey) }
    var geocoderBaseUrl by remember { mutableStateOf(store.geocoderBaseUrl) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Routing backend", fontSize = 22.sp)
        RouterBackend.entries.forEach { backend ->
            OptionRow(backend.name, selected = backend == routerBackend) {
                routerBackend = backend
                store.routerBackend = backend
            }
        }
        if (routerBackend == RouterBackend.STADIA_MAPS) {
            OutlinedTextField(
                value = routerApiKey,
                onValueChange = { routerApiKey = it; store.routerApiKey = it },
                label = { Text("Stadia Maps API key") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (routerBackend == RouterBackend.LOCAL) {
            Text(if (tilesReady) "Offline NZ tiles: downloaded" else "Offline NZ tiles: not downloaded", fontSize = 14.sp)
            when (val progress = tileDownloadProgress) {
                is TileDownloadProgress.InProgress -> {
                    val fraction = if (progress.totalBytes > 0) {
                        (progress.bytesRead.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text("${progress.bytesRead / 1_000_000} MB / ${progress.totalBytes / 1_000_000} MB", fontSize = 12.sp)
                }
                is TileDownloadProgress.Failed -> Text("Download failed: ${progress.message}", fontSize = 12.sp)
                TileDownloadProgress.Done, null -> Unit
            }
            Button(
                enabled = tileDownloadProgress !is TileDownloadProgress.InProgress,
                onClick = {
                    tileDownloadProgress = null
                    coroutineScope.launch {
                        OfflineTileDownloader.downloadNzTiles(context) { progress ->
                            tileDownloadProgress = progress
                            if (progress is TileDownloadProgress.Done) {
                                tilesReady = context.getExternalFilesDir(null)?.let { LocalTileFiles.tilesReady(it) } ?: false
                            }
                        }
                    }
                },
            ) {
                Text(if (tilesReady) "Re-download offline NZ data" else "Download offline NZ data")
            }
        }

        Text("Geocoding", fontSize = 22.sp)
        OutlinedTextField(
            value = geocoderBaseUrl,
            onValueChange = { geocoderBaseUrl = it; store.geocoderBaseUrl = it },
            label = { Text("Geocoder base URL") },
            modifier = Modifier.fillMaxWidth(),
        )

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

        Text("Nav page elements", fontSize = 22.sp)
        ToggleRow("Compass", showCompass) { showCompass = it; store.navShowCompass = it }
        if (showCompass) {
            CompassStyle.entries.forEach { style ->
                OptionRow("  ${style.name}", selected = style == compassStyle) {
                    compassStyle = style
                    store.navCompassStyle = style
                }
            }
        }
        ToggleRow("Speed", showSpeed) { showSpeed = it; store.navShowSpeed = it }
        ToggleRow("Speed limit", showSpeedLimit) { showSpeedLimit = it; store.navShowSpeedLimit = it }
        ToggleRow("ETA", showEta) { showEta = it; store.navShowEta = it }
        ToggleRow("Distance remaining", showDistanceRemaining) {
            showDistanceRemaining = it
            store.navShowDistanceRemaining = it
        }
        ToggleRow("Street name", showStreetName) { showStreetName = it; store.navShowStreetName = it }
        ToggleRow("Route line", showRouteLine) { showRouteLine = it; store.navShowRouteLine = it }

        Text("About", fontSize = 22.sp)
        Text("Map and routing data © OpenStreetMap contributors, ODbL", fontSize = 14.sp)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 18.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
