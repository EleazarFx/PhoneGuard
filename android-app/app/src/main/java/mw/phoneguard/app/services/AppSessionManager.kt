package mw.phoneguard.app.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore
import org.mindrot.jbcrypt.BCrypt

object AppSessionManager {
    private const val TAG = "AppSessionManager"
    private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var lastActivityTime: Long = System.currentTimeMillis()

    fun unlock(pin: String, context: Context): Boolean {
        val tokenStore = TokenStore.get(context)
        val lockoutUntil = tokenStore.getPinLockoutUntil()
        val now = System.currentTimeMillis()

        if (now < lockoutUntil) {
            Log.w(TAG, "PIN entry attempted during lockout. Remaining: ${(lockoutUntil - now) / 1000}s")
            return false
        }

        val storedHash = tokenStore.getAppPin()
        if (storedHash.isNullOrBlank()) {
            Log.w(TAG, "No app PIN configured")
            return false
        }

        val isValid = try {
            BCrypt.checkpw(pin, storedHash)
        } catch (e: Exception) {
            Log.e(TAG, "BCrypt check failed", e)
            false
        }

        if (isValid) {
            Log.i(TAG, "App PIN verified successfully. Session unlocked.")
            _isUnlocked.value = true
            lastActivityTime = now
            tokenStore.resetPinFailedAttempts()
            return true
        } else {
            Log.w(TAG, "Invalid App PIN entered")
            handleFailedAttempt(context)
            return false
        }
    }

    private fun handleFailedAttempt(context: Context) {
        val tokenStore = TokenStore.get(context)
        val now = System.currentTimeMillis()
        val firstFailureTime = tokenStore.getPinFirstFailureTime()

        // Reset tracking if outside 1 hour window
        val oneHourMs = 60 * 60 * 1000L
        val tenMinutesMs = 10 * 60 * 1000L

        if (firstFailureTime == 0L || (now - firstFailureTime > oneHourMs)) {
            tokenStore.savePinFirstFailureTime(now)
            tokenStore.savePinFailedAttempts(1)
        } else {
            tokenStore.incrementPinFailedAttempts()
        }

        val failedAttempts = tokenStore.getPinFailedAttempts()
        val failureDuration = now - tokenStore.getPinFirstFailureTime()

        Log.w(TAG, "PIN failed attempts: $failedAttempts, duration: ${failureDuration / 1000}s")

        // Log tamper_app_pin event to Room + backend
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val event = EventEntity(
                    deviceId = tokenStore.getDeviceId() ?: "unknown",
                    type = "tamper_app_pin",
                    timestamp = now
                )
                PhoneGuardDatabase.get(context).eventDao().upsert(event)
                ServiceLogger.log("Security", "Failed App PIN attempt recorded (total: $failedAttempts)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log tamper_app_pin event", e)
            }
        }

        // Lockout & Auto-wipe rules
        if (failedAttempts >= 10) {
            if (tokenStore.isAutoWipeOnPinFailureEnabled()) {
                Log.e(TAG, "10 failed PIN attempts reached with auto-wipe enabled! Self-wiping app data...")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        PhoneGuardDatabase.get(context).clearAllTables()
                    } catch (_: Exception) {}
                    tokenStore.clearAll()
                }
            } else {
                // Lockout for 15 minutes or 5 minutes
                tokenStore.savePinLockoutUntil(now + 5 * 60 * 1000L)
            }
        } else if (failedAttempts >= 5 && failureDuration <= oneHourMs) {
            // Lockout for 5 minutes
            tokenStore.savePinLockoutUntil(now + 5 * 60 * 1000L)
            Log.w(TAG, "5+ failures within 1 hour: locked out for 5 minutes")
        } else if (failedAttempts >= 3 && failureDuration <= tenMinutesMs) {
            // Lockout for 60 seconds
            tokenStore.savePinLockoutUntil(now + 60 * 1000L)
            Log.w(TAG, "3+ failures within 10 minutes: locked out for 60 seconds")
        }
    }

    fun touch() {
        if (_isUnlocked.value) {
            lastActivityTime = System.currentTimeMillis()
        }
    }

    fun checkExpiry() {
        if (_isUnlocked.value) {
            val now = System.currentTimeMillis()
            if (now - lastActivityTime > SESSION_TIMEOUT_MS) {
                Log.i(TAG, "Session expired after 5 minutes of inactivity. Locking app.")
                _isUnlocked.value = false
            }
        }
    }

    fun lock() {
        Log.i(TAG, "Locking app session")
        _isUnlocked.value = false
    }

    fun clear() {
        Log.i(TAG, "Clearing app session")
        _isUnlocked.value = false
        lastActivityTime = 0L
    }

    fun getRemainingLockoutSeconds(context: Context): Long {
        val tokenStore = TokenStore.get(context)
        val lockoutUntil = tokenStore.getPinLockoutUntil()
        val now = System.currentTimeMillis()
        return if (now < lockoutUntil) (lockoutUntil - now) / 1000 else 0L
    }
}
