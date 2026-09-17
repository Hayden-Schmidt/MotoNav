package com.motonav.app.ride

import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uniffi.ferrostar.CourseOverGround
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Speed
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind

class LocalRouteProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun userLocation(course: CourseOverGround? = null) = UserLocation(
        coordinates = GeographicCoordinate(-36.85, 174.76),
        horizontalAccuracy = 3.0,
        courseOverGround = course,
        timestamp = Instant.EPOCH,
        speed = Speed(0.0, null),
    )

    private fun waypoint(kind: WaypointKind) = Waypoint(
        coordinate = GeographicCoordinate(-36.73, 174.70),
        kind = kind,
        properties = null,
    )

    @Test
    fun `request json requests osrm format and the given profile`() {
        val json = buildValhallaRouteRequestJson(userLocation(), listOf(waypoint(WaypointKind.BREAK)), "motorcycle")
        assertTrue(json.contains("\"format\":\"osrm\""))
        assertTrue(json.contains("\"costing\":\"motorcycle\""))
    }

    @Test
    fun `request json carries the start coordinate and street side tolerance floor`() {
        val json = buildValhallaRouteRequestJson(userLocation(), listOf(waypoint(WaypointKind.BREAK)), "motorcycle")
        assertTrue(json.contains("\"lat\":-36.85"))
        assertTrue(json.contains("\"lon\":174.76"))
        // horizontalAccuracy=3.0 is below the 5m floor ferrostar's own generator enforces.
        assertTrue(json.contains("\"street_side_tolerance\":5"))
    }

    @Test
    fun `request json omits heading with no course and includes it with one`() {
        assertFalse(
            buildValhallaRouteRequestJson(userLocation(course = null), listOf(waypoint(WaypointKind.BREAK)), "motorcycle")
                .contains("heading"),
        )
        val withCourse = buildValhallaRouteRequestJson(
            userLocation(course = CourseOverGround(degrees = 90u, accuracy = null)),
            listOf(waypoint(WaypointKind.BREAK)),
            "motorcycle",
        )
        assertTrue(withCourse.contains("\"heading\":90"))
    }

    @Test
    fun `waypoint kind maps to valhalla's break-via vocabulary`() {
        assertTrue(
            buildValhallaRouteRequestJson(userLocation(), listOf(waypoint(WaypointKind.BREAK)), "motorcycle")
                .contains("\"type\":\"break\""),
        )
        assertTrue(
            buildValhallaRouteRequestJson(userLocation(), listOf(waypoint(WaypointKind.VIA)), "motorcycle")
                .contains("\"type\":\"via\""),
        )
    }

    @Test
    fun `tiles not ready until the tarball is side-loaded`() {
        val externalFiles = tmp.newFolder()
        assertFalse(LocalTileFiles.tilesReady(externalFiles))

        val tar = LocalTileFiles.tileTarFile(externalFiles)
        tar.parentFile?.mkdirs()
        tar.writeText("not a real tarball, just a marker for the test")

        assertTrue(LocalTileFiles.tilesReady(externalFiles))
    }

    @Test
    fun `tile and elevation paths sit under the app's external files dir`() {
        val externalFiles = tmp.newFolder()
        assertEquals(File(externalFiles, "valhalla/valhalla_tiles.tar").path, LocalTileFiles.tileTarFile(externalFiles).path)
        assertEquals(File(externalFiles, "elevation").path, LocalTileFiles.elevationDir(externalFiles).path)
    }
}
