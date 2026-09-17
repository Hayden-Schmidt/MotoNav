package com.motonav.app.location

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassMathTest {

    @Test
    fun `declination correction wraps into 0-360`() {
        assertEquals(20f, trueHeadingDeg(0f, 20f), 0.001f)
        assertEquals(0f, trueHeadingDeg(340f, 20f), 0.001f)
        assertEquals(350f, trueHeadingDeg(10f, -20f), 0.001f)
    }

    @Test
    fun `smoothing across the 0-360 boundary moves the short way`() {
        // 359 -> 1 is a 2 degree step forward, not a 358 degree swing backward through 180.
        // Result lands near 0/360, which are the same angle — normalize before comparing.
        val result = smoothAngleDeg(previousDeg = 359f, nextDeg = 1f, alpha = 0.5f) % 360f
        val distanceFromZero = minOf(result, 360f - result)
        assertEquals(0f, distanceFromZero, 0.5f)
    }

    @Test
    fun `smoothing with no previous value returns the new reading unchanged`() {
        assertEquals(123f, smoothAngleDeg(previousDeg = null, nextDeg = 123f), 0.001f)
    }
}
