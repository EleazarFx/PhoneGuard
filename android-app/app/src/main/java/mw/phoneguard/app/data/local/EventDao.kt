package mw.phoneguard.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
interface EventDao {

    /** Insert or update. Upsert means a re-inserted event doesn't duplicate. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EventEntity): Long

    /** All active (non-dismissed) events for this device, newest first, as a live stream for UI. */
    @Query("SELECT * FROM events WHERE deviceId = :deviceId AND isDismissed = 0 ORDER BY timestamp DESC")
    fun observeAll(deviceId: String): Flow<List<EventEntity>>

    /** Fetch a single event by ID. */
    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EventEntity?

    /** Everything that hasn't been synced to the backend yet. */
    @Query("SELECT * FROM events WHERE syncedAt IS NULL ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<EventEntity>

    /** Mark a synced event. */
    @Query("UPDATE events SET syncedAt = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long): Int

    /** Toggle or set preserve status. */
    @Query("UPDATE events SET isPreserved = :isPreserved WHERE id = :id")
    suspend fun updatePreserved(id: String, isPreserved: Boolean): Int

    /** Mark an event as dismissed ("Not a threat"). */
    @Query("UPDATE events SET isDismissed = 1 WHERE id = :id")
    suspend fun markDismissed(id: String): Int

    /** Delete un-preserved events older than cutoffTimestamp (e.g. 30 days ago). */
    @Query("DELETE FROM events WHERE isPreserved = 0 AND timestamp < :cutoffTimestamp")
    suspend fun purgeOldEvents(cutoffTimestamp: Long): Int

    /** Count of unsynced events (for the badge in the home screen). */
    @Query("SELECT COUNT(*) FROM events WHERE syncedAt IS NULL AND isDismissed = 0")
    fun observeUnsyncedCount(): Flow<Int>
}