package com.motonav.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import uniffi.ferrostar.DrivingSide
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.RouteStep

/**
 * Plan §5.4 asks for fixture-based regression tests, named specifically after "roundabout
 * countdowns near preceding junctions" as the class of bug that's miserable to find on a bike and
 * trivial to catch on replay. This is that fixture: a short recorded route — a junction step
 * immediately followed by a roundabout step, then the exit — replayed point-by-point through
 * [forwardRouteGeometry] as [RouteReplayFixtureTest.currentStepGeometryIndex] would advance on a
 * real ride, asserting the forward slice never leaks a point behind the rider and rolls over to
 * the next step exactly at the step boundary.
 */
private fun step(name: String, vararg coords: Pair<Double, Double>): RouteStep = RouteStep(
    geometry = coords.map { GeographicCoordinate(it.first, it.second) },
    distance = 0.0,
    duration = 0.0,
    roadName = name,
    exits = emptyList(),
    instruction = "",
    visualInstructions = emptyList(),
    spokenInstructions = emptyList(),
    annotations = emptyList(),
    incidents = emptyList(),
    drivingSide = DrivingSide.RIGHT,
    roundaboutExitNumber = 0u,
)

class RouteReplayFixtureTest {

    // A junction close enough to a roundabout that the two maneuvers' geometry nearly touch —
    // exactly the layout that makes a countdown-then-roundabout glitch show up in the real world.
    private val junctionApproach = step("Approach Rd", 0.0 to 0.0000, 0.0 to 0.0004, 0.0 to 0.0008)
    private val roundabout = step("Roundabout", 0.0 to 0.0008, 0.0001 to 0.0009, 0.0002 to 0.0008)
    private val exit = step("Exit Rd", 0.0002 to 0.0008, 0.0003 to 0.0007)
    private val route = listOf(junctionApproach, roundabout, exit)

    @Test
    fun `replaying every geometry index of the junction step never yields a point behind the rider`() {
        for (index in junctionApproach.geometry.indices) {
            val slice = forwardRouteGeometry(route, currentStepGeometryIndex = index)
            assertFalse(
                "index $index leaked a behind-rider point",
                slice.any { it == junctionApproach.geometry.getOrNull(index - 1) },
            )
            assertEquals(junctionApproach.geometry[index], slice.first())
        }
    }

    @Test
    fun `advancing past the junction step's last index rolls straight into the roundabout step`() {
        // On a real ride, currentStepGeometryIndex resets to 0 and remainingSteps drops the
        // consumed step once Ferrostar advances — simulated here by dropping junctionApproach.
        val slice = forwardRouteGeometry(route.drop(1), currentStepGeometryIndex = 0)
        assertEquals(roundabout.geometry.first(), slice.first())
    }

    @Test
    fun `the roundabout-to-exit boundary is never skipped`() {
        val slice = forwardRouteGeometry(route.drop(1), currentStepGeometryIndex = roundabout.geometry.size - 1)
        // Last point of the roundabout step, then straight into the exit step's geometry.
        assertEquals(roundabout.geometry.last(), slice.first())
        assertEquals(exit.geometry.first(), slice[1])
    }
}
