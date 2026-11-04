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

    /** All events for this device, newest first, as a live stream for UI. */
    @Query("SELECT * FROM events WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    fun observeAll(deviceId: String): Flow<List<EventEntity>>

    /** Everything that hasn't been synced to the backend yet. */
    @Query("SELECT * FROM events WHERE syncedAt IS NULL ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<EventEntity>

    /** Mark a synced event. */
    @Query("UPDATE events SET syncedAt = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long): Int

    /** Count of unsynced events (for the badge in the home screen). */
    @Query("SELECT COUNT(*) FROM events WHERE syncedAt IS NULL")
    fun observeUnsyncedCount(): Flow<Int>
}