package com.motonav.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Captures Google Maps navigation notifications directly via NotificationListenerService.
 * Previously delegated Maps parsing to navparser (GMapsParser), but that library never
 * recognizes Android 16's ProgressStyle notification format Maps now uses — see
 * GoogleMapsProgressStyleParser and docs/RESEARCH_NOTES.md.
 *
 * NOTE: Notification access must be granted manually by the user via
 * Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS — it is not a normal runtime permission
 * and resets on every reinstall during development. See docs/SETUP.md.
 *
 * This package is dead per MotoNav_TASK7_STAGED_PLAN.md §1 — RideSessionService no longer starts
 * from onListenerConnected() and no longer observes NavStateHolder. Left on disk for reference
 * only; not extended, not binding on anything built after stage 1.
 */
class NavNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NavListener"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName in RawNotificationRecorder.watchedPackages) {
            RawNotificationRecorder.record(this, sbn)
        }
        if (sbn.packageName != GoogleMapsProgressStyleParser.packageName) return
        val navState = runCatching { GoogleMapsProgressStyleParser.parse(sbn.notification.extras) }
            .onFailure { Log.w(TAG, "Failed to parse Maps notification", it) }
            .getOrNull() ?: return
        NavStateHolder.update(navState)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == GoogleMapsProgressStyleParser.packageName) {
            NavStateHolder.update(null)
        }
    }

    // TODO(Waze): branch on sbn.packageName == "com.waze" here into a NavDataParser-based
    // Bundle parser once the on-device notification-capture spike lands (PRD Timeline step 1/3).
}
