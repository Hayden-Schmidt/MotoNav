package com.motonav.app.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motonav.app.nav.GeocodeResult
import com.motonav.app.ride.geocode
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/**
 * Phase E item 1 — destination search over a Photon-compatible geocoder. Layout language from
 * moto_app_planning.webp (back arrow, single search field, pin+label result rows) — reference,
 * not a pixel clone.
 */
@Composable
fun DestinationSearchScreen(
    geocoderBaseUrl: String,
    onBack: () -> Unit,
    onResultSelected: (GeocodeResult) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<GeocodeResult>()) }
    var searchFailed by remember { mutableStateOf(false) }
    val client = remember { OkHttpClient() }

    // Debounced search — no search-as-you-type flood of the (fair-use, rate-limited) demo
    // endpoint. 400ms is a plain guess, not tuned against a real device; see plan Phase E item 1.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            searchFailed = false
        } else {
            delay(400)
            val outcome = runCatching { geocode(client, geocoderBaseUrl, query) }
            results = outcome.getOrDefault(emptyList())
            searchFailed = outcome.isFailure
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Destination") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            )
        }

        if (searchFailed) {
            Text(
                "Search failed — check your network or geocoder setting",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results) { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResultSelected(result) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Place, contentDescription = null)
                    Text(result.label, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}
