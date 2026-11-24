package mw.phoneguard.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import mw.phoneguard.app.services.OemAutoStartHelper
import mw.phoneguard.app.services.PhoneGuardService
import mw.phoneguard.app.services.ProtectionChecklistHelper
import mw.phoneguard.app.ui.activity.ActivityScreen
import mw.phoneguard.app.ui.auth.AuthViewModel
import mw.phoneguard.app.ui.auth.ForgotPasswordScreen
import mw.phoneguard.app.ui.auth.LoginScreen
import mw.phoneguard.app.ui.auth.NewDeviceVerifyScreen
import mw.phoneguard.app.ui.auth.RegisterScreen
import mw.phoneguard.app.ui.auth.ResetPasswordScreen
import mw.phoneguard.app.ui.auth.ResetStep
import mw.phoneguard.app.ui.contacts.ContactsScreen
import mw.phoneguard.app.ui.home.HomeScreen
import mw.phoneguard.app.ui.home.HomeViewModel
import mw.phoneguard.app.ui.settings.SettingsScreen
import mw.phoneguard.app.ui.theme.PhoneGuardTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

    private val bgLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            android.util.Log.w("PhoneGuard", "ACCESS_BACKGROUND_LOCATION denied — opening settings")
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
        homeViewModel.refreshAdminState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val launcher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (!granted) {
                    android.util.Log.w("PhoneGuard", "POST_NOTIFICATIONS denied")
                }
            }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val cameraLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                android.util.Log.w("PhoneGuard", "CAMERA permission denied")
            }
        }
        cameraLauncher.launch(Manifest.permission.CAMERA)

        val locationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (!fineGranted && !coarseGranted) {
                android.util.Log.w("PhoneGuard", "LOCATION permissions denied")
            }
        }
        locationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        val smsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val sendGranted = permissions[Manifest.permission.SEND_SMS] ?: false
            val receiveGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: false
            val phoneStateGranted = permissions[Manifest.permission.READ_PHONE_STATE] ?: false
            if (!sendGranted || !receiveGranted || !phoneStateGranted) {
                android.util.Log.w("PhoneGuard", "SMS or PhoneState permissions denied")
            }
        }

        fun getSmsPermissions(): Array<String> {
            val list = mutableListOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                list.add(Manifest.permission.READ_PHONE_NUMBERS)
            }
            return list.toTypedArray()
        }

        smsLauncher.launch(getSmsPermissions())

        setContent {
            PhoneGuardTheme {
                PhoneGuardApp(
                    authViewModel = authViewModel,
                    homeViewModel = homeViewModel,
                    onRequestBackgroundLocation = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            homeViewModel.refreshAdminState()
                        }
                    },
                    onRequestSmsPermission = {
                        smsLauncher.launch(getSmsPermissions())
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.refreshAdminState()
    }
}

@Composable
fun PhoneGuardApp(
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    onRequestBackgroundLocation: () -> Unit,
    onRequestSmsPermission: () -> Unit
) {
    val state by authViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showRegister by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(AppScreen.Home) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            state.isLoggedIn -> {
                val homeState by homeViewModel.uiState.collectAsState()

                LaunchedEffect(Unit) {
                    homeViewModel.triggerSync()
                    PhoneGuardService.start(context)
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
                        protectionState = homeState.protectionState,
                        currentTrackingMode = homeState.currentTrackingMode,
                        onToggleAlertMode = homeViewModel::toggleAlertMode,
                        onEnableAdminClick = {
                            ProtectionChecklistHelper.openDeviceAdminSettings(context)
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                homeViewModel.refreshAdminState()
                            }, 1500)
                        },
                        onRequestBackgroundLocation = onRequestBackgroundLocation,
                        onDisableBatteryOptimization = {
                            ProtectionChecklistHelper.openBatteryOptimizationSettings(context)
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                homeViewModel.refreshAdminState()
                            }, 1500)
                        },
                        onConfigureAutoStart = {
                            ProtectionChecklistHelper.markAutoStartAcknowledged(context)
                            val opened = OemAutoStartHelper.openAutoStartSettings(context)
                            if (opened) {
                                Toast.makeText(context, "Grant Auto-Start permission for ${OemAutoStartHelper.getManufacturerName()}", Toast.LENGTH_LONG).show()
                            }
                            homeViewModel.refreshAdminState()
                        },
                        onRequestSmsPermission = {
                            onRequestSmsPermission()
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                homeViewModel.refreshAdminState()
                            }, 1500)
                        },
                        onResetOriginalSim = {
                            homeViewModel.resetOriginalSim { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onTestDecoyShutdown = {
                            val intent = Intent(context, mw.phoneguard.app.services.DecoyShutdownActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        onLogout = {
                            authViewModel.logout()
                        },
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
                            onLogout = {
                                authViewModel.logout()
                            }
                        )
                    }
                }
            }

            state.isVerificationRequired -> NewDeviceVerifyScreen(
                state = state,
                onCodeChange = authViewModel::onVerificationCodeChange,
                onVerifyClick = authViewModel::verifyNewDevice,
                onResendClick = authViewModel::resendVerificationCode,
                onCancelClick = authViewModel::cancelVerification
            )

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
                onLoginClick = {
                    authViewModel.login()
                },
                onRegisterClick = { },
                onGoToRegister = { showRegister = true },
                onForgotPasswordClick = authViewModel::startForgotPassword
            )
        }
    }
}

enum class AppScreen {
    Home, Activity, Contacts, Settings
}
