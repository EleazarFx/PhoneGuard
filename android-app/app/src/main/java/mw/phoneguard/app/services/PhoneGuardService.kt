package mw.phoneguard.app.services

import android.app.KeyguardManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mw.phoneguard.app.MainActivity
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore
import mw.phoneguard.app.data.sync.SyncManager

class PhoneGuardService : Service() {

    private val TAG = "PhoneGuardService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationTickerJob: Job? = null

    private var alertModeManager: AlertModeManager? = null
    private var snatchGuard: SnatchGuard? = null
    private var simReceiver: BroadcastReceiver? = null
    private var screenStateReceiver: ScreenStateReceiver? = null
    private var airplaneReceiver: BroadcastReceiver? = null

    var currentTrackingMode: TrackingMode = TrackingMode.NORMAL
        private set

    companion object {
        private const val CHANNEL_ID = "phoneguard_service"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_ALERT_MODE = "mw.phoneguard.app.START_ALERT_MODE"
        const val ACTION_STOP_ALERT_MODE = "mw.phoneguard.app.STOP_ALERT_MODE"
        const val ACTION_TRIGGER_DECOY = "mw.phoneguard.app.TRIGGER_DECOY"
        const val ACTION_BOOT = "mw.phoneguard.app.BOOT"
        const val ACTION_SCREEN_LOCKED = "mw.phoneguard.app.SCREEN_LOCKED"
        const val ACTION_SCREEN_UNLOCKED = "mw.phoneguard.app.SCREEN_UNLOCKED"
        const val ACTION_NOT_UNLOCKED = "mw.phoneguard.app.NOT_UNLOCKED"
        const val ACTION_UNLOCKED = "mw.phoneguard.app.UNLOCKED"
        const val ACTION_FORCE_SIM_CHECK = "mw.phoneguard.app.FORCE_SIM_CHECK"
        const val ACTION_FORCE_WRONG_PIN = "mw.phoneguard.app.FORCE_WRONG_PIN"

        const val ACTION_SET_TRACKING_MODE = "mw.phoneguard.app.SET_TRACKING_MODE"
        const val EXTRA_TRACKING_MODE = "mw.phoneguard.app.EXTRA_TRACKING_MODE"

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

        fun startOnBoot(context: Context) {
            val intent = Intent(context, PhoneGuardService::class.java).apply {
                action = ACTION_BOOT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun setAlertMode(context: Context, armed: Boolean) {
            val intent = Intent(context, PhoneGuardService::class.java).apply {
                action = if (armed) ACTION_START_ALERT_MODE else ACTION_STOP_ALERT_MODE
            }
            context.startService(intent)
        }

        fun setTrackingMode(context: Context, mode: TrackingMode) {
            val intent = Intent(context, PhoneGuardService::class.java).apply {
                action = ACTION_SET_TRACKING_MODE
                putExtra(EXTRA_TRACKING_MODE, mode.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun triggerDecoy(context: Context) {
            val intent = Intent(context, PhoneGuardService::class.java).apply {
                action = ACTION_TRIGGER_DECOY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun forceSimCheck(context: Context) {
            val intent = Intent(context, PhoneGuardService::class.java).apply {
                action = ACTION_FORCE_SIM_CHECK
            }
            context.startService(intent)
        }

        fun forceWrongPinTrigger(context: Context) {
            val intent = Intent(context, PhoneGuardService::class.java).apply {
                action = ACTION_FORCE_WRONG_PIN
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate — initializing Adaptive Protection")
        ServiceLogger.log(TAG, "Service onCreate — initializing Adaptive Protection")

        val storedModeName = TokenStore.get(applicationContext).getTrackingModeName()
        currentTrackingMode = try { TrackingMode.valueOf(storedModeName) } catch (_: Exception) { TrackingMode.NORMAL }

        createNotificationChannel()
        startForegroundWithTypes()
        registerSimReceiver()
        registerScreenStateReceiver()
        registerAirplaneModeReceiver()
        handleSimState()
        startAdaptiveLocationTicker()

        val tokenStore = TokenStore.get(applicationContext)
        if (tokenStore.isAlertModeArmed()) {
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
            val isLocked = keyguardManager?.isDeviceLocked == true || keyguardManager?.isKeyguardLocked == true
            if (isLocked) {
                Log.i(TAG, "Alert Mode armed + locked — starting motion detection")
                startAlertMode()
            } else {
                Log.i(TAG, "Alert Mode armed but unlocked — motion detection standby")
                stopAlertMode()
            }
        } else {
            stopAlertMode()
        }

        updateCommandPollSchedule()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand (action=${intent?.action})")
        ServiceLogger.log(TAG, "onStartCommand (action=${intent?.action})")

        startForegroundWithTypes()
        handleSimState()
        updateCommandPollSchedule()

        when (intent?.action) {
            ACTION_SET_TRACKING_MODE -> {
                val modeName = intent.getStringExtra(EXTRA_TRACKING_MODE) ?: TrackingMode.NORMAL.name
                val newMode = try { TrackingMode.valueOf(modeName) } catch (_: Exception) { TrackingMode.NORMAL }
                updateTrackingMode(newMode)
            }

            ACTION_BOOT -> {
                Log.w(TAG, "!!! DEVICE BOOT / RESTART DETECTED !!!")
                ServiceLogger.log(TAG, "DEVICE BOOT / RESTART DETECTED")
                val tokenStore = TokenStore.get(applicationContext)
                val deviceId = tokenStore.getDeviceId()
                if (deviceId != null) {
                    queueEvent(deviceId, "restart")
                }
            }

            ACTION_START_ALERT_MODE -> {
                val tokenStore = TokenStore.get(applicationContext)
                if (tokenStore.isAlertModeArmed()) {
                    val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                    val isLocked = keyguardManager?.isDeviceLocked == true || keyguardManager?.isKeyguardLocked == true
                    if (isLocked) {
                        startAlertMode()
                    } else {
                        stopAlertMode()
                    }
                }
            }

            ACTION_STOP_ALERT_MODE -> stopAlertMode()

            ACTION_FORCE_SIM_CHECK -> handleSimState()

            ACTION_FORCE_WRONG_PIN -> {
                Log.w(TAG, "Force wrong-PIN trigger executed")
                val tokenStore = TokenStore.get(applicationContext)
                val deviceId = tokenStore.getDeviceId() ?: "debug_device"
                queueEvent(deviceId, "wrong_pin")
                updateTrackingMode(TrackingMode.SUSPICIOUS)
            }

            ACTION_NOT_UNLOCKED -> {
                val tokenStore = TokenStore.get(applicationContext)
                if (tokenStore.isAlertModeArmed()) {
                    val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                    val isLocked = keyguardManager?.isDeviceLocked == true || keyguardManager?.isKeyguardLocked == true
                    if (isLocked && alertModeManager == null) {
                        startAlertMode()
                    }
                }
            }

            ACTION_UNLOCKED -> {
                if (alertModeManager != null) {
                    stopAlertMode()
                }
                snatchGuard?.stop()
                snatchGuard = null
            }

            ACTION_TRIGGER_DECOY -> showDecoyFullScreen(applicationContext)
        }

        return START_STICKY
    }

    private fun startForegroundWithTypes() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateTrackingMode(newMode: TrackingMode) {
        if (currentTrackingMode == newMode) return
        val oldMode = currentTrackingMode
        currentTrackingMode = newMode
        TokenStore.get(applicationContext).saveTrackingModeName(newMode.name)
        Log.i(TAG, "Tracking mode changed: $oldMode -> $newMode (${newMode.description})")
        ServiceLogger.log(TAG, "Tracking mode updated to ${newMode.name}")

        updateForegroundNotification()
        restartAdaptiveLocationTicker()
    }

    private fun showDecoyFullScreen(context: Context) {
        try {
            val channelId = "phoneguard_urgent"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "PhoneGuard Security Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent security alerts and decoy lockouts"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val decoyIntent = Intent(context, DecoyShutdownActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                decoyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Security Warning")
                .setContentText("Unauthorized threat detected. Securing device...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(9999, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger full-screen decoy: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.w(TAG, "Service onDestroy — stopping receivers and timers")
        snatchGuard?.stop()
        snatchGuard = null
        stopAlertMode()
        unregisterSimReceiver()
        unregisterScreenStateReceiver()
        unregisterAirplaneModeReceiver()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────
    // Foreground Notification
    // ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AntiTheft is protecting your device",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when PhoneGuard AntiTheft is actively protecting this phone"
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

        val (title, text, priority) = when (currentTrackingMode) {
            TrackingMode.NORMAL -> Triple(
                "AntiTheft is protecting your device",
                "Normal Mode · Low-Power 15-min location check",
                NotificationCompat.PRIORITY_LOW
            )
            TrackingMode.SUSPICIOUS -> Triple(
                "PhoneGuard · SUSPICIOUS ACTIVITY",
                "High Accuracy 30s GPS Tracking active",
                NotificationCompat.PRIORITY_HIGH
            )
            TrackingMode.THEFT -> Triple(
                "PhoneGuard · THEFT TRACKING ACTIVE",
                "Live 15s GPS & Evidence Capture active",
                NotificationCompat.PRIORITY_MAX
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateForegroundNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    // ─────────────────────────────────────────────────────────
    // SIM Change Receiver
    // ─────────────────────────────────────────────────────────

    private fun registerSimReceiver() {
        if (simReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                if (action == "android.intent.action.SIM_STATE_CHANGED") {
                    val state = intent.getStringExtra("ss")
                    if (state == "LOADED" || state == "READY") {
                        handleSimState()
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
    }

    private fun unregisterSimReceiver() {
        simReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
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
    }

    private fun unregisterScreenStateReceiver() {
        screenStateReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            screenStateReceiver = null
        }
    }

    private fun registerAirplaneModeReceiver() {
        if (airplaneReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                    val isAirplaneModeOn = intent.getBooleanExtra("state", false)
                    if (isAirplaneModeOn) {
                        serviceScope.launch {
                            val tokenStore = TokenStore.get(applicationContext)
                            val deviceId = tokenStore.getDeviceId() ?: "unknown_device"
                            val db = PhoneGuardDatabase.get(applicationContext)
                            var event = EventEntity(
                                deviceId = deviceId,
                                type = "tamper",
                                timestamp = System.currentTimeMillis(),
                                captureStatus = "pending_capture"
                            )
                            event = EvidenceCapturer(applicationContext).captureAndAttachEvidence(event, capturePhoto = false)
                            db.eventDao().upsert(event)
                            Log.w(TAG, "Logged airplane mode tamper event")
                        }
                        updateTrackingMode(TrackingMode.SUSPICIOUS)
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        airplaneReceiver = receiver
    }

    private fun unregisterAirplaneModeReceiver() {
        airplaneReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            airplaneReceiver = null
        }
    }

    private fun handleSimState() {
        val tokenStore = TokenStore.get(applicationContext)
        val deviceId = tokenStore.getDeviceId() ?: return

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

        if (!tokenStore.hasOriginalSimIdentity()) {
            if (currentCodes.isNotEmpty()) {
                tokenStore.saveOriginalSimIdentity(currentOperators, currentCountries, currentCodes, currentSubscriptions)
                tokenStore.saveSimIdentity(operator, country, operatorCode, subscriptionId)
            }
            return
        }

        val originalCodes = tokenStore.getOriginalSimCodes() ?: emptySet()
        val matchesOriginal = currentCodes.isNotEmpty() && originalCodes.isNotEmpty() &&
                (currentCodes == originalCodes || currentCodes.intersect(originalCodes).isNotEmpty())

        if (matchesOriginal) {
            val lastAlerted = tokenStore.getLastAlertedSimCodes()
            if (lastAlerted != null) {
                tokenStore.clearLastAlertedSimCodes()
            }
            tokenStore.saveSimIdentity(operator, country, operatorCode, subscriptionId)
            return
        }

        val lastAlerted = tokenStore.getLastAlertedSimCodes()
        if (lastAlerted == currentCodes) return

        Log.w(TAG, "!!! FOREIGN SIM CHANGE DETECTED !!! Escalating to SUSPICIOUS mode.")
        queueEvent(deviceId, "sim_change")
        updateTrackingMode(TrackingMode.SUSPICIOUS)

        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} ${Build.MODEL}"
        val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val simInfo = if (operator.isNotBlank()) "$operator ($operatorCode)" else "Unknown SIM"
        val location = AdaptiveLocationManager.fetchLocationBlocking(applicationContext) ?: AdaptiveLocationManager.fallbackLocationManager(applicationContext)
        val mapsLink = AdaptiveLocationManager.formatMapsUrl(location)

        val alertMessage = "[PhoneGuard] ALERT: Unauthorized SIM inserted! Device: $deviceName. SIM: $simInfo. Time: $timeStr. Location: $mapsLink"
        SmsSender.sendAlertToTrustedContacts(applicationContext, alertMessage, bypassRateLimit = true)
        triggerDecoy(applicationContext)

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
        } catch (_: Exception) {}
        return ""
    }

    private fun queueEvent(deviceId: String, type: String) {
        serviceScope.launch {
            try {
                val db = PhoneGuardDatabase.get(applicationContext)
                var event = EventEntity(
                    deviceId = deviceId,
                    type = type,
                    timestamp = System.currentTimeMillis(),
                    captureStatus = "pending_capture",
                    clientSeq = System.currentTimeMillis()
                )
                event = EvidenceCapturer(applicationContext).captureAndAttachEvidence(event)
                db.eventDao().upsert(event)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue $type event", e)
            }
        }
    }

    fun startAlertMode() {
        if (alertModeManager != null) return
        alertModeManager = AlertModeManager(applicationContext) {
            onMotionDetected()
        }.also { it.start() }

        updateNotificationToArmed()
    }

    fun stopAlertMode() {
        snatchGuard?.stop()
        snatchGuard = null
        alertModeManager?.stop()
        alertModeManager = null
        updateNotificationToNormal()
    }

    private fun onMotionDetected() {
        val tokenStore = TokenStore.get(applicationContext)
        val deviceId = tokenStore.getDeviceId() ?: return

        Log.w(TAG, "Motion detected — queuing snatch event and escalating to SUSPICIOUS mode")
        updateTrackingMode(TrackingMode.SUSPICIOUS)

        serviceScope.launch {
            try {
                val db = PhoneGuardDatabase.get(applicationContext)
                var event = EventEntity(
                    deviceId = deviceId,
                    type = "snatch",
                    timestamp = System.currentTimeMillis(),
                    captureStatus = "pending_capture",
                    clientSeq = System.nanoTime()
                )
                event = EvidenceCapturer(applicationContext).captureAndAttachEvidence(event)
                db.eventDao().upsert(event)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue snatch event", e)
            }
        }

        if (snatchGuard == null) {
            snatchGuard = SnatchGuard(applicationContext).also { it.start() }
        }
    }

    private fun restartAdaptiveLocationTicker() {
        locationTickerJob?.cancel()
        locationTickerJob = null
        startAdaptiveLocationTicker()
    }

    private fun startAdaptiveLocationTicker() {
        if (locationTickerJob?.isActive == true) return
        locationTickerJob = serviceScope.launch {
            while (isActive) {
                val mode = currentTrackingMode
                try {
                    val tokenStore = TokenStore.get(applicationContext)
                    val deviceId = tokenStore.getDeviceId()

                    if (deviceId != null && (tokenStore.isPeriodicLocationEnabled() || mode != TrackingMode.NORMAL)) {
                        val location = AdaptiveLocationManager.fetchLocation(applicationContext, mode)
                        val checkResult = AdaptiveLocationManager.evaluateLocationMovement(location, mode, applicationContext)

                        when (checkResult) {
                            is LocationCheckResult.Dispatch -> {
                                var event = EventEntity(
                                    deviceId = deviceId,
                                    type = "location_ping",
                                    timestamp = System.currentTimeMillis(),
                                    captureStatus = "adaptive_${mode.name.lowercase()}",
                                    clientSeq = System.currentTimeMillis()
                                )

                                val capturePhoto = mode == TrackingMode.THEFT
                                event = EvidenceCapturer(applicationContext).captureAndAttachEvidence(event, capturePhoto = capturePhoto)

                                val db = PhoneGuardDatabase.get(applicationContext)
                                db.eventDao().upsert(event)
                                Log.i(TAG, "Adaptive Location Ping [${mode.name}]: lat=${event.lat}, lng=${event.lng}, moved=${"%.1f".format(checkResult.distanceMovedMeters)}m")

                                val syncSuccess = try {
                                    SyncManager(applicationContext).syncPendingEvents()
                                    true
                                } catch (e: Exception) {
                                    Log.e(TAG, "Sync error on location ping: ${e.message}")
                                    false
                                }

                                // Fallback SMS if internet/server fails during Suspicious or Theft Mode
                                if (!syncSuccess && mode != TrackingMode.NORMAL) {
                                    val mapsUrl = AdaptiveLocationManager.formatMapsUrl(location)
                                    val deviceName = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} ${Build.MODEL}"
                                    val batteryStr = event.battery?.let { "$it%" } ?: "Unknown"
                                    val smsMessage = "[PhoneGuard] ALERT (${mode.name} Tracking)! Device: $deviceName. Battery: $batteryStr. Location: $mapsUrl"
                                    Log.w(TAG, "Dispatching offline fallback SMS location alert to emergency contacts")
                                    SmsSender.sendAlertToTrustedContacts(applicationContext, smsMessage, bypassRateLimit = (mode == TrackingMode.THEFT))
                                }
                            }

                            is LocationCheckResult.Stationary -> {
                                Log.d(TAG, "Location stationary: ${checkResult.reason}. Suppressing redundant GPS update.")
                            }

                            is LocationCheckResult.PermissionDenied -> {
                                Log.w(TAG, "Adaptive location skipped: missing location permissions")
                            }

                            is LocationCheckResult.LocationUnavailable -> {
                                Log.w(TAG, "Adaptive location unavailable from Fused/Legacy providers")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Adaptive Location Ticker error: ${e.message}", e)
                }

                delay(mode.intervalMs)
            }
        }
    }

    private fun updateNotificationToArmed() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneGuard · Alert Mode Armed")
            .setContentText("Motion detection active (${currentTrackingMode.description})")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationToNormal() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun updateCommandPollSchedule() {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isDeviceLocked == true || keyguardManager?.isKeyguardLocked == true
        if (isLocked) {
            CommandPollWorker.schedule60sPoll(applicationContext)
        } else {
            CommandPollWorker.cancel60sPoll(applicationContext)
            CommandPollWorker.schedulePeriodicPoll(applicationContext)
        }
    }
}
