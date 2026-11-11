package mw.phoneguard.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mw.phoneguard.app.MainActivity
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.models.CommandResponse
import mw.phoneguard.app.data.storage.TokenStore
import mw.phoneguard.app.data.sync.SyncManager

class CommandExecutor(private val context: Context) {

    private val TAG = "CommandExecutor"

    /**
     * Executes the given command.
     * Returns Result.success(Unit) on success, or Result.failure(Exception) on error.
     */
    suspend fun execute(command: CommandResponse): Result<Unit> = withContext(Dispatchers.IO) {
        val type = command.type.uppercase().trim()
        Log.i(TAG, "Executing command ${command.id} of type $type")

        return@withContext try {
            when (type) {
                "LOCK" -> executeLock()
                "ALARM" -> executeAlarm(command)
                "MESSAGE" -> executeMessage(command)
                "LOCATE" -> executeLocate()
                "LIVE_TRACK" -> executeLiveTrack(command)
                "WIPE" -> executeWipe()
                else -> Result.failure(IllegalArgumentException("unknown_command"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command ${command.id} execution failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun executeLock(): Result<Unit> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, PhoneGuardAdminReceiver::class.java)

        return if (dpm != null && dpm.isAdminActive(adminComponent)) {
            dpm.lockNow()
            Log.i(TAG, "Device locked via LOCK command")
            Result.success(Unit)
        } else {
            Log.e(TAG, "Cannot execute LOCK: Device admin not active")
            Result.failure(IllegalStateException("device admin not active"))
        }
    }

    private suspend fun executeAlarm(command: CommandResponse): Result<Unit> {
        val durationSeconds = command.duration
            ?: command.params?.get("duration")?.toIntOrNull()
            ?: 30

        val alarmPlayer = AlarmPlayer(context)
        Log.i(TAG, "Playing alarm for $durationSeconds seconds")
        alarmPlayer.play()
        delay(durationSeconds * 1000L)
        alarmPlayer.stop()
        Log.i(TAG, "Alarm stopped")
        return Result.success(Unit)
    }

    private fun executeMessage(command: CommandResponse): Result<Unit> {
        val messageText = command.message
            ?: command.params?.get("message")
            ?: "Security Message from PhoneGuard"

        showHighPriorityMessageNotification(messageText)
        Log.i(TAG, "Displayed message notification: $messageText")
        return Result.success(Unit)
    }

    private fun showHighPriorityMessageNotification(messageText: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "phoneguard_remote_message"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PhoneGuard Remote Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Remote command security messages"
                enableVibration(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("PhoneGuard Security Message")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(8888, notification)
    }

    private suspend fun executeLocate(): Result<Unit> {
        val tokenStore = TokenStore.get(context)
        val deviceId = tokenStore.getDeviceId()
            ?: return Result.failure(IllegalStateException("device not registered"))

        val db = PhoneGuardDatabase.get(context)
        var event = EventEntity(
            deviceId = deviceId,
            type = "location_ping",
            timestamp = System.currentTimeMillis(),
            captureStatus = "locate_command",
            clientSeq = System.currentTimeMillis()
        )
        event = EvidenceCapturer(context).captureAndAttachEvidence(event)
        db.eventDao().upsert(event)
        SyncManager(context).syncPendingEvents()
        Log.i(TAG, "Locate command executed: posted fresh location ping")
        return Result.success(Unit)
    }

    private suspend fun executeLiveTrack(command: CommandResponse): Result<Unit> {
        val minutes = command.minutes
            ?: command.duration
            ?: command.params?.get("minutes")?.toIntOrNull()
            ?: 5

        val totalIntervals = (minutes * 60) / 30
        val tokenStore = TokenStore.get(context)
        val deviceId = tokenStore.getDeviceId()
            ?: return Result.failure(IllegalStateException("device not registered"))

        val db = PhoneGuardDatabase.get(context)
        val syncManager = SyncManager(context)

        Log.i(TAG, "Starting LIVE_TRACK for $minutes minutes ($totalIntervals pings)")
        for (i in 0 until totalIntervals) {
            var event = EventEntity(
                deviceId = deviceId,
                type = "location_ping",
                timestamp = System.currentTimeMillis(),
                captureStatus = "live_track",
                clientSeq = System.currentTimeMillis()
            )
            event = EvidenceCapturer(context).captureAndAttachEvidence(event)
            db.eventDao().upsert(event)
            syncManager.syncPendingEvents()
            Log.d(TAG, "Live track ping ${i + 1}/$totalIntervals sent")
            if (i < totalIntervals - 1) {
                delay(30_000L)
            }
        }
        return Result.success(Unit)
    }

    private fun executeWipe(): Result<Unit> {
        val tokenStore = TokenStore.get(context)
        val wipeAllowed = tokenStore.isRemoteWipeEnabled()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, PhoneGuardAdminReceiver::class.java)
        val isAdminActive = dpm?.isAdminActive(adminComponent) == true

        if (!wipeAllowed) {
            Log.e(TAG, "Cannot execute WIPE: remote wipe not allowed in settings (wipe_allowed=false)")
            return Result.failure(IllegalStateException("wipe not allowed in settings"))
        }

        if (!isAdminActive) {
            Log.e(TAG, "Cannot execute WIPE: device admin not active")
            return Result.failure(IllegalStateException("device admin not active"))
        }

        Log.w(TAG, "!!! WIPE COMMAND EXECUTING — WIPING DEVICE DATA !!!")
        try {
            dpm?.wipeData(0)
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute wipeData: ${e.message}", e)
            return Result.failure(e)
        }
    }
}
