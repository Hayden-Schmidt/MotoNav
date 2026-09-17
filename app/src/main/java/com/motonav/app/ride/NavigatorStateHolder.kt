package com.motonav.app.ride

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Same plain-singleton StateFlow bridge pattern as navsdk.NavSdkStateHolder — RideSessionService
// is the only thing allowed to own a Navigator instance (see MotoNav_TASK7_STAGED_PLAN.md §0.4);
// MainActivity observes this instead of holding one.
sealed interface NavigatorState {
    data object Initializing : NavigatorState
    data object Ready : NavigatorState
    data class Error(val reason: String) : NavigatorState
}

object NavigatorStateHolder {
    private val _state = MutableStateFlow<NavigatorState>(NavigatorState.Initializing)
    val state: StateFlow<NavigatorState> = _state.asStateFlow()

    fun update(newState: NavigatorState) {
        _state.value = newState
    }
}
