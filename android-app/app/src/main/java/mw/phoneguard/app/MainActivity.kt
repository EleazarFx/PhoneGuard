package mw.phoneguard.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import mw.phoneguard.app.services.PhoneGuardAdminReceiver
import mw.phoneguard.app.ui.activity.ActivityScreen
import mw.phoneguard.app.ui.activity.ActivityViewModel
import mw.phoneguard.app.ui.auth.AuthViewModel
import mw.phoneguard.app.ui.auth.ForgotPasswordScreen
import mw.phoneguard.app.ui.auth.LoginScreen
import mw.phoneguard.app.ui.auth.RegisterScreen
import mw.phoneguard.app.ui.auth.ResetPasswordScreen
import mw.phoneguard.app.ui.auth.ResetStep
import mw.phoneguard.app.ui.home.HomeScreen
import mw.phoneguard.app.ui.home.HomeViewModel
import mw.phoneguard.app.ui.contacts.ContactsScreen
import mw.phoneguard.app.ui.settings.SettingsScreen
import mw.phoneguard.app.ui.theme.PhoneGuardTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask for notification permission on Android 13+ (for the foreground service notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val launcher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (!granted) {
                    android.util.Log.w(
                        "PhoneGuard",
                        "POST_NOTIFICATIONS denied — persistent notification may be hidden"
                    )
                }
            }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Ask for camera permission for evidence capture
        val cameraLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                android.util.Log.w(
                    "PhoneGuard",
                    "CAMERA permission denied — stealth photo evidence capture will be blocked"
                )
            }
        }
        cameraLauncher.launch(Manifest.permission.CAMERA)

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

    when {
        state.isLoggedIn -> {
            val homeState by homeViewModel.uiState.collectAsState()

            LaunchedEffect(Unit) {
                homeViewModel.triggerSync()
                mw.phoneguard.app.services.PhoneGuardService.start(context)
            }

            when (currentScreen) {
                AppScreen.Home -> HomeScreen(
                    userEmail = homeState.userEmail,
                    deviceRegistered = homeState.deviceRegistered,
                    isAdminActive = homeState.isAdminActive,
                    alertModeArmed = homeState.alertModeArmed,
                    lastCheckIn = homeState.lastCheckInText,
                    trustedContactsCount = homeState.trustedContactsCount,
                    unsyncedEventCount = homeState.unsyncedEventCount,
                    onToggleAlertMode = homeViewModel::toggleAlertMode,
                    onEnableAdminClick = {
                        val adminComponent = ComponentName(
                            context,
                            PhoneGuardAdminReceiver::class.java
                        )
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "PhoneGuard needs this to detect wrong lock-screen PIN attempts."
                            )
                        }
                        context.startActivity(intent)
                        // Refresh admin state after a short delay (user will click "Activate" in the system dialog)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            homeViewModel.refreshAdminState()
                        }, 1500)
                    },
                    onResetOriginalSim = {
                        homeViewModel.resetOriginalSim { _, msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTestDecoyShutdown = {
                        val intent = Intent(context, mw.phoneguard.app.services.DecoyShutdownActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    onLogout = { authViewModel.logout() },
                    onNavigate = { currentScreen = it },
                    currentScreen = currentScreen,
                    onRefresh = {
                        homeViewModel.refresh()
                        homeViewModel.refreshAdminState()
                        homeViewModel.triggerSync()
                    }
                )

                AppScreen.Activity -> {
                    ActivityScreen(
                        currentScreen = currentScreen,
                        onNavigate = { currentScreen = it }
                    )
                }

                AppScreen.Contacts -> {
                    ContactsScreen(
                        currentScreen = currentScreen,
                        onNavigate = { currentScreen = it }
                    )
                }

                AppScreen.Settings -> {
                    SettingsScreen(
                        currentScreen = currentScreen,
                        onNavigate = { currentScreen = it },
                        onLogout = { authViewModel.logout() }
                    )
                }
            }
        }

        state.resetStep == ResetStep.RequestOtp -> ForgotPasswordScreen(
            state = state,
            onEmailChange = authViewModel::onEmailChange,
            onSubmit = authViewModel::submitForgotPasswordRequest,
            onCancel = authViewModel::cancelReset
        )

        state.resetStep == ResetStep.EnterOtp -> ResetPasswordScreen(
            state = state,
            onOtpChange = authViewModel::onOtpChange,
            onPasswordChange = authViewModel::onPasswordChange,
            onSubmit = authViewModel::submitNewPassword,
            onCancel = authViewModel::cancelReset
        )

        showRegister -> RegisterScreen(
            state = state,
            onEmailChange = authViewModel::onEmailChange,
            onPasswordChange = authViewModel::onPasswordChange,
            onRegisterClick = authViewModel::registerAndLogin,
            onGoToLogin = { showRegister = false }
        )

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