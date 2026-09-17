package com.motonav.app.ui.dial

import androidx.compose.ui.graphics.Color

// Every dial position/size below is a fraction of the dial's *radius* (noted per-constant where a
// different base is used), never a fixed dp — this file is what the ESP32 firmware later
// transcribes to C, per MotoNav_TASK7_STAGED_PLAN.md §0.5. Keep all dial geometry here; nothing
// buried in composables.
object DialLayout {
    // Outer ring
    const val RING_STROKE_FRACTION = 0.045f // stroke width, fraction of radius
    const val RING_INSET_FRACTION = 0.03f // gap between ring and the dial's outer edge

    // Upper zone — maneuver icon. ~55% of the dial per blk_nav.png; offset is from dial center,
    // negative = above center.
    const val ICON_CENTER_Y_FRACTION = -0.30f
    const val ICON_SIZE_FRACTION = 0.85f // icon bounding box side, fraction of radius

    // Lower zone — distance numeral + unit. ~35% of the dial per blk_nav.png.
    const val DISTANCE_CENTER_Y_FRACTION = 0.34f
    const val DISTANCE_TEXT_SIZE_FRACTION = 0.46f // numeral font size, fraction of radius
    const val UNIT_CENTER_Y_FRACTION = 0.62f
    const val UNIT_TEXT_SIZE_FRACTION = 0.16f

    // Compass marker — sits just inside the outer ring.
    const val COMPASS_RADIUS_FRACTION = 0.84f // marker center distance from dial center
    const val COMPASS_MARKER_SIZE_FRACTION = 0.13f // marker glyph bounding box, fraction of radius

    // Speed readout — small, above the maneuver icon (blk_nav.png has no speed on this screen;
    // this slot is our own addition, kept deliberately minor so the maneuver/distance stay primary).
    const val SPEED_CENTER_Y_FRACTION = -0.72f
    const val SPEED_TEXT_SIZE_FRACTION = 0.20f

    // Speed-limit sign — reserved slot only, per MotoNav_TASK7_STAGED_PLAN.md stage 3(c). Upper
    // right per blk_nav.png's red-circle sign next to the distance numeral. No data source this
    // phase (needs the Roads API) — position kept here so stage 4+ has a real slot, not a guess.
    const val SPEED_LIMIT_CENTER_X_FRACTION = 0.42f
    const val SPEED_LIMIT_CENTER_Y_FRACTION = 0.10f
    const val SPEED_LIMIT_SIZE_FRACTION = 0.30f

    // Route line (Phase B) — schematic, heading-relative slice of the route ahead, per
    // docs/MotoNav_REBUILD_PLAN_OSM.md Phase B item 1/2. The current position/heading arrow is
    // pinned at ROUTE_LINE_ANCHOR_Y_FRACTION; ROUTE_LINE_LOOKAHEAD_METERS of real-world distance
    // ahead maps onto ROUTE_LINE_SPAN_FRACTION of the radius, so the line runs "up" (forward) from
    // the anchor. Cheap geometric primitives (a handful of line segments) — ESP32-transcribable,
    // unlike a bitmap.
    const val ROUTE_LINE_ANCHOR_Y_FRACTION = 0.80f
    const val ROUTE_LINE_SPAN_FRACTION = 1.6f
    const val ROUTE_LINE_LOOKAHEAD_METERS = 250f
    const val ROUTE_LINE_WIDTH_FRACTION = 0.035f

    // Idle-state centre logo
    const val LOGO_SIZE_FRACTION = 0.5f // fraction of dial *diameter*

    // Legibility floor (MotoNav_TASK7_STAGED_PLAN.md §0.5): below ~8% of dial diameter (~19px on a
    // 240px ESP32 panel) a glyph isn't glanceable on a moving bike. Sanity-check @Preview against
    // this, don't design under it.
    const val MIN_LEGIBLE_DIAMETER_FRACTION = 0.08f

    // Staleness threshold — starting guess per MotoNav_UI_SPEC.md §6.2, tune from real ride data.
    const val STALE_THRESHOLD_MS = 6_000L

    // Ring/marker colors. One accent (instrument yellow, matches MotoNavTheme.primary) carries
    // "normal" state; everything else is a deliberately different hue so the five dial states never
    // rely on brightness alone to read apart.
    val RING_ACTIVE_COLOR = Color(0xFFFFD400)
    val RING_REROUTING_COLOR = Color(0xFF7A93A8) // cool, neutral — Maps recalculating, not a fault
    val RING_STALE_COLOR = Color(0xFFE8402F) // alarm red — MotoNav itself lost the feed
    val RING_IDLE_COLOR = Color(0xFF3A3A3A)
    val COMPASS_MARKER_COLOR = Color(0xFFE8402F) // per stage-2 spec: "a red marker", full stop
    // Dim/neutral — the maneuver icon + ring carry the primary signal, the route line is context.
    val ROUTE_LINE_COLOR = Color(0xFF6E7B85)
}
