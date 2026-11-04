package mw.phoneguard.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PhoneGuardDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: PhoneGuardDatabase? = null

        fun get(context: Context): PhoneGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhoneGuardDatabase::class.java,
                    "phoneguard.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}