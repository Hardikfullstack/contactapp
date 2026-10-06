package com.phone.contacts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM call_reminders WHERE number = :number ORDER BY timeMillis ASC")
    fun getForNumber(number: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM call_reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("DELETE FROM call_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
