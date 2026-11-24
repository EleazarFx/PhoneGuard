package mw.phoneguard.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mw.phoneguard.app.services.ProtectionChecklistState
import mw.phoneguard.app.ui.theme.GuardTeal
import mw.phoneguard.app.ui.theme.LineColor
import mw.phoneguard.app.ui.theme.PanelDark
import mw.phoneguard.app.ui.theme.TextFaint
import mw.phoneguard.app.ui.theme.TextMuted
import mw.phoneguard.app.ui.theme.TextPrimary
import mw.phoneguard.app.ui.theme.WarnAmber

@Composable
fun ProtectionChecklistCard(
    state: ProtectionChecklistState,
    onRequestBackgroundLocation: () -> Unit,
    onEnableDeviceAdmin: () -> Unit,
    onDisableBatteryOptimization: () -> Unit,
    onConfigureAutoStart: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(!state.isFullyProtected) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PanelDark)
            .border(
                1.dp,
                if (!state.isFullyProtected) WarnAmber.copy(alpha = 0.4f) else LineColor,
                RoundedCornerShape(14.dp)
            )
    ) {
        // Collapsible Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (!state.isFullyProtected) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = WarnAmber,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 6.dp)
                    )
                }

                Column {
                    Text(
                        text = "BACKGROUND SURVIVAL CHECKLIST",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextFaint
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Anti-Kill Configuration",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (state.isFullyProtected) {
                            "All 5 anti-kill protections active"
                        } else {
                            "${5 - state.completedStepCount} missing setup step${if (5 - state.completedStepCount > 1) "s" else ""}"
                        },
                        fontSize = 11.sp,
                        color = if (state.isFullyProtected) GuardTeal else WarnAmber,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (state.isFullyProtected) GuardTeal.copy(alpha = 0.15f) else WarnAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${state.completedStepCount}/5 Active",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isFullyProtected) GuardTeal else WarnAmber
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Expandable Content Block
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Warning Banner inside expanded view
                if (!state.isFullyProtected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(WarnAmber.copy(alpha = 0.12f))
                            .border(1.dp, WarnAmber.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = WarnAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Protection incomplete (${state.completedStepCount}/5 active). Android Doze & background app killers will stop tracking unless all 5 steps are setup.",
                                fontSize = 11.sp,
                                color = TextPrimary.copy(alpha = 0.9f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Step 1: Background Location
                ChecklistItem(
                    title = "1. Allow Location All The Time",
                    subtitle = "Select 'Allow all the time' so tracking works when phone screen is locked.",
                    isDone = state.hasBackgroundLocation,
                    icon = Icons.Default.LocationOn,
                    onClick = onRequestBackgroundLocation
                )

                // Step 2: Device Admin
                ChecklistItem(
                    title = "2. Setup Device Administrator",
                    subtitle = "Enables wrong PIN failure detection & remote lock/wipe capabilities.",
                    isDone = state.hasDeviceAdmin,
                    icon = Icons.Default.Security,
                    onClick = onEnableDeviceAdmin
                )

                // Step 3: Battery Optimization
                ChecklistItem(
                    title = "3. Disable Battery Optimization",
                    subtitle = "Prevents Android Doze Mode from killing 15-min and emergency location pings.",
                    isDone = state.isBatteryOptimizationDisabled,
                    icon = Icons.Default.BatteryAlert,
                    onClick = onDisableBatteryOptimization
                )

                // Step 4: Auto-Start
                ChecklistItem(
                    title = "4. Allow Auto-Start & Background Run",
                    subtitle = "Overrides Tecno, Infinix, Xiaomi & Samsung aggressive app killers.",
                    isDone = state.isAutoStartConfigured,
                    icon = Icons.Default.PowerSettingsNew,
                    onClick = onConfigureAutoStart
                )

                // Step 5: SMS Permission
                ChecklistItem(
                    title = "5. Grant SMS & Telephony Access",
                    subtitle = "Enables emergency SMS alerts to trusted contacts on SIM swap or tamper events.",
                    isDone = state.hasSmsPermission,
                    icon = Icons.Default.Sms,
                    onClick = onRequestSmsPermission
                )
            }
        }
    }
}

@Composable
private fun ChecklistItem(
    title: String,
    subtitle: String,
    isDone: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
            .clickable(enabled = !isDone, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isDone) GuardTeal.copy(alpha = 0.15f) else LineColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDone) Icons.Default.Check else icon,
                contentDescription = null,
                tint = if (isDone) GuardTeal else TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDone) TextPrimary else TextPrimary.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 15.sp
            )
        }

        Spacer(Modifier.width(8.dp))

        if (isDone) {
            Text(
                text = "Active",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GuardTeal
            )
        } else {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GuardTeal,
                    contentColor = MaterialTheme.colorScheme.background
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "SETUP",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
