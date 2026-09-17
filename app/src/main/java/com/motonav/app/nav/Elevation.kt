package com.motonav.app.nav

import uniffi.ferrostar.GeographicCoordinate

/**
 * Phase C item 4 — the request/response glue for valhalla-mobile's `height` action, kept as pure
 * string logic (no org.json — see docs/MotoNav_REBUILD_PLAN_OSM.md Phase C and
 * ride/LocalRouteProvider.kt) so it's unit-testable without a device. The engine call itself
 * ([com.motonav.app.ride.LocalValhallaRouteProvider.sampleElevation]) needs the native Valhalla
 * actor and can't run outside the app.
 */
fun buildValhallaHeightRequestJson(shape: List<GeographicCoordinate>): String {
    require(shape.isNotEmpty()) { "Need at least one point to sample" }
    val points = shape.joinToString(",") { "{\"lat\":${it.lat},\"lon\":${it.lng}}" }
    return "{\"shape\":[$points],\"range\":false}"
}

/**
 * Valhalla's height response is `{"shape":[...], "height":[12.3, null, ...]}` — an entry is null
 * wherever the config has no elevation tile for that point (see valhalla-mobile's README
 * "Elevation" section). Regex over a small, fixed shape rather than a JSON parser for the same
 * device-independent-testability reason as [buildValhallaHeightRequestJson].
 */
fun parseValhallaHeights(responseJson: String): List<Double?> {
    val heightsSection = Regex("\"height\"\\s*:\\s*\\[([^]]*)]").find(responseJson)?.groupValues?.get(1)
        ?: return emptyList()
    if (heightsSection.isBlank()) return emptyList()
    return heightsSection.split(",").map { it.trim() }.map { if (it == "null") null else it.toDouble() }
}
