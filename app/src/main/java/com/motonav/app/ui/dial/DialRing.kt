package com.motonav.app.ui.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

// The dial's only "chrome" (MotoNav_UI_SPEC.md §3.2) — solid/dashed/broken/dim are the sole visual
// signal for the whole state machine, so each pattern is deliberately distinct in both color and
// dash rhythm, not just brightness.
enum class RingStyle { SOLID, DASHED, BROKEN, DIM }

fun DrawScope.drawDialRing(style: RingStyle, color: Color) {
    val strokeWidth = size.minDimension / 2 * DialLayout.RING_STROKE_FRACTION
    val inset = size.minDimension / 2 * DialLayout.RING_INSET_FRACTION + strokeWidth / 2
    val ringRadius = size.minDimension / 2 - inset
    val center = Offset(size.width / 2, size.height / 2)

    val pathEffect = when (style) {
        RingStyle.SOLID, RingStyle.DIM -> null
        RingStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(ringRadius * 0.22f, ringRadius * 0.14f))
        // Irregular on/off run lengths (vs. DASHED's uniform rhythm) read as "broken," not "regular
        // pattern" — the same trick hazard tape uses to look distinct from a dashed lane line.
        RingStyle.BROKEN -> PathEffect.dashPathEffect(
            floatArrayOf(
                ringRadius * 0.38f,
                ringRadius * 0.08f,
                ringRadius * 0.10f,
                ringRadius * 0.20f,
            ),
        )
    }

    drawCircle(
        color = color,
        radius = ringRadius,
        center = center,
        style = Stroke(width = strokeWidth, pathEffect = pathEffect),
    )
}
