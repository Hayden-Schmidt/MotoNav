package com.motonav.app.notification

/**
 * Text-derivation helpers shared by Google Maps notification parsers: maneuver text only
 * gives us free text (e.g. "Turn right onto Main St"), so maneuver type and street name are
 * both derived from it on a best-effort basis. See GoogleMapsProgressStyleParser for the
 * current (Android 16 ProgressStyle) notification parser that calls these.
 */
object GoogleMapsNavMapper {

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
}
