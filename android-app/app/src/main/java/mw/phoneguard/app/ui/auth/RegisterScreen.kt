package mw.phoneguard.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mw.phoneguard.app.R
import mw.phoneguard.app.ui.theme.*

@Composable
fun RegisterScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onGoToLogin: () -> Unit
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
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(GuardTeal.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "PhoneGuard Logo",
                    modifier = Modifier.size(40.dp),
                    tint = GuardTeal
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Create your account",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "One account protects every phone you own",
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
                    .padding(20.dp)
            ) {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !state.isLoading,
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
                    label = { Text("Password (minimum 8)") },
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
                    onClick = onRegisterClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GuardTeal)
                ) {
                    Text(
                        "Create account",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BackgroundBlack
                    )
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = onGoToLogin) {
                    Text(
                        text = "Already have an account? Log in",
                        color = GuardTeal,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
