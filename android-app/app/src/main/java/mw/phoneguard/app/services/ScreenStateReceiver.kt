package mw.phoneguard.app.services

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
 * Detects "unlock after lockout" as a proxy for failed PIN attempts.
 *
 * Android 10+ on many OEM ROMs (Transsion, Xiaomi, Samsung) does NOT fire
 * the standard onPasswordFailed callback. This receiver works around that:
 *
 *  1. On screen-off (or lock), record the time.
 *  2. On unlock (ACTION_USER_PRESENT), measure the elapsed time.
 *  3. If elapsed > LOCKOUT_THRESHOLD_MS, treat it as a probable failed-attempt
 *     lockout and log a wrong_pin event.
 *
 * This is a heuristic, not a guarantee. False positives are low (users don't
 * normally take 30+ seconds to unlock after locking). False negatives are low
 * on Android 10+ because the lockout timer is enforced by the OS.
 */
class ScreenStateReceiver : BroadcastReceiver() {

    private val TAG = "PhoneGuardScreenState"

    // 30 seconds from lock to unlock = probable failed-attempt lockout
    private val LOCKOUT_THRESHOLD_MS = 30_000L

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received: $action")

        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                // Screen turned off (likely going to lock state)
                val store = TokenStore.get(context.applicationContext)
                store.saveLockTime(System.currentTimeMillis())
                Log.d(TAG, "Lock time recorded: ${System.currentTimeMillis()}")
            }

            Intent.ACTION_USER_PRESENT -> {
                // User successfully unlocked
                handleUnlock(context)
            }
        }
    }

    private fun handleUnlock(context: Context) {
        val store = TokenStore.get(context.applicationContext)
        val deviceId = store.getDeviceId() ?: run {
            Log.w(TAG, "No device registered, skipping unlock analysis")
            return
        }

        val lockTime = store.getLockTime()
        if (lockTime == 0L) {
            Log.d(TAG, "No lock time recorded — skipping")
            return
        }

        val elapsed = System.currentTimeMillis() - lockTime
        Log.i(TAG, "Time from lock to unlock: ${elapsed}ms")

        // Clear the lock time so the same lock session isn't re-analyzed
        store.clearLockTime()

        if (elapsed < LOCKOUT_THRESHOLD_MS) {
            Log.d(TAG, "Normal unlock (${elapsed}ms) — no event")
            return
        }

        Log.w(TAG, "!!! PROBABLE FAILED-ATTEMPT LOCKOUT DETECTED (${elapsed}ms) !!!")

        val app = context.applicationContext as? mw.phoneguard.app.PhoneGuardApplication
        val scope = app?.appScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val db = PhoneGuardDatabase.get(context.applicationContext)
                val event = EventEntity(
                    deviceId = deviceId,
                    type = "wrong_pin",
                    timestamp = System.currentTimeMillis(),
                    captureStatus = "pending_capture",
                    clientSeq = System.nanoTime()
                )
                db.eventDao().upsert(event)
                Log.i(TAG, "wrong_pin event queued (from unlock-delay heuristic): ${event.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue event", e)
            }
        }
    }
}