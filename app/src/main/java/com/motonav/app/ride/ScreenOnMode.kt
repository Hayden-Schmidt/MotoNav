package com.motonav.app.ride

// Persisted setting (see SettingsStore) controlling FLAG_KEEP_SCREEN_ON while MainActivity is visible.
enum class ScreenOnMode {
    ALWAYS_ON,
    NEVER,
    UNLESS_EXTERNAL_DISPLAY,
}

fun shouldKeepScreenOn(mode: ScreenOnMode, externalDisplayConnected: Boolean): Boolean = when (mode) {
    ScreenOnMode.ALWAYS_ON -> true
    ScreenOnMode.NEVER -> false
    ScreenOnMode.UNLESS_EXTERNAL_DISPLAY -> !externalDisplayConnected
}
