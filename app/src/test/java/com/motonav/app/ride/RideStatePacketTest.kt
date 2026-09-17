package com.motonav.app.ride

import com.motonav.app.location.RideSensors
import com.motonav.app.nav.BucketedManeuver
import org.junit.Assert.assertEquals
import org.junit.Test

class RideStatePacketTest {

    @Test
    fun `no ride state packs idle sentinels`() {
        val packet = packRideStatePacket(null, RideSensors(speedKmh = null, headingDegrees = null))
        assertEquals(RIDE_STATE_PACKET_SIZE, packet.size)
        assertEquals(1, packet[0].toInt()) // version
        assertEquals(3, packet[1].toInt()) // PHASE_IDLE
        assertEquals(0xFF, packet[2].toInt() and 0xFF) // no maneuver
        assertEquals(0xFFFF, readU16(packet, 3)) // distance to turn unknown
        assertEquals(0xFF, packet[9].toInt() and 0xFF) // speed unknown
        assertEquals(0xFFFF, readU16(packet, 10)) // heading unknown
    }

    @Test
    fun `active navigating state round-trips its fields`() {
        val rideState = RideState(
            phase = RidePhase.ENROUTE,
            maneuver = BucketedManeuver.LEFT,
            distanceToNextManeuverMeters = 250,
            distanceRemainingMeters = 4_530,
            durationRemainingSeconds = 300,
        )
        val sensors = RideSensors(speedKmh = 82.4f, headingDegrees = 358.6f)

        val packet = packRideStatePacket(rideState, sensors)

        assertEquals(RidePhase.ENROUTE.ordinal, packet[1].toInt())
        assertEquals(BucketedManeuver.LEFT.ordinal, packet[2].toInt() and 0xFF)
        assertEquals(250, readU16(packet, 3))
        assertEquals(453, readU16(packet, 5)) // 4530m in units of 10m
        assertEquals(300, readU16(packet, 7))
        assertEquals(82, packet[9].toInt() and 0xFF) // rounded
        assertEquals(359, readU16(packet, 10)) // rounded, still < 360
    }

    @Test
    fun `heading rounding to 360 wraps to 0`() {
        val rideState = RideState(RidePhase.ENROUTE, BucketedManeuver.STRAIGHT, 0, 0, 0)
        val packet = packRideStatePacket(rideState, RideSensors(speedKmh = 0f, headingDegrees = 359.6f))
        assertEquals(0, readU16(packet, 10))
    }

    @Test
    fun `values beyond the representable range clamp, not wrap`() {
        val rideState = RideState(RidePhase.ENROUTE, BucketedManeuver.STRAIGHT, 200_000, 700_000, 100_000)
        val packet = packRideStatePacket(rideState, RideSensors(speedKmh = 400f, headingDegrees = 0f))
        assertEquals(0xFFFE, readU16(packet, 3)) // clamped below the 0xFFFF unknown sentinel
        assertEquals(0xFE, packet[9].toInt() and 0xFF) // clamped below the 0xFF unknown sentinel
    }

    private fun readU16(packet: ByteArray, offset: Int): Int =
        (packet[offset].toInt() and 0xFF) or ((packet[offset + 1].toInt() and 0xFF) shl 8)
}
