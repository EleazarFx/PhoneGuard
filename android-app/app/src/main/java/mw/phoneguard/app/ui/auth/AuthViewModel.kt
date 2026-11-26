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
import mw.phoneguard.app.data.api.LoginApiResult
import mw.phoneguard.app.data.models.DeviceInfo
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
    val verificationCode: String = "",
    val verificationMessage: String? = null,
    val isVerificationRequired: Boolean = false,
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

    fun onVerificationCodeChange(newCode: String) {
        _uiState.value = _uiState.value.copy(verificationCode = newCode, errorMessage = null)
    }

    private fun getDeviceInfo(): DeviceInfo {
        val androidId = try {
            android.provider.Settings.Secure.getString(
                application.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
        } catch (_: Exception) { null }
        return DeviceInfo(
            model = android.os.Build.MODEL,
            manufacturer = android.os.Build.MANUFACTURER,
            androidId = androidId
        )
    }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Email and password are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = authApi.login(state.email, state.password, getDeviceInfo())) {
                is ApiResult.Success -> {
                    when (val loginResult = result.value) {
                        is LoginApiResult.Success -> {
                            tokenStore.saveUserTokens(loginResult.response.accessToken, loginResult.response.refreshToken)
                            tokenStore.saveUserEmail(state.email)

                            ensureDeviceRegistered(loginResult.response.accessToken, state.email)
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
                        is LoginApiResult.VerificationRequired -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isVerificationRequired = true,
                                verificationMessage = loginResult.message,
                                errorMessage = null
                            )
                        }
                    }
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

    fun verifyNewDevice() {
        val state = _uiState.value
        if (state.verificationCode.length != 6) {
            _uiState.value = state.copy(errorMessage = "Enter the 6-digit verification code")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)

            when (val result = authApi.verifyNewDevice(state.email, state.verificationCode, getDeviceInfo())) {
                is ApiResult.Success -> {
                    tokenStore.saveUserTokens(result.value.accessToken, result.value.refreshToken)
                    tokenStore.saveUserEmail(state.email)

                    ensureDeviceRegistered(result.value.accessToken, state.email)
                    saveCurrentSimIdentity(application)
                    startProtectionService()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        isVerificationRequired = false,
                        isDeviceRegistered = tokenStore.hasDeviceRegistered(),
                        userEmail = state.email,
                        verificationCode = "",
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

    fun resendVerificationCode() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            when (val result = authApi.login(state.email, state.password, getDeviceInfo())) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "New verification code sent to email."
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

    fun cancelVerification() {
        _uiState.value = _uiState.value.copy(
            isVerificationRequired = false,
            verificationCode = "",
            errorMessage = null
        )
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

            val registerResult = authApi.register(state.email, state.password)
            if (registerResult is ApiResult.Failure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = registerResult.message
                )
                return@launch
            }

            when (val loginResult = authApi.login(state.email, state.password, getDeviceInfo())) {
                is ApiResult.Success -> {
                    when (val res = loginResult.value) {
                        is LoginApiResult.Success -> {
                            tokenStore.saveUserTokens(res.response.accessToken, res.response.refreshToken)
                            tokenStore.saveUserEmail(state.email)

                            ensureDeviceRegistered(res.response.accessToken, state.email)
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
                        is LoginApiResult.VerificationRequired -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isVerificationRequired = true,
                                verificationMessage = res.message,
                                errorMessage = null
                            )
                        }
                    }
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

    private suspend fun ensureDeviceRegistered(userToken: String, email: String) {
        if (tokenStore.hasDeviceRegistered()) return

        val deviceName = android.os.Build.MODEL ?: "Android Device"
        val result = deviceApi.register(
            userAccessToken = userToken,
            deviceName = deviceName,
            model = android.os.Build.MODEL,
            imei1 = tokenStore.getImei1(),
            imei2 = tokenStore.getImei2()
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
            val simDetails = mw.phoneguard.app.services.SimHelper.extractSimDetails(context)

            if (simDetails.isLoaded()) {
                tokenStore.saveSimIdentity(
                    simDetails.operatorName,
                    simDetails.countryIso,
                    simDetails.operatorCode,
                    simDetails.subscriptionId
                )
                android.util.Log.i("AuthViewModel", "Initial SIM identity saved: ${simDetails.operatorName}/${simDetails.countryIso} (Code: ${simDetails.operatorCode}, Sub: ${simDetails.subscriptionId})")
            }

            if (!tokenStore.hasOriginalSimIdentity() && simDetails.isLoaded()) {
                tokenStore.saveOriginalSimIdentity(
                    simDetails.operators,
                    simDetails.countries,
                    simDetails.codes,
                    simDetails.subscriptions
                )
                android.util.Log.i("AuthViewModel", "Original SIM baseline saved: codes=${simDetails.codes}")
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthViewModel", "Failed to save SIM identity", e)
        }
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
