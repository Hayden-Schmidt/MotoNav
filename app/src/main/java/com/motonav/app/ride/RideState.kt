package com.motonav.app.ride

import com.motonav.app.nav.BucketedManeuver
import com.motonav.app.nav.LocalOffsetMeters
import com.motonav.app.nav.forwardRouteGeometry
import com.motonav.app.nav.fromFerrostarManeuver
import com.motonav.app.nav.projectToLocalMeters
import com.stadiamaps.ferrostar.core.NavigationState
import uniffi.ferrostar.TripState

/**
 * The single state object the dial (and eventually the BLE link / ESP32) reads — see
 * docs/MotoNav_REBUILD_PLAN_OSM.md §2.1. Ferrostar's own NavigationState/TripState stay inside
 * RideSessionService; [toRideState] is the one mapping function from those into this. Was
 * navsdk.NavSdkUiState.
 */
data class RideState(
    val phase: RidePhase,
    val maneuver: BucketedManeuver,
    val distanceToNextManeuverMeters: Int?,
    val distanceRemainingMeters: Int?,
    val durationRemainingSeconds: Int?,
    // Phase B — route ahead of the current position, projected to local east/north metre offsets
    // (see nav/RouteGeometry.kt). Rotated to heading-relative dial fractions at draw time
    // (ui/dial/RouteLine.kt), since the compass heading updates faster than RideState does. Empty
    // when not navigating — never a fabricated placeholder.
    val routeAheadMeters: List<LocalOffsetMeters> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis(),
)

enum class RidePhase { ENROUTE, REROUTING, ARRIVED }

// Null = no active guidance (Ferrostar's TripState.Idle) — the dial's own deriveDialState already
// treats null as its Idle state, same contract the old NavSdkStateHolder had.
fun NavigationState.toRideState(): RideState? = when (val trip = tripState) {
    is TripState.Idle -> null
    is TripState.Complete -> RideState(
        phase = RidePhase.ARRIVED,
        maneuver = BucketedManeuver.DESTINATION,
        distanceToNextManeuverMeters = 0,
        distanceRemainingMeters = 0,
        durationRemainingSeconds = 0,
    )
    is TripState.Navigating -> {
        val content = trip.visualInstruction?.primaryContent
        val forwardGeometry = forwardRouteGeometry(trip.remainingSteps, trip.currentStepGeometryIndex?.toInt() ?: 0)
        RideState(
            phase = if (isCalculatingNewRoute) RidePhase.REROUTING else RidePhase.ENROUTE,
            maneuver = fromFerrostarManeuver(content?.maneuverType, content?.maneuverModifier),
            distanceToNextManeuverMeters = trip.progress.distanceToNextManeuver.toInt(),
            distanceRemainingMeters = trip.progress.distanceRemaining.toInt(),
            durationRemainingSeconds = trip.progress.durationRemaining.toInt(),
            routeAheadMeters = projectToLocalMeters(trip.snappedUserLocation.coordinates, forwardGeometry),
        )
    }
}
