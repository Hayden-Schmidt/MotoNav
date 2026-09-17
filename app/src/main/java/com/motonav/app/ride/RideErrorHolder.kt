package com.motonav.app.ride

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Same plain-singleton StateFlow bridge as RideStateHolder — the one thing missing after the
// Navigation-SDK-specific NavApiErrorScreen was deleted in Phase A/E: a way for a route-fetch
// failure to reach the UI at all instead of only Log.e. MainActivity shows it as a Toast and
// clears it (see MainActivity's LaunchedEffect(rideError)).
object RideErrorHolder {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun report(message: String) {
        _error.value = message
    }

    fun clear() {
        _error.value = null
    }
}
