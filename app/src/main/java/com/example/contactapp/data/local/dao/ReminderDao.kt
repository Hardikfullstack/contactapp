package com.example.contactapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.contactapp.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("SELECT * FROM after_call_reminders WHERE number = :number ORDER BY reminderTimeMillis ASC")
    fun getAllByNumberFlow(number: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM after_call_reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("DELETE FROM after_call_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
