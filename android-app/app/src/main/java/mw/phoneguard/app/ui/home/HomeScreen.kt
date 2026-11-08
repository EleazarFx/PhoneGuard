package mw.phoneguard.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mw.phoneguard.app.ui.theme.GuardTeal
import mw.phoneguard.app.ui.theme.LineColor
import mw.phoneguard.app.ui.theme.PanelDark
import mw.phoneguard.app.ui.theme.TextFaint
import mw.phoneguard.app.ui.theme.TextMuted
import mw.phoneguard.app.ui.theme.TextPrimary
import mw.phoneguard.app.ui.theme.WarnAmber

import androidx.compose.ui.platform.LocalContext

@Composable
fun HomeScreen(
    userEmail: String?,
    deviceRegistered: Boolean,
    isAdminActive: Boolean,
    alertModeArmed: Boolean,
    lastCheckIn: String,
    trustedContactsCount: Int,
    unsyncedEventCount: Int,
    onToggleAlertMode: (Boolean) -> Unit,
    onEnableAdminClick: () -> Unit,
    onResetOriginalSim: () -> Unit,
    onLogout: () -> Unit,
    onNavigate: (mw.phoneguard.app.AppScreen) -> Unit,
    currentScreen: mw.phoneguard.app.AppScreen
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---- Top content ----
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Guardian status ring
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                GuardTeal.copy(alpha = 0.12f),
                                GuardTeal.copy(alpha = 0.02f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(GuardTeal.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(GuardTeal)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "GUARDIAN MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextFaint
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Protected",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Last check-in $lastCheckIn",
                fontSize = 13.sp,
                color = TextMuted
            )

            Spacer(Modifier.height(28.dp))

            // Stats card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(PanelDark)
                    .padding(20.dp)
            ) {
                // Alert Mode row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Alert Mode",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            if (alertModeArmed) "Motion alarm is armed" else "Off",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = alertModeArmed,
                        onCheckedChange = onToggleAlertMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GuardTeal,
                            checkedTrackColor = GuardTeal.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = PanelDark
                        )
                    )
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                Spacer(Modifier.height(14.dp))

                // Trusted contacts row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Trusted contacts", fontSize = 14.sp, color = TextMuted)
                    Text(
                        if (trustedContactsCount == 0) "None registered"
                        else "$trustedContactsCount registered",
                        fontSize = 14.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Pending events row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pending sync", fontSize = 14.sp, color = TextMuted)
                    Text(
                        if (unsyncedEventCount == 0) "All synced"
                        else "$unsyncedEventCount event${if (unsyncedEventCount == 1) "" else "s"}",
                        fontSize = 14.sp,
                        color = if (unsyncedEventCount > 0) GuardTeal else TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Device status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Device", fontSize = 14.sp, color = TextMuted)
                    Text(
                        if (deviceRegistered) "Registered" else "Not registered",
                        fontSize = 14.sp,
                        color = if (deviceRegistered) GuardTeal else TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Device admin row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Device admin", fontSize = 14.sp, color = TextMuted)
                        Text(
                            if (isAdminActive) "Active — wrong-PIN detection enabled"
                            else "Required for wrong-PIN detection",
                            fontSize = 11.sp,
                            color = if (isAdminActive) GuardTeal.copy(alpha = 0.7f) else TextFaint
                        )
                    }
                    if (isAdminActive) {
                        Text(
                            "Done",
                            fontSize = 13.sp,
                            color = GuardTeal,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        androidx.compose.material3.TextButton(onClick = onEnableAdminClick) {
                            Text(
                                "Set up",
                                fontSize = 13.sp,
                                color = GuardTeal,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(Modifier, DividerDefaults.Thickness, color = LineColor)
                Spacer(Modifier.height(14.dp))

                // Reset Original SIM row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableNoRipple { onResetOriginalSim() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Reset Original SIM", fontSize = 14.sp, color = TextMuted)
                        Text(
                            "Update trusted baseline SIM/eSIM",
                            fontSize = 11.sp,
                            color = TextFaint
                        )
                    }
                    Text(
                        "Reset",
                        fontSize = 13.sp,
                        color = GuardTeal,
                        fontWeight = FontWeight.SemiBold
                    )
                }






            }

            Spacer(Modifier.height(24.dp))

            // Account footer
            Text(
                text = userEmail ?: "Not signed in",
                fontSize = 12.sp,
                color = TextFaint
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(
                onClick = onLogout,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = TextMuted
                )
            ) {
                Text("Log out", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        // ---- Bottom nav ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDark)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            mw.phoneguard.app.ui.activity.BottomNavItem("Status", currentScreen == mw.phoneguard.app.AppScreen.Home) { onNavigate(mw.phoneguard.app.AppScreen.Home) }
            mw.phoneguard.app.ui.activity.BottomNavItem("Activity", currentScreen == mw.phoneguard.app.AppScreen.Activity) { onNavigate(mw.phoneguard.app.AppScreen.Activity) }
            mw.phoneguard.app.ui.activity.BottomNavItem("Contacts", currentScreen == mw.phoneguard.app.AppScreen.Contacts) { onNavigate(mw.phoneguard.app.AppScreen.Contacts) }
            mw.phoneguard.app.ui.activity.BottomNavItem("Settings", currentScreen == mw.phoneguard.app.AppScreen.Settings) { onNavigate(mw.phoneguard.app.AppScreen.Settings) }
        }
    }
}

@Composable
private fun BottomNavItem(label: String, active: Boolean) {
    Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (active) TextPrimary else TextFaint
    )
}

// Helper: Modifier.clickable without the ripple effect (cleaner for footers)
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )

