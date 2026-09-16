package com.motonav.app.notification

import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dumps every extras key/value from Maps + Waze notifications verbatim to an on-device file,
 * for offline analysis after a real drive — see docs/RESEARCH_NOTES.md. Data-collection only,
 * not wired into NavState; safe to delete once Waze support is built and Maps is fully mapped.
 */
object RawNotificationRecorder {
    private const val TAG = "RawNotifCapture"
    val watchedPackages = setOf(GoogleMapsProgressStyleParser.packageName, "com.waze")
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun record(context: Context, sbn: StatusBarNotification) {
        runCatching {
            val extras = sbn.notification.extras
            val fields = extras.keySet().sorted().joinToString(" | ") { key ->
                "$key=${extras.get(key)}".replace('\n', ' ').replace('\r', ' ')
            }
            val line = "${timeFmt.format(Date())} pkg=${sbn.packageName} $fields\n"
            val file = File(context.getExternalFilesDir(null), "nav_capture.log")
            file.appendText(line)
        }.onFailure { Log.w(TAG, "Failed to record notification", it) }
    }
}
