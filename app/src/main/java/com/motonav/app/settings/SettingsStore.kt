package com.motonav.app.settings

import android.content.Context
import com.motonav.app.ride.AutoLaunchMode
import com.motonav.app.ride.ScreenOnMode
import com.motonav.app.ui.dial.CompassStyle
import com.motonav.app.ui.dial.NavDialConfig

// Small persisted enums/booleans — plain SharedPreferences, no DataStore dependency needed.
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoLaunchMode: AutoLaunchMode
        get() = AutoLaunchMode.valueOf(prefs.getString(KEY_AUTO_LAUNCH, AutoLaunchMode.OFF.name)!!)
        set(value) = prefs.edit().putString(KEY_AUTO_LAUNCH, value.name).apply()

    var screenOnMode: ScreenOnMode
        get() = ScreenOnMode.valueOf(prefs.getString(KEY_SCREEN_ON, ScreenOnMode.NEVER.name)!!)
        set(value) = prefs.edit().putString(KEY_SCREEN_ON, value.name).apply()

    // Stage 4 — nav page element toggles. Only the nav page is wired (plan §5 scope discipline);
    // a second page would add its own key prefix here, not a page/element abstraction.
    var navShowCompass: Boolean
        get() = prefs.getBoolean(KEY_NAV_SHOW_COMPASS, true)
        set(value) = prefs.edit().putBoolean(KEY_NAV_SHOW_COMPASS, value).apply()

    var navCompassStyle: CompassStyle
        get() = CompassStyle.valueOf(prefs.getString(KEY_NAV_COMPASS_STYLE, CompassStyle.TRIANGLE.name)!!)
        set(value) = prefs.edit().putString(KEY_NAV_COMPASS_STYLE, value.name).apply()

    var navShowSpeed: Boolean
        get() = prefs.getBoolean(KEY_NAV_SHOW_SPEED, true)
        set(value) = prefs.edit().putBoolean(KEY_NAV_SHOW_SPEED, value).apply()

    var navShowSpeedLimit: Boolean
        get() = prefs.getBoolean(KEY_NAV_SHOW_SPEED_LIMIT, true)
        set(value) = prefs.edit().putBoolean(KEY_NAV_SHOW_SPEED_LIMIT, value).apply()

    var navShowEta: Boolean
        get() = prefs.getBoolean(KEY_NAV_SHOW_ETA, true)
        set(value) = prefs.edit().putBoolean(KEY_NAV_SHOW_ETA, value).apply()

    var navShowDistanceRemaining: Boolean
        get() = prefs.getBoolean(KEY_NAV_SHOW_DISTANCE_REMAINING, true)
        set(value) = prefs.edit().putBoolean(KEY_NAV_SHOW_DISTANCE_REMAINING, value).apply()

    var navShowStreetName: Boolean
        get() = prefs.getBoolean(KEY_NAV_SHOW_STREET_NAME, true)
        set(value) = prefs.edit().putBoolean(KEY_NAV_SHOW_STREET_NAME, value).apply()

    fun navDialConfig(): NavDialConfig = NavDialConfig(
        showCompass = navShowCompass,
        compassStyle = navCompassStyle,
        showSpeed = navShowSpeed,
        showSpeedLimit = navShowSpeedLimit,
        showEta = navShowEta,
        showDistanceRemaining = navShowDistanceRemaining,
        showStreetName = navShowStreetName,
    )

    private companion object {
        const val PREFS_NAME = "motonav_settings"
        const val KEY_AUTO_LAUNCH = "auto_launch_mode"
        const val KEY_SCREEN_ON = "screen_on_mode"
        const val KEY_NAV_SHOW_COMPASS = "nav_show_compass"
        const val KEY_NAV_COMPASS_STYLE = "nav_compass_style"
        const val KEY_NAV_SHOW_SPEED = "nav_show_speed"
        const val KEY_NAV_SHOW_SPEED_LIMIT = "nav_show_speed_limit"
        const val KEY_NAV_SHOW_ETA = "nav_show_eta"
        const val KEY_NAV_SHOW_DISTANCE_REMAINING = "nav_show_distance_remaining"
        const val KEY_NAV_SHOW_STREET_NAME = "nav_show_street_name"
    }
}
