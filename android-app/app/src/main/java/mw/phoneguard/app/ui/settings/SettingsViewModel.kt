package mw.phoneguard.app.ui.settings

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore
import mw.phoneguard.app.services.PhoneGuardAdminReceiver
import mw.phoneguard.app.services.PhoneGuardService
import mw.phoneguard.app.services.ServiceLogger

data class SettingsUiState(
    val userEmail: String = "",
    val videoEvidenceEnabled: Boolean = false,
    val periodicLocationEnabled: Boolean = true,
    val offlineAutoLockEnabled: Boolean = false,
    val remoteWipeEnabled: Boolean = false,
    val escalationSmsEnabled: Boolean = false,
    val silenceEmailEnabled: Boolean = false,
    val gracePeriodSeconds: Int = 20,
    val motionSensitivity: String = "MEDIUM",
    val scheduledAlertEnabled: Boolean = false,
    val scheduledStartHour: Int = 22,
    val scheduledStartMinute: Int = 0,
    val scheduledEndHour: Int = 6,
    val scheduledEndMinute: Int = 0,
    val isAdminActive: Boolean = false,
    val imei: String = "",
    val hasAppPin: Boolean = false,
    val autoWipeEnabled: Boolean = false,
    val crashReportConsent: Boolean = false,
    val logs: List<String> = emptyList()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore.get(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            userEmail = tokenStore.getUserEmail() ?: "Not logged in",
            videoEvidenceEnabled = tokenStore.isVideoEvidenceEnabled(),
            periodicLocationEnabled = tokenStore.isPeriodicLocationEnabled(),
            offlineAutoLockEnabled = tokenStore.isOfflineAutoLockEnabled(),
            remoteWipeEnabled = tokenStore.isRemoteWipeEnabled(),
            escalationSmsEnabled = tokenStore.isEscalationSmsEnabled(),
            silenceEmailEnabled = tokenStore.isSilenceEmailEnabled(),
            gracePeriodSeconds = tokenStore.getGracePeriodSeconds(),
            motionSensitivity = tokenStore.getMotionSensitivity(),
            scheduledAlertEnabled = tokenStore.isScheduledAlertEnabled(),
            scheduledStartHour = tokenStore.getScheduledStartHour(),
            scheduledStartMinute = tokenStore.getScheduledStartMinute(),
            scheduledEndHour = tokenStore.getScheduledEndHour(),
            scheduledEndMinute = tokenStore.getScheduledEndMinute(),
            isAdminActive = checkAdminActive(),
            imei = tokenStore.getImei() ?: "",
            hasAppPin = tokenStore.hasAppPin(),
            autoWipeEnabled = tokenStore.isAutoWipeOnPinFailureEnabled(),
            crashReportConsent = tokenStore.getCrashReportConsent(),
            logs = ServiceLogger.getLogs()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun toggleCrashReportConsent(enabled: Boolean) {
        tokenStore.setCrashReportConsent(enabled)
        _uiState.value = _uiState.value.copy(crashReportConsent = enabled)
    }

    fun toggleVideoEvidence(enabled: Boolean) {
        tokenStore.saveVideoEvidenceEnabled(enabled)
        _uiState.value = _uiState.value.copy(videoEvidenceEnabled = enabled)
    }

    fun togglePeriodicLocation(enabled: Boolean) {
        tokenStore.savePeriodicLocationEnabled(enabled)
        _uiState.value = _uiState.value.copy(periodicLocationEnabled = enabled)
    }

    fun toggleOfflineAutoLock(enabled: Boolean) {
        tokenStore.saveOfflineAutoLockEnabled(enabled)
        _uiState.value = _uiState.value.copy(offlineAutoLockEnabled = enabled)
    }

    fun toggleRemoteWipe(enabled: Boolean) {
        tokenStore.saveRemoteWipeEnabled(enabled)
        _uiState.value = _uiState.value.copy(remoteWipeEnabled = enabled)
    }

    fun toggleEscalationSms(enabled: Boolean) {
        tokenStore.saveEscalationSmsEnabled(enabled)
        _uiState.value = _uiState.value.copy(escalationSmsEnabled = enabled)
    }

    fun toggleSilenceEmail(enabled: Boolean) {
        tokenStore.saveSilenceEmailEnabled(enabled)
        _uiState.value = _uiState.value.copy(silenceEmailEnabled = enabled)
    }

    fun setGracePeriod(seconds: Int) {
        tokenStore.saveGracePeriodSeconds(seconds)
        _uiState.value = _uiState.value.copy(gracePeriodSeconds = seconds)
    }

    fun setMotionSensitivity(sensitivity: String) {
        tokenStore.saveMotionSensitivity(sensitivity)
        _uiState.value = _uiState.value.copy(motionSensitivity = sensitivity)
    }

    fun setScheduledAlertEnabled(enabled: Boolean) {
        tokenStore.saveScheduledAlertEnabled(enabled)
        _uiState.value = _uiState.value.copy(scheduledAlertEnabled = enabled)
        val context = getApplication<Application>()
        if (enabled) {
            mw.phoneguard.app.services.ScheduledAlertScheduler.scheduleAlarms(context)
        } else {
            mw.phoneguard.app.services.ScheduledAlertScheduler.cancelAlarms(context)
        }
    }

    fun setScheduledTimes(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        tokenStore.saveScheduledAlertTimes(startHour, startMinute, endHour, endMinute)
        _uiState.value = _uiState.value.copy(
            scheduledStartHour = startHour,
            scheduledStartMinute = startMinute,
            scheduledEndHour = endHour,
            scheduledEndMinute = endMinute
        )
        val context = getApplication<Application>()
        if (tokenStore.isScheduledAlertEnabled()) {
            mw.phoneguard.app.services.ScheduledAlertScheduler.scheduleAlarms(context)
        }
    }

    fun saveImei(imei: String) {
        tokenStore.saveImei(imei)
        _uiState.value = _uiState.value.copy(imei = imei)
    }

    fun saveAppPin(pin: String) {
        try {
            val hashed = org.mindrot.jbcrypt.BCrypt.hashpw(pin, org.mindrot.jbcrypt.BCrypt.gensalt(12))
            tokenStore.saveAppPin(hashed)
            _uiState.value = _uiState.value.copy(hasAppPin = true)
            ServiceLogger.log("Settings", "App PIN saved/updated")
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Failed to save app PIN", e)
        }
    }

    fun verifyOldPin(oldPin: String): Boolean {
        val storedHash = tokenStore.getAppPin() ?: return false
        return try {
            org.mindrot.jbcrypt.BCrypt.checkpw(oldPin, storedHash)
        } catch (_: Exception) {
            false
        }
    }

    fun toggleAutoWipe(enabled: Boolean) {
        tokenStore.saveAutoWipeOnPinFailureEnabled(enabled)
        _uiState.value = _uiState.value.copy(autoWipeEnabled = enabled)
    }

    fun wipeAppDataWithPin(pin: String, onResult: (Boolean, String) -> Unit) {
        if (!verifyOldPin(pin)) {
            onResult(false, "Incorrect PIN")
            return
        }
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                PhoneGuardDatabase.get(context).clearAllTables()
                tokenStore.clearAll()
                mw.phoneguard.app.services.AppSessionManager.clear()
                onResult(true, "App data wiped successfully")
            } catch (e: Exception) {
                onResult(false, "Failed to wipe data: ${e.message}")
            }
        }
    }

    fun deactivateDeviceAdmin(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val admin = ComponentName(context, PhoneGuardAdminReceiver::class.java)
            if (dpm != null && dpm.isAdminActive(admin)) {
                dpm.removeActiveAdmin(admin)
                ServiceLogger.log("Settings", "Device Admin deactivated by user")
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Failed to deactivate admin", e)
        }
        refreshAdminState()
    }

    fun refreshAdminState() {
        _uiState.value = _uiState.value.copy(isAdminActive = checkAdminActive())
    }

    private fun checkAdminActive(): Boolean {
        val context = getApplication<Application>()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val admin = ComponentName(context, PhoneGuardAdminReceiver::class.java)
        return dpm.isAdminActive(admin)
    }

    fun resetOriginalSim(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
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
                    val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
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

                if (codes.isNotEmpty() || operator.isNotBlank()) {
                    tokenStore.resetOriginalSimIdentity(operators, countries, codes, subscriptions)
                    tokenStore.saveSimIdentity(operator, country, operatorCode, subId)
                    val displayOp = if (operator.isNotBlank()) operator else if (operatorCode.isNotBlank()) operatorCode else "Current Operator"
                    val successMsg = "Original SIM updated to $displayOp"
                    ServiceLogger.log("Settings", successMsg)
                    onResult(true, successMsg)
                } else {
                    onResult(false, "No active SIM detected to set as baseline")
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to reset original SIM", e)
                onResult(false, "Error: ${e.message}")
            }
        }
    }

    private fun getActiveSubscriptionId(context: Application): String {
        try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
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

    fun forceSimCheck(context: Context, onResult: (String) -> Unit) {
        PhoneGuardService.forceSimCheck(context)
        refreshLogs()
        onResult("Forced SIM check executed")
    }

    fun forceWrongPinTrigger(context: Context, onResult: (String) -> Unit) {
        PhoneGuardService.forceWrongPinTrigger(context)
        refreshLogs()
        onResult("Forced wrong-PIN trigger executed")
    }

    fun refreshLogs() {
        _uiState.value = _uiState.value.copy(logs = ServiceLogger.getLogs())
    }

    fun refresh() {
        _uiState.value = SettingsUiState(
            userEmail = tokenStore.getUserEmail() ?: "Not logged in",
            videoEvidenceEnabled = tokenStore.isVideoEvidenceEnabled(),
            periodicLocationEnabled = tokenStore.isPeriodicLocationEnabled(),
            offlineAutoLockEnabled = tokenStore.isOfflineAutoLockEnabled(),
            remoteWipeEnabled = tokenStore.isRemoteWipeEnabled(),
            escalationSmsEnabled = tokenStore.isEscalationSmsEnabled(),
            silenceEmailEnabled = tokenStore.isSilenceEmailEnabled(),
            gracePeriodSeconds = tokenStore.getGracePeriodSeconds(),
            motionSensitivity = tokenStore.getMotionSensitivity(),
            scheduledAlertEnabled = tokenStore.isScheduledAlertEnabled(),
            scheduledStartHour = tokenStore.getScheduledStartHour(),
            scheduledStartMinute = tokenStore.getScheduledStartMinute(),
            scheduledEndHour = tokenStore.getScheduledEndHour(),
            scheduledEndMinute = tokenStore.getScheduledEndMinute(),
            isAdminActive = checkAdminActive(),
            imei = tokenStore.getImei() ?: "",
            hasAppPin = tokenStore.hasAppPin(),
            autoWipeEnabled = tokenStore.isAutoWipeOnPinFailureEnabled(),
            logs = ServiceLogger.getLogs()
        )
    }
}
