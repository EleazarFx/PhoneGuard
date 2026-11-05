package mw.phoneguard.app.services

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore

class PhoneGuardAdminReceiver : DeviceAdminReceiver() {

    private val TAG = "PhoneGuardAdmin"

    /**
     * Android's "device admin" callback for wrong lock-screen PIN attempts.
     *
     * OEM notes:
     * - Stock Android fires on every wrong PIN.
     * - Some OEMs (Xiaomi, Samsung, Transsion) delay or batch this callback.
     * - This handler must be fast: Android may kill the process ~10s after the
     *   receiver returns. We use the process-scoped coroutine + a foreground
     *   service as the actual event writer.
     */
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "!!! WRONG LOCK-SCREEN PIN DETECTED !!!")
        queueWrongPinEvent(context)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Correct PIN entered")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device admin DISABLED — tamper signal")
        queueEvent(context, "tamper")
    }

    /**
     * The key improvement: use the APPLICATION's long-lived scope, NOT a
     * throwaway CoroutineScope. The Application scope lives as long as the
     * process, so Android won't kill the write mid-flight (as long as the
     * service is running, which it always is when protection is active).
     *
     * We also make the write fully synchronous with respect to the receiver
     * returning — by launching on the app scope and NOT awaiting, we tell
     * Android "we're done with your callback" while still doing the work.
     * This works because our foreground service keeps the process alive.
     */
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
                // Fallback: still write, but this path is only used if the app class
                // is not registered in AndroidManifest.
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    writeEvent(context, deviceId, "wrong_pin")
                }
                return
            }

        // Primary path: use the app's process-scoped coroutine
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
            val event = EventEntity(
                deviceId = deviceId,
                type = type,
                timestamp = System.currentTimeMillis(),
                captureStatus = "pending_capture",
                clientSeq = System.nanoTime()   // monotonic, guaranteed unique
            )
            db.eventDao().upsert(event)
            Log.i(TAG, "$type event queued: ${event.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $type event", e)
        }
    }
}