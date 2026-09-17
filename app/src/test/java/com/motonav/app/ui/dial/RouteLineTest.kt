package com.motonav.app.ui.dial

import com.motonav.app.nav.LocalOffsetMeters
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteLineTest {

    @Test
    fun `heading zero maps north offsets straight up`() {
        val points = headingRelativeRoutePoints(
            listOf(LocalOffsetMeters(east = 0.0, north = 100.0)),
            headingDegrees = 0f,
            lookaheadMeters = 100f,
        )
        assertEquals(0f, points[0].first, 1e-4f)
        assertEquals(-1f, points[0].second, 1e-4f) // "up" on screen is negative y
    }

    @Test
    fun `heading east rotates an eastward offset to straight up`() {
        val points = headingRelativeRoutePoints(
            listOf(LocalOffsetMeters(east = 100.0, north = 0.0)),
            headingDegrees = 90f,
            lookaheadMeters = 100f,
        )
        assertEquals(0f, points[0].first, 1e-3f)
        assertEquals(-1f, points[0].second, 1e-3f)
    }

    @Test
    fun `a point behind the heading maps below the anchor`() {
        val points = headingRelativeRoutePoints(
            listOf(LocalOffsetMeters(east = 0.0, north = -50.0)),
            headingDegrees = 0f,
            lookaheadMeters = 100f,
        )
        assertEquals(0.5f, points[0].second, 1e-4f)
    }
}
