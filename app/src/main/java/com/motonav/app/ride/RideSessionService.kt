package com.motonav.app.ride

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.motonav.app.MainActivity
import com.motonav.app.notification.NavStateHolder
import com.motonav.app.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

// Owns ride-session lifecycle: reacts to BT connect/disconnect (via BluetoothConnectionReceiver)
// and nav-start (by observing NavStateHolder directly), evaluates the auto-launch setting, and
// holds the wake lock + foreground state gated on BT-connected per PRD (only while BT connected).
// Started by NavNotificationListenerService.onListenerConnected() — alive exactly as long as the
// OS already keeps the notification listener bound, no separate always-on component.
class RideSessionService : Service() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var btConnected = false
    private var scope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        createNotificationChannel()

        val job = Job()
        scope = CoroutineScope(Dispatchers.Default + job).also { s ->
            NavStateHolder.state
                .map { it != null }
                .distinctUntilChanged()
                .drop(1) // skip the initial value — only react to transitions
                .onEach { isActive -> if (isActive) onEvent(TriggerEvent.NAV_START) }
                .launchIn(s)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(EXTRA_BT_EVENT)) {
            BT_EVENT_CONNECTED -> {
                btConnected = true
                startForegroundSession()
                onEvent(TriggerEvent.BT_CONNECTED)
            }
            BT_EVENT_DISCONNECTED -> {
                btConnected = false
                stopForegroundSession()
            }
        }
        return START_STICKY
    }

    private fun onEvent(event: TriggerEvent) {
        if (shouldLaunch(settingsStore.autoLaunchMode, event, btConnected)) {
            val launchIntent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }
    }

    private fun startForegroundSession() {
        if (wakeLock?.isHeld == true) return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MotoNav:ride").apply { acquire() }
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun stopForegroundSession() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MotoNav ride active")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Ride session", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopForegroundSession()
        scope?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_BT_EVENT = "bt_event"
        const val BT_EVENT_CONNECTED = "connected"
        const val BT_EVENT_DISCONNECTED = "disconnected"
        private const val CHANNEL_ID = "ride_session"
        private const val NOTIFICATION_ID = 1
    }
}
