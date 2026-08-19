package com.example.contactapp.domain.repository

import android.net.Uri
import com.example.contactapp.domain.model.Contact
import com.example.contactapp.domain.model.DetailedContact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun fetchContacts(): Flow<List<Contact>>
    fun fetchFavorites(): Flow<List<Contact>>
    fun isFavorite(number: String): Flow<Boolean>
    suspend fun toggleFavorite(number: String, isFavorite: Boolean)
    suspend fun deleteContact(number: String)
    suspend fun deleteContactsByIds(ids: List<String>)
    suspend fun saveContact(name: String, number: String)
    suspend fun updateContact(contactId: String, newName: String, newNumber: String)
    suspend fun findContactByNumber(number: String): Contact?
    suspend fun fetchDetailedContacts(): List<DetailedContact>
    suspend fun fetchDetailedContactById(id: String): DetailedContact?

    /** Writes [uri]'s image bytes as this contact's system photo. Returns the fresh photo Uri to display, or null on failure. */
    suspend fun updateContactPhoto(contactId: String, uri: Uri): Uri?
    suspend fun removeContactPhoto(contactId: String)

    /**
     * Reads/writes the contact's standard CUSTOM_RINGTONE column — the same field Android's own
     * system Ringer consults when a real call comes in, so this is what makes a per-contact
     * ringtone actually take effect for real calls (not just something this app tracks locally).
     */
    suspend fun getContactRingtone(number: String): Uri?
    suspend fun updateContactRingtone(contactId: String, uri: Uri?)

    // Recycle Bin
    fun fetchDeletedContacts(): kotlinx.coroutines.flow.Flow<List<com.example.contactapp.data.local.entity.DeletedContactEntity>>
    suspend fun restoreContacts(ids: List<String>)
    suspend fun permanentlyDeleteContacts(ids: List<String>)
}
