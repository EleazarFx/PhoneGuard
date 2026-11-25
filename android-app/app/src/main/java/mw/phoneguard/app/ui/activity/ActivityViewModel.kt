package mw.phoneguard.app.ui.activity

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore
import mw.phoneguard.app.data.sync.SyncManager
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class EventFilter {
    ALL,
    HIGH_THREAT,
    PHOTOS,
    UNSYNCED
}

data class EventGroup(
    val title: String,
    val events: List<EventEntity>
)

class ActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ActivityViewModel"
    private val tokenStore = TokenStore.get(application.applicationContext)
    private val db = PhoneGuardDatabase.get(application.applicationContext)

    val selectedFilter = MutableStateFlow(EventFilter.ALL)
    val selectedEventId = MutableStateFlow<String?>(null)
    val enlargedPhotoFile = MutableStateFlow<File?>(null)
    val activeVideoFile = MutableStateFlow<File?>(null)

    val allEvents: StateFlow<List<EventEntity>> = db.eventDao()
        .observeAll(tokenStore.getDeviceId() ?: "")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredEvents: StateFlow<List<EventEntity>> = combine(allEvents, selectedFilter) { events, filter ->
        when (filter) {
            EventFilter.ALL -> events
            EventFilter.HIGH_THREAT -> events.filter { isHighThreat(it.type) }
            EventFilter.PHOTOS -> events.filter { hasPhoto(it) || hasVideo(it) }
            EventFilter.UNSYNCED -> events.filter { it.syncedAt == null }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val groupedEvents: StateFlow<List<EventGroup>> = filteredEvents.map { events ->
        groupEventsByDate(events)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val selectedEvent: StateFlow<EventEntity?> = combine(allEvents, selectedEventId) { events, id ->
        if (id == null) null else events.find { it.id == id }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    init {
        // Run auto-purge on ViewModel creation
        purgeOldEvents()
    }

    fun selectFilter(filter: EventFilter) {
        selectedFilter.value = filter
    }

    fun selectEvent(eventId: String?) {
        selectedEventId.value = eventId
    }

    fun openEnlargedPhoto(file: File?) {
        enlargedPhotoFile.value = file
    }

    fun closeEnlargedPhoto() {
        enlargedPhotoFile.value = null
    }

    fun openVideo(file: File?) {
        activeVideoFile.value = file
    }

    fun closeVideo() {
        activeVideoFile.value = null
    }

    fun togglePreserve(event: EventEntity) {
        viewModelScope.launch {
            db.eventDao().updatePreserved(event.id, !event.isPreserved)
        }
    }

    fun dismissEvent(eventId: String) {
        viewModelScope.launch {
            db.eventDao().markDismissed(eventId)
            if (selectedEventId.value == eventId) {
                selectedEventId.value = null
            }
        }
    }

    fun purgeOldEvents(cutoffTimestamp: Long = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L) {
        viewModelScope.launch {
            try {
                val purgedCount = db.eventDao().purgeOldEvents(cutoffTimestamp)
                if (purgedCount > 0) {
                    Log.d(TAG, "Purged $purgedCount events older than 30 days")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to purge old events", e)
            }
        }
    }



    fun refresh() {
        viewModelScope.launch {
            try {
                purgeOldEvents()
                val syncManager = SyncManager(getApplication())
                syncManager.syncPendingEvents()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync during refresh", e)
            }
        }
    }

    private fun isHighThreat(type: String): Boolean {
        return when (type) {
            "sim_change", "snatch", "tamper" -> true
            else -> false
        }
    }

    private fun hasPhoto(event: EventEntity): Boolean {
        val photoFile = File(getApplication<Application>().filesDir, "captures/${event.id}.jpg")
        return photoFile.exists() || event.photoSha256 != null || event.captureStatus in setOf("captured", "no_face", "back_fallback")
    }

    fun hasVideo(event: EventEntity): Boolean {
        val videoFile = File(getApplication<Application>().filesDir, "captures/${event.id}.mp4")
        return videoFile.exists() || event.videoSha256 != null
    }

    fun groupEventsByDate(events: List<EventEntity>): List<EventGroup> {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        val monthDayFormatter = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())
        val fullDateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())

        val grouped = LinkedHashMap<String, MutableList<EventEntity>>()

        for (event in events) {
            val date = Instant.ofEpochMilli(event.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            val title = when {
                date == today -> "Today"
                date == yesterday -> "Yesterday"
                date.year == today.year -> date.format(monthDayFormatter)
                else -> date.format(fullDateFormatter)
            }

            grouped.getOrPut(title) { mutableListOf() }.add(event)
        }

        return grouped.map { (title, list) -> EventGroup(title, list) }
    }
}
