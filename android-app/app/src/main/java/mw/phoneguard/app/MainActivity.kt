package mw.phoneguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import mw.phoneguard.app.ui.activity.ActivityViewModel

import mw.phoneguard.app.ui.auth.AuthViewModel
import mw.phoneguard.app.ui.auth.LoginScreen
import mw.phoneguard.app.ui.auth.RegisterScreen
import mw.phoneguard.app.ui.home.HomeScreen
import mw.phoneguard.app.ui.theme.PhoneGuardTheme

import mw.phoneguard.app.ui.auth.ForgotPasswordScreen
import mw.phoneguard.app.ui.auth.ResetPasswordScreen
import mw.phoneguard.app.ui.auth.ResetStep
import mw.phoneguard.app.ui.home.HomeViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneGuardTheme {
                PhoneGuardApp(authViewModel, homeViewModel)
            }
        }
    }


}
@Composable
fun PhoneGuardApp(authViewModel: AuthViewModel, homeViewModel: HomeViewModel) {
    val state by authViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showRegister by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(AppScreen.Home) }

    // Define the activityViewModel only if logged in
    val activityViewModel: ActivityViewModel? = if (state.isLoggedIn) {
        androidx.lifecycle.viewmodel.compose.viewModel()
    } else null

    when {
        state.isLoggedIn -> {
            val homeState by homeViewModel.uiState.collectAsState()

            androidx.compose.runtime.LaunchedEffect(Unit) {
                homeViewModel.triggerSync()
            }

            when (currentScreen) {
                AppScreen.Home -> HomeScreen(
                    userEmail = homeState.userEmail,
                    deviceRegistered = homeState.deviceRegistered,
                    alertModeArmed = homeState.alertModeArmed,
                    lastCheckIn = homeState.lastCheckInText,
                    trustedContactsCount = homeState.trustedContactsCount,
                    unsyncedEventCount = homeState.unsyncedEventCount,
                    onToggleAlertMode = homeViewModel::toggleAlertMode,
                    onEnableAdminClick = { /* ... existing ... */ },
                    onLogout = { authViewModel.logout() },
                    onNavigate = { currentScreen = it },
                    currentScreen = currentScreen
                )

                AppScreen.Activity -> {
                    val events by activityViewModel!!.events.collectAsState()
                    mw.phoneguard.app.ui.activity.ActivityScreen(
                        events = events,
                        currentScreen = currentScreen,
                        onNavigate = { currentScreen = it }
                    )
                }

                else -> {
                    // Placeholder for Contacts/Settings
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Coming soon", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }


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


enum class AppScreen {
    Home, Activity, Contacts, Settings
}