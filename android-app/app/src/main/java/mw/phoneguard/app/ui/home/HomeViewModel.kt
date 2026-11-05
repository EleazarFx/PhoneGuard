package mw.phoneguard.app.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore

data class HomeUiState(
    val userEmail: String? = null,
    val deviceRegistered: Boolean = false,
    val isAdminActive: Boolean = false,
    val alertModeArmed: Boolean = false,
    val lastCheckInText: String = "Just now",
    val trustedContactsCount: Int = 0,
    val unsyncedEventCount: Int = 0
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore.get(application.applicationContext)
    private val database = PhoneGuardDatabase.get(application.applicationContext)

    private val _uiState = MutableStateFlow(
        HomeUiState(
            userEmail = tokenStore.getUserEmail(),
            deviceRegistered = tokenStore.hasDeviceRegistered(),
            isAdminActive = checkAdminActive(),
            alertModeArmed = tokenStore.isAlertModeArmed(),
            trustedContactsCount = 0,
            unsyncedEventCount = 0
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        Log.d("HomeViewModel", "Initializing HomeViewModel; deviceId=${tokenStore.getDeviceId()}")
        // Observe the pending-sync count from Room in real time
        viewModelScope.launch {
            database.eventDao().observeUnsyncedCount().collect { count ->
                Log.d("HomeViewModel", "Unsynced count changed to: $count")
                _uiState.value = _uiState.value.copy(unsyncedEventCount = count)
            }
        }
    }


    fun toggleAlertMode(armed: Boolean) {
        tokenStore.saveAlertModeArmed(armed)
        _uiState.value = _uiState.value.copy(alertModeArmed = armed)

        val context = getApplication<android.app.Application>()
        try {
            mw.phoneguard.app.services.PhoneGuardService.setAlertMode(context, armed)
            android.util.Log.i("HomeViewModel", "Alert Mode toggle → $armed")
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Failed to toggle Alert Mode: ${e.message}", e)
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            userEmail = tokenStore.getUserEmail(),
            deviceRegistered = tokenStore.hasDeviceRegistered()
        )
    }


    private val syncManager = mw.phoneguard.app.data.sync.SyncManager(application.applicationContext)

    fun triggerSync() {
        viewModelScope.launch {
            syncManager.syncPendingEvents()
        }
    }


    private fun checkAdminActive(): Boolean {
        val context = getApplication<android.app.Application>()
        val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE)
                as? android.app.admin.DevicePolicyManager ?: return false
        val component = android.content.ComponentName(
            context,
            mw.phoneguard.app.services.PhoneGuardAdminReceiver::class.java
        )
        return dpm.isAdminActive(component)
    }

    fun refreshAdminState() {
        _uiState.value = _uiState.value.copy(isAdminActive = checkAdminActive())
    }

    fun refreshFromRoom() {
        viewModelScope.launch {
            val count = database.eventDao().getUnsynced().size
            android.util.Log.d("HomeViewModel", "Manual refresh: count=$count")
            _uiState.value = _uiState.value.copy(unsyncedEventCount = count)
        }
    }






}