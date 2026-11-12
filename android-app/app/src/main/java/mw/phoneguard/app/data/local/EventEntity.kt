package mw.phoneguard.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One theft-trigger event, stored on the phone until it can be synced.
 *
 * This is the local mirror of the backend's `events` table. The `id` is a
 * client-generated UUID — it's the primary key both here and on the server,
 * so a retry after a network failure upserts the same row instead of
 * creating a duplicate.
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val type: String,                     // "wrong_pin", "sim_change", "snatch", "tamper", "restart"
    val timestamp: Long,                  // epoch millis when the trigger fired
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracy: Double? = null,
    val battery: Int? = null,
    val captureStatus: String? = null,    // "captured", "no_face", "back_fallback", "camera_blocked", "deferred"
    val photoSha256: String? = null,
    val clientSeq: Long? = null,
    val syncedAt: Long? = null,           // null = not yet synced; non-null = synced to server at this time
    val isPreserved: Boolean = false,     // exempt from 30-day auto-purge
    val isDismissed: Boolean = false      // marked "Not a threat" by user
)