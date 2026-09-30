package com.phone.contacts.data

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** One entry in the system's own block list ([BlockedNumberContract]) — [displayName] is resolved
 * from the device's contacts by number, and is null (shown as "Unknown") for a blocked number
 * that isn't saved as a contact, matching the reference app's own blocked-list row. */
data class BlockedNumberEntry(val id: Long, val number: String, val displayName: String?)

private const val BLOCKING_PREFS = "blocking_prefs"
private const val KEY_BLOCK_UNKNOWN_CALLERS = "block_unknown_callers"

/** Settings > Blocking. Number-blocking itself (block/unblock a specific number) is backed by the
 * system's own [BlockedNumberContract] table, which the telephony stack already honors on its own
 * for any app holding the default-dialer role — no custom CallScreeningService is needed for that
 * part. The "Block calls from unidentified callers" toggle is UI/preference-only here: actually
 * rejecting private/unknown-number calls needs a CallScreeningService the user separately grants
 * the Caller ID & spam role to, which this app doesn't have yet — a materially bigger, separate
 * feature deliberately left out of this pass. */
object BlockRepository {

    fun isBlockingAvailable(context: Context): Boolean =
        BlockedNumberContract.canCurrentUserBlockNumbers(context)

    suspend fun blockNumber(context: Context, number: String): Boolean = withContext(Dispatchers.IO) {
        if (number.isBlank() || !BlockedNumberContract.canCurrentUserBlockNumbers(context)) return@withContext false
        try {
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
            }
            context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values) != null
        } catch (_: Exception) {
            false
        }
    }

    /** One-shot check — used to show a "Blocked" indicator for a specific number (e.g. the
     * Contact Detail call-history title), as opposed to [blockedNumbersFlow]'s full reactive
     * list. */
    suspend fun isNumberBlocked(context: Context, number: String): Boolean = withContext(Dispatchers.IO) {
        if (number.isBlank() || !BlockedNumberContract.canCurrentUserBlockNumbers(context)) return@withContext false
        try {
            BlockedNumberContract.isBlocked(context, number)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun unblockNumber(context: Context, id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = android.content.ContentUris.withAppendedId(BlockedNumberContract.BlockedNumbers.CONTENT_URI, id)
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Convenience for call sites (like Contact Detail's "Unblock" menu item) that only have the
     * number, not the block-list row id [unblockNumber] needs. */
    suspend fun unblockByNumber(context: Context, number: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?",
                arrayOf(number)
            ) > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Reactive — re-queries whenever a number is blocked/unblocked from anywhere (this screen,
     * the Contact Detail "Block" action, or another app). */
    fun blockedNumbersFlow(context: Context): Flow<List<BlockedNumberEntry>> = callbackFlow {
        if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val resolver = context.contentResolver
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(queryBlockedNumbers(context))
            }
        }
        resolver.registerContentObserver(BlockedNumberContract.BlockedNumbers.CONTENT_URI, true, observer)
        trySend(queryBlockedNumbers(context))
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    private fun queryBlockedNumbers(context: Context): List<BlockedNumberEntry> {
        val entries = mutableListOf<BlockedNumberEntry>()
        context.contentResolver.query(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            arrayOf(
                BlockedNumberContract.BlockedNumbers.COLUMN_ID,
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER
            ),
            null, null, null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ID)
            val numberIndex = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex) ?: continue
                entries.add(BlockedNumberEntry(cursor.getLong(idIndex), number, lookupDisplayName(context, number)))
            }
        }
        return entries
    }

    private fun lookupDisplayName(context: Context, number: String): String? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    fun isBlockUnknownCallersEnabled(context: Context): Boolean =
        context.getSharedPreferences(BLOCKING_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BLOCK_UNKNOWN_CALLERS, false)

    fun setBlockUnknownCallersEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(BLOCKING_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BLOCK_UNKNOWN_CALLERS, enabled).apply()
    }
}
