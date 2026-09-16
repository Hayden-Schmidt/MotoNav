package com.motonav.app.notification

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
}
