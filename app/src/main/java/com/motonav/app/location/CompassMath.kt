package com.motonav.app.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Pure, testable math for compass heading — kept separate from CompassTracker's sensor plumbing
// so the two things stage 3 flags as non-trivial (declination correction, angular-wrap smoothing)
// have a runnable check (see CompassMathTest) without needing a device or a sensor mock.

/**
 * Corrects a magnetometer-derived azimuth to true north using magnetic declination.
 * Auckland's declination is ~20°E — this is mandatory, not a refinement (see stage 3 plan §4.1).
 */
fun trueHeadingDeg(magneticAzimuthDeg: Float, declinationDeg: Float): Float {
    val corrected = (magneticAzimuthDeg + declinationDeg) % 360f
    return if (corrected < 0f) corrected + 360f else corrected
}

/**
 * Exponential smoothing across the 0/360 wrap: averages the sin/cos components rather than the
 * angles directly, so 359° -> 1° moves 2° forward instead of swinging back through 180°.
 */
fun smoothAngleDeg(previousDeg: Float?, nextDeg: Float, alpha: Float = 0.25f): Float {
    if (previousDeg == null) return nextDeg
    val prevRad = Math.toRadians(previousDeg.toDouble())
    val nextRad = Math.toRadians(nextDeg.toDouble())
    val sinAvg = (1 - alpha) * sin(prevRad) + alpha * sin(nextRad)
    val cosAvg = (1 - alpha) * cos(prevRad) + alpha * cos(nextRad)
    val deg = Math.toDegrees(atan2(sinAvg, cosAvg)).toFloat()
    return if (deg < 0f) deg + 360f else deg
}
