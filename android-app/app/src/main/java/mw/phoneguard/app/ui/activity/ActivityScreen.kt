package mw.phoneguard.app.ui.activity

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val allEvents by viewModel.allEvents.collectAsState()
    val filteredEvents by viewModel.filteredEvents.collectAsState()
    val groupedEvents by viewModel.groupedEvents.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedEvent by viewModel.selectedEvent.collectAsState()
    val enlargedPhotoFile by viewModel.enlargedPhotoFile.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Back handler for detail overlay & enlarged photo modal
    BackHandler(enabled = selectedEvent != null || enlargedPhotoFile != null) {
        if (enlargedPhotoFile != null) {
            viewModel.closeEnlargedPhoto()
        } else if (selectedEvent != null) {
            viewModel.selectEvent(null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Main List & Pull to Refresh
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
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) {
                        Spacer(Modifier.height(16.dp))
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
                        Spacer(Modifier.height(16.dp))

                        // 10c Filter Chips Bar
                        FilterChipRow(
                            selectedFilter = selectedFilter,
                            onFilterSelected = viewModel::selectFilter
                        )
                        Spacer(Modifier.height(16.dp))

                        // 10d Empty States or Grouped List (10a, 10f)
                        if (allEvents.isEmpty()) {
                            EmptyStateView(
                                message = "No events yet. When a theft trigger fires, evidence will appear here.",
                                icon = Icons.Default.Shield,
                                onSeedClick = viewModel::seedSampleEvents
                            )
                        } else if (filteredEvents.isEmpty()) {
                            EmptyStateView(
                                message = "No events match this filter.",
                                icon = Icons.Default.FilterList
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                groupedEvents.forEach { group ->
                                    item(key = "header_${group.title}") {
                                        DateHeader(title = group.title)
                                    }
                                    items(group.events, key = { it.id }) { event ->
                                        EventCard(
                                            event = event,
                                            onCardClick = { viewModel.selectEvent(event.id) },
                                            onPhotoClick = { photoFile -> viewModel.openEnlargedPhoto(photoFile) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 10b Event Details Screen Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedEvent != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    selectedEvent?.let { event ->
                        EventDetailScreen(
                            event = event,
                            onClose = { viewModel.selectEvent(null) },
                            onTogglePreserve = { viewModel.togglePreserve(event) },
                            onDismissEvent = { viewModel.dismissEvent(event.id) },
                            onPhotoClick = { photoFile -> viewModel.openEnlargedPhoto(photoFile) }
                        )
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

        // Enlarged Photo Dialog / Lightbox
        enlargedPhotoFile?.let { file ->
            EnlargedPhotoDialog(
                photoFile = file,
                onDismiss = { viewModel.closeEnlargedPhoto() }
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

@Composable
private fun FilterChipRow(
    selectedFilter: EventFilter,
    onFilterSelected: (EventFilter) -> Unit
) {
    val filters = listOf(
        EventFilter.ALL to "All",
        EventFilter.HIGH_THREAT to "High threat",
        EventFilter.PHOTOS to "Photos",
        EventFilter.UNSYNCED to "Unsynced"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(filters) { (filter, label) ->
            val isSelected = selectedFilter == filter
            val backgroundColor = if (isSelected) GuardTeal else PanelDark
            val contentColor = if (isSelected) Color(0xFF0D1117) else TextMuted
            val borderColor = if (isSelected) GuardTeal else LineColor

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun DateHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = GuardTeal,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = LineColor.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun EmptyStateView(
    message: String,
    icon: ImageVector,
    onSeedClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = TextFaint
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                fontSize = 15.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            if (onSeedClick != null) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onSeedClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GuardTeal),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GuardTeal.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Add Demo Events", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: EventEntity,
    onCardClick: () -> Unit,
    onPhotoClick: (File) -> Unit
) {
    val context = LocalContext.current
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeString = timeFormat.format(Date(event.timestamp))

    val isHighThreat = event.type in setOf("sim_change", "snatch", "tamper")
    val threatColor = if (isHighThreat) AlertRed else WarnAmber
    val threatText = if (isHighThreat) "HIGH" else "MEDIUM"

    val photoFile = remember(event.id) {
        File(context.filesDir, "captures/${event.id}.jpg").takeIf { it.exists() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCardClick() },
        colors = CardDefaults.cardColors(containerColor = PanelDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 10f Thumbnail in List
            if (photoFile != null) {
                AsyncImage(
                    model = photoFile,
                    contentDescription = "Evidence Thumbnail",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPhotoClick(photoFile) },
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(14.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PanelLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getEventIcon(event.type),
                        contentDescription = null,
                        tint = threatColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getEventTitle(event.type),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeString,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Threat Badge
                    BadgeChip(
                        label = threatText,
                        color = threatColor
                    )

                    // Preserved Badge
                    if (event.isPreserved) {
                        BadgeChip(
                            label = "PRESERVED",
                            color = GuardTeal
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Sync Status Badge
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
            }
        }
    }
}

@Composable
private fun BadgeChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun EventDetailScreen(
    event: EventEntity,
    onClose: () -> Unit,
    onTogglePreserve: () -> Unit,
    onDismissEvent: () -> Unit,
    onPhotoClick: (File) -> Unit
) {
    val context = LocalContext.current
    val fullDateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy · HH:mm:ss", Locale.getDefault())
    val fullTimestamp = fullDateFormat.format(Date(event.timestamp))

    val isHighThreat = event.type in setOf("sim_change", "snatch", "tamper")
    val threatColor = if (isHighThreat) AlertRed else WarnAmber
    val threatLabel = if (isHighThreat) "HIGH THREAT" else "MEDIUM THREAT"

    val photoFile = remember(event.id) {
        File(context.filesDir, "captures/${event.id}.jpg").takeIf { it.exists() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        Surface(
            color = PanelDark,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Event Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // Details Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PanelDark)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(threatColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getEventIcon(event.type),
                        contentDescription = null,
                        tint = threatColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = getEventTitle(event.type),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))

                BadgeChip(label = threatLabel, color = threatColor)

                Spacer(Modifier.height(12.dp))
                Text(
                    text = fullTimestamp,
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }

            // Photo Evidence Card (if captured)
            if (photoFile != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelDark)
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = GuardTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Photo Evidence",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPhotoClick(photoFile) }
                    ) {
                        AsyncImage(
                            model = photoFile,
                            contentDescription = "Evidence Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "Tap to enlarge",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Location Card (if captured)
            if (event.lat != null && event.lng != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelDark)
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = GuardTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Location Captured",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = String.format(Locale.US, "%.6f, %.6f", event.lat, event.lng),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (event.accuracy != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Accuracy: ±${event.accuracy.toInt()}m",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            val uri = Uri.parse("geo:${event.lat},${event.lng}?q=${event.lat},${event.lng}(Theft+Event+Location)")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No map application available", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GuardTeal),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GuardTeal)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("View on map", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Sync Status & Diagnostics Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PanelDark)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Event Details & Sync",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                HorizontalDivider(color = LineColor)

                // Sync Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sync Status", fontSize = 13.sp, color = TextMuted)
                    if (event.syncedAt != null) {
                        val syncedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.syncedAt))
                        Text(
                            "Synced at $syncedTime",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GuardTeal
                        )
                    } else {
                        Text(
                            "Pending sync",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WarnAmber
                        )
                    }
                }

                // Battery Level
                if (event.battery != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Battery Level", fontSize = 13.sp, color = TextMuted)
                        Text("${event.battery}%", fontSize = 13.sp, color = TextPrimary)
                    }
                }

                // Capture Status
                if (event.captureStatus != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Capture Status", fontSize = 13.sp, color = TextMuted)
                        Text(event.captureStatus, fontSize = 13.sp, color = TextPrimary)
                    }
                }
            }

            // Action Buttons (10b: "Preserve" & "Not a threat")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Preserve Button
                if (event.isPreserved) {
                    Button(
                        onClick = onTogglePreserve,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = GuardTeal, contentColor = Color(0xFF0D1117)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Preserved (Exempt from auto-purge)", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onTogglePreserve,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GuardTeal),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GuardTeal),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Preserve (Exempt from auto-purge)", fontWeight = FontWeight.Bold)
                    }
                }

                // Not a threat (Dismiss) Button
                OutlinedButton(
                    onClick = onDismissEvent,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Not a threat (Dismiss)", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EnlargedPhotoDialog(
    photoFile: File,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = photoFile,
                contentDescription = "Enlarged Evidence Photo",
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

private fun getEventIcon(type: String): ImageVector {
    return when (type) {
        "wrong_pin" -> Icons.Default.Lock
        "sim_change" -> Icons.Default.SimCard
        "snatch" -> Icons.Default.DirectionsRun
        "tamper" -> Icons.Default.ReportProblem
        "restart" -> Icons.Default.PowerSettingsNew
        else -> Icons.Default.Warning
    }
}

private fun getEventTitle(type: String): String {
    return when (type) {
        "wrong_pin" -> "Wrong PIN Attempt"
        "sim_change" -> "SIM Card Changed"
        "snatch" -> "Snatch Theft Detected"
        "tamper" -> "Device Tamper Detected"
        "restart" -> "Decoy Shutdown / Reboot"
        else -> type.replace("_", " ").uppercase()
    }
}
