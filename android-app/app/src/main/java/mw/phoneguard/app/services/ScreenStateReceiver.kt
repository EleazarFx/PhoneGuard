package mw.phoneguard.app.services

import android.app.KeyguardManager
import android.content.BroadcastReceiver
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

/**
 * Tracks screen state to keep the Alert Mode motion sensor active
 * whenever the phone is NOT unlocked.
 *
 * Rules:
 *  - SCREEN_OFF      → not unlocked → start monitoring
 *  - SCREEN_ON       → check KeyguardManager:
 *                       locked   → keep monitoring
 *                       unlocked → stop monitoring
 *  - USER_PRESENT    → real unlock → stop monitoring
 *  - BOOT_COMPLETED  → check current lock state → monitor if locked
 */
class ScreenStateReceiver : BroadcastReceiver() {

    private val TAG = "PhoneGuardScreenState"
    private val LOCKOUT_THRESHOLD_MS = 30_000L

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received: $action")

        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                val store = TokenStore.get(context.applicationContext)
                store.saveLockTime(System.currentTimeMillis())
                Log.d(TAG, "Lock time recorded")

                // Not unlocked → monitor motion
                notifyService(context, PhoneGuardService.ACTION_NOT_UNLOCKED)
            }

            Intent.ACTION_SCREEN_ON -> {
                val kgm = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val isLocked = kgm?.isDeviceLocked() == true || kgm?.isKeyguardLocked == true
                Log.d(TAG, "Screen on. isLocked=$isLocked")

                if (isLocked) {
                    // Screen on but still locked → keep monitoring
                    notifyService(context, PhoneGuardService.ACTION_NOT_UNLOCKED)
                } else {
                    // Screen on and already unlocked → stop monitoring
                    notifyService(context, PhoneGuardService.ACTION_UNLOCKED)
                }
            }

            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present (real unlock)")
                notifyService(context, PhoneGuardService.ACTION_UNLOCKED)
                handleUnlockDelay(context)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                // After reboot: check if phone is currently locked
                val kgm = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val isLocked = kgm?.isDeviceLocked() == true || kgm?.isKeyguardLocked == true
                Log.d(TAG, "Boot completed. isLocked=$isLocked")

                if (isLocked) {
                    notifyService(context, PhoneGuardService.ACTION_NOT_UNLOCKED)
                } else {
                    notifyService(context, PhoneGuardService.ACTION_UNLOCKED)
                }
            }
        }
    }

    private fun notifyService(context: Context, action: String) {
        try {
            val serviceIntent = Intent(context, PhoneGuardService::class.java).apply {
                this.action = action
            }
            context.startService(serviceIntent)
            Log.d(TAG, "Sent $action to service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify service: ${e.message}", e)
        }
    }

    private fun handleUnlockDelay(context: Context) {
        val store = TokenStore.get(context.applicationContext)
        val deviceId = store.getDeviceId() ?: return

        val lockTime = store.getLockTime()
        if (lockTime == 0L) return

        val elapsed = System.currentTimeMillis() - lockTime
        Log.i(TAG, "Time from lock to unlock: ${elapsed}ms")
        store.clearLockTime()

        if (elapsed < LOCKOUT_THRESHOLD_MS) {
            Log.d(TAG, "Normal unlock — no event")
            return
        }

        Log.w(TAG, "!!! PROBABLE FAILED-ATTEMPT LOCKOUT DETECTED (${elapsed}ms) !!!")
        queueEvent(context, deviceId, "wrong_pin")
    }

    private fun queueEvent(context: Context, deviceId: String, type: String) {
        val app = context.applicationContext as? mw.phoneguard.app.PhoneGuardApplication
        val scope = app?.appScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
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
                Log.e(TAG, "Failed to queue event", e)
            }
        }
    }
}