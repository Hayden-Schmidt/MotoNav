package com.motonav.app.notification

import me.trevi.navparser.lib.DistanceUnit
import me.trevi.navparser.lib.NavigationData
import me.trevi.navparser.lib.NavigationDistance

/**
 * Converts navparser's NavigationData into our own NavState. navparser has no semantic
 * maneuver-type or street-name field — only free text (nextDirection.localeString) — so
 * both are derived from that text on a best-effort basis.
 */
object GoogleMapsNavMapper {

    fun toNavState(data: NavigationData): NavState {
        val maneuverText = data.nextDirection.localeString
        return NavState(
            sourceApp = NavSourceApp.GOOGLE_MAPS,
            maneuverText = maneuverText,
            distanceToTurnMeters = data.nextDirection.navigationDistance?.toMeters(),
            streetName = maneuverText?.let(::extractStreetName),
            etaText = data.eta.localeString,
            remainingDistanceMeters = data.remainingDistance.toMeters(),
            isRerouting = data.isRerouting,
            maneuverType = maneuverText?.let(::deriveManeuverType) ?: ManeuverType.UNKNOWN,
        )
    }

    // ponytail: English-only keyword matching, brittle across locales/GMaps phrasing changes.
    // Upgrade path: once real on-device notification text is captured, expand/replace with
    // observed phrasings; consider locale-aware matching if non-English use is ever needed.
    internal fun deriveManeuverType(text: String): ManeuverType {
        val t = text.lowercase()
        return when {
            "u-turn" in t || "u turn" in t -> ManeuverType.U_TURN
            "roundabout" in t -> ManeuverType.ROUNDABOUT
            "arrive" in t || "destination" in t -> ManeuverType.ARRIVE
            "sharp right" in t -> ManeuverType.SHARP_RIGHT
            "sharp left" in t -> ManeuverType.SHARP_LEFT
            "slight right" in t || "keep right" in t -> ManeuverType.SLIGHT_RIGHT
            "slight left" in t || "keep left" in t -> ManeuverType.SLIGHT_LEFT
            "right" in t -> ManeuverType.RIGHT
            "left" in t -> ManeuverType.LEFT
            "straight" in t || "continue" in t -> ManeuverType.STRAIGHT
            else -> ManeuverType.UNKNOWN
        }
    }

    // ponytail: only handles "...onto/on <street>" phrasing; roundabout/arrive/reroute text
    // won't match, streetName stays null for those — acceptable per PRD's graceful missing-field
    // handling.
    internal fun extractStreetName(text: String): String? =
        Regex("(?:onto|on)\\s+(.+)$", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)

    private fun NavigationDistance.toMeters(): Double? {
        if (distance < 0) return null
        return when (unit) {
            DistanceUnit.M -> distance
            DistanceUnit.KM -> distance * 1000
            DistanceUnit.FT -> distance * 0.3048
            DistanceUnit.YD -> distance * 0.9144
            DistanceUnit.MI -> distance * 1609.34
            DistanceUnit.INVALID -> null
        }
    }
}
