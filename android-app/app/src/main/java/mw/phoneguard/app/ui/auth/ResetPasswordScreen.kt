package mw.phoneguard.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mw.phoneguard.app.ui.theme.*

@Composable
fun ResetPasswordScreen(
    state: AuthUiState,
    onOtpChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val pwd = state.password
    val strengthLevel = remember(pwd) {
        when {
            pwd.length < 6 -> 0
            pwd.length < 8 -> 1
            pwd.length >= 8 && pwd.any { it.isDigit() } && pwd.any { it.isUpperCase() } -> 3
            else -> 2
        }
    }
    val strengthText = when (strengthLevel) {
        0, 1 -> "Weak"
        2 -> "Medium"
        else -> "Strong"
    }
    val strengthColor = when (strengthLevel) {
        0, 1 -> AlertRed
        2 -> WarnAmber
        else -> GuardTeal
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enter your code",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Check your email for a 6-digit code. Then choose a new password.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = TextMuted
            )

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(PanelDark)
                    .border(1.dp, LineColor, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "6-Digit Security Code",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextFaint
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val otpValue = state.otpCode
                    for (i in 0 until 6) {
                        val char = if (i < otpValue.length) otpValue[i].toString() else ""
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BackgroundBlack)
                                .border(
                                    width = if (char.isNotEmpty()) 2.dp else 1.dp,
                                    color = if (char.isNotEmpty()) GuardTeal else LineColor,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.otpCode,
                    onValueChange = { new -> onOtpChange(new.filter { it.isDigit() }.take(6)) },
                    label = { Text("Type 6-digit code") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GuardTeal,
                        unfocusedBorderColor = LineColor,
                        focusedLabelColor = GuardTeal,
                        cursorColor = GuardTeal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                PasswordTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("New password (minimum 8)") },
                    enabled = !state.isLoading
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { index ->
                            Box(
                                modifier = Modifier
                                    .size(width = 36.dp, height = 6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (index < strengthLevel) strengthColor
                                        else LineColor
                                    )
                            )
                        }
                    }
                    Text(
                        text = if (pwd.isEmpty()) "Enter password" else strengthText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pwd.isEmpty()) TextFaint else strengthColor
                    )
                }
            }

            if (state.errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.errorMessage,
                    color = AlertRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(20.dp))

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = GuardTeal)
            } else {
                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GuardTeal)
                ) {
                    Text(
                        "Reset password",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BackgroundBlack
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = TextMuted)
                }
            }
        }
    }
}
