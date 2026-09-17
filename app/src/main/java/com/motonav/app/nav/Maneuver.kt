package com.motonav.app.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.ui.graphics.vector.ImageVector
import uniffi.ferrostar.ManeuverModifier
import uniffi.ferrostar.ManeuverType

/**
 * Bucketed, UI-facing maneuver set — the dial only has room for one dominant directional glyph
 * (see docs/MotoNav_UI_SPEC.md). Was navsdk/Maneuver.kt, mapping Google's 62-constant `Maneuver`
 * int down to this set; per docs/MotoNav_REBUILD_PLAN_OSM.md §1.3 the bucketing concept survives
 * the Navigation SDK -> Ferrostar swap unchanged, only [fromFerrostarManeuver]'s source type does.
 * Ferrostar's maneuver set is OSRM-style: a coarse [ManeuverType] plus a directional
 * [ManeuverModifier] — fewer constants, same bucketing job.
 */
enum class BucketedManeuver {
    STRAIGHT,
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    U_TURN_LEFT,
    U_TURN_RIGHT,
    ROUNDABOUT_LEFT,
    ROUNDABOUT_RIGHT,
    ROUNDABOUT_STRAIGHT,
    RAMP_LEFT,
    RAMP_RIGHT,
    MERGE,
    FORK_LEFT,
    FORK_RIGHT,
    // Valhalla surfaces ferry legs as a road class annotation, not a maneuver type, so this bucket
    // is unreachable via fromFerrostarManeuver for now. Kept for icon() parity; revisit in Phase C.
    FERRY,
    DEPART,
    DESTINATION,
    NAME_CHANGE,
    UNKNOWN,
}

private fun ManeuverModifier?.isLeftish() =
    this == ManeuverModifier.LEFT || this == ManeuverModifier.SLIGHT_LEFT || this == ManeuverModifier.SHARP_LEFT

private fun ManeuverModifier?.isRightish() =
    this == ManeuverModifier.RIGHT || this == ManeuverModifier.SLIGHT_RIGHT || this == ManeuverModifier.SHARP_RIGHT

/**
 * Maps Ferrostar's OSRM-style (type, modifier) pair onto our bucket set. Both nullable because a
 * step's visual instruction (or its content) can be absent — same defensive posture the old
 * SDK-based mapper had with its "else -> UNKNOWN" catch-all for a beta/evolving API.
 */
fun fromFerrostarManeuver(type: ManeuverType?, modifier: ManeuverModifier?): BucketedManeuver = when (type) {
    null -> BucketedManeuver.UNKNOWN
    ManeuverType.DEPART -> BucketedManeuver.DEPART
    ManeuverType.ARRIVE -> BucketedManeuver.DESTINATION
    ManeuverType.NEW_NAME -> BucketedManeuver.NAME_CHANGE
    ManeuverType.MERGE -> BucketedManeuver.MERGE
    ManeuverType.ON_RAMP, ManeuverType.OFF_RAMP ->
        if (modifier.isLeftish()) BucketedManeuver.RAMP_LEFT else BucketedManeuver.RAMP_RIGHT
    ManeuverType.FORK -> if (modifier.isLeftish()) BucketedManeuver.FORK_LEFT else BucketedManeuver.FORK_RIGHT
    ManeuverType.ROUNDABOUT, ManeuverType.ROTARY, ManeuverType.ROUNDABOUT_TURN,
    ManeuverType.EXIT_ROUNDABOUT, ManeuverType.EXIT_ROTARY,
    -> when {
        modifier.isLeftish() -> BucketedManeuver.ROUNDABOUT_LEFT
        modifier.isRightish() -> BucketedManeuver.ROUNDABOUT_RIGHT
        else -> BucketedManeuver.ROUNDABOUT_STRAIGHT
    }
    ManeuverType.TURN, ManeuverType.END_OF_ROAD -> when (modifier) {
        ManeuverModifier.LEFT -> BucketedManeuver.LEFT
        ManeuverModifier.RIGHT -> BucketedManeuver.RIGHT
        ManeuverModifier.SLIGHT_LEFT -> BucketedManeuver.SLIGHT_LEFT
        ManeuverModifier.SLIGHT_RIGHT -> BucketedManeuver.SLIGHT_RIGHT
        ManeuverModifier.SHARP_LEFT -> BucketedManeuver.SHARP_LEFT
        ManeuverModifier.SHARP_RIGHT -> BucketedManeuver.SHARP_RIGHT
        // OSRM's UTURN modifier carries no rotation side, unlike the old SDK's clockwise/
        // counterclockwise constants — default to left. ponytail: revisit if a real ride shows
        // the dial picking the wrong side on a U-turn.
        ManeuverModifier.U_TURN -> BucketedManeuver.U_TURN_LEFT
        ManeuverModifier.STRAIGHT, null -> BucketedManeuver.STRAIGHT
    }
    ManeuverType.CONTINUE, ManeuverType.NOTIFICATION -> BucketedManeuver.STRAIGHT
    else -> BucketedManeuver.UNKNOWN // absorbs any future ManeuverType constant we don't recognize yet
}

fun BucketedManeuver.icon(): ImageVector = when (this) {
    BucketedManeuver.STRAIGHT, BucketedManeuver.NAME_CHANGE, BucketedManeuver.DEPART, BucketedManeuver.UNKNOWN -> Icons.Filled.Straight
    BucketedManeuver.LEFT, BucketedManeuver.RAMP_LEFT, BucketedManeuver.FORK_LEFT, BucketedManeuver.MERGE -> Icons.Filled.TurnLeft
    BucketedManeuver.RIGHT, BucketedManeuver.RAMP_RIGHT, BucketedManeuver.FORK_RIGHT -> Icons.Filled.TurnRight
    BucketedManeuver.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    BucketedManeuver.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    BucketedManeuver.SHARP_LEFT -> Icons.Filled.TurnSharpLeft
    BucketedManeuver.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
    // material-icons-extended only ships left-handed UTurn/Roundabout glyphs — no verified
    // UTurnRight/RoundaboutRight symbol exists, so right-turning variants reuse the left glyph
    // rather than risk an unresolved-reference build break. Revisit with a custom icon set per
    // docs/MotoNav_UI_SPEC.md.
    BucketedManeuver.U_TURN_LEFT, BucketedManeuver.U_TURN_RIGHT -> Icons.Filled.UTurnLeft
    BucketedManeuver.ROUNDABOUT_LEFT, BucketedManeuver.ROUNDABOUT_RIGHT, BucketedManeuver.ROUNDABOUT_STRAIGHT -> Icons.Filled.RoundaboutLeft
    BucketedManeuver.FERRY -> Icons.Filled.Straight // no ferry glyph in material-icons-extended; revisit with a custom icon later
    BucketedManeuver.DESTINATION -> Icons.Filled.Flag
}
