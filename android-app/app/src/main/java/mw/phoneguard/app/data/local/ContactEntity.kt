package mw.phoneguard.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val createdAt: String = "",
    val lastSyncedTimestamp: Long = System.currentTimeMillis()
)
