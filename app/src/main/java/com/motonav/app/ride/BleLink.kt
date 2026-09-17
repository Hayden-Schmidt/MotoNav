package com.motonav.app.ride

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.UUID

/**
 * BLE peripheral side of Phase D: a GATT server advertising one service with one NOTIFY
 * characteristic, updated with [packRideStatePacket]'s bytes — see that file's KDoc for the wire
 * format the ESP32 firmware decodes. The phone is the peripheral/advertiser and the puck is the
 * central that connects and subscribes; that's the smaller diff on the phone (no scanning, no
 * pairing UI, no target-address setting) and matches how bikenavi_esp32-style pucks work.
 *
 * ponytail: no bonding/encryption — the puck is a passive display, and BLE-classic pairing UX is
 * a distribution-tooling concern explicitly out of scope for this phase (plan §Phase D item 3).
 * Add if the puck design ever needs to keep anything private.
 */
class BleLink(private val context: Context) {

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private var gattServer: BluetoothGattServer? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private val subscribedDevices = mutableSetOf<BluetoothDevice>()
    private var lastPacket: ByteArray = ByteArray(RIDE_STATE_PACKET_SIZE)

    @SuppressLint("MissingPermission") // guarded by hasPermissions() below
    fun start() {
        val adapter = bluetoothManager?.adapter
        if (adapter?.isEnabled != true || !hasPermissions()) {
            Log.w(TAG, "Bluetooth off or BLE permissions not granted — puck link disabled")
            return
        }

        val char = BluetoothGattCharacteristic(
            RIDE_STATE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    CLIENT_CONFIG_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
                ),
            )
        }
        characteristic = char

        val service = BluetoothGattService(MOTONAV_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(char)

        val server = bluetoothManager.openGattServer(context, gattServerCallback)
        server?.addService(service)
        gattServer = server

        startAdvertising(adapter)
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(adapter: BluetoothAdapter) {
        val advertiser = adapter.bluetoothLeAdvertiser ?: run {
            Log.w(TAG, "No BLE advertiser on this device — puck link disabled")
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(android.os.ParcelUuid(MOTONAV_SERVICE_UUID))
            .setIncludeDeviceName(true)
            .build()
        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!hasPermissions()) return
        bluetoothManager?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
        characteristic = null
        subscribedDevices.clear()
    }

    /** Notifies every subscribed central with the latest packet. No-op if nobody is subscribed. */
    @SuppressLint("MissingPermission")
    fun update(packet: ByteArray) {
        lastPacket = packet
        val char = characteristic ?: return
        val server = gattServer ?: return
        char.value = packet
        for (device in subscribedDevices) {
            server.notifyCharacteristicChanged(device, char, false)
        }
    }

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true // legacy BLUETOOTH_ADMIN, normal perm
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "BLE advertise failed to start: $errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState != BluetoothGatt.STATE_CONNECTED) subscribedDevices.remove(device)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, lastPacket)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                subscribedDevices.add(device)
            } else {
                subscribedDevices.remove(device)
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    companion object {
        private const val TAG = "BleLink"

        // Fixed, MotoNav-specific 128-bit UUIDs — same pair the firmware side must hardcode to
        // find this service and characteristic.
        val MOTONAV_SERVICE_UUID: UUID = UUID.fromString("c9c6d0a0-0001-4f0a-9c8e-2f6b1a2d3e4f")
        val RIDE_STATE_CHARACTERISTIC_UUID: UUID = UUID.fromString("c9c6d0a0-0002-4f0a-9c8e-2f6b1a2d3e4f")
        private val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
