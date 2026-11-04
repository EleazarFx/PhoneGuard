package mw.phoneguard.app.services

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore

class PhoneGuardAdminReceiver : DeviceAdminReceiver() {

    private val TAG = "PhoneGuardAdmin"

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
        Log.w(TAG, "Device admin was DISABLED — this is a tamper signal")
        queueTamperEvent(context)
    }

    private fun queueWrongPinEvent(context: Context) {
        val tokenStore = TokenStore(context.applicationContext)
        val deviceId = tokenStore.getDeviceId() ?: run {
            Log.w(TAG, "No device registered — can't queue event yet")
            return
        }

        val db = PhoneGuardDatabase.get(context.applicationContext)
        val event = EventEntity(
            deviceId = deviceId,
            type = "wrong_pin",
            timestamp = System.currentTimeMillis(),
            captureStatus = "pending_capture",   // We'll update this when camera capture is added
            clientSeq = System.currentTimeMillis()
        )

        // Use applicationScope-like fire-and-forget pattern — the DAO is suspend,
        // so we launch on a background scope. The receiver's process is short-lived,
        // but Room's insert is fast enough that it completes before the process ends.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.eventDao().upsert(event)
                Log.i(TAG, "Wrong-PIN event queued: ${event.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue event", e)
            }
        }
    }

    private fun queueTamperEvent(context: Context) {
        val tokenStore = TokenStore(context.applicationContext)
        val deviceId = tokenStore.getDeviceId() ?: return

        val db = PhoneGuardDatabase.get(context.applicationContext)
        val event = EventEntity(
            deviceId = deviceId,
            type = "tamper",
            timestamp = System.currentTimeMillis(),
            captureStatus = "n/a",
            clientSeq = System.currentTimeMillis()
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.eventDao().upsert(event)
                Log.i(TAG, "Tamper event queued: ${event.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue tamper event", e)
            }
        }
    }
}