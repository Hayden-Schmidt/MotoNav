package com.motonav.app.nav

import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.RouteStep
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A point's offset from some origin, in metres east/north on a local flat plane. Pure data, no
 * Android/Compose dependency — the dial rotates/scales this to screen fractions (ui/dial/RouteLine.kt),
 * per docs/MotoNav_REBUILD_PLAN_OSM.md Phase B.
 */
data class LocalOffsetMeters(val east: Double, val north: Double)

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Equirectangular projection of [points] onto a flat plane centered at [origin], in metres
 * east/north. Fine for the few-hundred-metre forward slice this exists for (Phase B's schematic
 * route line) — accuracy degrades over kilometres, which is not our use case.
 */
fun projectToLocalMeters(origin: GeographicCoordinate, points: List<GeographicCoordinate>): List<LocalOffsetMeters> {
    val originLatRad = Math.toRadians(origin.lat)
    return points.map { p ->
        LocalOffsetMeters(
            east = Math.toRadians(p.lng - origin.lng) * EARTH_RADIUS_METERS * cos(originLatRad),
            north = Math.toRadians(p.lat - origin.lat) * EARTH_RADIUS_METERS,
        )
    }
}

fun haversineMeters(a: GeographicCoordinate, b: GeographicCoordinate): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val sinDLat = sin(dLat / 2)
    val sinDLng = sin(dLng / 2)
    val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLng * sinDLng
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/**
 * Forward-looking slice of the route ahead of the current position — the remainder of the current
 * step's geometry (from [currentStepGeometryIndex] on) followed by later steps' geometry in full,
 * capped at [maxDistanceMeters] / [maxPoints]. Phase B item 1's "forward slice only": nothing
 * behind the rider, no whole-route squiggle.
 */
fun forwardRouteGeometry(
    remainingSteps: List<RouteStep>,
    currentStepGeometryIndex: Int,
    maxDistanceMeters: Double = 250.0,
    maxPoints: Int = 60,
): List<GeographicCoordinate> {
    val points = mutableListOf<GeographicCoordinate>()
    remainingSteps.forEachIndexed { stepIndex, step ->
        val geometry = step.geometry
        val startIndex = if (stepIndex == 0) currentStepGeometryIndex.coerceIn(0, geometry.size) else 0
        points.addAll(geometry.subList(startIndex, geometry.size))
    }
    if (points.isEmpty()) return points

    val result = mutableListOf(points.first())
    var cumulative = 0.0
    for (i in 1 until points.size) {
        if (result.size >= maxPoints) break
        cumulative += haversineMeters(points[i - 1], points[i])
        result.add(points[i])
        if (cumulative >= maxDistanceMeters) break
    }
    return result
}
