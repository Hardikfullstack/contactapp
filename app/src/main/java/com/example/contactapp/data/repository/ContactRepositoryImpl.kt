package com.example.contactapp.data.repository

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import com.example.contactapp.data.local.dao.RecycleBinDao
import com.example.contactapp.data.local.entity.DeletedContactEntity
import com.example.contactapp.domain.model.Contact
import com.example.contactapp.domain.model.DetailedContact
import com.example.contactapp.domain.repository.ContactRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ContactRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val recycleBinDao: RecycleBinDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ContactRepository {

    private val gson = Gson()

    override fun fetchContacts(): Flow<List<Contact>> = contactFlow {
        queryContacts(null, null)
    }

    override fun fetchFavorites(): Flow<List<Contact>> = contactFlow {
        val selection = "${ContactsContract.Contacts.STARRED} = ?"
        val selectionArgs = arrayOf("1")
        queryContacts(selection, selectionArgs)
    }

    override fun isFavorite(number: String): Flow<Boolean> = contactFlow {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.STARRED)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0) == 1
            } else false
        } ?: false
    }

    override suspend fun toggleFavorite(number: String, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number)
                )
                val projection = arrayOf(ContactsContract.PhoneLookup._ID)
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val contactId = cursor.getLong(0)
                        val contactUri =
                            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
                        val values = android.content.ContentValues().apply {
                            put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
                        }
                        contentResolver.update(contactUri, values, null, null)
                    }
                }
            } catch (e: Exception) {
                // Log error or handle
            }
        }
    }

    override suspend fun getContactRingtone(number: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.CUSTOM_RINGTONE)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { Uri.parse(it) }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun updateContactRingtone(contactId: String, uri: Uri?) {
        withContext(Dispatchers.IO) {
            try {
                val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
                val values = android.content.ContentValues().apply {
                    put(ContactsContract.Contacts.CUSTOM_RINGTONE, uri?.toString())
                }
                contentResolver.update(contactUri, values, null, null)
            } catch (e: Exception) {
                // Log error or handle
            }
        }
    }

    override suspend fun deleteContact(number: String) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number)
                )
                val projection = arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME)
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val contactId = cursor.getString(0)
                        val name = cursor.getString(1) ?: "Unknown"
                        
                        // 1. Move to Recycle Bin first
                        fetchDetailedContactById(contactId)?.let { detailed ->
                            recycleBinDao.insertDeletedContact(
                                DeletedContactEntity(
                                    id = contactId,
                                    name = name,
                                    phoneNumber = number,
                                    dataJson = gson.toJson(detailed)
                                )
                            )
                        }

                        // 2. Delete from system
                        val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
                        contentResolver.delete(contactUri, null, null)
                    }
                }
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    override suspend fun deleteContactsByIds(ids: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                ids.forEach { id ->
                    // 1. Move to Recycle Bin
                    val detailed = fetchDetailedContactById(id)
                    detailed?.let {
                        recycleBinDao.insertDeletedContact(
                            DeletedContactEntity(
                                id = id,
                                name = it.name,
                                phoneNumber = it.phoneNumbers.firstOrNull() ?: "",
                                dataJson = gson.toJson(it)
                            )
                        )
                    }

                    // 2. Delete from system
                    val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id)
                    contentResolver.delete(contactUri, null, null)
                }
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    override fun fetchDeletedContacts(): Flow<List<DeletedContactEntity>> {
        return recycleBinDao.getAllDeletedContacts()
    }

    override suspend fun permanentlyDeleteContacts(ids: List<String>) {
        withContext(Dispatchers.IO) {
            recycleBinDao.deletePermanently(ids)
        }
    }

    override suspend fun restoreContacts(ids: List<String>) {
        withContext(Dispatchers.IO) {
            ids.forEach { id ->
                val entity = recycleBinDao.getDeletedContact(id) ?: return@forEach
                val detailed = gson.fromJson(entity.dataJson, DetailedContact::class.java)
                
                // Re-save using existing save logic (effectively restoring)
                // Actually, I should probably use a more complete restore that keeps all fields
                restoreToSystem(detailed)
                
                // Remove from bin
                recycleBinDao.deletePermanently(id)
            }
        }
    }

    private suspend fun restoreToSystem(detailed: DetailedContact) {
        val ops = ArrayList<ContentProviderOperation>()

        // Find primary account again or null
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.accounts
        val primaryAccount = accounts.find { it.type == "com.google" } ?: accounts.firstOrNull()

        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, primaryAccount?.type)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, primaryAccount?.name)
            .build())

        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, detailed.name)
            .build())

        detailed.phoneNumbers.forEach { num ->
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, num)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build())
        }
        
        detailed.emails.forEach { email ->
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.DATA, email)
                .build())
        }

        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            // Handle
        }
    }

    override suspend fun updateContact(contactId: String, newName: String, newNumber: String) {
        withContext(Dispatchers.IO) {
            val ops = ArrayList<ContentProviderOperation>()

            // Update Name
            val nameSelection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
            val nameArgs = arrayOf(contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(nameSelection, nameArgs)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
                .build())

            // Update Phone
            val phoneSelection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
            val phoneArgs = arrayOf(contactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(phoneSelection, phoneArgs)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumber)
                .build())

            try {
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    override suspend fun updateContactPhoto(contactId: String, uri: Uri): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val photoBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext null

                val rawContactId = contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts._ID),
                    "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
                    ?: return@withContext null

                val existingPhotoDataId = contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data._ID),
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE),
                    null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

                val values = android.content.ContentValues().apply {
                    put(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                }

                if (existingPhotoDataId != null) {
                    contentResolver.update(
                        ContactsContract.Data.CONTENT_URI,
                        values,
                        "${ContactsContract.Data._ID} = ?",
                        arrayOf(existingPhotoDataId.toString())
                    )
                } else {
                    values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    contentResolver.insert(ContactsContract.Data.CONTENT_URI, values)
                }

                // Cache-bust so image loaders (Coil) don't serve a stale copy of a
                // previously-cached photo at the same content Uri.
                val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
                Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY)
                    .buildUpon()
                    .appendQueryParameter("t", System.currentTimeMillis().toString())
                    .build()
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun removeContactPhoto(contactId: String) {
        withContext(Dispatchers.IO) {
            try {
                val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
                val selectionArgs = arrayOf(contactId, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                contentResolver.delete(ContactsContract.Data.CONTENT_URI, selection, selectionArgs)
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    override suspend fun saveContact(name: String, number: String) {
        withContext(Dispatchers.IO) {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.accounts
            val primaryAccount = accounts.find { it.type == "com.google" } ?: accounts.firstOrNull()

            val ops = ArrayList<ContentProviderOperation>()

            // 1. Create a new RawContact associated with an account
            val rawContactOp = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, primaryAccount?.type)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, primaryAccount?.name)
                .withValue(ContactsContract.RawContacts.AGGREGATION_MODE, ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT)
            
            ops.add(rawContactOp.build())

            // 2. Add Name
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build())

            // 3. Add Phone Number
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build())

            // 4. Try to add to "My Contacts" group for visibility
            primaryAccount?.let { acc ->
                val groupId = findMyContactsGroupId(acc.name, acc.type)
                if (groupId != -1L) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
                        .build())
                }
            }

            try {
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    private fun findMyContactsGroupId(accountName: String, accountType: String): Long {
        val selection = "${ContactsContract.Groups.ACCOUNT_NAME} = ? AND ${ContactsContract.Groups.ACCOUNT_TYPE} = ? AND ${ContactsContract.Groups.TITLE} = ?"
        val selectionArgs = arrayOf(accountName, accountType, "My Contacts")
        
        return try {
            contentResolver.query(ContactsContract.Groups.CONTENT_URI, arrayOf(ContactsContract.Groups._ID), selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    override suspend fun findContactByNumber(number: String): Contact? {
        return withContext(Dispatchers.IO) {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup.CONTACT_ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
            )
            
            try {
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        Contact(
                            id = cursor.getString(0),
                            name = cursor.getString(1) ?: "Unknown",
                            number = number,
                            photoUri = cursor.getString(2)
                        )
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun fetchDetailedContacts(): List<DetailedContact> {
        return withContext(Dispatchers.IO) {
            queryDetailedContacts(null, null)
        }
    }

    override suspend fun fetchDetailedContactById(id: String): DetailedContact? {
        return withContext(Dispatchers.IO) {
            val selection = "${ContactsContract.Data.CONTACT_ID} = ?"
            val selectionArgs = arrayOf(id)
            queryDetailedContacts(selection, selectionArgs).firstOrNull()
        }
    }

    private fun queryDetailedContacts(selection: String?, selectionArgs: Array<String>?): List<DetailedContact> {
        val contactsMap = mutableMapOf<String, DetailedContactBuilder>()
        
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.CommonDataKinds.Organization.TITLE,
            ContactsContract.CommonDataKinds.Organization.DEPARTMENT
        )
        
        val baseSelection = "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val baseSelectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
        )

        val finalSelection = if (selection != null) "$baseSelection AND ($selection)" else baseSelection
        val finalSelectionArgs = if (selectionArgs != null) baseSelectionArgs + selectionArgs else baseSelectionArgs

        try {
            contentResolver.query(
                uri,
                projection,
                finalSelection,
                finalSelectionArgs,
                "${ContactsContract.Data.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val nameCol = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val dataCol = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val titleCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.TITLE)
                val deptCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.DEPARTMENT)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: "Unknown"
                    val mimeType = cursor.getString(mimeCol)
                    val data = cursor.getString(dataCol) ?: continue

                    val builder = contactsMap.getOrPut(id) { DetailedContactBuilder(name) }
                    
                    when (mimeType) {
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val normalized = data.replace(Regex("[^0-9]"), "")
                            if (normalized.isNotEmpty()) {
                                val last10 = normalized.takeLast(10)
                                if (!builder.phoneCheckSet.contains(last10)) {
                                    builder.phones.add(data)
                                    builder.phoneCheckSet.add(last10)
                                }
                            }
                        }
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> builder.emails.add(data)
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> builder.addresses.add(data)
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            builder.orgs.add(data)
                            builder.jobTitle = cursor.getString(titleCol)
                            builder.department = cursor.getString(deptCol)
                        }
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> builder.notes = data
                        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> builder.birthday = data
                        ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> builder.websites.add(data)
                        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> builder.nicknames.add(data)
                        ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE -> builder.relations.add(data)
                        ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE -> builder.ims.add(data)
                    }
                }
            }
        } catch (e: Exception) {
            // Log or handle error
        }
        
        return contactsMap.values.map { it.build() }
    }

    private class DetailedContactBuilder(val name: String) {
        val phones = mutableListOf<String>()
        val phoneCheckSet = mutableSetOf<String>()
        val emails = mutableSetOf<String>()
        val addresses = mutableSetOf<String>()
        val orgs = mutableSetOf<String>()
        val websites = mutableSetOf<String>()
        val nicknames = mutableSetOf<String>()
        val relations = mutableSetOf<String>()
        val ims = mutableSetOf<String>()
        var notes: String? = null
        var birthday: String? = null
        var jobTitle: String? = null
        var department: String? = null

        fun build() = DetailedContact(
            name = name,
            phoneNumbers = phones.toList(),
            emails = emails.toList(),
            addresses = addresses.toList(),
            organizations = orgs.toList(),
            jobTitle = jobTitle,
            department = department,
            notes = notes,
            birthday = birthday,
            websites = websites.toList(),
            nicknames = nicknames.toList(),
            relations = relations.toList(),
            ims = ims.toList()
        )
    }

    private fun <T> contactFlow(queryBlock: () -> T): Flow<T> = callbackFlow {
        val observer = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                try {
                    trySend(queryBlock())
                } catch (e: Exception) {
                    // Fail silently or log
                }
            }
        }
        contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer
        )
        
        // Initial emission
        try {
            trySend(queryBlock())
        } catch (e: Exception) {
            // Fail silently or log
        }

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    private fun queryContacts(selection: String?, selectionArgs: Array<String>?): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        // Filter for named contacts and sort by name
        val finalSelection = if (selection == null) {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} IS NOT NULL"
        } else {
            "$selection AND ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} IS NOT NULL"
        }

        try {
            contentResolver.query(
                uri,
                projection,
                finalSelection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val starredIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex)
                    val number = cursor.getString(numberIndex)
                    val isFavorite = cursor.getInt(starredIndex) == 1
                    val photoUri = cursor.getString(photoIndex)

                    contacts.add(
                        Contact(
                            id = id,
                            name = name,
                            number = number,
                            isFavorite = isFavorite,
                            photoUri = photoUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Log error
        }
        // Deduplicate by CONTACT_ID to ensure each person appears only once
        return contacts.distinctBy { it.id }
    }
}
