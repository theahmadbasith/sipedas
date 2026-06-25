package com.sipedas.ponorogo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
@Database(entities = [DraftReport::class, DraftPhoto::class], version = 2, exportSchema = false)
abstract class SipedasDatabase : RoomDatabase() {
    abstract fun sipedasDao(): SipedasDao

    companion object {
        @Volatile
        private var INSTANCE: SipedasDatabase? = null

        fun getDatabase(context: Context): SipedasDatabase {
            return INSTANCE ?: synchronized(this) {
                @Suppress("DEPRECATION")
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SipedasDatabase::class.java,
                    "sipedas_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
