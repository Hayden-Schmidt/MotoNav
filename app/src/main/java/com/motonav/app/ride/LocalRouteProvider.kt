package com.motonav.app.ride

import android.content.Context
import com.motonav.app.nav.buildValhallaHeightRequestJson
import com.motonav.app.nav.parseValhallaHeights
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteAdapter
import uniffi.ferrostar.RouteAdapterInterface
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.WellKnownRouteProvider

/**
 * Phase C — routing against on-device Valhalla tiles, no network. See
 * docs/MotoNav_REBUILD_PLAN_OSM.md Phase C.
 *
 * Tile sourcing (deliberately NOT built or shipped by this change — plan Phase C item 2):
 * download or build an NZ-only Valhalla tile tarball (e.g. `valhalla_build_tiles` against a
 * `New Zealand` extract from https://download.geofabrik.de, or a prebuilt NZ tarball from the
 * Valhalla/OSM community — see the #valhalla-mobile channel linked from valhalla-mobile's
 * README) and side-load it onto the device with:
 *
 *   adb push valhalla_tiles.tar /sdcard/Android/data/com.motonav.app/files/valhalla/valhalla_tiles.tar
 *
 * (or copy the same file over USB/MTP into that app-external-files path — no in-app downloader,
 * no dynamic multi-region sync; this is a single periodically-rebuilt tarball, replaced by
 * pushing a new file over the old one). Elevation is optional and separate: side-load skadi
 * `.hgt`/`.hgt.gz` tiles for the same NZ extent (e.g. from a Mapzen/AWS terrain-tiles mirror) into
 * `.../files/elevation/` the same way. [LocalTileFiles.tilesReady] is what settings/diagnostics
 * should check before offering the LOCAL router backend.
 */
object LocalTileFiles {
    private const val TILE_SUBDIR = "valhalla"
    private const val TILE_TAR_NAME = "valhalla_tiles.tar"
    private const val ELEVATION_SUBDIR = "elevation"

    fun tileTarFile(externalFilesDir: File): File = File(File(externalFilesDir, TILE_SUBDIR), TILE_TAR_NAME)

    fun elevationDir(externalFilesDir: File): File = File(externalFilesDir, ELEVATION_SUBDIR)

    /** True once a tarball has been side-loaded per the class doc — false leaves height() null too. */
    fun tilesReady(externalFilesDir: File): Boolean = tileTarFile(externalFilesDir).isFile
}

/**
 * Feeds Ferrostar's [CustomRouteProvider] hook from the on-device Valhalla engine
 * (`io.github.rallista:valhalla-mobile`) instead of an HTTP call. Builds the exact request body
 * Ferrostar's own remote Valhalla path sends (see ferrostar's `ValhallaHttpRequestGenerator` /
 * `generate_request` — format `osrm`, same filter set, banner + voice instructions on) and hands
 * the raw response to that same path's OSRM decoder, so parsing isn't duplicated: the remote and
 * local backends produce identical [Route] objects to the rest of the app.
 *
 * The elevation tile directory (Phase C item 4) is wired into the engine's config here — without
 * it, valhalla-mobile's `height` action always returns null. Nothing in this app calls `height`
 * yet (no elevation field on [RideState]/the dial — out of scope for this phase, see
 * [sampleElevation]); the action is wired and callable, ready for whichever phase surfaces it.
 */
class LocalValhallaRouteProvider(
    private val context: Context,
    private val profile: String,
) : CustomRouteProvider, AutoCloseable {

    // Reused purely for its OSRM response parser (see class doc) — endpointUrl/optionsJson are
    // never sent anywhere locally.
    private val responseAdapter: RouteAdapterInterface =
        RouteAdapter.fromWellKnownRouteProvider(
            WellKnownRouteProvider.Valhalla(endpointUrl = "", profile = profile, optionsJson = "{}"),
        )

    // Built once and reused across requests: construction mmaps the tile extract (valhalla-mobile
    // README "Lifecycle" — expensive, do it once). If the tarball hasn't been side-loaded yet
    // (LocalTileFiles.tilesReady == false), construction still succeeds; requests fail with a
    // ValhallaException instead, which the existing catch in startGuidanceToDestination logs.
    private val engine: Valhalla by lazy {
        val externalFilesDir = context.getExternalFilesDir(null)
            ?: error("No external files dir — cannot locate offline Valhalla tiles")
        val config = ValhallaConfigFactory.usingTileExtract(
            tileExtractTar = LocalTileFiles.tileTarFile(externalFilesDir).absolutePath,
            elevationDir = LocalTileFiles.elevationDir(externalFilesDir).absolutePath,
        )
        Valhalla(context, config)
    }

    override suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> =
        withContext(Dispatchers.IO) {
            val requestJson = buildValhallaRouteRequestJson(userLocation, waypoints, profile)
            val responseBytes = engine.routeRaw(requestJson).toByteArray(Charsets.UTF_8)
            responseAdapter.parseResponse(responseBytes)
        }

    /**
     * Phase C item 4 — Valhalla's `height` action, sampling elevation under [shape] from the
     * elevation tile directory wired into [engine]'s config above. Returns null per point where
     * no elevation tile covers it (or none has been side-loaded — see [LocalTileFiles]). Not
     * called from anywhere yet: no elevation field exists on [RideState]/the dial, and adding one
     * is out of scope for this phase — exposed here so that phase can call it directly.
     */
    fun sampleElevation(shape: List<GeographicCoordinate>): List<Double?> =
        parseValhallaHeights(engine.heightRaw(buildValhallaHeightRequestJson(shape)))

    override fun close() {
        engine.close()
    }
}

// Mirrors ferrostar's own ValhallaHttpRequestGenerator.generate_request() body exactly, so the
// on-device engine and the remote FOSSGIS/Stadia path request (and can parse) the same shape. See
// https://github.com/stadiamaps/ferrostar common/ferrostar/src/routing_adapters/valhalla.rs.
// Plain string building, not org.json: this needs to run (and be tested) as pure JVM logic, and
// the shape is fixed and small enough that a JSON library buys nothing here.
fun buildValhallaRouteRequestJson(
    userLocation: UserLocation,
    waypoints: List<Waypoint>,
    profile: String,
): String {
    require(waypoints.isNotEmpty()) { "Need at least one waypoint" }

    val streetSideTolerance = maxOf(5, userLocation.horizontalAccuracy.toInt())
    val start = buildString {
        append("{\"lat\":${userLocation.coordinates.lat},\"lon\":${userLocation.coordinates.lng}")
        append(",\"street_side_tolerance\":$streetSideTolerance")
        userLocation.courseOverGround?.let { append(",\"heading\":${it.degrees}") }
        append('}')
    }
    val rest = waypoints.joinToString(",") { wp ->
        val kind = if (wp.kind == WaypointKind.BREAK) "break" else "via"
        "{\"lat\":${wp.coordinate.lat},\"lon\":${wp.coordinate.lng},\"type\":\"$kind\"}"
    }

    return "{\"format\":\"osrm\"," +
        "\"filters\":{\"action\":\"include\",\"attributes\":[" +
        "\"shape_attributes.speed\",\"shape_attributes.speed_limit\"," +
        "\"shape_attributes.time\",\"shape_attributes.length\"]}," +
        "\"banner_instructions\":true,\"voice_instructions\":true," +
        "\"costing\":\"$profile\"," +
        "\"locations\":[$start,$rest]}"
}
