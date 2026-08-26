package com.example.contactapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.contactapp.data.local.dao.RecycleBinDao
import com.example.contactapp.data.local.dao.ReminderDao
import com.example.contactapp.data.local.entity.DeletedContactEntity
import com.example.contactapp.data.local.entity.ReminderEntity

@Database(
    entities = [DeletedContactEntity::class, ReminderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recycleBinDao(): RecycleBinDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val DATABASE_NAME = "contact_app_db"
    }
}
