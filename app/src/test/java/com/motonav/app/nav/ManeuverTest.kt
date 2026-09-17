package com.motonav.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import uniffi.ferrostar.ManeuverModifier
import uniffi.ferrostar.ManeuverType

class ManeuverTest {

    @Test
    fun `every bucket maps to an icon`() {
        BucketedManeuver.entries.forEach { bucket ->
            assertNotNull("missing icon for $bucket", bucket.icon())
        }
    }

    @Test
    fun `turn type honors modifier direction`() {
        assertEquals(BucketedManeuver.LEFT, fromFerrostarManeuver(ManeuverType.TURN, ManeuverModifier.LEFT))
        assertEquals(BucketedManeuver.RIGHT, fromFerrostarManeuver(ManeuverType.TURN, ManeuverModifier.RIGHT))
        assertEquals(BucketedManeuver.SHARP_LEFT, fromFerrostarManeuver(ManeuverType.TURN, ManeuverModifier.SHARP_LEFT))
        assertEquals(BucketedManeuver.U_TURN_LEFT, fromFerrostarManeuver(ManeuverType.TURN, ManeuverModifier.U_TURN))
    }

    @Test
    fun `roundabout buckets by modifier side`() {
        assertEquals(BucketedManeuver.ROUNDABOUT_LEFT, fromFerrostarManeuver(ManeuverType.ROUNDABOUT, ManeuverModifier.LEFT))
        assertEquals(BucketedManeuver.ROUNDABOUT_RIGHT, fromFerrostarManeuver(ManeuverType.ROTARY, ManeuverModifier.RIGHT))
        assertEquals(
            BucketedManeuver.ROUNDABOUT_STRAIGHT,
            fromFerrostarManeuver(ManeuverType.EXIT_ROUNDABOUT, ManeuverModifier.STRAIGHT),
        )
    }

    @Test
    fun `depart and arrive map to their own buckets`() {
        assertEquals(BucketedManeuver.DEPART, fromFerrostarManeuver(ManeuverType.DEPART, null))
        assertEquals(BucketedManeuver.DESTINATION, fromFerrostarManeuver(ManeuverType.ARRIVE, null))
    }

    @Test
    fun `null type falls back to unknown`() {
        assertEquals(BucketedManeuver.UNKNOWN, fromFerrostarManeuver(null, null))
    }
}
