package com.motonav.app.navsdk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Process-wide holder bridging NavSdkTurnByTurnService (producer) and Compose UI (consumer).
// Mirrors notification.NavStateHolder's shape/role exactly — same plain-singleton pattern,
// same StateFlow-based bridge — but fed by the Navigation SDK's turn-by-turn callback instead of
// the notification listener. See docs/RESEARCH_NOTES.md for why the data source changed.
object NavSdkStateHolder {
    private val _state = MutableStateFlow<NavSdkUiState?>(null)
    val state: StateFlow<NavSdkUiState?> = _state.asStateFlow()

    fun update(uiState: NavSdkUiState?) {
        _state.value = uiState
    }
}
