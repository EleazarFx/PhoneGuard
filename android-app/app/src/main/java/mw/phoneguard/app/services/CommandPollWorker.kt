package mw.phoneguard.app.services

import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import mw.phoneguard.app.data.api.ApiResult
import mw.phoneguard.app.data.api.DeviceApi
import mw.phoneguard.app.data.storage.TokenStore
import java.util.concurrent.TimeUnit

class CommandPollWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val TAG = "CommandPollWorker"

    override suspend fun doWork(): Result {
        val context = applicationContext
        val tokenStore = TokenStore.get(context)
        val deviceId = tokenStore.getDeviceId()
        val deviceToken = tokenStore.getDeviceToken()

        if (deviceId.isNullOrBlank() || deviceToken.isNullOrBlank()) {
            Log.w(TAG, "Device not registered; skipping command poll")
            return Result.success()
        }

        val deviceApi = DeviceApi(context)
        Log.d(TAG, "Polling commands for device $deviceId...")

        when (val result = deviceApi.getCommands(deviceToken, deviceId)) {
            is ApiResult.Success -> {
                TokenStore.get(context).saveLastCheckInTime(System.currentTimeMillis())
                val commands = result.value
                Log.i(TAG, "Fetched ${commands.size} command(s)")
                val executor = CommandExecutor(context)
                val now = System.currentTimeMillis()

                for (cmd in commands) {
                    // Check if expired before fetch
                    if (cmd.expiresAt != null && cmd.expiresAt < now) {
                        Log.w(TAG, "Command ${cmd.id} (${cmd.type}) has expired. Reporting 'expired'")
                        deviceApi.postCommandStatus(deviceToken, deviceId, cmd.id, "expired", "Command expired before processing")
                        continue
                    }

                    // Report 'delivered' immediately after fetching
                    deviceApi.postCommandStatus(deviceToken, deviceId, cmd.id, "delivered")

                    // Execute command
                    val execResult = executor.execute(cmd)

                    if (execResult.isSuccess) {
                        Log.i(TAG, "Command ${cmd.id} (${cmd.type}) executed successfully")
                        deviceApi.postCommandStatus(deviceToken, deviceId, cmd.id, "executed")
                    } else {
                        val errorMsg = execResult.exceptionOrNull()?.message ?: "unknown_error"
                        Log.e(TAG, "Command ${cmd.id} (${cmd.type}) failed: $errorMsg")
                        deviceApi.postCommandStatus(deviceToken, deviceId, cmd.id, "failed", errorMsg)
                    }
                }
            }
            is ApiResult.Failure -> {
                Log.e(TAG, "Command poll failed: ${result.message}")
            }
        }

        // Check screen state: if locked, enqueue next 60s poll
        val kgm = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = kgm?.isDeviceLocked == true || kgm?.isKeyguardLocked == true

        if (isLocked) {
            Log.d(TAG, "Screen is locked — re-scheduling 60s command poll worker")
            schedule60sPoll(context)
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_ONE_TIME_WORK_NAME = "phoneguard_command_poll_60s"
        const val UNIQUE_PERIODIC_WORK_NAME = "phoneguard_command_poll_periodic"

        fun schedule60sPoll(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<CommandPollWorker>()
                .setInitialDelay(60, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun schedulePeriodicPoll(context: Context) {
            val periodicRequest = PeriodicWorkRequestBuilder<CommandPollWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        fun cancel60sPoll(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_ONE_TIME_WORK_NAME)
        }
    }
}
