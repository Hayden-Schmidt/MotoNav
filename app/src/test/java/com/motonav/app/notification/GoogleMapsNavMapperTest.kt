package com.motonav.app.notification

import me.trevi.navparser.lib.DistanceUnit
import me.trevi.navparser.lib.NavigationData
import me.trevi.navparser.lib.NavigationDirection
import me.trevi.navparser.lib.NavigationDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleMapsNavMapperTest {

    @Test
    fun `derives right turn from locale text`() {
        assertEquals(ManeuverType.RIGHT, GoogleMapsNavMapper.deriveManeuverType("Turn right onto Main St"))
    }

    @Test
    fun `derives roundabout over generic right match`() {
        assertEquals(
            ManeuverType.ROUNDABOUT,
            GoogleMapsNavMapper.deriveManeuverType("At the roundabout, take the right exit"),
        )
    }

    @Test
    fun `unmatched text falls back to UNKNOWN`() {
        assertEquals(ManeuverType.UNKNOWN, GoogleMapsNavMapper.deriveManeuverType("Rerouting"))
    }

    @Test
    fun `extracts street name after onto`() {
        assertEquals("Main St", GoogleMapsNavMapper.extractStreetName("Turn right onto Main St"))
    }

    @Test
    fun `street name is null when phrasing has no onto-on clause`() {
        assertNull(GoogleMapsNavMapper.extractStreetName("Rerouting"))
    }

    @Test
    fun `maps NavigationData with km distance to meters`() {
        val data = navigationData(distance = 0.4, unit = DistanceUnit.KM)
        assertEquals(400.0, GoogleMapsNavMapper.toNavState(data).distanceToTurnMeters!!, 0.001)
    }

    private fun navigationData(distance: Double, unit: DistanceUnit): NavigationData = NavigationData(
        isRerouting = false,
        canStop = false,
        nextDirection = NavigationDirection(
            localeString = "Turn right onto Main St",
            localeHtml = null,
            navigationDistance = NavigationDistance(localeString = "", distance = distance, unit = unit),
        ),
        remainingDistance = NavigationDistance(localeString = "", distance = 0.0, unit = DistanceUnit.M),
    )
}
