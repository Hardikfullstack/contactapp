package com.phone.contacts.util

import android.content.Context

private const val PREFS_NAME = "recently_viewed_prefs"
private const val KEY_VIEWED = "viewed_numbers"
private const val ENTRY_DELIMITER = ","
private const val FIELD_DELIMITER = ":"

/** Tracks when a contact's detail screen was actually opened — the reference app's own
 * "Recent viewed" filter (it ships a "Clear recently viewed" action, meaning it keeps its own
 * list) rather than Android's system "last contacted" timestamp, which only reflects real calls
 * or texts and never actually opening a profile. */
object RecentlyViewedContacts {
    fun recordView(context: Context, number: String) {
        if (number.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = viewedTimestamps(context).toMutableMap()
        updated[number] = System.currentTimeMillis()
        val encoded = updated.entries.joinToString(ENTRY_DELIMITER) { (num, ts) -> "$num$FIELD_DELIMITER$ts" }
        prefs.edit().putString(KEY_VIEWED, encoded).apply()
    }

    fun viewedTimestamps(context: Context): Map<String, Long> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VIEWED, null) ?: return emptyMap()
        return raw.split(ENTRY_DELIMITER)
            .mapNotNull { entry ->
                val parts = entry.split(FIELD_DELIMITER)
                val timestamp = parts.lastOrNull()?.toLongOrNull() ?: return@mapNotNull null
                val number = parts.dropLast(1).joinToString(FIELD_DELIMITER)
                if (number.isBlank()) null else number to timestamp
            }
            .toMap()
    }
}
