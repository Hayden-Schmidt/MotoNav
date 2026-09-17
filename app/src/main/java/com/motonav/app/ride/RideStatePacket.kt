package com.motonav.app.ride

import com.motonav.app.location.RideSensors

/**
 * Phase D wire format — the byte layout [BleLink] notifies over BLE to the ESP32 puck. Pure
 * function, no android.bluetooth import, so it's unit-testable on the JVM; [BleLink] itself is
 * the untestable (real-hardware-only) GATT/advertising plumbing around this.
 *
 * FIRMWARE NOTE — this is the contract the ESP32 side decodes against. 12 bytes, little-endian,
 * fits in one notification at the default 23-byte MTU (20-byte ATT payload after the 3-byte
 * header). Offsets:
 *
 * | Offset | Size | Field                          | Encoding                                   |
 * |--------|------|---------------------------------|---------------------------------------------|
 * | 0      | 1    | protocol version                | constant `1`, bump on any layout change     |
 * | 1      | 1    | phase                            | 0=ENROUTE 1=REROUTING 2=ARRIVED 3=IDLE      |
 * | 2      | 1    | maneuver                        | com.motonav.app.nav.BucketedManeuver.ordinal, 0xFF = none |
 * | 3      | 2    | distance to next maneuver (m)   | uint16 LE, 0xFFFF = unknown                 |
 * | 5      | 2    | distance remaining (x10 m)      | uint16 LE, 0xFFFF = unknown                 |
 * | 7      | 2    | duration remaining (s)          | uint16 LE, 0xFFFF = unknown                 |
 * | 9      | 1    | speed (km/h, rounded)           | uint8, 0xFF = unknown                       |
 * | 10     | 2    | heading (degrees, 0-359)        | uint16 LE, 0xFFFF = unknown                 |
 *
 * All "unknown" sentinels are the field's max representable value — never a fabricated 0, same
 * null-means-no-reading contract as [com.motonav.app.ride.RideState] and [RideSensors] upstream.
 */
const val RIDE_STATE_PACKET_SIZE = 12
private const val PROTOCOL_VERSION: Int = 1
private const val PHASE_IDLE = 3
private const val U8_UNKNOWN = 0xFF
private const val U16_UNKNOWN = 0xFFFF

fun packRideStatePacket(rideState: RideState?, sensors: RideSensors): ByteArray {
    val phaseCode = rideState?.phase?.ordinal ?: PHASE_IDLE
    val maneuverCode = rideState?.maneuver?.ordinal ?: U8_UNKNOWN
    val distanceToTurn = rideState?.distanceToNextManeuverMeters.toU16()
    val distanceRemainingX10 = rideState?.distanceRemainingMeters?.let { it / 10 }.toU16()
    val durationRemaining = rideState?.durationRemainingSeconds.toU16()
    val speed = sensors.speedKmh?.let { Math.round(it) }.toU8()
    val heading = sensors.headingDegrees?.let { Math.round(it).mod(360) }.toU16()

    val packet = ByteArray(RIDE_STATE_PACKET_SIZE)
    packet[0] = PROTOCOL_VERSION.toByte()
    packet[1] = phaseCode.toByte()
    packet[2] = maneuverCode.toByte()
    packet.putU16(3, distanceToTurn)
    packet.putU16(5, distanceRemainingX10)
    packet.putU16(7, durationRemaining)
    packet[9] = speed.toByte()
    packet.putU16(10, heading)
    return packet
}

// Clamp to the representable range rather than wrapping — a very large real value should read as
// "far/long", not silently truncate into a wrong nearby value.
private fun Int?.toU16(): Int = when {
    this == null -> U16_UNKNOWN
    this < 0 -> 0
    this >= U16_UNKNOWN -> U16_UNKNOWN - 1
    else -> this
}

private fun Int?.toU8(): Int = when {
    this == null -> U8_UNKNOWN
    this < 0 -> 0
    this >= U8_UNKNOWN -> U8_UNKNOWN - 1
    else -> this
}

private fun ByteArray.putU16(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
}
