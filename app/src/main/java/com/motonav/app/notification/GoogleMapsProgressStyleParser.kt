package com.motonav.app.notification

import android.os.Bundle

/**
 * Parses Google Maps' Android 16 "ProgressStyle" (Live Updates) navigation notification.
 * navparser (GMapsParser) only understands the older RemoteViews-based layout and never
 * fires on this format at all — see docs/RESEARCH_NOTES.md. This reads the notification's
 * extras directly instead.
 */
object GoogleMapsProgressStyleParser : NavDataParser {
    override val sourceApp = NavSourceApp.GOOGLE_MAPS
    override val packageName = "com.google.android.apps.maps"

    override fun parse(extras: Bundle): NavState? = buildNavState(
        title = extras.getCharSequence("android.title")?.toString(),
        subText = extras.getCharSequence("android.subText")?.toString(),
        shortCriticalText = extras.getCharSequence("android.shortCriticalText")?.toString(),
        progress = extras.getInt("android.progress", -1),
        progressMax = extras.getInt("android.progressMax", -1),
    )

    internal fun buildNavState(
        title: String?,
        subText: String?,
        shortCriticalText: String?,
        progress: Int,
        progressMax: Int,
    ): NavState? {
        if (title.isNullOrBlank()) return null
        val remaining = if (progress in 0..progressMax) (progressMax - progress).toDouble() else null
        return NavState(
            sourceApp = NavSourceApp.GOOGLE_MAPS,
            maneuverText = title,
            // android.shortCriticalText holds the plain distance-to-turn number (observed: "60"
            // for "60 m"). progressSegments/progressPoints carry no per-turn distance at all —
            // confirmed via device dump (one segment spanning the whole route, empty points).
            distanceToTurnMeters = shortCriticalText?.toDoubleOrNull(),
            streetName = GoogleMapsNavMapper.extractStreetName(title),
            etaText = subText,
            remainingDistanceMeters = remaining,
            maneuverType = GoogleMapsNavMapper.deriveManeuverType(title),
        )
    }
}
