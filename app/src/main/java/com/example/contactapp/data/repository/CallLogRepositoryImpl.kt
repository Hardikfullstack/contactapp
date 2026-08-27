package com.example.contactapp.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import com.example.contactapp.domain.model.CallLogItem
import com.example.contactapp.domain.model.CallType
import com.example.contactapp.domain.repository.CallLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.example.contactapp.util.LocalBlockManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CallLogRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    @ApplicationContext private val context: android.content.Context,
    private val localBlockManager: LocalBlockManager
) : CallLogRepository {

    override fun fetchCallLogs(): Flow<List<CallLogItem>> = callLogFlow {
        queryCallLogs(null, null)
    }

    override fun fetchCallHistory(phoneNumber: String): Flow<List<CallLogItem>> = callLogFlow {
        // Exact NUMBER match would miss history when formats differ (spacing/country code) —
        // normalize both sides to the last 10 digits, matching blocked-number/spam checks elsewhere.
        val target = phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)
        if (target.isEmpty()) {
            emptyList()
        } else {
            queryCallLogs(null, null).filter { log ->
                log.number.replace(Regex("[^0-9]"), "").takeLast(10) == target
            }
        }
    }

    override suspend fun fetchCallLogsForAnalytics(): List<CallLogItem> = withContext(Dispatchers.IO) {
        queryCallLogs(selection = null, selectionArgs = null, shouldResolveContactInfo = false)
    }

    override suspend fun resolveContactDisplayInfo(number: String): Pair<String?, String?> =
        withContext(Dispatchers.IO) { resolveContactInfo(number) }

    private fun <T> callLogFlow(queryBlock: () -> T): Flow<T> = callbackFlow {
        val observer = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                try {
                    trySend(queryBlock())
                } catch (e: Exception) {
                    // Fail silently
                }
            }
        }
        
        // Watch for changes in Call Logs
        contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            observer
        )

        // Also watch for changes in Contacts (e.g., name edits)
        contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer
        )

        // Initial emission
        try {
            trySend(queryBlock())
        } catch (e: Exception) {
            // Fail silently
        }

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    override fun deleteCallLog(id: Long) {
        try {
            val selection = "${CallLog.Calls._ID} = ?"
            val selectionArgs = arrayOf(id.toString())
            contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, selectionArgs)
        } catch (e: Exception) {
            // Log or handle
        }
    }

    override suspend fun deleteCallLogsByNumber(number: String) {
        withContext(Dispatchers.IO) {
            try {
                val selection = "${CallLog.Calls.NUMBER} = ?"
                val selectionArgs = arrayOf(number)
                contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, selectionArgs)
            } catch (e: Exception) {
                // Log or handle
            }
        }
    }

    override suspend fun deleteCallLogsByNumbers(numbers: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                if (numbers.isEmpty()) return@withContext
                // Use a batched approach if possible, or loop for simplicity
                numbers.forEach { number ->
                    val selection = "${CallLog.Calls.NUMBER} = ?"
                    val selectionArgs = arrayOf(number)
                    contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, selectionArgs)
                }
            } catch (e: Exception) {
                // Log or handle
            }
        }
    }

    override suspend fun blockNumber(number: String, block: Boolean) {
        withContext(Dispatchers.IO) {
            val cleanNumber = number.replace(Regex("[^0-9]"), "")
            val last10 = cleanNumber.takeLast(10)
            
            // 1. Always update local block list for instant UI feedback and fallback
            localBlockManager.setLocalBlocked(number, block)

            // 2. Attempt to update system block list (requires being default dialer)
            try {
                if (block) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
                    }
                    contentResolver.insert(android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
                } else {
                    android.provider.BlockedNumberContract.unblock(context, number)
                    if (last10.isNotEmpty()) {
                        android.provider.BlockedNumberContract.unblock(context, last10)
                    }
                }
            } catch (e: Exception) {
                // Not the default dialer or other restriction.
            }
        }
    }

    override fun isBlocked(number: String): Flow<Boolean> {
        return localBlockManager.isBlocked(number)
    }

    override fun getBlockedNumbers(): Flow<List<String>> {
        return localBlockManager.getBlockedNumbers()
    }



    private fun queryCallLogs(
        selection: String?,
        selectionArgs: Array<String>?,
        shouldResolveContactInfo: Boolean = true
    ): List<CallLogItem> {
        val callLogs = mutableListOf<CallLogItem>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_PHOTO_URI,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        // One batched query for every saved contact (same pattern the fast Contacts list
        // uses), instead of a per-row ContactsProvider round trip for each call log entry
        // whose cache is empty.
        val contactIndex = if (shouldResolveContactInfo) buildContactIndex() else emptyMap()

        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val photoUriIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    var name = cursor.getString(nameIndex)
                    val number = cursor.getString(numberIndex)
                    val type = mapCallType(cursor.getInt(typeIndex))
                    val date = cursor.getLong(dateIndex)
                    val duration = cursor.getString(durationIndex)
                    val durationSec = try { duration.toLong() } catch (e: Exception) { 0L }
                    var photoUri: String? = cursor.getString(photoUriIndex)

                    // Call log caches name/photo at call time — fall back to the contact index
                    // only when either is missing (e.g. saved/updated after the call was logged).
                    if (shouldResolveContactInfo && (name.isNullOrBlank() || photoUri.isNullOrBlank())) {
                        val normalized = number?.replace(Regex("[^0-9]"), "")?.takeLast(10)
                        val contactInfo = contactIndex[normalized]
                        if (contactInfo != null) {
                            if (name.isNullOrBlank()) name = contactInfo.first
                            if (photoUri.isNullOrBlank()) photoUri = contactInfo.second
                        }
                    }

                    callLogs.add(
                        CallLogItem(
                            id = id,
                            name = name,
                            number = number,
                            type = type,
                            timestamp = date,
                            duration = duration,
                            durationSeconds = durationSec,
                            photoUri = photoUri
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Handled in ViewModel/UI
        }
        return callLogs
    }

    private fun resolveContactInfo(phoneNumber: String): Pair<String?, String?> {
        if (phoneNumber.isBlank()) return Pair(null, null)
        
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        )
        
        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    val photo = cursor.getString(1)
                    Pair(name, photo)
                } else Pair(null, null)
            } ?: Pair(null, null)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private fun buildContactIndex(): Map<String, Pair<String?, String?>> {
        val index = mutableMapOf<String, Pair<String?, String?>>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex) ?: continue
                    val normalized = number.replace(Regex("[^0-9]"), "").takeLast(10)
                    if (normalized.isEmpty() || index.containsKey(normalized)) continue
                    index[normalized] = Pair(cursor.getString(nameIndex), cursor.getString(photoIndex))
                }
            }
        } catch (e: Exception) {
            // Log or handle
        }
        return index
    }

    private fun mapCallType(type: Int): CallType {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
            CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
            CallLog.Calls.MISSED_TYPE -> CallType.MISSED
            CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
            CallLog.Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
            CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
            else -> CallType.OTHER
        }
    }
}
