package com.motonav.app.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// One combined holder for both live sensor values the dial needs (MotoNav_TASK7_STAGED_PLAN.md
// stage 3) — speed and heading are always consumed together by the dial and always produced
// together by RideSessionService, so a shared holder is the smaller diff than two near-identical
// singletons. Either field is null when its source has no usable reading right now (no fix, no
// speed on this hardware, sensor+GPS both unavailable) — the dial renders nothing for that field
// rather than a stale or fabricated value.
data class RideSensors(
    val speedKmh: Float?,
    val headingDegrees: Float?,
)

object RideSensorsStateHolder {
    private val _state = MutableStateFlow(RideSensors(speedKmh = null, headingDegrees = null))
    val state: StateFlow<RideSensors> = _state.asStateFlow()

    fun update(sensors: RideSensors) {
        _state.value = sensors
    }
}
