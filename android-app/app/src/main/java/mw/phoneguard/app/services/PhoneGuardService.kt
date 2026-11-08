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
import android.app.KeyguardManager


class PhoneGuardService : Service() {

    private val TAG = "PhoneGuardService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var alertModeManager: AlertModeManager? = null

    private var snatchGuard: SnatchGuard? = null
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

        const val ACTION_SCREEN_LOCKED = "mw.phoneguard.app.SCREEN_LOCKED"
        const val ACTION_SCREEN_UNLOCKED = "mw.phoneguard.app.SCREEN_UNLOCKED"
        const val ACTION_NOT_UNLOCKED = "mw.phoneguard.app.NOT_UNLOCKED"
        const val ACTION_UNLOCKED = "mw.phoneguard.app.UNLOCKED"






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

        // Start motion detection only if alert mode is armed AND device/screen locked
        val tokenStore = TokenStore.get(applicationContext)
        if (tokenStore.isAlertModeArmed()) {
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
            val isLocked = keyguardManager?.isDeviceLocked() == true || keyguardManager?.isKeyguardLocked == true
            if (isLocked) {
                Log.i(TAG, "Alert Mode armed + device/screen locked — starting motion detection")
                startAlertMode()
            } else {
                Log.i(TAG, "Alert Mode armed but device/screen unlocked — motion detection will not start")
                stopAlertMode()
            }
        } else {
            stopAlertMode()
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand (action=${intent?.action})")
        startForeground(NOTIFICATION_ID, buildNotification())
        handleSimState()

        when (intent?.action) {
            ACTION_START_ALERT_MODE -> {
                val tokenStore = TokenStore.get(applicationContext)
                if (tokenStore.isAlertModeArmed()) {
                    val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                    val isLocked = keyguardManager?.isDeviceLocked() == true || keyguardManager?.isKeyguardLocked == true
                    if (isLocked) {
                        Log.i(TAG, "Alert Mode armed via action + device/screen locked — starting motion detection")
                        startAlertMode()
                    } else {
                        Log.i(TAG, "Alert Mode armed via action but device/screen unlocked — waiting for lock")
                        stopAlertMode()
                    }
                }
            }
            ACTION_STOP_ALERT_MODE -> stopAlertMode()

            ACTION_NOT_UNLOCKED -> {
                // Phone is NOT unlocked (screen off, or screen on but locked)
                // → ensure motion sensor is on if actually locked
                val tokenStore = TokenStore.get(applicationContext)
                if (tokenStore.isAlertModeArmed()) {
                    val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                    val isLocked = keyguardManager?.isDeviceLocked() == true || keyguardManager?.isKeyguardLocked == true
                    if (isLocked) {
                        if (alertModeManager == null) {
                            Log.i(TAG, "Phone not unlocked + Alert Mode armed — starting motion detection")
                            startAlertMode()
                        }
                    } else {
                        Log.i(TAG, "ACTION_NOT_UNLOCKED received but device is actually unlocked — stopping motion detection")
                        stopAlertMode()
                    }
                }
            }

            ACTION_UNLOCKED -> {
                // Phone is unlocked → owner is present → stop motion sensor
                // Also cancel any active SnatchGuard (owner proved identity)
                if (alertModeManager != null) {
                    Log.i(TAG, "Phone unlocked — stopping motion detection")
                    stopAlertMode()
                }
                snatchGuard?.stop()
                snatchGuard = null
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.w(TAG, "Service onDestroy — this shouldn't happen unless user or system stopped us")
        snatchGuard?.stop()
        snatchGuard = null
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
            addAction(Intent.ACTION_SCREEN_ON)
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

        val currentCodes = mutableSetOf<String>()
        if (operatorCode.isNotBlank()) currentCodes.add(operatorCode)

        val currentOperators = mutableSetOf<String>()
        if (operator.isNotBlank()) currentOperators.add(operator)

        val currentCountries = mutableSetOf<String>()
        if (country.isNotBlank()) currentCountries.add(country)

        val currentSubscriptions = mutableSetOf<String>()
        if (subscriptionId.isNotBlank()) currentSubscriptions.add(subscriptionId)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                if (sm != null) {
                    @Suppress("MissingPermission")
                    val activeList = sm.activeSubscriptionInfoList
                    if (!activeList.isNullOrEmpty()) {
                        for (sub in activeList) {
                            if (!sub.carrierName.isNullOrBlank()) currentOperators.add(sub.carrierName.toString())
                            if (!sub.countryIso.isNullOrBlank()) currentCountries.add(sub.countryIso.toString())
                            val mccMnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                "${sub.mccString.orEmpty()}${sub.mncString.orEmpty()}"
                            } else ""
                            if (mccMnc.isNotBlank()) currentCodes.add(mccMnc)
                            val carrier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) sub.carrierId else sub.simSlotIndex
                            currentSubscriptions.add("${sub.subscriptionId}:$carrier")
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (!tokenStore.hasOriginalSimIdentity()) {
            if (currentCodes.isNotEmpty()) {
                tokenStore.saveOriginalSimIdentity(currentOperators, currentCountries, currentCodes, currentSubscriptions)
                tokenStore.saveSimIdentity(operator, country, operatorCode, subscriptionId)
                Log.i(TAG, "First SIM identity baseline stored: codes=$currentCodes")
            }
            return
        }

        val originalCodes = tokenStore.getOriginalSimCodes() ?: emptySet()
        val matchesOriginal = currentCodes.isNotEmpty() && originalCodes.isNotEmpty() && (currentCodes == originalCodes || currentCodes.intersect(originalCodes).isNotEmpty())

        if (matchesOriginal) {
            val lastAlerted = tokenStore.getLastAlertedSimCodes()
            if (lastAlerted != null) {
                Log.i(TAG, "Original SIM re-inserted; clearing previous SIM change alert state.")
                tokenStore.clearLastAlertedSimCodes()
            }
            tokenStore.saveSimIdentity(operator, country, operatorCode, subscriptionId)
            Log.d(TAG, "SIM check: Original trusted SIM active (codes=$currentCodes)")
            return
        }

        val lastAlerted = tokenStore.getLastAlertedSimCodes()
        if (lastAlerted == currentCodes) {
            Log.d(TAG, "Foreign SIM active but already alerted for codes=$currentCodes")
            return
        }

        Log.w(TAG, "!!! FOREIGN SIM CHANGE DETECTED !!! (currentCodes=$currentCodes, originalCodes=$originalCodes)")
        queueEvent(deviceId, "sim_change")
        tokenStore.saveLastAlertedSimCodes(currentCodes)
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
        snatchGuard?.stop()
        snatchGuard = null
        alertModeManager?.stop()
        alertModeManager = null
        updateNotificationToNormal()
        Log.i(TAG, "Alert Mode stopped")
    }

    private fun onMotionDetected() {
        val tokenStore = TokenStore.get(applicationContext)
        val deviceId = tokenStore.getDeviceId() ?: return

        Log.w(TAG, "Motion detected — queuing snatch event and activating SnatchGuard")

        // Queue the initial event (queued on first motion, not after grace period)
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
                Log.i(TAG, "snatch event queued on motion: ${event.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue snatch event", e)
            }
        }

        // Activate SnatchGuard (plays alarm, starts grace period, locks if not unlocked)
        if (snatchGuard == null) {
            snatchGuard = SnatchGuard(applicationContext).also { it.start() }
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



    // (removed redundant runtime receiver code, using ScreenStateReceiver instead)









}