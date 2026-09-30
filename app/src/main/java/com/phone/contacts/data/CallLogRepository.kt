package com.phone.contacts.data

import android.content.ContentResolver
import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CallLogRepository {

    suspend fun fetchCallLogs(context: Context): List<CallLogItem> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        // One bulk query for every saved number, reused for every row below — this is what the
        // reference app does too (resolve against an already-loaded contacts list, not a live
        // lookup per number): a query per distinct number here was correct but made every screen
        // resume noticeably slower the more distinct numbers were in the call history.
        val contactIndex = buildContactNameIndex(resolver)
        val rawEntries = queryRawCallLogs(resolver, contactIndex)
        groupConsecutiveCalls(rawEntries)
    }

    /** Deletes only the single most-recent call log row [id] refers to — a grouped row like
     * "Parth (3)" only removes its latest entry, leaving the earlier calls in the group intact. */
    suspend fun deleteCallLog(context: Context, id: Long) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} = ?",
                arrayOf(id.toString())
            )
        } catch (_: SecurityException) {
            // WRITE_CALL_LOG not granted — nothing to do.
        }
    }

    /** Every individual call to/from [number] (not collapsed like [fetchCallLogs]'s
     * "(3)"-style grouping) — used by the contact detail screen's own Call History page, where
     * each call needs its own timestamp and duration shown. */
    suspend fun fetchCallHistoryForNumber(context: Context, number: String): List<CallLogItem> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<CallLogItem>()
        val target = normalizeNumber(number)
        if (target.isEmpty()) return@withContext entries
        // Resolve this number's current contact name once up front instead of trusting each row's
        // own CACHED_NAME (see buildContactNameIndex's doc).
        val liveName = buildContactNameIndex(context.contentResolver)[target]
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                while (cursor.moveToNext()) {
                    val rowNumber = cursor.getString(numberIndex) ?: continue
                    if (normalizeNumber(rowNumber) != target) continue
                    entries.add(
                        CallLogItem(
                            id = cursor.getLong(idIndex),
                            name = liveName ?: cursor.getString(nameIndex),
                            number = rowNumber,
                            type = mapCallType(cursor.getInt(typeIndex)),
                            timestamp = cursor.getLong(dateIndex),
                            durationSeconds = cursor.getLong(durationIndex)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted — caller is responsible for checking beforehand.
        }
        entries
    }

    /** Deletes every call log row to/from [number] — used by the contact detail screen's "Clear
     * Call History" confirmation. */
    suspend fun deleteAllForNumber(context: Context, number: String): Boolean = withContext(Dispatchers.IO) {
        val target = normalizeNumber(number)
        if (target.isEmpty()) return@withContext false
        try {
            val ids = mutableListOf<Long>()
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                while (cursor.moveToNext()) {
                    val rowNumber = cursor.getString(numberIndex) ?: continue
                    if (normalizeNumber(rowNumber) == target) ids.add(cursor.getLong(idIndex))
                }
            }
            if (ids.isEmpty()) return@withContext true
            val placeholders = ids.joinToString(",") { "?" }
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} IN ($placeholders)",
                ids.map { it.toString() }.toTypedArray()
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private data class RawEntry(
        val id: Long,
        val name: String?,
        val number: String,
        val type: CallType,
        val timestamp: Long
    )

    private fun queryRawCallLogs(resolver: ContentResolver, contactIndex: Map<String, String>): List<RawEntry> {
        val entries = mutableListOf<RawEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE
        )
        try {
            resolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)

                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex) ?: continue
                    val normalized = normalizeNumber(number)
                    val resolvedName = contactIndex[normalized] ?: cursor.getString(nameIndex)

                    entries.add(
                        RawEntry(
                            id = cursor.getLong(idIndex),
                            name = resolvedName,
                            number = number,
                            type = mapCallType(cursor.getInt(typeIndex)),
                            timestamp = cursor.getLong(dateIndex)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted — caller is responsible for checking beforehand.
        }
        return entries
    }

    /** Collapses consecutive calls to/from the same number AND of the same type into one row
     * with a count, matching how stock dialer call logs group repeated back-to-back calls (e.g.
     * "Parth (3)"). Type must match too — an outgoing call followed by an incoming one from the
     * same number are two distinct events, not a repeat, even though the number is identical. */
    private fun groupConsecutiveCalls(entries: List<RawEntry>): List<CallLogItem> {
        val grouped = mutableListOf<CallLogItem>()
        var index = 0
        while (index < entries.size) {
            val current = entries[index]
            var count = 1
            var next = index + 1
            while (next < entries.size && entries[next].number == current.number && entries[next].type == current.type) {
                count++
                next++
            }
            grouped.add(
                CallLogItem(
                    id = current.id,
                    name = current.name,
                    number = current.number,
                    type = current.type,
                    timestamp = current.timestamp,
                    callCount = count
                )
            )
            index = next
        }
        return grouped
    }

    /** One query for every saved phone number, keyed by normalized number — matches the reference
     * app's own approach (resolve call-log names against an already-loaded contacts list) and how
     * this app's own Contacts screen already reads names, so a post-edit name change shows up here
     * the same way it does there. A single bulk query, reused for every row, instead of a live
     * lookup per number — the per-number version was correct but made every screen resume visibly
     * slower on a call history with many distinct numbers. */
    private fun buildContactNameIndex(resolver: ContentResolver): Map<String, String> {
        val index = mutableMapOf<String, String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        try {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex) ?: continue
                    val normalized = normalizeNumber(number)
                    if (normalized.isEmpty() || index.containsKey(normalized)) continue
                    cursor.getString(nameIndex)?.let { index[normalized] = it }
                }
            }
        } catch (_: SecurityException) {
            // READ_CONTACTS not granted — call log still shows raw numbers.
        }
        return index
    }

    private fun normalizeNumber(number: String): String =
        number.filter { it.isDigit() }.takeLast(10)

    private fun mapCallType(type: Int): CallType = when (type) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
        else -> CallType.OTHER
    }
}
