package mw.phoneguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.api.ApiResult
import mw.phoneguard.app.data.api.AuthApi
import mw.phoneguard.app.data.storage.TokenStore
import mw.phoneguard.app.ui.theme.PhoneGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TestLoginScreen()
                }
            }
        }
    }
}

@Composable
fun TestLoginScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authApi = remember { AuthApi() }
    val tokenStore = remember { TokenStore(context.applicationContext) }

    var status by remember { mutableStateOf("Tap a button to test") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PhoneGuard Test", style = MaterialTheme.typography.headlineSmall)
        Text(status)

        Button(onClick = {
            scope.launch {
                status = "Registering..."
                val result = authApi.register("testuser@example.com", "hunter2hunter2")
                status = when (result) {
                    is ApiResult.Success -> "Registered: ${result.value.email}"
                    is ApiResult.Failure -> "Error: ${result.message}"
                }
            }
        }) {
            Text("Register test user")
        }

        Button(onClick = {
            scope.launch {
                status = "Logging in..."
                val result = authApi.login("testuser@example.com", "hunter2hunter2")
                status = when (result) {
                    is ApiResult.Success -> {
                        tokenStore.saveUserTokens(result.value.accessToken, result.value.refreshToken)
                        "Logged in! Token saved."
                    }
                    is ApiResult.Failure -> "Error: ${result.message}"
                }
            }
        }) {
            Text("Login test user")
        }

        Button(onClick = {
            val token = tokenStore.getUserAccessToken()
            status = if (token != null) {
                "Stored token: ${token.take(30)}..."
            } else {
                "No token stored"
            }
        }) {
            Text("Show stored token")
        }
    }
}

@Composable
@Preview
fun TestLoginScreenPreview() {
    PhoneGuardTheme {
        TestLoginScreen()
    }
}