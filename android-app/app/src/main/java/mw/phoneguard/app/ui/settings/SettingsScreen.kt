package mw.phoneguard.app.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import mw.phoneguard.app.AppScreen
import mw.phoneguard.app.BuildConfig
import mw.phoneguard.app.services.AppSessionManager
import mw.phoneguard.app.services.PhoneGuardAdminReceiver
import mw.phoneguard.app.ui.theme.AlertRed
import mw.phoneguard.app.ui.theme.GuardTeal
import mw.phoneguard.app.ui.theme.LineColor
import mw.phoneguard.app.ui.theme.PanelDark
import mw.phoneguard.app.ui.theme.TextFaint
import mw.phoneguard.app.ui.theme.TextMuted
import mw.phoneguard.app.ui.theme.TextPrimary
import mw.phoneguard.app.ui.theme.WarnAmber

@Composable
fun SettingsScreen(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Dialog States
    var showDeactivateAdminDialog by remember { mutableStateOf(false) }
    var showImeiDialog by remember { mutableStateOf(false) }
    var imeiInput by remember(state.imei) { mutableStateOf(state.imei) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showWipeDataDialog by remember { mutableStateOf(false) }

    // PIN change inputs
    var oldPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmNewPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    // Wipe data inputs
    var wipePinInput by remember { mutableStateOf("") }
    var wipeDeleteInput by remember { mutableStateOf("") }
    var wipeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.refreshAdminState()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top scrollable content with Pull to Refresh
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.refresh()
                    kotlinx.coroutines.delay(600)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "PREFERENCES & SECURITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextFaint
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Guardian Settings",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Configure Account, Alert Triggers, Security policies, and Notifications.",
                    fontSize = 13.sp,
                    color = TextMuted
                )

                Spacer(Modifier.height(20.dp))

                // ──────────────────────────────────────────────
                // 1. ACCOUNT SECTION
                // ──────────────────────────────────────────────
                SettingsSectionHeader("ACCOUNT")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(PanelDark)
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Account Email",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextFaint
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (state.userEmail.isNotBlank()) state.userEmail else "Not Logged In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                    Spacer(Modifier.height(16.dp))

                    // Reset Original SIM button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Reset Original SIM",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Store current SIM as baseline to prevent false alerts",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Button(
                            onClick = {
                                viewModel.resetOriginalSim { success, message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GuardTeal.copy(alpha = 0.15f),
                                contentColor = GuardTeal
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reset", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                    Spacer(Modifier.height(16.dp))

                    // Log Out button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Log Out",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AlertRed
                            )
                            Text(
                                "Clear active session and return to login screen",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Button(
                            onClick = { showLogoutDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlertRed.copy(alpha = 0.15f),
                                contentColor = AlertRed
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Log Out", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ──────────────────────────────────────────────
                // 2. ALERT MODE SECTION
                // ──────────────────────────────────────────────
                SettingsSectionHeader("ALERT MODE")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(PanelDark)
                        .padding(20.dp)
                ) {
                    // Video Evidence Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Video Evidence Capture",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Record front camera video during threat events",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = state.videoEvidenceEnabled,
                            onCheckedChange = viewModel::toggleVideoEvidence,
                            colors = SwitchDefaults.colors(checkedThumbColor = GuardTeal)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                    Spacer(Modifier.height(14.dp))

                    // Periodic Location Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Periodic GPS Location",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Include fresh GPS coordinates in telemetry syncs",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = state.periodicLocationEnabled,
                            onCheckedChange = viewModel::togglePeriodicLocation,
                            colors = SwitchDefaults.colors(checkedThumbColor = GuardTeal)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ──────────────────────────────────────────────
                // 3. DEFENSE & LOCKDOWN POLICIES
                // ──────────────────────────────────────────────
                SettingsSectionHeader("DEFENSE & LOCKDOWN")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(PanelDark)
                        .padding(20.dp)
                ) {
                    // Offline Auto-Lock Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Offline Auto-Lock",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Lock down device if offline for extended period",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = state.offlineAutoLockEnabled,
                            onCheckedChange = viewModel::toggleOfflineAutoLock,
                            colors = SwitchDefaults.colors(checkedThumbColor = GuardTeal)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                    Spacer(Modifier.height(14.dp))

                    // Remote Wipe Permission Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Remote Wipe Permission",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Allow remote wipe command execution",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = state.remoteWipeEnabled,
                            onCheckedChange = viewModel::toggleRemoteWipe,
                            colors = SwitchDefaults.colors(checkedThumbColor = GuardTeal)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ──────────────────────────────────────────────
                // 4. NOTIFICATIONS & ESCALATION
                // ──────────────────────────────────────────────
                SettingsSectionHeader("NOTIFICATIONS & ESCALATION")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(PanelDark)
                        .padding(20.dp)
                ) {
                    // Escalation SMS Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Escalation SMS",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Send emergency SMS to trusted contacts on threat",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = state.escalationSmsEnabled,
                            onCheckedChange = viewModel::toggleEscalationSms,
                            colors = SwitchDefaults.colors(checkedThumbColor = GuardTeal)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                    Spacer(Modifier.height(14.dp))

                    // Silent Email Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Silent Email Alert",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Send discreet email notifications to account",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = state.silenceEmailEnabled,
                            onCheckedChange = viewModel::toggleSilenceEmail,
                            colors = SwitchDefaults.colors(checkedThumbColor = GuardTeal)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ──────────────────────────────────────────────
                // 5. SECURITY & ADMIN SECTION
                // ──────────────────────────────────────────────
                SettingsSectionHeader("SECURITY & ADMIN")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(PanelDark)
                        .padding(20.dp)
                ) {
                    // Device Admin status & deactivation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Device Admin Status",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (state.isAdminActive) "Active — Wrong-PIN detection active" else "Inactive — Tap to enable Admin permission",
                                fontSize = 12.sp,
                                color = if (state.isAdminActive) GuardTeal else WarnAmber
                            )
                        }

                        if (state.isAdminActive) {
                            Button(
                                onClick = { showDeactivateAdminDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AlertRed.copy(alpha = 0.15f),
                                    contentColor = AlertRed
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Deactivate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val adminComponent = ComponentName(context, PhoneGuardAdminReceiver::class.java)
                                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "PhoneGuard needs Device Admin to detect wrong lock-screen PIN attempts.")
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GuardTeal,
                                    contentColor = MaterialTheme.colorScheme.background
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Activate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                    Spacer(Modifier.height(14.dp))

                    // IMEI row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Device IMEI",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (state.imei.isNotBlank()) "IMEI: ${state.imei}" else "Not Set — Tap to configure IMEI",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        OutlinedButton(
                            onClick = { showImeiDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (state.imei.isNotBlank()) "Edit IMEI" else "Set IMEI", fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                    Spacer(Modifier.height(14.dp))

                    // Local App PIN row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "PhoneGuard App PIN",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (state.hasAppPin) "App PIN set (local protection)" else "No local app PIN configured",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                oldPinInput = ""
                                newPinInput = ""
                                confirmNewPinInput = ""
                                pinError = null
                                showPinDialog = true
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (state.hasAppPin) "Change PIN" else "Set PIN", fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                    Spacer(Modifier.height(14.dp))

                    // Auto-wipe on 10 failed PIN attempts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Auto-Wipe on PIN Failures",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Self-wipe app data after 10 failed PIN attempts",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = state.autoWipeEnabled,
                            onCheckedChange = viewModel::toggleAutoWipe,
                            colors = SwitchDefaults.colors(checkedThumbColor = GuardTeal)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                    Spacer(Modifier.height(14.dp))

                    // Wipe App Data button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Wipe App Data",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AlertRed
                            )
                            Text(
                                "Requires PIN & confirmation text",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Button(
                            onClick = {
                                wipePinInput = ""
                                wipeDeleteInput = ""
                                wipeError = null
                                showWipeDataDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlertRed.copy(alpha = 0.15f),
                                contentColor = AlertRed
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Wipe Data", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ──────────────────────────────────────────────
                // 6. DEBUG SECTION (Visible only in Debug builds)
                // ──────────────────────────────────────────────
                if (BuildConfig.DEBUG) {
                    SettingsSectionHeader("DEBUG & DIAGNOSTICS")

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(PanelDark)
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Service Log Output (Last 20)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            TextButton(onClick = { viewModel.refreshLogs() }) {
                                Text("Refresh", fontSize = 12.sp, color = GuardTeal)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.dp, LineColor, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (state.logs.isEmpty()) {
                                Text(
                                    "No log entries available yet.",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                            } else {
                                state.logs.forEach { logLine ->
                                    Text(
                                        text = logLine,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Debug Manual Actions",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.forceSimCheck(context) { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Force SIM Check", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    viewModel.forceWrongPinTrigger(context) { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WarnAmber.copy(alpha = 0.2f),
                                    contentColor = WarnAmber
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Force Wrong PIN", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }

                // ──────────────────────────────────────────────
                // ABOUT & LIMITATIONS
                // ──────────────────────────────────────────────
                SettingsSectionHeader("ABOUT & LIMITATIONS")
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(PanelDark)
                        .padding(20.dp)
                ) {
                    Text(
                        "PhoneGuard v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) [${BuildConfig.BUILD_TYPE}]",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Backend URL: ${mw.phoneguard.app.data.api.ApiClient.BASE_URL}",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Device ID: ${mw.phoneguard.app.data.storage.TokenStore.get(context).getDeviceId() ?: "Not registered"}",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = LineColor)
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Send crash reports",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Automatically email crash reports to developer when unhandled exceptions occur.",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = state.crashReportConsent,
                            onCheckedChange = { viewModel.toggleCrashReportConsent(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GuardTeal,
                                checkedTrackColor = GuardTeal.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = LineColor)
                    Spacer(Modifier.height(16.dp))

                    Text(
                        "What PhoneGuard Cannot Do (Limitations):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• Cannot prevent physical theft or armed robbery (protects against snatching and unauthorized access attempts).\n" +
                                "• Cannot bypass OS-level hardware security or kernel compromises.\n" +
                                "• Cannot recover a device if powered off before snatch detection triggers or if factory reset is performed externally.\n" +
                                "• Cannot guarantee network delivery of evidence if device is in a 100% Faraday / zero-signal zone.\n" +
                                "• Cannot replace proper operational physical security practices.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = LineColor)
                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:eleazartoto50@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "PhoneGuard Feedback")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GuardTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Developer Feedback", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        // Bottom Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDark)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            mw.phoneguard.app.ui.activity.BottomNavItem("Status", currentScreen == AppScreen.Home) { onNavigate(AppScreen.Home) }
            mw.phoneguard.app.ui.activity.BottomNavItem("Activity", currentScreen == AppScreen.Activity) { onNavigate(AppScreen.Activity) }
            mw.phoneguard.app.ui.activity.BottomNavItem("Contacts", currentScreen == AppScreen.Contacts) { onNavigate(AppScreen.Contacts) }
            mw.phoneguard.app.ui.activity.BottomNavItem("Settings", currentScreen == AppScreen.Settings) { onNavigate(AppScreen.Settings) }
        }
    }

    // ──────────────────────────────────────────────
    // DIALOGS
    // ──────────────────────────────────────────────

    // Deactivate Device Admin Warning Dialog
    if (showDeactivateAdminDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateAdminDialog = false },
            title = {
                Text("Deactivate Device Admin?", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    "WARNING: Deactivating Device Admin will disable wrong lock-screen PIN detection and automatic phone locking during theft alerts. Are you sure?",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeactivateAdminDialog = false
                        viewModel.deactivateDeviceAdmin(context)
                        Toast.makeText(context, "Device Admin deactivated", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Deactivate", color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateAdminDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = PanelDark
        )
    }

    // Edit IMEI Dialog
    if (showImeiDialog) {
        AlertDialog(
            onDismissRequest = { showImeiDialog = false },
            title = {
                Text("Set Device IMEI", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column {
                    Text("Enter 15-digit IMEI for hardware identification:", fontSize = 12.sp, color = TextMuted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = imeiInput,
                        onValueChange = { imeiInput = it },
                        label = { Text("IMEI") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GuardTeal,
                            unfocusedBorderColor = LineColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImeiDialog = false
                        viewModel.saveImei(imeiInput.trim())
                        Toast.makeText(context, "IMEI saved", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save", color = GuardTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImeiDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = PanelDark
        )
    }

    // Logout Confirmation Dialog (Requirement 15f)
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text("Log Out", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    "Are you sure? You'll need your email and password to log back in.",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        AppSessionManager.lock()
                        onLogout()
                    }
                ) {
                    Text("Log Out", color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = PanelDark
        )
    }

    // Wipe App Data Dialog (Requirement 15f)
    if (showWipeDataDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDataDialog = false },
            title = {
                Text("Wipe App Data", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column {
                    Text("This will permanently delete all local app data, logs, and settings. Enter your App PIN and type DELETE to confirm:", fontSize = 12.sp, color = TextMuted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = wipePinInput,
                        onValueChange = { wipePinInput = it },
                        label = { Text("App PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GuardTeal,
                            unfocusedBorderColor = LineColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = wipeDeleteInput,
                        onValueChange = { wipeDeleteInput = it },
                        label = { Text("Type DELETE") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GuardTeal,
                            unfocusedBorderColor = LineColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (wipeError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(wipeError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (wipeDeleteInput != "DELETE") {
                            wipeError = "Must type DELETE"
                            return@TextButton
                        }
                        viewModel.wipeAppDataWithPin(wipePinInput.trim()) { success, msg ->
                            if (success) {
                                showWipeDataDialog = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                AppSessionManager.lock()
                                onLogout()
                            } else {
                                wipeError = msg
                            }
                        }
                    }
                ) {
                    Text("Wipe Data", color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDataDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = PanelDark
        )
    }

    // Set/Change PIN Dialog (Requirement 15e)
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = {
                Text(if (state.hasAppPin) "Change App PIN" else "Set App PIN", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column {
                    if (state.hasAppPin) {
                        Text("Enter current (OLD) PIN:", fontSize = 12.sp, color = TextMuted)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = oldPinInput,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) oldPinInput = it },
                            label = { Text("Old PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GuardTeal,
                                unfocusedBorderColor = LineColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("Enter new PIN (4-6 digits):", fontSize = 12.sp, color = TextMuted)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPinInput = it },
                        label = { Text("New PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GuardTeal,
                            unfocusedBorderColor = LineColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmNewPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmNewPinInput = it },
                        label = { Text("Confirm New PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GuardTeal,
                            unfocusedBorderColor = LineColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(pinError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (state.hasAppPin) {
                            if (!viewModel.verifyOldPin(oldPinInput)) {
                                pinError = "Incorrect old PIN"
                                return@TextButton
                            }
                        }
                        if (newPinInput.length !in 4..6) {
                            pinError = "New PIN must be 4-6 digits"
                            return@TextButton
                        }
                        if (newPinInput != confirmNewPinInput) {
                            pinError = "New PINs do not match"
                            return@TextButton
                        }
                        showPinDialog = false
                        viewModel.saveAppPin(newPinInput.trim())
                        Toast.makeText(context, "App PIN updated successfully", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save", color = GuardTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = PanelDark
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextFaint,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}
