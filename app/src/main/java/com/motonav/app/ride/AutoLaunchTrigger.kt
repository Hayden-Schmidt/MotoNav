package com.motonav.app.ride

// Persisted setting (see SettingsStore) controlling when MotoNav self-launches.
enum class AutoLaunchMode {
    OFF,
    BT_CONNECT,
    NAV_START,
    BT_AND_NAV_START,
}

enum class TriggerEvent {
    BT_CONNECTED,
    NAV_START,
}

// Per PRD: BT_AND_NAV_START only fires on nav-start, checking BT state at that moment —
// never on a bare BT connect (a rider connecting BT before parking shouldn't launch nav).
fun shouldLaunch(mode: AutoLaunchMode, event: TriggerEvent, btConnected: Boolean): Boolean = when (mode) {
    AutoLaunchMode.OFF -> false
    AutoLaunchMode.BT_CONNECT -> event == TriggerEvent.BT_CONNECTED
    AutoLaunchMode.NAV_START -> event == TriggerEvent.NAV_START
    AutoLaunchMode.BT_AND_NAV_START -> event == TriggerEvent.NAV_START && btConnected
}
