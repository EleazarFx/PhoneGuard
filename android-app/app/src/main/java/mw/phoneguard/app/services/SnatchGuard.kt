package mw.phoneguard.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch
import mw.phoneguard.app.MainActivity
import mw.phoneguard.app.R
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore

class SnatchGuard(
    private val context: Context,
    private val gracePeriodMs: Long = 20_000L
) {

    private val TAG = "PhoneGuardSnatchGuard"

    private val alarmPlayer = AlarmPlayer(context)
    private val handler = Handler(Looper.getMainLooper())

    private var isActive = false
    private var unlockReceiver: BroadcastReceiver? = null

    private val gracePeriodRunnable = Runnable {
        onGracePeriodExpired()
    }

    fun start() {
        if (isActive) {
            Log.d(TAG, "SnatchGuard already active, ignoring duplicate trigger")
            return
        }

        Log.w(TAG, "SnatchGuard activated — alarm starting, grace period ${gracePeriodMs}ms")
        isActive = true

        alarmPlayer.play()
        registerUnlockReceiver()
        showGracePeriodNotification()

        handler.postDelayed(gracePeriodRunnable, gracePeriodMs)
    }

    fun stop() {
        if (!isActive) return
        Log.i(TAG, "SnatchGuard stopping")
        isActive = false
        handler.removeCallbacks(gracePeriodRunnable)
        unregisterUnlockReceiver()
        alarmPlayer.stop()
        cancelGraceNotification()
    }

    // ─────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────

    private fun showGracePeriodNotification() {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureNotificationChannel(manager)

        // Tapping the notification opens the app so the user can unlock normally
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, GRACE_CHANNEL_ID)
            .setContentTitle("PhoneGuard · Alarm Active")
            .setContentText("Unlock within ${gracePeriodMs / 1000} seconds to silence")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .build()

        manager.notify(GRACE_NOTIFICATION_ID, notification)
        Log.i(TAG, "Grace-period notification shown")
    }

    private fun cancelGraceNotification() {
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.cancel(GRACE_NOTIFICATION_ID)
            Log.i(TAG, "Grace-period notification cancelled")
        } catch (e: Exception) {
            Log.w(TAG, "cancelGraceNotification: ${e.message}")
        }
    }

    private fun ensureNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = manager.getNotificationChannel(GRACE_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    GRACE_CHANNEL_ID,
                    "PhoneGuard Alarm",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Shown when Snatch Guard alarm is active"
                    setShowBadge(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 300, 500)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    // ─────────────────────────────────────────────────────
    // Unlock listener
    // ─────────────────────────────────────────────────────

    private fun registerUnlockReceiver() {
        if (unlockReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) {
                    Log.i(TAG, "User unlocked during grace period — silencing alarm")
                    onUnlockedInTime()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        unlockReceiver = receiver
    }

    private fun unregisterUnlockReceiver() {
        unlockReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterReceiver: ${e.message}")
            }
            unlockReceiver = null
        }
    }

    // ─────────────────────────────────────────────────────
    // State transitions
    // ─────────────────────────────────────────────────────

    private fun onUnlockedInTime() {
        Log.i(TAG, "False alarm — owner unlocked within grace period")
        queueEvent(type = "false_alarm")
        stop()
    }

    private fun onGracePeriodExpired() {
        if (!isActive) return
        Log.w(TAG, "!!! Grace period expired — Snatch Guard LOCKING PHONE !!!")
        lockPhone()
        queueEvent(type = "snatch", forceHighThreat = true)
        stop()
    }

    private fun lockPhone() {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as? DevicePolicyManager
            if (dpm == null) {
                Log.e(TAG, "DevicePolicyManager unavailable — cannot lock")
                return
            }
            val admin = ComponentName(context, PhoneGuardAdminReceiver::class.java)
            if (!dpm.isAdminActive(admin)) {
                Log.e(TAG, "Device admin not active — cannot lock")
                return
            }
            dpm.lockNow()
            Log.w(TAG, "Phone locked via DevicePolicyManager.lockNow()")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lock phone: ${e.message}", e)
        }
    }

    private fun queueEvent(type: String, forceHighThreat: Boolean = false) {
        val store = TokenStore.get(context.applicationContext)
        val deviceId = store.getDeviceId() ?: run {
            Log.w(TAG, "No device registered — cannot queue $type event")
            return
        }

        val app = context.applicationContext as? mw.phoneguard.app.PhoneGuardApplication
        val scope = app?.appScope
        if (scope == null) {
            Log.e(TAG, "appScope not available — cannot queue $type event")
            return
        }

        scope.launch {
            try {
                val db = PhoneGuardDatabase.get(context.applicationContext)
                var event = EventEntity(
                    deviceId = deviceId,
                    type = type,
                    timestamp = System.currentTimeMillis(),
                    captureStatus = if (type == "snatch") "pending_capture" else "n/a",
                    clientSeq = System.nanoTime()
                )
                event = EvidenceCapturer(context.applicationContext).captureAndAttachEvidence(event)
                db.eventDao().upsert(event)
                Log.i(TAG, "$type event queued with evidence: ${event.id}${if (forceHighThreat) " (High threat)" else ""}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue $type event", e)
            }
        }
    }

    companion object {
        private const val GRACE_CHANNEL_ID = "phoneguard_grace"
        private const val GRACE_NOTIFICATION_ID = 1002
    }
}