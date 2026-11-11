package mw.phoneguard.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM trusted_contacts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM trusted_contacts ORDER BY createdAt DESC")
    suspend fun getAll(): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity)

    @Query("DELETE FROM trusted_contacts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM trusted_contacts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM trusted_contacts")
    suspend fun getCount(): Int

    @Query("SELECT MAX(lastSyncedTimestamp) FROM trusted_contacts")
    suspend fun getLastSyncedTime(): Long?
}
