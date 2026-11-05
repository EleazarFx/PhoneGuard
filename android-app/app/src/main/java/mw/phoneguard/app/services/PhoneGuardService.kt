package mw.phoneguard.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import mw.phoneguard.app.MainActivity
import mw.phoneguard.app.R
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore

class PhoneGuardService : Service() {

    private val TAG = "PhoneGuardService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var alertModeManager: AlertModeManager? = null
    private var simReceiver: BroadcastReceiver? = null
    private var screenStateReceiver: ScreenStateReceiver? = null

    companion object {
        private const val CHANNEL_ID = "phoneguard_service"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, PhoneGuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PhoneGuardService::class.java))
        }




        const val ACTION_START_ALERT_MODE = "mw.phoneguard.app.START_ALERT_MODE"
        const val ACTION_STOP_ALERT_MODE = "mw.phoneguard.app.STOP_ALERT_MODE"

        fun setAlertMode(context: Context, armed: Boolean) {
            val intent = Intent(context, PhoneGuardService::class.java).apply {
                action = if (armed) ACTION_START_ALERT_MODE else ACTION_STOP_ALERT_MODE
            }
            context.startService(intent)
        }






    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate — starting foreground")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerSimReceiver()
        registerScreenStateReceiver()
        // Immediate SIM check on service creation (catches boot-time SIM swaps)
        handleSimState()

        //Start Alert Mode if it was armed
        val tokenStore = TokenStore.get(applicationContext)
        if (tokenStore.isAlertModeArmed()) {
            Log.i(TAG, "Alert Mode was armed — starting motion detection")
            startAlertMode()
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand (action=${intent?.action})")
        startForeground(NOTIFICATION_ID, buildNotification())
        handleSimState()

        // NEW: Handle intent actions
        when (intent?.action) {
            ACTION_START_ALERT_MODE -> startAlertMode()
            ACTION_STOP_ALERT_MODE -> stopAlertMode()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.w(TAG, "Service onDestroy — this shouldn't happen unless user or system stopped us")
        stopAlertMode()
        unregisterSimReceiver()
        unregisterScreenStateReceiver()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────
    // Foreground notification
    // ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PhoneGuard Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when PhoneGuard is actively protecting this phone"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneGuard · Protected")
            .setContentText("Monitoring for theft triggers")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // ─────────────────────────────────────────────────────────
    // SIM change detection (runtime receiver — more reliable than manifest)
    // ─────────────────────────────────────────────────────────

    private fun registerSimReceiver() {
        if (simReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                Log.d(TAG, "Broadcast received: $action")

                when (action) {
                    "android.intent.action.SIM_STATE_CHANGED" -> {
                        val state = intent.getStringExtra("ss")
                        Log.d(TAG, "SIM state: $state")
                        // SIM_STATE_CHANGED fires with multiple states (ABSENT, PIN_REQUIRED, LOADED, READY).
                        // We act only on the terminal "usable" states.
                        if (state == "LOADED" || state == "READY") {
                            handleSimState()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter("android.intent.action.SIM_STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        simReceiver = receiver
        Log.i(TAG, "SIM receiver registered")
    }

    private fun unregisterSimReceiver() {
        simReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterReceiver: ${e.message}")
            }
            simReceiver = null
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenStateReceiver != null) return
        val receiver = ScreenStateReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        screenStateReceiver = receiver
        Log.i(TAG, "Screen state receiver registered dynamically")
    }

    private fun unregisterScreenStateReceiver() {
        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterScreenStateReceiver: ${e.message}")
            }
            screenStateReceiver = null
        }
    }

    private fun handleSimState() {
        val tokenStore = TokenStore.get(applicationContext)
        val deviceId = tokenStore.getDeviceId()
        if (deviceId == null) {
            Log.w(TAG, "No device registered; cannot compare SIM")
            return
        }

        val tm = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager ?: return
        val operator = tm.simOperatorName ?: ""
        val country = tm.simCountryIso ?: ""
        val operatorCode = tm.simOperator ?: ""
        val subscriptionId = getActiveSubscriptionId()

        val storedOperator = tokenStore.getSimOperatorName() ?: ""
        val storedCountry = tokenStore.getSimCountryIso() ?: ""
        val storedOperatorCode = tokenStore.getSimOperatorCode() ?: ""
        val storedSubId = tokenStore.getSimSubscriptionId() ?: ""

        Log.i(TAG, "SIM check: current='$operator/$country' (code='$operatorCode', sub='$subscriptionId') stored='$storedOperator/$storedCountry' (code='$storedOperatorCode', sub='$storedSubId')")

        // First run — store initial identity
        if (storedOperator.isBlank() && storedCountry.isBlank() && storedOperatorCode.isBlank() && storedSubId.isBlank()) {
            if (operator.isNotBlank() || country.isNotBlank() || operatorCode.isNotBlank() || subscriptionId.isNotBlank()) {
                tokenStore.saveSimIdentity(operator, country, operatorCode, subscriptionId)
                Log.i(TAG, "First SIM identity stored")
            }
            return
        }

        val nameChanged = operator.isNotBlank() && storedOperator.isNotBlank() && operator != storedOperator
        val countryChanged = country.isNotBlank() && storedCountry.isNotBlank() && country != storedCountry
        val codeChanged = operatorCode.isNotBlank() && storedOperatorCode.isNotBlank() && operatorCode != storedOperatorCode
        val subChanged = subscriptionId.isNotBlank() && storedSubId.isNotBlank() && subscriptionId != storedSubId

        val changed = nameChanged || countryChanged || codeChanged || subChanged

        if (!changed) {
            Log.d(TAG, "SIM unchanged")
            return
        }

        Log.w(TAG, "!!! SIM CHANGE DETECTED !!! (name=$nameChanged, country=$countryChanged, code=$codeChanged, sub=$subChanged)")
        queueEvent(deviceId, "sim_change")
        tokenStore.saveSimIdentity(operator, country, operatorCode, subscriptionId)
    }

    private fun getActiveSubscriptionId(): String {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                if (sm != null) {
                    @Suppress("MissingPermission")
                    val activeList = sm.activeSubscriptionInfoList
                    if (!activeList.isNullOrEmpty()) {
                        return activeList.joinToString(",") { sub ->
                            val carrier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) sub.carrierId else sub.simSlotIndex
                            "${sub.subscriptionId}:$carrier"
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            Log.d(TAG, "READ_PHONE_STATE permission not granted; falling back to TelephonyManager fields")
        } catch (e: Exception) {
            Log.w(TAG, "SubscriptionManager query failed: ${e.message}")
        }
        return ""
    }

    // ─────────────────────────────────────────────────────────
    // Event queueing
    // ─────────────────────────────────────────────────────────

    private fun queueEvent(deviceId: String, type: String) {
        serviceScope.launch {
            try {
                val db = PhoneGuardDatabase.get(applicationContext)
                val event = EventEntity(
                    deviceId = deviceId,
                    type = type,
                    timestamp = System.currentTimeMillis(),
                    captureStatus = "pending_capture",
                    clientSeq = System.currentTimeMillis()
                )
                db.eventDao().upsert(event)
                Log.i(TAG, "$type event queued: ${event.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue $type event", e)
            }
        }
    }





    fun startAlertMode() {
        if (alertModeManager != null) {
            Log.d(TAG, "Alert Mode already active")
            return
        }

        alertModeManager = AlertModeManager(applicationContext) {
            onMotionDetected()
        }.also { it.start() }

        updateNotificationToArmed()
        Log.i(TAG, "Alert Mode started")
    }

    fun stopAlertMode() {
        alertModeManager?.stop()
        alertModeManager = null
        updateNotificationToNormal()
        Log.i(TAG, "Alert Mode stopped")
    }

    private fun onMotionDetected() {
        val tokenStore = TokenStore.get(applicationContext)
        val deviceId = tokenStore.getDeviceId() ?: return

        serviceScope.launch {
            try {
                val db = PhoneGuardDatabase.get(applicationContext)
                val event = EventEntity(
                    deviceId = deviceId,
                    type = "snatch",
                    timestamp = System.currentTimeMillis(),
                    captureStatus = "pending_capture",
                    clientSeq = System.nanoTime()
                )
                db.eventDao().upsert(event)
                Log.i(TAG, "snatch event queued: ${event.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue snatch event", e)
            }
        }
    }

    private fun updateNotificationToArmed() {
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneGuard · Alert Mode Armed")
            .setContentText("Motion detection active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationToNormal() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }











}