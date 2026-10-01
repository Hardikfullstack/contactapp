package com.phone.contacts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY addedAt ASC")
    fun getAll(): Flow<List<EmergencyContactEntity>>

    @Query("SELECT sourceKey FROM emergency_contacts")
    suspend fun getAllSourceKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<EmergencyContactEntity>)

    @Query("DELETE FROM emergency_contacts WHERE sourceKey IN (:sourceKeys)")
    suspend fun deleteBySourceKeys(sourceKeys: List<String>)

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
