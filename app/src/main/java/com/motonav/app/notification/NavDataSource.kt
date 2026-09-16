package com.motonav.app.notification

/**
 * Common shape for parsed navigation state, regardless of source app (Google Maps, Waze).
 *
 * Per PRD: this must stay serialization-ready (simple types only, no UI-coupled classes)
 * since Phase 2 will broadcast this same shape over BLE to an ESP32 receiver.
 *
 * See docs/MotoNav_PRD_Phase1.md and docs/RESEARCH_NOTES.md for field-source rationale.
 */
data class NavState(
    val sourceApp: NavSourceApp,
    val maneuverText: String?,      // e.g. "Turn right onto Main St" — combined text, not pre-split
    val distanceToTurnMeters: Double?,
    val streetName: String?,        // best-effort extraction; may be null if not separable from maneuverText
    val etaText: String?,
    val remainingDistanceMeters: Double?,
    val isRerouting: Boolean = false,
    val alertText: String? = null,  // Waze police/hazard best-effort (P1) — null if unavailable
    val lastUpdated: Long = System.currentTimeMillis(),
)

enum class NavSourceApp {
    GOOGLE_MAPS,
    WAZE,
    UNKNOWN,
}

/**
 * Implemented per source app. Each parser reads a StatusBarNotification's extras
 * (from NavNotificationListenerService) and produces a NavState, or null if the
 * notification isn't a navigation notification / doesn't parse cleanly.
 *
 * Google Maps: recommend delegating to GMapsParser's navparser library rather than
 * hand-rolling extras parsing — see docs/RESEARCH_NOTES.md.
 *
 * Waze: no equivalent library found yet. Needs an on-device notification capture
 * spike before this can be implemented — see PRD Open Questions / Timeline step 1.
 */
interface NavDataParser {
    val sourceApp: NavSourceApp
    val packageName: String
    fun parse(extras: android.os.Bundle): NavState?
}
