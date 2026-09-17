package com.motonav.app.navsdk

import com.google.android.libraries.mapsplatform.turnbyturn.model.NavInfo
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavState as SdkNavState

/**
 * App-level nav state, adapted from the Navigation SDK's `NavInfo`/`StepInfo` (turn-by-turn data
 * feed — see docs/RESEARCH_NOTES.md "Turn-by-turn data feed — what it actually provides").
 *
 * Supersedes `notification.NavState`, which was built from best-effort Google Maps notification
 * text parsing. Field-for-field, everything here is either a direct passthrough of a real SDK
 * value or (for [lastUpdated]) something we stamp ourselves on receipt, since the SDK does not
 * timestamp its own messages — staleness detection (docs/MotoNav_UI_SPEC.md §3.4) still needs a
 * receive-side timestamp regardless of data source.
 *
 * Deliberately excludes route polyline/geometry — the SDK's turn-by-turn feed does not expose it,
 * same gap the notification-based approach had. See UI_SPEC for why the dial uses a bucketed
 * icon instead of a schematic route line.
 */
data class NavSdkUiState(
    val navState: SdkNavState_,
    val maneuver: BucketedManeuver,
    val fullRoadName: String?,
    val simpleRoadName: String?,
    val distanceToCurrentStepMeters: Int?,
    val timeToCurrentStepSeconds: Int?,
    val distanceToFinalDestinationMeters: Int?,
    val timeToFinalDestinationSeconds: Int?,
    val exitNumber: String?,
    val roundaboutTurnNumber: Int?,
    val lastUpdated: Long = System.currentTimeMillis(),
)

/**
 * Mirrors the SDK's `NavState` int constants (ENROUTE / REROUTING / STOPPED / UNKNOWN) as a real
 * Kotlin enum for exhaustive `when` safety in UI code, rather than passing the raw SDK `int`
 * around. Named with a trailing underscore only to avoid a same-name clash with the SDK's own
 * `NavState` class in this file's imports — rename freely once the SDK import is aliased elsewhere.
 */
enum class SdkNavState_ {
    ENROUTE, // Actively navigating, current-step info available.
    REROUTING, // Actively navigating but recalculating; no current-step info yet.
    STOPPED, // Navigation ended.
    UNKNOWN, // Error or unspecified — SDK's own catch-all, not one we invented.
}

fun fromSdkNavState(value: Int): SdkNavState_ = when (value) {
    SdkNavState.ENROUTE -> SdkNavState_.ENROUTE
    SdkNavState.REROUTING -> SdkNavState_.REROUTING
    SdkNavState.STOPPED -> SdkNavState_.STOPPED
    else -> SdkNavState_.UNKNOWN
}

/**
 * Converts an SDK `NavInfo` message into our app-level [NavSdkUiState]. Returns null only when
 * `navState == ENROUTE` but `getCurrentStep()` is unexpectedly null (shouldn't happen per the
 * SDK's own contract, but the getter is nullable in the reference — handled defensively rather
 * than asserted).
 */
fun NavInfo.toUiState(): NavSdkUiState {
    val state = fromSdkNavState(getNavState())
    val step = getCurrentStep()
    return NavSdkUiState(
        navState = state,
        maneuver = step?.let { fromSdkManeuver(it.getManeuver()) } ?: BucketedManeuver.UNKNOWN,
        fullRoadName = step?.getFullRoadName(),
        simpleRoadName = step?.getSimpleRoadName(),
        distanceToCurrentStepMeters = getDistanceToCurrentStepMeters(),
        timeToCurrentStepSeconds = getTimeToCurrentStepSeconds(),
        // getDistanceToFinalDestinationMeters/getTimeToFinalDestinationSeconds are the current
        // (non-deprecated) methods — NavInfo also exposes getDistanceToDestinationMeters()/
        // getTimeToDestinationSeconds() but those are marked deprecated in the SDK reference.
        distanceToFinalDestinationMeters = getDistanceToFinalDestinationMeters(),
        timeToFinalDestinationSeconds = getTimeToFinalDestinationSeconds(),
        exitNumber = step?.getExitNumber(),
        roundaboutTurnNumber = step?.getRoundaboutTurnNumber(),
    )
}
