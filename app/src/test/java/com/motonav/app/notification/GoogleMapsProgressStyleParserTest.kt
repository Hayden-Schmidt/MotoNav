package com.motonav.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleMapsProgressStyleParserTest {

    @Test
    fun `null title yields no NavState`() {
        assertNull(GoogleMapsProgressStyleParser.buildNavState(null, "Arrive 03:22", "60", 0, 3049))
    }

    @Test
    fun `parses title, eta, remaining and distance-to-turn`() {
        val state = GoogleMapsProgressStyleParser.buildNavState(
            title = "60 U-turn",
            subText = "Arrive 03:53",
            shortCriticalText = "60",
            progress = 0,
            progressMax = 3052,
        )
        requireNotNull(state)
        assertEquals("60 U-turn", state.maneuverText)
        assertEquals("Arrive 03:53", state.etaText)
        assertEquals(3052.0, state.remainingDistanceMeters!!, 0.001)
        assertEquals(60.0, state.distanceToTurnMeters!!, 0.001)
        assertEquals(ManeuverType.U_TURN, state.maneuverType)
    }

    @Test
    fun `invalid progress range leaves remaining distance null`() {
        val state = GoogleMapsProgressStyleParser.buildNavState("Turn right onto Main St", null, null, -1, -1)
        requireNotNull(state)
        assertNull(state.remainingDistanceMeters)
        assertNull(state.distanceToTurnMeters)
    }
}
