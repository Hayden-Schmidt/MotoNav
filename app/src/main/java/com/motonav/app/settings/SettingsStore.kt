package com.motonav.app.settings

import android.content.Context
import com.motonav.app.ride.AutoLaunchMode
import com.motonav.app.ride.ScreenOnMode

// Two small persisted enums — plain SharedPreferences, no DataStore dependency needed.
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoLaunchMode: AutoLaunchMode
        get() = AutoLaunchMode.valueOf(prefs.getString(KEY_AUTO_LAUNCH, AutoLaunchMode.OFF.name)!!)
        set(value) = prefs.edit().putString(KEY_AUTO_LAUNCH, value.name).apply()

    var screenOnMode: ScreenOnMode
        get() = ScreenOnMode.valueOf(prefs.getString(KEY_SCREEN_ON, ScreenOnMode.NEVER.name)!!)
        set(value) = prefs.edit().putString(KEY_SCREEN_ON, value.name).apply()

    private companion object {
        const val PREFS_NAME = "motonav_settings"
        const val KEY_AUTO_LAUNCH = "auto_launch_mode"
        const val KEY_SCREEN_ON = "screen_on_mode"
    }
}
