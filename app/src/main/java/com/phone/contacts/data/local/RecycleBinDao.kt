package com.phone.contacts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycleBinDao {
    @Query("SELECT * FROM deleted_contacts ORDER BY deletedAt DESC")
    fun getAll(): Flow<List<DeletedContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DeletedContactEntity)

    @Query("DELETE FROM deleted_contacts WHERE id IN (:ids)")
    suspend fun deletePermanently(ids: List<String>)

    @Query("DELETE FROM deleted_contacts")
    suspend fun clearAll()
}
