package com.phone.contacts.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Plain manual singleton, no Hilt — matching this project's other `object`-style
 * repositories/managers rather than a DI-provided instance. */
@Database(
    entities = [DeletedContactEntity::class, EmergencyContactEntity::class, ReminderEntity::class, ScheduledMessageEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recycleBinDao(): RecycleBinDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun reminderDao(): ReminderDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Adds only the scheduled_messages table, so existing reminders and recycle bin survive. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scheduled_messages` (`id` INTEGER NOT NULL, `number` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `body` TEXT NOT NULL, `timeMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "contacts_app_db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
