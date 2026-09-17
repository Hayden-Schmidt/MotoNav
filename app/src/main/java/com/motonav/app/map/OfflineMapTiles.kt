package com.motonav.app.map

import android.content.Context
import java.io.File

/**
 * Phase E item 2 — the local-PMTiles loading path for the route-selection map. No PMTiles data is
 * bundled or downloaded by this app (a full-planet basemap is ~120GB; an NZ cutout is still large)
 * — see docs/MotoNav_OFFLINE_MAP.md for how a user side-loads one. This class only knows the file
 * name and builds the `pmtiles://file://` URL MapLibre Native needs.
 *
 * `pmtiles://asset://` is unsupported by MapLibre Native — only `pmtiles://file://` against a
 * real filesystem path works, which is why this reads from [Context.getExternalFilesDir] rather
 * than bundling into `assets/`. See plan Phase E item 2.
 */
object OfflineMapTiles {
    const val FILE_NAME = "nz.pmtiles"

    fun file(context: Context): File = File(context.getExternalFilesDir(null), FILE_NAME)

    fun isInstalled(context: Context): Boolean = file(context).isFile

    fun pmtilesUrl(context: Context): String = pmtilesUrlForFile(file(context))

    fun styleJson(context: Context): String = styleJsonForSource(pmtilesUrl(context))
}

// Pure, Context-free half of the above — the part worth a JVM unit test (see
// app/src/test/java/com/motonav/app/map/OfflineMapTilesTest.kt). getExternalFilesDir needs a real
// Android Context, so the two file() wrappers above stay thin callers of these.
fun pmtilesUrlForFile(file: File): String = "pmtiles://file://${file.absolutePath}"

/**
 * A minimal MapLibre style referencing the local PMTiles source, styled against the Protomaps
 * "basemap" source-layer schema (the plan's chosen PMTiles archive format — plan §0.5). This is
 * deliberately plain: legibility for route selection, not a cartographic clone of Google Maps.
 * Layers silently no-op (draw nothing) if the side-loaded file uses a different schema — see
 * docs/MotoNav_OFFLINE_MAP.md.
 */
fun styleJsonForSource(source: String): String {
    return """
        {
          "version": 8,
          "sources": {
            "nz": {"type": "vector", "url": "$source"}
          },
          "layers": [
            {"id": "background", "type": "background", "paint": {"background-color": "#141414"}},
            {"id": "water", "type": "fill", "source": "nz", "source-layer": "water",
             "paint": {"fill-color": "#1c2b3a"}},
            {"id": "landuse", "type": "fill", "source": "nz", "source-layer": "landuse",
             "paint": {"fill-color": "#1e241b"}},
            {"id": "roads", "type": "line", "source": "nz", "source-layer": "roads",
             "paint": {"line-color": "#8a8a8a", "line-width": 1.4}},
            {"id": "buildings", "type": "fill", "source": "nz", "source-layer": "buildings",
             "paint": {"fill-color": "#2a2a2a"}},
            {"id": "boundaries", "type": "line", "source": "nz", "source-layer": "boundaries",
             "paint": {"line-color": "#555555", "line-dasharray": [2, 2]}},
            {"id": "places", "type": "symbol", "source": "nz", "source-layer": "places",
             "layout": {"text-field": ["get", "name"], "text-size": 12},
             "paint": {"text-color": "#e0e0e0"}}
          ]
        }
        """.trimIndent()
}
