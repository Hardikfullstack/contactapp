package com.phone.contacts.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Plain manual singleton, no Hilt — matching this project's other `object`-style
 * repositories/managers rather than a DI-provided instance. No explicit migrations - the app
 * hasn't shipped to any real device yet, so there's no installed data to preserve across a schema
 * bump; fallbackToDestructiveMigration() just recreates the database on any version change. */
@Database(
    entities = [DeletedContactEntity::class, EmergencyContactEntity::class, ReminderEntity::class, NumberSeriesEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recycleBinDao(): RecycleBinDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun reminderDao(): ReminderDao
    abstract fun numberSeriesDao(): NumberSeriesDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "contacts_app_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
