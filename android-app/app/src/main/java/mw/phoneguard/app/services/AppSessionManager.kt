package mw.phoneguard.app.services

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSessionManager {
    private val _isUnlocked = MutableStateFlow(true)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    @Suppress("UNUSED_PARAMETER")
    fun unlock(pin: String, context: Context): Boolean = true

    fun touch() {}

    fun checkExpiry() {}

    fun lock() {
        _isUnlocked.value = true
    }

    fun clear() {
        _isUnlocked.value = true
    }

    @Suppress("UNUSED_PARAMETER")
    fun getRemainingLockoutSeconds(context: Context): Long = 0L
}
