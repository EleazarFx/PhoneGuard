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
import mw.phoneguard.app.services.ProtectionChecklistHelper
import mw.phoneguard.app.services.ProtectionChecklistState
import mw.phoneguard.app.services.TrackingMode

data class HomeUiState(
    val userEmail: String? = null,
    val deviceRegistered: Boolean = false,
    val isAdminActive: Boolean = false,
    val alertModeArmed: Boolean = false,
    val lastCheckInText: String = "Just now",
    val trustedContactsCount: Int = 0,
    val unsyncedEventCount: Int = 0,
    val protectionState: ProtectionChecklistState = ProtectionChecklistState(
        hasBackgroundLocation = false,
        hasDeviceAdmin = false,
        isBatteryOptimizationDisabled = false,
        isAutoStartConfigured = false,
        hasSmsPermission = false
    ),
    val currentTrackingMode: TrackingMode = TrackingMode.NORMAL
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore.get(application.applicationContext)
    private val database = PhoneGuardDatabase.get(application.applicationContext)
    private val syncManager = mw.phoneguard.app.data.sync.SyncManager(application.applicationContext)

    private val _uiState = MutableStateFlow(
        HomeUiState(
            userEmail = tokenStore.getUserEmail(),
            deviceRegistered = tokenStore.hasDeviceRegistered(),
            isAdminActive = checkAdminActive(),
            alertModeArmed = tokenStore.isAlertModeArmed(),
            lastCheckInText = computeLastCheckInText(),
            trustedContactsCount = tokenStore.getTrustedContacts().size,
            unsyncedEventCount = 0,
            protectionState = ProtectionChecklistHelper.getProtectionState(application.applicationContext),
            currentTrackingMode = getStoredTrackingMode()
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private fun getStoredTrackingMode(): TrackingMode {
        val modeName = tokenStore.getTrackingModeName()
        return try { TrackingMode.valueOf(modeName) } catch (_: Exception) { TrackingMode.NORMAL }
    }

    private fun computeLastCheckInText(): String {
        val lastCheckIn = tokenStore.getLastCheckInTime()
        val diffMs = System.currentTimeMillis() - lastCheckIn
        val diffMinutes = diffMs / (60 * 1000)
        return if (diffMinutes >= 15) {
            "Device silent since $diffMinutes minutes"
        } else if (diffMinutes <= 1) {
            "Just now"
        } else {
            "${diffMinutes}m ago"
        }
    }

    init {
        Log.d("HomeViewModel", "Initializing HomeViewModel; deviceId=${tokenStore.getDeviceId()}")
        viewModelScope.launch {
            database.eventDao().observeUnsyncedCount().collect { count ->
                _uiState.value = _uiState.value.copy(unsyncedEventCount = count)
            }
        }
        viewModelScope.launch {
            database.contactDao().observeAll().collect { contacts ->
                _uiState.value = _uiState.value.copy(trustedContactsCount = contacts.size)
            }
        }
    }

    fun toggleAlertMode(armed: Boolean) {
        tokenStore.saveAlertModeArmed(armed)
        _uiState.value = _uiState.value.copy(alertModeArmed = armed)

        val context = getApplication<Application>()
        try {
            mw.phoneguard.app.services.PhoneGuardService.setAlertMode(context, armed)
            Log.i("HomeViewModel", "Alert Mode toggle → $armed")
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Failed to toggle Alert Mode: ${e.message}", e)
        }
    }

    fun refresh() {
        val context = getApplication<Application>().applicationContext
        val pState = ProtectionChecklistHelper.getProtectionState(context)
        _uiState.value = _uiState.value.copy(
            userEmail = tokenStore.getUserEmail(),
            deviceRegistered = tokenStore.hasDeviceRegistered(),
            trustedContactsCount = tokenStore.getTrustedContacts().size,
            lastCheckInText = computeLastCheckInText(),
            protectionState = pState,
            isAdminActive = pState.hasDeviceAdmin,
            currentTrackingMode = getStoredTrackingMode()
        )
    }

    fun triggerSync() {
        viewModelScope.launch {
            syncManager.syncPendingEvents()
        }
    }

    private fun checkAdminActive(): Boolean {
        val context = getApplication<Application>()
        val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager ?: return false
        val component = android.content.ComponentName(
            context,
            mw.phoneguard.app.services.PhoneGuardAdminReceiver::class.java
        )
        return dpm.isAdminActive(component)
    }

    fun refreshAdminState() {
        val context = getApplication<Application>().applicationContext
        val pState = ProtectionChecklistHelper.getProtectionState(context)
        _uiState.value = _uiState.value.copy(
            isAdminActive = pState.hasDeviceAdmin,
            protectionState = pState,
            currentTrackingMode = getStoredTrackingMode()
        )
    }

    fun refreshFromRoom() {
        viewModelScope.launch {
            val count = database.eventDao().getUnsynced().size
            Log.d("HomeViewModel", "Manual refresh: count=$count")
            _uiState.value = _uiState.value.copy(unsyncedEventCount = count)
        }
    }

    fun resetOriginalSim(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val simDetails = mw.phoneguard.app.services.SimHelper.extractSimDetails(context)

                if (simDetails.isLoaded()) {
                    tokenStore.resetOriginalSimIdentity(
                        simDetails.operators,
                        simDetails.countries,
                        simDetails.codes,
                        simDetails.subscriptions
                    )
                    tokenStore.saveSimIdentity(
                        simDetails.operatorName,
                        simDetails.countryIso,
                        simDetails.operatorCode,
                        simDetails.subscriptionId
                    )
                    onResult(true, "Trusted SIM identity successfully reset to active SIM.")
                } else {
                    onResult(false, "No active SIM detected to set as baseline")
                }
            } catch (e: Exception) {
                onResult(false, "Failed to reset SIM identity: ${e.message}")
            }
        }
    }
}
