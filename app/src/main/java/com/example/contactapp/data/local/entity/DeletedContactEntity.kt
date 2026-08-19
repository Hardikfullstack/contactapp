package com.example.contactapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_contacts")
data class DeletedContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phoneNumber: String, // Principal number for easy display
    val dataJson: String,    // Serialized DetailedContact
    val deletedAt: Long = System.currentTimeMillis()
)
