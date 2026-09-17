package com.motonav.app.ride

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Same plain-singleton StateFlow bridge as the navsdk.NavSdkStateHolder / NavigatorStateHolder it
// replaces — RideSessionService is the only thing allowed to own a FerrostarCore instance;
// MainActivity and the dial only ever observe this.
object RideStateHolder {
    private val _state = MutableStateFlow<RideState?>(null)
    val state: StateFlow<RideState?> = _state.asStateFlow()

    fun update(rideState: RideState?) {
        _state.value = rideState
    }
}
