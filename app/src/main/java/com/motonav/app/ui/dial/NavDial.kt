package com.motonav.app.ui.dial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motonav.app.navsdk.BucketedManeuver
import com.motonav.app.navsdk.NavSdkUiState
import com.motonav.app.navsdk.SdkNavState_
import com.motonav.app.navsdk.icon

// The dial's five states (MotoNav_UI_SPEC.md §3). Derived, not sourced directly — see
// [deriveDialState] — so the rest of this file only ever branches on this, never on raw SDK enums.
sealed interface DialState {
    data object Idle : DialState
    data class Active(val maneuver: BucketedManeuver, val distanceMeters: Int?) : DialState
    data class Rerouting(val lastManeuver: BucketedManeuver?) : DialState
    data class Stale(val maneuver: BucketedManeuver, val distanceMeters: Int?) : DialState
    data object Arrived : DialState
}

fun deriveDialState(
    uiState: NavSdkUiState?,
    nowMs: Long,
    staleThresholdMs: Long = DialLayout.STALE_THRESHOLD_MS,
): DialState {
    if (uiState == null) return DialState.Idle
    if (uiState.maneuver == BucketedManeuver.DESTINATION) return DialState.Arrived
    if (nowMs - uiState.lastUpdated > staleThresholdMs) {
        return DialState.Stale(uiState.maneuver, uiState.distanceToCurrentStepMeters)
    }
    if (uiState.navState == SdkNavState_.REROUTING) return DialState.Rerouting(uiState.maneuver)
    return DialState.Active(uiState.maneuver, uiState.distanceToCurrentStepMeters)
}

private fun DialState.ringStyle(): RingStyle = when (this) {
    is DialState.Idle -> RingStyle.DIM
    is DialState.Active, is DialState.Arrived -> RingStyle.SOLID
    is DialState.Rerouting -> RingStyle.DASHED
    is DialState.Stale -> RingStyle.BROKEN
}

private fun RingStyle.color(): Color = when (this) {
    RingStyle.SOLID -> DialLayout.RING_ACTIVE_COLOR
    RingStyle.DASHED -> DialLayout.RING_REROUTING_COLOR
    RingStyle.BROKEN -> DialLayout.RING_STALE_COLOR
    RingStyle.DIM -> DialLayout.RING_IDLE_COLOR
}

// Metric-only, per MotoNav_TASK7_STAGED_PLAN.md §1. Returns null (render nothing) when there's no
// value yet — never a fabricated placeholder.
fun formatDialDistance(meters: Int?): Pair<String, String>? {
    if (meters == null) return null
    return if (meters >= 1000) {
        "%.1f".format(meters / 1000.0) to "km"
    } else {
        meters.toString() to "m"
    }
}

@Composable
fun NavDial(
    dialState: DialState,
    config: NavDialConfig = DefaultNavDialConfig,
    // Null = no reliable heading source (no sensor, no GPS bearing) — hide the marker rather than
    // freeze it at a stale bearing, which looks identical to a working compass (stage 3 §4.3).
    compassBearingDegrees: Float? = null,
    speedKmh: Float? = null,
    // Always null this phase — no speed-limit data source exists yet (needs the Roads API, out of
    // scope per MotoNav_TASK7_STAGED_PLAN.md stage 3(c)). Slot is wired so a real value is additive.
    speedLimitKmh: Int? = null,
    modifier: Modifier = Modifier,
) {
    val ringColor by animateColorAsState(
        targetValue = dialState.ringStyle().color(),
        animationSpec = tween(200),
        label = "ringColor",
    )

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val diameter = if (maxWidth < maxHeight) maxWidth else maxHeight
        val radius = diameter / 2

        Surface(modifier = Modifier.size(diameter), shape = CircleShape, color = Color.Black) {
            Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(diameter)) {
                    drawDialRing(dialState.ringStyle(), ringColor)
                    if (config.showCompass && compassBearingDegrees != null) {
                        drawCompassMarker(config.compassStyle, compassBearingDegrees)
                    }
                }

                if (config.showSpeed && speedKmh != null) {
                    SpeedReadout(speedKmh, radius)
                }
                if (config.showSpeedLimit) {
                    SpeedLimitSign(speedLimitKmh, radius)
                }

                AnimatedContent(targetState = dialState, label = "dialContent") { state ->
                    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
                        when (state) {
                            is DialState.Idle -> DialLogo(logoSize = diameter * DialLayout.LOGO_SIZE_FRACTION)
                            is DialState.Arrived -> ManeuverGlyph(Icons.Filled.Flag, radius, alpha = 1f)
                            is DialState.Active -> {
                                ManeuverGlyph(state.maneuver.icon(), radius, alpha = 1f)
                                DistanceReadout(state.distanceMeters, radius, alpha = 1f)
                            }
                            is DialState.Rerouting -> state.lastManeuver?.let {
                                ManeuverGlyph(it.icon(), radius, alpha = 0.4f)
                            }
                            is DialState.Stale -> {
                                ManeuverGlyph(state.maneuver.icon(), radius, alpha = 0.4f)
                                DistanceReadout(state.distanceMeters, radius, alpha = 0.4f)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManeuverGlyph(icon: ImageVector, radius: Dp, alpha: Float) {
    Icon(
        icon,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .size(radius * DialLayout.ICON_SIZE_FRACTION)
            .offset(y = radius * DialLayout.ICON_CENTER_Y_FRACTION)
            .alpha(alpha),
    )
}

@Composable
private fun DistanceReadout(meters: Int?, radius: Dp, alpha: Float) {
    val readout = formatDialDistance(meters) ?: return
    val (value, unit) = readout
    Text(
        value,
        color = Color.White,
        fontSize = (radius.value * DialLayout.DISTANCE_TEXT_SIZE_FRACTION).sp,
        modifier = Modifier
            .offset(y = radius * DialLayout.DISTANCE_CENTER_Y_FRACTION)
            .alpha(alpha),
    )
    Text(
        unit,
        color = Color.White,
        fontSize = (radius.value * DialLayout.UNIT_TEXT_SIZE_FRACTION).sp,
        modifier = Modifier
            .offset(y = radius * DialLayout.UNIT_CENTER_Y_FRACTION)
            .alpha(alpha),
    )
}

@Composable
private fun SpeedReadout(speedKmh: Float, radius: Dp) {
    Text(
        "${speedKmh.toInt()} km/h",
        color = Color.White,
        fontSize = (radius.value * DialLayout.SPEED_TEXT_SIZE_FRACTION).sp,
        modifier = Modifier.offset(y = radius * DialLayout.SPEED_CENTER_Y_FRACTION),
    )
}

// Reserved slot only — no speed-limit data source exists this phase (see NavDial's speedLimitKmh
// param doc). Renders nothing until a real value shows up; never a placeholder box.
@Composable
private fun SpeedLimitSign(speedLimitKmh: Int?, radius: Dp) {
    if (speedLimitKmh == null) return
    Box(
        modifier = Modifier
            .offset(
                x = radius * DialLayout.SPEED_LIMIT_CENTER_X_FRACTION,
                y = radius * DialLayout.SPEED_LIMIT_CENTER_Y_FRACTION,
            )
            .size(radius * DialLayout.SPEED_LIMIT_SIZE_FRACTION)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(speedLimitKmh.toString(), color = Color.Black, fontSize = (radius.value * 0.18f).sp)
    }
}

// The existing launcher mark (yellow circle, black upward triangle) redrawn as flat shapes —
// already a legible-at-240px silhouette, no new brand asset needed for a stage-2 placeholder.
@Composable
private fun DialLogo(logoSize: Dp) {
    Canvas(modifier = Modifier.size(logoSize).alpha(0.5f)) {
        drawCircle(color = DialLayout.RING_ACTIVE_COLOR, radius = this.size.minDimension / 2)
        val triangleSide = this.size.minDimension * 0.5f
        val top = (this.size.minDimension - triangleSide) / 2
        val bottom = top + triangleSide
        val left = (this.size.width - triangleSide) / 2
        val right = left + triangleSide
        val path = Path().apply {
            moveTo(this@Canvas.size.width / 2, top)
            lineTo(left, bottom)
            lineTo(right, bottom)
            close()
        }
        drawPath(path, color = Color.Black)
    }
}

@Preview(name = "Active", widthDp = 360, heightDp = 360)
@Composable
private fun NavDialActivePreview() {
    NavDial(
        dialState = DialState.Active(BucketedManeuver.LEFT, 240),
        modifier = Modifier.background(Color.Black),
    )
}

@Preview(name = "Rerouting", widthDp = 360, heightDp = 360)
@Composable
private fun NavDialReroutingPreview() {
    NavDial(
        dialState = DialState.Rerouting(BucketedManeuver.LEFT),
        modifier = Modifier.background(Color.Black),
    )
}

@Preview(name = "Stale", widthDp = 360, heightDp = 360)
@Composable
private fun NavDialStalePreview() {
    NavDial(
        dialState = DialState.Stale(BucketedManeuver.RIGHT, 80),
        modifier = Modifier.background(Color.Black),
    )
}

@Preview(name = "Idle", widthDp = 360, heightDp = 360)
@Composable
private fun NavDialIdlePreview() {
    NavDial(dialState = DialState.Idle, modifier = Modifier.background(Color.Black))
}

@Preview(name = "Arrived", widthDp = 360, heightDp = 360)
@Composable
private fun NavDialArrivedPreview() {
    NavDial(dialState = DialState.Arrived, modifier = Modifier.background(Color.Black))
}

// Legibility sanity check per MotoNav_TASK7_STAGED_PLAN.md §0.5 — "does this still read small,"
// not a rendering constraint. 240x240dp stands in for the eventual ESP32 panel.
@Preview(name = "ESP32 240x240 legibility check", widthDp = 240, heightDp = 240)
@Composable
private fun NavDialEsp32SizePreview() {
    NavDial(
        dialState = DialState.Active(BucketedManeuver.ROUNDABOUT_LEFT, 65),
        modifier = Modifier.background(Color.Black),
    )
}
