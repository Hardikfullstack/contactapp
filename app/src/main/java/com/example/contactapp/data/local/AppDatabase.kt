package com.example.contactapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.contactapp.data.local.dao.RecycleBinDao
import com.example.contactapp.data.local.entity.DeletedContactEntity

@Database(entities = [DeletedContactEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recycleBinDao(): RecycleBinDao

    companion object {
        const val DATABASE_NAME = "contact_app_db"
    }
}
