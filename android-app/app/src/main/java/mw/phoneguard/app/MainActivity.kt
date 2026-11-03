package mw.phoneguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import mw.phoneguard.app.ui.auth.AuthViewModel
import mw.phoneguard.app.ui.auth.LoginScreen
import mw.phoneguard.app.ui.auth.RegisterScreen
import mw.phoneguard.app.ui.home.HomeScreen
import mw.phoneguard.app.ui.theme.PhoneGuardTheme

import mw.phoneguard.app.ui.auth.ForgotPasswordScreen
import mw.phoneguard.app.ui.auth.ResetPasswordScreen
import mw.phoneguard.app.ui.auth.ResetStep

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhoneGuardTheme {
                PhoneGuardApp(authViewModel)
            }
        }
    }
}
@Composable
fun PhoneGuardApp(authViewModel: AuthViewModel) {
    val state by authViewModel.uiState.collectAsState()

    var showRegister by remember { mutableStateOf(false) }

    when {
        // 1. Logged in → Home
        state.isLoggedIn -> HomeScreen(
            userEmail = state.userEmail,
            deviceRegistered = state.isDeviceRegistered,
            onLogout = { authViewModel.logout() }
        )

        // 2. Reset flow: request code
        state.resetStep == ResetStep.RequestOtp -> ForgotPasswordScreen(
            state = state,
            onEmailChange = authViewModel::onEmailChange,
            onSubmit = authViewModel::submitForgotPasswordRequest,
            onCancel = authViewModel::cancelReset
        )

        // 3. Reset flow: enter code + new password
        state.resetStep == ResetStep.EnterOtp -> ResetPasswordScreen(
            state = state,
            onOtpChange = authViewModel::onOtpChange,
            onPasswordChange = authViewModel::onPasswordChange,
            onSubmit = authViewModel::submitNewPassword,
            onCancel = authViewModel::cancelReset
        )

        // 4. Register screen
        showRegister -> RegisterScreen(
            state = state,
            onEmailChange = authViewModel::onEmailChange,
            onPasswordChange = authViewModel::onPasswordChange,
            onRegisterClick = authViewModel::registerAndLogin,
            onGoToLogin = { showRegister = false }
        )

        // 5. Login screen (default)
        else -> LoginScreen(
            state = state,
            onEmailChange = authViewModel::onEmailChange,
            onPasswordChange = authViewModel::onPasswordChange,
            onLoginClick = authViewModel::login,
            onRegisterClick = { },
            onGoToRegister = { showRegister = true },
            onForgotPasswordClick = authViewModel::startForgotPassword
        )
    }
}