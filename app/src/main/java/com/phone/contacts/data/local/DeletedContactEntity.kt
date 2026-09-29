package com.phone.contacts.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A contact moved to the Recycle Bin instead of being permanently deleted. [id] reuses the
 * original system contact id (not a fresh row id) — matching contactapp's own approach. No photo
 * field: a contact's photoUri from the system provider becomes a dangling reference the moment
 * that contact row is actually deleted, so there's nothing valid to restore later. */
@Entity(tableName = "deleted_contacts")
data class DeletedContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val number: String,
    val deletedAt: Long = System.currentTimeMillis()
)
