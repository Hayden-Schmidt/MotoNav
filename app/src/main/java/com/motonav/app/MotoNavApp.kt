package com.motonav.app

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import org.maplibre.android.MapLibre

// Zero-dependency crash logging for a single-user POC — writes uncaught exceptions to a local
// file, inspectable via `adb pull`/`adb logcat`. Upgrade to Crashlytics if remote visibility is
// ever needed; not justified for Phase 1.
class MotoNavApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Phase E item 2 — MapLibre Native's one-time init, needed before any MapView is created.
        MapLibre.getInstance(this)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(filesDir, "crash.log").appendText("${System.currentTimeMillis()}\n$sw\n")
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
