package mw.phoneguard.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import mw.phoneguard.app.AppScreen
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    viewModel: ActivityViewModel = viewModel()
) {
    val events by viewModel.events.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.refresh()
                    kotlinx.coroutines.delay(800)
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
                    .padding(20.dp)
            ) {
                Text(
                    "Activity Log",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Chronological theft and tamper events",
                    fontSize = 13.sp,
                    color = TextMuted
                )
                Spacer(Modifier.height(20.dp))

                if (events.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No events yet", color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(events) { event ->
                            EventCard(event)
                        }
                    }
                }
            }
        }

        // Bottom Nav
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDark)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomNavItem("Status", currentScreen == AppScreen.Home) { onNavigate(AppScreen.Home) }
            BottomNavItem("Activity", currentScreen == AppScreen.Activity) { onNavigate(AppScreen.Activity) }
            BottomNavItem("Contacts", currentScreen == AppScreen.Contacts) { onNavigate(AppScreen.Contacts) }
            BottomNavItem("Settings", currentScreen == AppScreen.Settings) { onNavigate(AppScreen.Settings) }
        }
    }
}

@Composable
private fun EventCard(event: EventEntity) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("MMM dd, yyyy · HH:mm:ss", Locale.getDefault())
    val timeString = dateFormat.format(Date(event.timestamp))

    val threatColor = when (event.type) {
        "sim_change", "snatch", "tamper" -> AlertRed
        "wrong_pin" -> WarnAmber
        else -> GuardTeal
    }

    val photoFile = remember(event.id) {
        File(context.filesDir, "captures/${event.id}.jpg").takeIf { it.exists() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelDark, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = event.type.replace("_", " ").uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            // Pending Sync Badge
            if (event.syncedAt == null) {
                Text(
                    "PENDING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarnAmber
                )
            } else {
                Text(
                    "SYNCED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = GuardTeal
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(timeString, fontSize = 12.sp, color = TextMuted)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Threat: ${if (event.type == "wrong_pin") "Medium" else "High"}", fontSize = 12.sp, color = threatColor)
            if (event.captureStatus != null) {
                Text("Capture: ${event.captureStatus}", fontSize = 12.sp, color = TextMuted)
            }
        }

        if (photoFile != null) {
            Spacer(Modifier.height(12.dp))
            AsyncImage(
                model = photoFile,
                contentDescription = "Evidence Thumbnail",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
fun BottomNavItem(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (active) TextPrimary else TextFaint
        )
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
