package com.phone.contacts.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.provider.ContactsContract
import com.phone.contacts.data.local.AppDatabase
import com.phone.contacts.data.local.DeletedContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

object ContactRepository {

    /** Saves a new contact with the given name/phone (both required — the only fields the current
     * Add Contact screen actually wires up; email/address/etc. are UI-only placeholders for now). */
    suspend fun saveContact(context: Context, name: String, phone: String): Boolean = withContext(Dispatchers.IO) {
        val ops = ArrayList<ContentProviderOperation>()

        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
        )

        try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Deletes the given contacts (by CONTACT_ID) from the device's contacts provider — used by
     * the Contacts screen's multi-select "delete" action. */
    suspend fun deleteContacts(context: Context, ids: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            ids.forEach { id ->
                val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id.toLong())
                context.contentResolver.delete(uri, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Moves contacts to the Recycle Bin instead of deleting them outright: captures a minimal
     * record (name + number, no photo — see [DeletedContactEntity]'s own doc) for each, then
     * removes them from the system provider via the existing [deleteContacts]. */
    suspend fun moveToRecycleBin(context: Context, contacts: List<Contact>): Boolean = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(context).recycleBinDao()
        contacts.forEach { contact ->
            dao.insert(DeletedContactEntity(id = contact.id, name = contact.name, number = contact.number))
        }
        deleteContacts(context, contacts.map { it.id })
    }

    /** Re-inserts a Recycle Bin entry as a contact — reuses [saveContact], which already does
     * exactly the RawContact+StructuredName+Phone insert this needs. Android assigns a brand-new
     * contact id; the original one isn't preserved (not possible on this platform). */
    suspend fun restoreFromRecycleBin(context: Context, entity: DeletedContactEntity): Boolean {
        val restored = saveContact(context, entity.name, entity.number)
        if (restored) {
            AppDatabase.getInstance(context).recycleBinDao().deletePermanently(listOf(entity.id))
        }
        return restored
    }

    /** Permanently removes Recycle Bin entries without restoring them. */
    suspend fun deleteFromRecycleBinPermanently(context: Context, ids: List<String>) = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(context).recycleBinDao().deletePermanently(ids)
    }

    /** Empties the Recycle Bin entirely. */
    suspend fun clearRecycleBin(context: Context) = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(context).recycleBinDao().clearAll()
    }

    fun recycleBinFlow(context: Context): Flow<List<DeletedContactEntity>> =
        AppDatabase.getInstance(context).recycleBinDao().getAll()

    /** Toggles a contact's starred (favorite) state — used by the contact detail screen's
     * Favorites action. */
    suspend fun setStarred(context: Context, contactId: String, starred: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId.toLong())
            val values = android.content.ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
            }
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Sets (or clears, when [ringtoneUri] is null) a contact's custom ringtone — used by the
     * contact detail screen's "Set Ringtone" action. */
    suspend fun setCustomRingtone(context: Context, contactId: String, ringtoneUri: android.net.Uri?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId.toLong())
                val values = android.content.ContentValues().apply {
                    put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri?.toString())
                }
                context.contentResolver.update(uri, values, null, null) > 0
            } catch (_: Exception) {
                false
            }
        }

    /** Resolves a raw phone number (as Telecom hands it over — no guaranteed formatting) to a
     * matching contact's name/photo, for the call screen. Uses PhoneLookup, which does its own
     * number-normalization matching internally, so exact string formatting doesn't need to match. */
    suspend fun findContactByNumber(context: Context, number: String): Contact? = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext null
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.STARRED
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val starredIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.STARRED)
                    return@withContext Contact(
                        id = cursor.getString(idIndex) ?: "",
                        name = cursor.getString(nameIndex) ?: number,
                        number = number,
                        photoUri = cursor.getString(photoIndex),
                        isStarred = starredIndex >= 0 && cursor.getInt(starredIndex) == 1
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted — caller falls back to showing the raw number.
        }
        null
    }

    /** Reactive — re-queries and re-emits whenever a contact is added/edited/deleted, instead of
     * needing an explicit manual refresh (e.g. on screen resume). */
    fun fetchContacts(context: Context): Flow<List<Contact>> =
        observeContacts(context, favoritesOnly = false)

    /** Same as [fetchContacts] but limited to contacts starred (favorited) in the device's
     * contacts provider. */
    fun fetchFavoriteContacts(context: Context): Flow<List<Contact>> =
        observeContacts(context, favoritesOnly = true)

    private fun observeContacts(context: Context, favoritesOnly: Boolean): Flow<List<Contact>> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(queryContacts(context, favoritesOnly))
            }
        }
        resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        trySend(queryContacts(context, favoritesOnly))
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    private fun queryContacts(context: Context, favoritesOnly: Boolean): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED
        )
        val selection = buildString {
            append("${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} IS NOT NULL")
            if (favoritesOnly) append(" AND ${ContactsContract.CommonDataKinds.Phone.STARRED} = 1")
        }
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val starredIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                val lastContactedIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED)
                while (cursor.moveToNext()) {
                    contacts.add(
                        Contact(
                            id = cursor.getString(idIndex) ?: continue,
                            name = cursor.getString(nameIndex) ?: continue,
                            number = cursor.getString(numberIndex) ?: continue,
                            photoUri = cursor.getString(photoIndex),
                            isStarred = cursor.getInt(starredIndex) == 1,
                            lastTimeContacted = cursor.getLong(lastContactedIndex)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted — caller is responsible for checking beforehand.
        }
        // A contact with multiple phone numbers produces one row per number — keep just the first.
        return contacts.distinctBy { it.id }
    }
}
