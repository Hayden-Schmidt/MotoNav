package com.motonav.app.ui.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.motonav.app.nav.LocalOffsetMeters
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rotates each metre offset by [headingDegrees] so "forward" (the direction of travel) maps to
 * screen-up, then scales real-world metres down to dial-radius fractions ([lookaheadMeters] maps
 * onto one radius fraction). Phase B item 1: heading-relative, current-position-pinned schematic —
 * see docs/MotoNav_REBUILD_PLAN_OSM.md Phase B. Pure math, no Compose drawing types, so it's
 * unit-testable without a device (see RouteLineTest).
 */
fun headingRelativeRoutePoints(
    offsets: List<LocalOffsetMeters>,
    headingDegrees: Float,
    lookaheadMeters: Float = DialLayout.ROUTE_LINE_LOOKAHEAD_METERS,
): List<Pair<Float, Float>> {
    if (lookaheadMeters <= 0f) return emptyList()
    val headingRad = Math.toRadians(headingDegrees.toDouble())
    val sinH = sin(headingRad)
    val cosH = cos(headingRad)
    return offsets.map { o ->
        // Rotate the east/north vector by -heading: "forward" (along heading) becomes screen-up
        // (negative y), "right of heading" becomes screen-right (positive x).
        val forward = o.north * cosH + o.east * sinH
        val rightward = o.east * cosH - o.north * sinH
        (rightward / lookaheadMeters).toFloat() to (-forward / lookaheadMeters).toFloat()
    }
}

// Current position/heading arrow is pinned at DialLayout.ROUTE_LINE_ANCHOR_Y_FRACTION; the line
// is a handful of connected segments — cheap on an ESP32, unlike a bitmap. The Surface hosting
// this Canvas already clips to CircleShape, so points beyond the dial edge are simply cut off,
// which is the intended "forward slice" look.
fun DrawScope.drawRouteLine(offsets: List<LocalOffsetMeters>, headingDegrees: Float) {
    if (offsets.size < 2) return
    val radius = size.minDimension / 2
    val anchor = Offset(
        size.width / 2,
        size.height / 2 + radius * DialLayout.ROUTE_LINE_ANCHOR_Y_FRACTION,
    )
    val points = headingRelativeRoutePoints(offsets, headingDegrees)

    val path = Path()
    points.forEachIndexed { index, (x, y) ->
        val screen = Offset(
            anchor.x + x * radius * DialLayout.ROUTE_LINE_SPAN_FRACTION,
            anchor.y + y * radius * DialLayout.ROUTE_LINE_SPAN_FRACTION,
        )
        if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }
    drawPath(
        path = path,
        color = DialLayout.ROUTE_LINE_COLOR,
        style = Stroke(width = radius * DialLayout.ROUTE_LINE_WIDTH_FRACTION),
    )
}
