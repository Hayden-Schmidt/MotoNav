package com.motonav.app.navsdk

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
import com.google.android.libraries.mapsplatform.turnbyturn.model.Maneuver as SdkManeuver

/**
 * The Navigation SDK's `Maneuver` (turn-by-turn model) has 62 constants — far more detail than
 * a single glanceable dial icon can show (see docs/MotoNav_UI_SPEC.md: the dial only has room
 * for one dominant directional glyph). This enum is our bucketed, UI-facing set; [fromSdkManeuver]
 * maps every real SDK constant down onto one of these buckets.
 *
 * Supersedes the old notification-parsing `ManeuverType` in notification/NavDataSource.kt, which
 * was derived from best-effort English keyword matching on Google Maps notification text. This
 * one is derived from the SDK's own typed `int` constant — no text-guessing involved. See
 * docs/RESEARCH_NOTES.md "Google Navigation SDK — architecture decision" for the full rationale.
 *
 * Note: the underlying `com.google.android.libraries.mapsplatform.turnbyturn.model` package is
 * documented by Google as a beta API, "subject to change without guaranteeing backward
 * compatibility" — [fromSdkManeuver]'s `else -> UNKNOWN` fallback exists specifically to absorb
 * any future constant additions gracefully rather than crashing.
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
    ROUNDABOUT_LEFT, // covers all ROUNDABOUT_*_(CLOCKWISE|COUNTERCLOCKWISE) left-turning variants
    ROUNDABOUT_RIGHT, // covers all ROUNDABOUT_*_(CLOCKWISE|COUNTERCLOCKWISE) right-turning variants
    ROUNDABOUT_STRAIGHT,
    RAMP_LEFT, // on-ramp or off-ramp, left-turning variants
    RAMP_RIGHT, // on-ramp or off-ramp, right-turning variants
    MERGE,
    FORK_LEFT,
    FORK_RIGHT,
    FERRY,
    DEPART,
    DESTINATION,
    NAME_CHANGE,
    UNKNOWN,
}

fun fromSdkManeuver(maneuver: Int): BucketedManeuver = when (maneuver) {
    SdkManeuver.STRAIGHT -> BucketedManeuver.STRAIGHT
    SdkManeuver.TURN_LEFT -> BucketedManeuver.LEFT
    SdkManeuver.TURN_RIGHT -> BucketedManeuver.RIGHT
    SdkManeuver.TURN_SLIGHT_LEFT, SdkManeuver.TURN_KEEP_LEFT -> BucketedManeuver.SLIGHT_LEFT
    SdkManeuver.TURN_SLIGHT_RIGHT, SdkManeuver.TURN_KEEP_RIGHT -> BucketedManeuver.SLIGHT_RIGHT
    SdkManeuver.TURN_SHARP_LEFT -> BucketedManeuver.SHARP_LEFT
    SdkManeuver.TURN_SHARP_RIGHT -> BucketedManeuver.SHARP_RIGHT
    SdkManeuver.TURN_U_TURN_CLOCKWISE, SdkManeuver.TURN_U_TURN_COUNTERCLOCKWISE -> {
        // Clockwise U-turn ends up heading right-of-original, counterclockwise ends up left —
        // used as the left/right split since the dial only distinguishes turn side, not rotation.
        if (maneuver == SdkManeuver.TURN_U_TURN_CLOCKWISE) BucketedManeuver.U_TURN_RIGHT else BucketedManeuver.U_TURN_LEFT
    }

    SdkManeuver.ON_RAMP_LEFT, SdkManeuver.ON_RAMP_SLIGHT_LEFT, SdkManeuver.ON_RAMP_SHARP_LEFT,
    SdkManeuver.ON_RAMP_KEEP_LEFT, SdkManeuver.ON_RAMP_U_TURN_CLOCKWISE, SdkManeuver.ON_RAMP_U_TURN_COUNTERCLOCKWISE,
    SdkManeuver.OFF_RAMP_LEFT, SdkManeuver.OFF_RAMP_SLIGHT_LEFT, SdkManeuver.OFF_RAMP_SHARP_LEFT,
    SdkManeuver.OFF_RAMP_KEEP_LEFT, SdkManeuver.OFF_RAMP_U_TURN_CLOCKWISE, SdkManeuver.OFF_RAMP_U_TURN_COUNTERCLOCKWISE,
    -> BucketedManeuver.RAMP_LEFT

    SdkManeuver.ON_RAMP_RIGHT, SdkManeuver.ON_RAMP_SLIGHT_RIGHT, SdkManeuver.ON_RAMP_SHARP_RIGHT,
    SdkManeuver.ON_RAMP_KEEP_RIGHT, SdkManeuver.OFF_RAMP_RIGHT, SdkManeuver.OFF_RAMP_SLIGHT_RIGHT,
    SdkManeuver.OFF_RAMP_SHARP_RIGHT, SdkManeuver.OFF_RAMP_KEEP_RIGHT,
    -> BucketedManeuver.RAMP_RIGHT

    SdkManeuver.ON_RAMP_UNSPECIFIED, SdkManeuver.OFF_RAMP_UNSPECIFIED -> BucketedManeuver.STRAIGHT

    SdkManeuver.MERGE_LEFT, SdkManeuver.MERGE_RIGHT, SdkManeuver.MERGE_UNSPECIFIED -> BucketedManeuver.MERGE

    SdkManeuver.FORK_LEFT -> BucketedManeuver.FORK_LEFT
    SdkManeuver.FORK_RIGHT -> BucketedManeuver.FORK_RIGHT

    SdkManeuver.ROUNDABOUT_LEFT_CLOCKWISE, SdkManeuver.ROUNDABOUT_LEFT_COUNTERCLOCKWISE,
    SdkManeuver.ROUNDABOUT_SHARP_LEFT_CLOCKWISE, SdkManeuver.ROUNDABOUT_SHARP_LEFT_COUNTERCLOCKWISE,
    SdkManeuver.ROUNDABOUT_SLIGHT_LEFT_CLOCKWISE, SdkManeuver.ROUNDABOUT_SLIGHT_LEFT_COUNTERCLOCKWISE,
    SdkManeuver.ROUNDABOUT_U_TURN_CLOCKWISE, SdkManeuver.ROUNDABOUT_U_TURN_COUNTERCLOCKWISE,
    -> BucketedManeuver.ROUNDABOUT_LEFT

    SdkManeuver.ROUNDABOUT_RIGHT_CLOCKWISE, SdkManeuver.ROUNDABOUT_RIGHT_COUNTERCLOCKWISE,
    SdkManeuver.ROUNDABOUT_SHARP_RIGHT_CLOCKWISE, SdkManeuver.ROUNDABOUT_SHARP_RIGHT_COUNTERCLOCKWISE,
    SdkManeuver.ROUNDABOUT_SLIGHT_RIGHT_CLOCKWISE, SdkManeuver.ROUNDABOUT_SLIGHT_RIGHT_COUNTERCLOCKWISE,
    -> BucketedManeuver.ROUNDABOUT_RIGHT

    SdkManeuver.ROUNDABOUT_STRAIGHT_CLOCKWISE, SdkManeuver.ROUNDABOUT_STRAIGHT_COUNTERCLOCKWISE,
    SdkManeuver.ROUNDABOUT_CLOCKWISE, SdkManeuver.ROUNDABOUT_COUNTERCLOCKWISE,
    SdkManeuver.ROUNDABOUT_EXIT_CLOCKWISE, SdkManeuver.ROUNDABOUT_EXIT_COUNTERCLOCKWISE,
    -> BucketedManeuver.ROUNDABOUT_STRAIGHT

    SdkManeuver.FERRY_BOAT, SdkManeuver.FERRY_TRAIN -> BucketedManeuver.FERRY
    SdkManeuver.DEPART -> BucketedManeuver.DEPART
    SdkManeuver.DESTINATION, SdkManeuver.DESTINATION_LEFT, SdkManeuver.DESTINATION_RIGHT -> BucketedManeuver.DESTINATION
    SdkManeuver.NAME_CHANGE -> BucketedManeuver.NAME_CHANGE
    else -> BucketedManeuver.UNKNOWN // covers SdkManeuver.UNKNOWN and any future constant we don't recognize yet
}

fun BucketedManeuver.icon(): ImageVector = when (this) {
    BucketedManeuver.STRAIGHT, BucketedManeuver.NAME_CHANGE, BucketedManeuver.DEPART, BucketedManeuver.UNKNOWN -> Icons.Filled.Straight
    BucketedManeuver.LEFT, BucketedManeuver.RAMP_LEFT, BucketedManeuver.FORK_LEFT, BucketedManeuver.MERGE -> Icons.Filled.TurnLeft
    BucketedManeuver.RIGHT, BucketedManeuver.RAMP_RIGHT, BucketedManeuver.FORK_RIGHT -> Icons.Filled.TurnRight
    BucketedManeuver.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    BucketedManeuver.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    BucketedManeuver.SHARP_LEFT -> Icons.Filled.TurnSharpLeft
    BucketedManeuver.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
    // Note: material-icons-extended only ships left-handed UTurn/Roundabout glyphs (confirmed by
    // what the pre-existing notification-based ManeuverType.icon() already used, in
    // notification/NavDataSource.kt) — no verified UTurnRight/RoundaboutRight symbol exists, so
    // right-turning variants reuse the left glyph rather than risk an unresolved-reference build
    // break on an unverified icon name. Revisit with a custom icon set per docs/MotoNav_UI_SPEC.md.
    BucketedManeuver.U_TURN_LEFT, BucketedManeuver.U_TURN_RIGHT -> Icons.Filled.UTurnLeft
    BucketedManeuver.ROUNDABOUT_LEFT, BucketedManeuver.ROUNDABOUT_RIGHT, BucketedManeuver.ROUNDABOUT_STRAIGHT -> Icons.Filled.RoundaboutLeft
    BucketedManeuver.FERRY -> Icons.Filled.Straight // no ferry glyph in material-icons-extended; revisit with a custom icon later
    BucketedManeuver.DESTINATION -> Icons.Filled.Flag
}
