package com.example.contactapp.di

import android.content.Context
import androidx.room.Room
import com.example.contactapp.data.local.AppDatabase
import com.example.contactapp.data.local.dao.RecycleBinDao
import com.example.contactapp.data.local.dao.ReminderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            // No real migrations written yet — acceptable pre-release (no shipped user data to
            // preserve). Revisit with a proper Migration once this app has real users.
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
    }

    @Provides
    @Singleton
    fun provideRecycleBinDao(database: AppDatabase): RecycleBinDao {
        return database.recycleBinDao()
    }

    @Provides
    @Singleton
    fun provideReminderDao(database: AppDatabase): ReminderDao {
        return database.reminderDao()
    }
}
