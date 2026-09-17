package com.motonav.app.navsdk

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.google.android.libraries.mapsplatform.turnbyturn.TurnByTurnManager

/**
 * Receives turn-by-turn navigation updates from the Navigation SDK's `Navigator`, per Google's
 * documented pattern (developers.google.com/maps/documentation/navigation/android-sdk/tbt-feed).
 *
 * This service only *receives* updates — it does not itself start navigation or hold a
 * `Navigator` instance. Registration (`navigator.registerServiceForNavUpdates(...)`) happens
 * wherever the `Navigator` lives, which requires:
 *   1. A `NavigationApi.getNavigator(activity, callback)` call from an Activity context
 *      (needs the Navigation SDK's terms-of-use dialog to be accepted at least once).
 *   2. A destination request (`Navigator.setDestination(Waypoint)`) — MotoNav's own destination
 *      search/entry UI, which does not exist yet. This is new UI surface, not a data-model change
 *      — deliberately out of scope for this file. See docs/MotoNav_UI_SPEC.md for where a
 *      destination-entry screen fits alongside the round-dial design.
 *
 * Until both of those exist, this service is wired but inert — it will simply never receive a
 * bound connection, since nothing calls `registerServiceForNavUpdates` yet.
 */
class NavSdkTurnByTurnService : Service() {

    private lateinit var turnByTurnManager: TurnByTurnManager
    private lateinit var incomingMessenger: Messenger

    private inner class IncomingNavInfoHandler(looper: android.os.Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            if (msg.what == TurnByTurnManager.MSG_NAV_INFO) {
                val navInfo = turnByTurnManager.readNavInfoFromBundle(msg.data)
                NavSdkStateHolder.update(navInfo?.toUiState())
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        turnByTurnManager = TurnByTurnManager.createInstance()
        val thread = HandlerThread("NavSdkTurnByTurnService", Process.THREAD_PRIORITY_DEFAULT)
        thread.start()
        incomingMessenger = Messenger(IncomingNavInfoHandler(thread.looper))
    }

    override fun onBind(intent: Intent?): IBinder = incomingMessenger.binder

    override fun onDestroy() {
        // Navigator-side unregistration (navigator.unregisterServiceForNavUpdates()) belongs with
        // whatever owns the Navigator instance, not here — this service has no reference to it.
        super.onDestroy()
    }
}
