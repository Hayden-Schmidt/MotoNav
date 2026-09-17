package com.motonav.app.settings

import android.content.Context
import com.motonav.app.ride.AutoLaunchMode
import com.motonav.app.ride.DEFAULT_GEOCODER_BASE_URL
import com.motonav.app.ride.RouterBackend
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

    // User-configurable from day one, never hardcoded — see
    // docs/MotoNav_REBUILD_PLAN_OSM.md Phase A item 4.
    var routerBackend: RouterBackend
        get() = RouterBackend.valueOf(prefs.getString(KEY_ROUTER_BACKEND, RouterBackend.FOSSGIS.name)!!)
        set(value) = prefs.edit().putString(KEY_ROUTER_BACKEND, value.name).apply()

    // Only meaningful for RouterBackend.STADIA_MAPS. Never shipped in the repo — plan §5.1.
    var routerApiKey: String
        get() = prefs.getString(KEY_ROUTER_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ROUTER_API_KEY, value).apply()

    // Phase E item 1 — geocoding endpoint, configurable exactly like the router (plan §5.1: never
    // hardcode a shared endpoint). Defaults to Photon's public demo server.
    var geocoderBaseUrl: String
        get() = prefs.getString(KEY_GEOCODER_BASE_URL, DEFAULT_GEOCODER_BASE_URL) ?: DEFAULT_GEOCODER_BASE_URL
        set(value) = prefs.edit().putString(KEY_GEOCODER_BASE_URL, value).apply()

    // Phase E — the user's chosen destination, replacing the Phase A hardcoded Albany test point.
    // Null (absent) means "no destination chosen yet"; RideSessionService leaves the dial idle
    // rather than fabricating one. Plain lat/lng floats, not a richer "saved place" model — nobody
    // asked for saved places yet (YAGNI).
    var destinationLat: Float?
        get() = if (prefs.contains(KEY_DEST_LAT)) prefs.getFloat(KEY_DEST_LAT, 0f) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_DEST_LAT) else putFloat(KEY_DEST_LAT, value)
        }.apply()

    var destinationLng: Float?
        get() = if (prefs.contains(KEY_DEST_LNG)) prefs.getFloat(KEY_DEST_LNG, 0f) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_DEST_LNG) else putFloat(KEY_DEST_LNG, value)
        }.apply()

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

    // Phase B — route line toggle, same element-toggle model as the rest of the nav page.
    var navShowRouteLine: Boolean
        get() = prefs.getBoolean(KEY_NAV_SHOW_ROUTE_LINE, true)
        set(value) = prefs.edit().putBoolean(KEY_NAV_SHOW_ROUTE_LINE, value).apply()

    fun navDialConfig(): NavDialConfig = NavDialConfig(
        showCompass = navShowCompass,
        compassStyle = navCompassStyle,
        showSpeed = navShowSpeed,
        showSpeedLimit = navShowSpeedLimit,
        showEta = navShowEta,
        showDistanceRemaining = navShowDistanceRemaining,
        showStreetName = navShowStreetName,
        showRouteLine = navShowRouteLine,
    )

    private companion object {
        const val PREFS_NAME = "motonav_settings"
        const val KEY_AUTO_LAUNCH = "auto_launch_mode"
        const val KEY_SCREEN_ON = "screen_on_mode"
        const val KEY_ROUTER_BACKEND = "router_backend"
        const val KEY_ROUTER_API_KEY = "router_api_key"
        const val KEY_GEOCODER_BASE_URL = "geocoder_base_url"
        const val KEY_DEST_LAT = "destination_lat"
        const val KEY_DEST_LNG = "destination_lng"
        const val KEY_NAV_SHOW_COMPASS = "nav_show_compass"
        const val KEY_NAV_COMPASS_STYLE = "nav_compass_style"
        const val KEY_NAV_SHOW_SPEED = "nav_show_speed"
        const val KEY_NAV_SHOW_SPEED_LIMIT = "nav_show_speed_limit"
        const val KEY_NAV_SHOW_ETA = "nav_show_eta"
        const val KEY_NAV_SHOW_DISTANCE_REMAINING = "nav_show_distance_remaining"
        const val KEY_NAV_SHOW_STREET_NAME = "nav_show_street_name"
        const val KEY_NAV_SHOW_ROUTE_LINE = "nav_show_route_line"
    }
}
