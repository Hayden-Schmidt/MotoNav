package com.motonav.app.notification

import android.util.Log
import me.trevi.navparser.lib.NavigationNotification
import me.trevi.navparser.service.NavigationListener

/**
 * Captures Google Maps navigation notifications via navparser's NavigationListener, which
 * owns notification filtering/debouncing/RemoteViews parsing internally (see
 * docs/RESEARCH_NOTES.md — navparser has no public Bundle-based parse API, so this class
 * extends its service rather than implementing NavDataParser directly).
 *
 * NOTE: Notification access must be granted manually by the user via
 * Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS — it is not a normal runtime permission
 * and resets on every reinstall during development. See docs/SETUP.md.
 */
class NavNotificationListenerService : NavigationListener() {

    companion object {
        private const val TAG = "NavListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        enabled = true
    }

    override fun onNavigationNotificationAdded(navNotification: NavigationNotification) =
        publish(navNotification)

    override fun onNavigationNotificationUpdated(navNotification: NavigationNotification) =
        publish(navNotification)

    override fun onNavigationNotificationRemoved(navNotification: NavigationNotification) {
        NavStateHolder.update(null)
    }

    private fun publish(navNotification: NavigationNotification) {
        val navState = runCatching { GoogleMapsNavMapper.toNavState(navNotification.navigationData) }
            .onFailure { Log.w(TAG, "Failed to map NavigationData", it) }
            .getOrNull() ?: return
        NavStateHolder.update(navState)
    }

    // TODO(Waze): override onNotificationPosted/onNotificationRemoved here (calling super
    // first so Maps handling via NavigationListener still runs), branching on
    // sbn.packageName == "com.waze" into a NavDataParser-based Bundle parser once the
    // on-device notification-capture spike lands (PRD Timeline step 1/3).
}
