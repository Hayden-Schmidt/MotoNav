package com.motonav.app.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Process-wide holder bridging the listener service (producer) and Compose UI (consumer).
// Plain singleton, not a ViewModel — state is Service-fed, not Activity-scoped.
object NavStateHolder {
    private val _state = MutableStateFlow<NavState?>(null)
    val state: StateFlow<NavState?> = _state.asStateFlow()

    fun update(navState: NavState?) {
        _state.value = navState
    }
}
