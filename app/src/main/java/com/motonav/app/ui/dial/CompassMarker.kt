package com.motonav.app.ui.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the north marker on the dial rim. [bearingDegrees] is the angle, clockwise from straight
 * up (0°), at which true north currently sits on the dial — stage 2 passes a hardcoded value;
 * stage 3 replaces it with the real fused-sensor heading. Always red per
 * MotoNav_TASK7_STAGED_PLAN.md stage 2 §3.2(b), regardless of style.
 */
fun DrawScope.drawCompassMarker(style: CompassStyle, bearingDegrees: Float) {
    if (style == CompassStyle.OFF) return

    val radius = size.minDimension / 2
    val markerRadius = radius * DialLayout.COMPASS_RADIUS_FRACTION
    val markerSize = radius * DialLayout.COMPASS_MARKER_SIZE_FRACTION
    val center = Offset(size.width / 2, size.height / 2)

    val theta = Math.toRadians(bearingDegrees.toDouble())
    val markerCenter = Offset(
        x = center.x + (markerRadius * sin(theta)).toFloat(),
        y = center.y - (markerRadius * cos(theta)).toFloat(),
    )

    when (style) {
        CompassStyle.DOT -> drawCircle(
            color = DialLayout.COMPASS_MARKER_COLOR,
            radius = markerSize / 2,
            center = markerCenter,
        )
        CompassStyle.TRIANGLE -> drawPath(
            path = outwardTrianglePath(markerCenter, markerSize, bearingDegrees),
            color = DialLayout.COMPASS_MARKER_COLOR,
        )
        CompassStyle.LETTER_N -> {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.RED
                textSize = markerSize
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val fontMetrics = paint.fontMetrics
            val verticalOffset = (fontMetrics.ascent + fontMetrics.descent) / 2
            drawContext.canvas.nativeCanvas.drawText(
                "N",
                markerCenter.x,
                markerCenter.y - verticalOffset,
                paint,
            )
        }
        CompassStyle.OFF -> Unit
    }
}

// A small triangle centered at [center], pointing outward along the bearing from the dial center —
// i.e. away from the dial's middle, matching blk_Journey_tracking.png's outward-pointing convention.
private fun outwardTrianglePath(center: Offset, size: Float, bearingDegrees: Float): Path {
    val theta = Math.toRadians(bearingDegrees.toDouble())
    val outward = Offset(sin(theta).toFloat(), -cos(theta).toFloat())
    val perpendicular = Offset(-outward.y, outward.x)
    val tip = Offset(center.x + outward.x * size / 2, center.y + outward.y * size / 2)
    val baseCenter = Offset(center.x - outward.x * size / 2, center.y - outward.y * size / 2)
    val baseLeft = Offset(
        baseCenter.x + perpendicular.x * size / 2,
        baseCenter.y + perpendicular.y * size / 2,
    )
    val baseRight = Offset(
        baseCenter.x - perpendicular.x * size / 2,
        baseCenter.y - perpendicular.y * size / 2,
    )
    return Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseLeft.x, baseLeft.y)
        lineTo(baseRight.x, baseRight.y)
        close()
    }
}
