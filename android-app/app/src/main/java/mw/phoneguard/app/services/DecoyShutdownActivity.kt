package mw.phoneguard.app.services

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore

class DecoyShutdownActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        CoroutineScope(Dispatchers.IO).launch {
            val tokenStore = TokenStore.get(applicationContext)
            val deviceId = tokenStore.getDeviceId() ?: "unknown_device"
            val db = PhoneGuardDatabase.get(applicationContext)
            var event = EventEntity(
                deviceId = deviceId,
                type = "tamper",
                timestamp = System.currentTimeMillis(),
                captureStatus = "pending_capture"
            )
            event = EvidenceCapturer(applicationContext).captureAndAttachEvidence(event)
            db.eventDao().upsert(event)
        }

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Powering off...",
                        color = Color.LightGray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Prevent backing out without correct PIN
    }
}
