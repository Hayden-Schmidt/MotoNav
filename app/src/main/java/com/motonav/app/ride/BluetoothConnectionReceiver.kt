package com.motonav.app.ride

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Forwards BT ACL connect/disconnect to RideSessionService — no decision logic here, keeps
// trigger evaluation in one place. ACL_CONNECTED/DISCONNECTED are exempt from Android 8+
// implicit-broadcast restrictions, so manifest registration works without the app running.
class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, RideSessionService::class.java)
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // Service must call startForeground() promptly after a startForegroundService() launch.
                serviceIntent.putExtra(RideSessionService.EXTRA_BT_EVENT, RideSessionService.BT_EVENT_CONNECTED)
                context.startForegroundService(serviceIntent)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                serviceIntent.putExtra(RideSessionService.EXTRA_BT_EVENT, RideSessionService.BT_EVENT_DISCONNECTED)
                context.startService(serviceIntent)
            }
        }
    }
}
