package com.motonav.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.DrivingSide

private fun step(vararg coords: Pair<Double, Double>): RouteStep = RouteStep(
    geometry = coords.map { GeographicCoordinate(it.first, it.second) },
    distance = 0.0,
    duration = 0.0,
    roadName = "",
    exits = emptyList(),
    instruction = "",
    visualInstructions = emptyList(),
    spokenInstructions = emptyList(),
    annotations = emptyList(),
    incidents = emptyList(),
    drivingSide = DrivingSide.RIGHT,
    roundaboutExitNumber = 0u,
)

class RouteGeometryTest {

    @Test
    fun `projecting the origin onto itself is zero offset`() {
        val origin = GeographicCoordinate(-36.8485, 174.7633) // Auckland
        val offsets = projectToLocalMeters(origin, listOf(origin))
        assertEquals(0.0, offsets[0].east, 1e-6)
        assertEquals(0.0, offsets[0].north, 1e-6)
    }

    @Test
    fun `moving one degree north is roughly 111km north, zero east`() {
        val origin = GeographicCoordinate(0.0, 0.0)
        val offsets = projectToLocalMeters(origin, listOf(GeographicCoordinate(1.0, 0.0)))
        assertEquals(0.0, offsets[0].east, 1.0)
        assertTrue(offsets[0].north in 110_000.0..112_000.0)
    }

    @Test
    fun `forward slice starts at the current geometry index within the current step`() {
        val current = step(0.0 to 0.0, 0.0 to 0.001, 0.0 to 0.002)
        val next = step(0.0 to 0.002, 0.0 to 0.003)
        val slice = forwardRouteGeometry(listOf(current, next), currentStepGeometryIndex = 1)
        // Skips index 0 of the current step, keeps all of the next step.
        assertEquals(3, slice.size)
        assertEquals(0.001, slice[0].lng, 1e-9)
    }

    @Test
    fun `forward slice is capped by max distance`() {
        // Each step is ~111m of longitude at the equator (0.001 deg); five steps is ~555m.
        val longSteps = (0..5).map { step(0.0 to it * 0.001, 0.0 to (it + 1) * 0.001) }
        val slice = forwardRouteGeometry(longSteps, currentStepGeometryIndex = 0, maxDistanceMeters = 250.0)
        val totalMeters = (1 until slice.size).sumOf { haversineMeters(slice[it - 1], slice[it]) }
        assertTrue("expected slice to stop near the 250m cap, got $totalMeters", totalMeters < 350.0)
    }

    @Test
    fun `forward slice on empty steps is empty`() {
        assertEquals(0, forwardRouteGeometry(emptyList(), 0).size)
    }
}
