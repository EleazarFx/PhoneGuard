package mw.phoneguard.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.api.ApiResult
import mw.phoneguard.app.data.api.AuthApi
import mw.phoneguard.app.data.api.DeviceApi
import mw.phoneguard.app.data.storage.TokenStore
import mw.phoneguard.app.services.PhoneGuardService

/**
 * The state of the auth flow, as seen by the UI.
 *
 * UI renders this. Nothing more. If you want to show something, put it here.
 */
enum class ResetStep { None, RequestOtp, EnterOtp }

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val otpCode: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val isDeviceRegistered: Boolean = false,
    val userEmail: String? = null,
    val resetStep: ResetStep = ResetStep.None
)

class AuthViewModel(private val application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore.get(application.applicationContext)
    private val authApi = AuthApi()
    private val deviceApi = DeviceApi()

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isLoggedIn = tokenStore.hasUserSession(),
            isDeviceRegistered = tokenStore.hasDeviceRegistered(),
            userEmail = tokenStore.getUserEmail()
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(newEmail: String) {
        _uiState.value = _uiState.value.copy(email = newEmail, errorMessage = null)
    }

    fun onPasswordChange(newPassword: String) {
        _uiState.value = _uiState.value.copy(password = newPassword, errorMessage = null)
    }




    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Email and password are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = authApi.login(state.email, state.password)) {
                is ApiResult.Success -> {
                    tokenStore.saveUserTokens(result.value.accessToken, result.value.refreshToken)
                    tokenStore.saveUserEmail(state.email)

                    // After login, ensure this phone is registered as a device.
                    ensureDeviceRegistered(result.value.accessToken, state.email)

                    // Save current SIM identity on first login so we can detect future swaps
                    saveCurrentSimIdentity(application)

                    startProtectionService()


                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        isDeviceRegistered = tokenStore.hasDeviceRegistered(),
                        userEmail = state.email,
                        errorMessage = null
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun registerAndLogin() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Email and password are required")
            return
        }
        if (state.password.length < 8) {
            _uiState.value = state.copy(errorMessage = "Password must be at least 8 characters")
            return
        }
        if (tokenStore.hasDeviceRegistered()) {
            _uiState.value = state.copy(errorMessage = "Device already registered")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Register first. If the email is taken, we get a Failure and stop.
            val registerResult = authApi.register(state.email, state.password)
            if (registerResult is ApiResult.Failure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = registerResult.message
                )
                return@launch
            }

            // Then log in — this gives us tokens.
            when (val loginResult = authApi.login(state.email, state.password)) {
                is ApiResult.Success -> {
                    tokenStore.saveUserTokens(loginResult.value.accessToken, loginResult.value.refreshToken)
                    tokenStore.saveUserEmail(state.email)

                    ensureDeviceRegistered(loginResult.value.accessToken, state.email)

                    // Save current SIM identity on first login so we can detect future swaps
                    saveCurrentSimIdentity(application)

                    startProtectionService()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        isDeviceRegistered = tokenStore.hasDeviceRegistered(),
                        userEmail = state.email,
                        errorMessage = null
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = loginResult.message
                    )
                }
            }
        }
    }

    /**
     * Registers this phone as a device if it isn't already.
     * Runs after login, but failures here are not fatal — the user can
     * retry later from the home screen.
     */
    private suspend fun ensureDeviceRegistered(userToken: String, email: String) {
        if (tokenStore.hasDeviceRegistered()) return

        val deviceName = android.os.Build.MODEL ?: "Android Device"
        val result = deviceApi.register(
            userAccessToken = userToken,
            deviceName = deviceName,
            model = android.os.Build.MODEL
        )
        if (result is ApiResult.Success) {
            tokenStore.saveDeviceIdentity(result.value.deviceId, result.value.deviceToken)
            _uiState.value = _uiState.value.copy(isDeviceRegistered = true)
        }
    }

    fun logout() {
        tokenStore.clearUserSession()
        _uiState.value = AuthUiState()
    }








    fun onOtpChange(newOtp: String) {
        _uiState.value = _uiState.value.copy(otpCode = newOtp, errorMessage = null)
    }

    fun startForgotPassword() {
        _uiState.value = _uiState.value.copy(
            resetStep = ResetStep.RequestOtp,
            errorMessage = null
        )
    }

    fun cancelReset() {
        _uiState.value = _uiState.value.copy(
            resetStep = ResetStep.None,
            otpCode = "",
            password = "",
            errorMessage = null
        )
    }

    fun submitForgotPasswordRequest() {
        val email = _uiState.value.email
        if (email.isBlank() || !email.contains("@")) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter a valid email")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = authApi.requestPasswordReset(email)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        resetStep = ResetStep.EnterOtp
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun submitNewPassword() {
        val state = _uiState.value
        if (state.otpCode.length != 6) {
            _uiState.value = state.copy(errorMessage = "Enter the 6-digit code")
            return
        }
        if (state.password.length < 8) {
            _uiState.value = state.copy(errorMessage = "New password must be at least 8 characters")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            when (val result = authApi.resetPassword(state.email, state.otpCode, state.password)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        resetStep = ResetStep.None,
                        otpCode = "",
                        password = "",
                        errorMessage = "Password reset. You can log in now."
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }



    private fun saveCurrentSimIdentity(context: android.content.Context) {
        try {
            val tm = context.getSystemService(android.content.Context.TELEPHONY_SERVICE)
                    as? android.telephony.TelephonyManager ?: return
            val operator = tm.simOperatorName ?: ""
            val country = tm.simCountryIso ?: ""
            val operatorCode = tm.simOperator ?: ""
            val subId = getActiveSubscriptionId(context)

            if (operator.isNotBlank() || country.isNotBlank() || operatorCode.isNotBlank() || subId.isNotBlank()) {
                tokenStore.saveSimIdentity(operator, country, operatorCode, subId)
                android.util.Log.i("AuthViewModel", "Initial SIM identity saved: $operator/$country (Code: $operatorCode, Sub: $subId)")
            }

            if (!tokenStore.hasOriginalSimIdentity()) {
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
                    tokenStore.saveOriginalSimIdentity(operators, countries, codes, subscriptions)
                    android.util.Log.i("AuthViewModel", "Original SIM baseline saved: codes=$codes")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthViewModel", "Failed to save SIM identity", e)
        }
    }

    private fun getActiveSubscriptionId(context: android.content.Context): String {
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
        } catch (_: SecurityException) {
            // READ_PHONE_STATE permission not granted
        } catch (e: Exception) {
            android.util.Log.w("AuthViewModel", "SubscriptionManager query failed: ${e.message}")
        }
        return ""
    }


    private fun startProtectionService() {
        val context = getApplication<android.app.Application>()
        try {
            PhoneGuardService.start(context)
            android.util.Log.i("AuthViewModel", "PhoneGuardService start requested after login")
        } catch (e: Exception) {
            android.util.Log.e("AuthViewModel", "Failed to start service: ${e.message}", e)
        }
    }




}