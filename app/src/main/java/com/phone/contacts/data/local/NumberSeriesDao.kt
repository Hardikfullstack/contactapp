package com.phone.contacts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NumberSeriesDao {
    @Query("SELECT * FROM number_series ORDER BY addedAt DESC")
    fun getAll(): Flow<List<NumberSeriesEntity>>

    /** One-shot (not Flow) read for the call-screening service, which needs a plain snapshot per
     * incoming call rather than a long-lived collector. */
    @Query("SELECT * FROM number_series")
    suspend fun getAllOnce(): List<NumberSeriesEntity>

    @Insert
    suspend fun insert(entity: NumberSeriesEntity)

    @Query("DELETE FROM number_series WHERE id = :id")
    suspend fun deleteById(id: Long)
}
