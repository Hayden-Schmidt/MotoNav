package com.motonav.app.map

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.motonav.app.nav.GeocodeResult
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Phase E item 2 — route-selection map, MapLibre Native rendering local PMTiles via `pmtiles://`.
 * Selection only, not a live nav surface (the dial owns that) — see plan §1.3 / Phase E.
 *
 * No basemap tiles ship with the app (plan explicitly rules this out — a usable cutout is still
 * hundreds of MB to GB). If [OfflineMapTiles.isInstalled] is false this still renders — just a
 * dark background with the destination pin fixed at screen centre — rather than blocking route
 * confirmation on a side-loaded file existing. See docs/MotoNav_OFFLINE_MAP.md.
 *
 * The pin is a fixed Compose overlay at the box centre, not a geo-anchored map annotation: the
 * camera is centred on [destination] below, so screen-centre IS the destination, and adding the
 * MapLibre annotation plugin dependency just to draw one static pin isn't justified here.
 * ponytail: if a second/movable pin is ever needed (e.g. drag-to-adjust), add the annotation
 * plugin then — not speculatively now.
 */
@Composable
fun RouteSelectionMapScreen(
    destination: GeocodeResult,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(destination.lat, destination.lng))
                    .zoom(13.0)
                    .build()
                map.setStyle(Style.Builder().fromJson(OfflineMapTiles.styleJson(context)))
            }
        }
    }
    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Icon(
            Icons.Filled.Place,
            contentDescription = "Destination",
            tint = Color(0xFFFFD400),
            modifier = Modifier.align(Alignment.Center),
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        if (!OfflineMapTiles.isInstalled(context)) {
            Text(
                "No offline map installed — see docs/MotoNav_OFFLINE_MAP.md",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp),
            )
        }

        // Layout language from screenshot_7.png (dark overlay bar, ETA + distance) — reference,
        // not a pixel clone. This bar confirms the destination rather than showing live progress.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(destination.label, color = Color.White, fontSize = 18.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onConfirm) { Text("Start guidance") }
            }
        }
    }
}
