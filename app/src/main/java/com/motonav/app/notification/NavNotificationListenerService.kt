package com.motonav.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Captures navigation notifications from Google Maps / Waze and routes them to the
 * matching NavDataParser. Registration is passive — Android calls onNotificationPosted
 * only when something changes; no polling. See PRD "Background power management".
 *
 * NOTE: Notification access must be granted manually by the user via
 * Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS — it is not a normal runtime permission
 * and resets on every reinstall during development. See docs/SETUP.md.
 */
class NavNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NavListener"
        private const val PKG_GOOGLE_MAPS = "com.google.android.apps.maps"
        private const val PKG_WAZE = "com.waze"
    }

    // Registered per source app. Waze parser is a stub pending the on-device
    // notification-capture spike (PRD Timeline step 1).
    private val parsers: List<NavDataParser> = listOf(
        // GoogleMapsNavParser(),  // TODO: wire up once GMapsParser dependency is integrated
        // WazeNavParser(),        // TODO: implement after Waze notification capture spike
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val parser = parsers.firstOrNull { it.packageName == sbn.packageName } ?: return
        val extras = sbn.notification.extras ?: return

        val navState = runCatching { parser.parse(extras) }
            .onFailure { Log.w(TAG, "Failed to parse notification from ${sbn.packageName}", it) }
            .getOrNull() ?: return

        Log.d(TAG, "Parsed nav state: $navState")
        // TODO: publish navState to app-wide state holder (e.g. a StateFlow) for the UI to observe.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // TODO: if the active nav notification is removed, transition UI to idle/"waiting for navigation".
    }
}
