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

    fun resetOriginalSim(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val tm = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                if (tm == null) {
                    onResult(false, "TelephonyManager unavailable")
                    return@launch
                }

                val operator = tm.simOperatorName ?: ""
                val country = tm.simCountryIso ?: ""
                val operatorCode = tm.simOperator ?: ""
                val subId = getActiveSubscriptionId(context)

                val operators = mutableSetOf<String>()
                val countries = mutableSetOf<String>()
                val codes = mutableSetOf<String>()
                val subscriptions = mutableSetOf<String>()

                if (operator.isNotBlank()) operators.add(operator)
                if (country.isNotBlank()) countries.add(country)
                if (operatorCode.isNotBlank()) codes.add(operatorCode)
                if (subId.isNotBlank()) subscriptions.add(subId)

                try {
                    val sm = context.getSystemService(android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                            as? android.telephony.SubscriptionManager
                    if (sm != null) {
                        @Suppress("MissingPermission")
                        val activeList = sm.activeSubscriptionInfoList
                        if (!activeList.isNullOrEmpty()) {
                            for (sub in activeList) {
                                if (!sub.carrierName.isNullOrBlank()) operators.add(sub.carrierName.toString())
                                if (!sub.countryIso.isNullOrBlank()) countries.add(sub.countryIso.toString())
                                val mccMnc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    "${sub.mccString.orEmpty()}${sub.mncString.orEmpty()}"
                                } else ""
                                if (mccMnc.isNotBlank()) codes.add(mccMnc)
                                val carrier = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) sub.carrierId else sub.simSlotIndex
                                subscriptions.add("${sub.subscriptionId}:$carrier")
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (codes.isEmpty() && operatorCode.isNotBlank()) codes.add(operatorCode)

                if (codes.isNotEmpty()) {
                    tokenStore.resetOriginalSimIdentity(operators, countries, codes, subscriptions)
                    tokenStore.saveSimIdentity(operator, country, operatorCode, subId)
                    onResult(true, "Original SIM baseline updated successfully (${codes.size} SIM(s))")
                } else {
                    onResult(false, "No active SIM detected to set as baseline")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to reset original SIM", e)
                onResult(false, "Error: ${e.message}")
            }
        }
    }

    private fun getActiveSubscriptionId(context: Application): String {
        try {
            val sm = context.getSystemService(android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? android.telephony.SubscriptionManager
            if (sm != null) {
                @Suppress("MissingPermission")
                val activeList = sm.activeSubscriptionInfoList
                if (!activeList.isNullOrEmpty()) {
                    return activeList.joinToString(",") { sub ->
                        val carrier = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) sub.carrierId else sub.simSlotIndex
                        "${sub.subscriptionId}:$carrier"
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }






}