package com.example.contactapp.data.local.dao

import androidx.room.*
import com.example.contactapp.data.local.entity.DeletedContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycleBinDao {
    @Query("SELECT * FROM deleted_contacts ORDER BY deletedAt DESC")
    fun getAllDeletedContacts(): Flow<List<DeletedContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedContact(contact: DeletedContactEntity)

    @Query("DELETE FROM deleted_contacts WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("DELETE FROM deleted_contacts WHERE id IN (:ids)")
    suspend fun deletePermanently(ids: List<String>)

    @Query("SELECT * FROM deleted_contacts WHERE id = :id")
    suspend fun getDeletedContact(id: String): DeletedContactEntity?
}
