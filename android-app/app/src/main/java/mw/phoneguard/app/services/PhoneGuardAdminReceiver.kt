package mw.phoneguard.app.services

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore
import mw.phoneguard.app.data.sync.SyncManager

class PhoneGuardAdminReceiver : DeviceAdminReceiver() {

    private val TAG = "PhoneGuardAdmin"

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin ENABLED")
        val tokenStore = TokenStore.get(context.applicationContext)
        tokenStore.resetTamperCount()
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "!!! WRONG LOCK-SCREEN PIN DETECTED !!!")
        queueWrongPinEvent(context)
        PhoneGuardService.triggerDecoy(context)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Correct PIN entered")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device admin DISABLED — tamper signal")

        val appContext = context.applicationContext
        val tokenStore = TokenStore.get(appContext)
        val tamperCount = tokenStore.incrementTamperCount()

        queueEvent(appContext, "tamper")

        val app = appContext as? mw.phoneguard.app.PhoneGuardApplication
        val scope = app?.appScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                // 1. Sync tamper event immediately
                SyncManager(appContext).syncPendingEvents()

                // 2. Send SMS to trusted contacts
                val timeStr = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis()).toString()
                val smsMessage = "[PhoneGuard] Tamper attempt at $timeStr (Deactivation count: $tamperCount)"
                SmsSender.sendAlertToTrustedContacts(appContext, smsMessage)

                // 3. Play alarm 3 times (10s each), escalate if multiple disables
                val alarmPlayer = AlarmPlayer(appContext)
                val plays = if (tamperCount > 1) 5 else 3
                for (i in 1..plays) {
                    try {
                        alarmPlayer.play()
                        delay(10_000)
                    } catch (_: Exception) {
                    } finally {
                        alarmPlayer.stop()
                    }
                    if (i < plays) {
                        delay(2_000)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling admin deactivation alert", e)
            }
        }

        // 4. Show screen overlay or fallback notification
        try {
            showDeactivationOverlayOrNotification(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show deactivation overlay/notification", e)
        }
    }

    private fun showDeactivationOverlayOrNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && android.provider.Settings.canDrawOverlays(context)) {
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP
                }

                val view = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setBackgroundColor(android.graphics.Color.parseColor("#CC1A1A1A"))
                    setPadding(40, 60, 40, 60)

                    val textView = android.widget.TextView(context).apply {
                        text = "PhoneGuard protection was just disabled. If this wasn't you, re-enable immediately."
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 16f
                        gravity = Gravity.CENTER
                    }
                    addView(textView)

                    setOnClickListener {
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                            windowManager.removeView(this)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to open app from overlay", e)
                        }
                    }
                }

                windowManager.addView(view, layoutParams)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add overlay view, falling back to notification", e)
                showDeactivationNotification(context)
            }
        } else {
            showDeactivationNotification(context)
        }
    }

    private fun showDeactivationNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "phoneguard_tamper_channel",
                "Tamper Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when device admin or protection is disabled"
            }
            notificationManager?.createNotificationChannel(channel)
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, "phoneguard_tamper_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("PhoneGuard Protection Disabled")
            .setContentText("PhoneGuard protection was just disabled. If this wasn't you, re-enable immediately.")
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("PhoneGuard protection was just disabled. If this wasn't you, re-enable immediately."))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager?.notify(9999, notification)
    }

    private fun queueWrongPinEvent(context: Context) {
        val tokenStore = TokenStore.get(context.applicationContext)
        val deviceId = tokenStore.getDeviceId()
        if (deviceId == null) {
            Log.w(TAG, "No device registered — cannot queue wrong_pin event")
            return
        }

        val app = context.applicationContext as? mw.phoneguard.app.PhoneGuardApplication
            ?: run {
                Log.e(TAG, "Application is not PhoneGuardApplication — using fallback scope")
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    writeEvent(context, deviceId, "wrong_pin")
                }
                return
            }

        app.appScope.launch {
            writeEvent(context, deviceId, "wrong_pin")
        }
    }

    private fun queueEvent(context: Context, type: String) {
        val tokenStore = TokenStore.get(context.applicationContext)
        val deviceId = tokenStore.getDeviceId() ?: return

        val app = context.applicationContext as? mw.phoneguard.app.PhoneGuardApplication
        val scope = app?.appScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            writeEvent(context, deviceId, type)
        }
    }

    private suspend fun writeEvent(context: Context, deviceId: String, type: String) {
        try {
            val db = PhoneGuardDatabase.get(context.applicationContext)
            var event = EventEntity(
                deviceId = deviceId,
                type = type,
                timestamp = System.currentTimeMillis(),
                captureStatus = "pending_capture",
                clientSeq = System.nanoTime()
            )
            event = EvidenceCapturer(context.applicationContext).captureAndAttachEvidence(event)
            db.eventDao().upsert(event)
            Log.i(TAG, "$type event queued with evidence: ${event.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $type event", e)
        }
    }
}
