package mw.phoneguard.app.ui.auth

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import mw.phoneguard.app.services.AppSessionManager
import mw.phoneguard.app.ui.theme.*

@Composable
fun PinEntryScreen(
    onUnlocked: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableStateOf(AppSessionManager.getRemainingLockoutSeconds(context)) }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {}
    }

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000L)
            lockoutSeconds = AppSessionManager.getRemainingLockoutSeconds(context)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(GuardTeal.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Shield Logo",
                    modifier = Modifier.size(32.dp),
                    tint = GuardTeal
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Enter your PIN",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Your 4-digit security PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(Modifier.height(28.dp))

            if (lockoutSeconds > 0) {
                Text(
                    text = "Too many failed attempts. Locked out for ${lockoutSeconds}s",
                    color = AlertRed,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val filled = index < pinInput.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (errorMessage != null) AlertRed
                                    else if (filled) GuardTeal
                                    else PanelLight
                                )
                                .border(
                                    1.dp,
                                    if (errorMessage != null) AlertRed else LineColor,
                                    CircleShape
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = errorMessage!!,
                        color = AlertRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(36.dp))

                val buttons = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "del")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    buttons.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            row.forEach { item ->
                                if (item.isEmpty()) {
                                    Spacer(Modifier.size(72.dp))
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(PanelDark)
                                            .border(1.dp, LineColor, CircleShape)
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                errorMessage = null
                                                if (item == "del") {
                                                    if (pinInput.isNotEmpty()) {
                                                        pinInput = pinInput.dropLast(1)
                                                    }
                                                } else {
                                                    if (pinInput.length < 4) {
                                                        pinInput += item
                                                        if (pinInput.length == 4) {
                                                            val success = AppSessionManager.unlock(pinInput, context)
                                                            if (success) {
                                                                pinInput = ""
                                                                errorMessage = null
                                                                onUnlocked()
                                                            } else {
                                                                lockoutSeconds = AppSessionManager.getRemainingLockoutSeconds(context)
                                                                errorMessage = if (lockoutSeconds > 0) {
                                                                    "Incorrect PIN. Locked out for ${lockoutSeconds}s"
                                                                } else {
                                                                    "Incorrect PIN. Try again."
                                                                }
                                                                pinInput = ""
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item == "del") {
                                            Icon(
                                                imageVector = Icons.Default.Backspace,
                                                contentDescription = "Backspace",
                                                tint = TextPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Text(
                                                text = item,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            TextButton(onClick = onLogout) {
                Text("Forgot PIN / Log out", color = AlertRed, fontSize = 13.sp)
            }
        }
    }
}
