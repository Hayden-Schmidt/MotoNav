package com.motonav.app.nav

/**
 * Phase E item 1 — parses a Photon-compatible geocoder's GeoJSON response. Regex over the small,
 * well-known response shape rather than a JSON library, same call as Elevation.kt: keeps this a
 * pure-JVM unit test with no device/Robolectric dependency. The HTTP call itself lives in
 * ride/Geocoding.kt (needs OkHttp + a live endpoint, so it isn't unit-tested the same way — same
 * split as ride/LocalRouteProvider.kt vs this file).
 */
data class GeocodeResult(
    val label: String,
    val lat: Double,
    val lng: Double,
)

/**
 * Photon's response is `{"features":[{"geometry":{"coordinates":[lon,lat]},"properties":{"name":
 * ...,"city":...,"country":...}},...]}`. Split on each feature's `"type":"Feature"` marker rather
 * than a full recursive-descent parse — good enough for a fixed, known response shape.
 */
fun parsePhotonResults(responseJson: String): List<GeocodeResult> {
    val chunks = responseJson.split("\"type\":\"Feature\"").drop(1)
    return chunks.mapNotNull { chunk ->
        val coords = Regex("\"coordinates\"\\s*:\\s*\\[\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)").find(chunk)
            ?: return@mapNotNull null
        val lng = coords.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
        val lat = coords.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
        val name = Regex("\"name\"\\s*:\\s*\"([^\"]*)\"").find(chunk)?.groupValues?.get(1)
        val city = Regex("\"city\"\\s*:\\s*\"([^\"]*)\"").find(chunk)?.groupValues?.get(1)
        val country = Regex("\"country\"\\s*:\\s*\"([^\"]*)\"").find(chunk)?.groupValues?.get(1)
        val label = listOfNotNull(name, city, country).distinct().joinToString(", ")
        if (label.isBlank()) null else GeocodeResult(label, lat, lng)
    }
}
