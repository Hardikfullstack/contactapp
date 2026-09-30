package com.phone.contacts.util

import android.content.Context

private const val PREFS_NAME = "recently_added_prefs"
private const val KEY_ADDED = "added_numbers"
private const val ENTRY_DELIMITER = ","
private const val FIELD_DELIMITER = ":"

/** Tracks when a contact was actually created through this app's own Add Contact screen — the
 * reference app's "Recent added" filter (it ships a "Clear Recently Added" action, meaning it
 * keeps its own list, date-grouped with an added time per row) rather than any real "date added"
 * column, which the system Contacts provider doesn't reliably expose. Mirrors
 * [RecentlyViewedContacts]'s same number-keyed SharedPreferences approach. */
object RecentlyAddedContacts {
    fun recordAdd(context: Context, number: String) {
        if (number.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = addedTimestamps(context).toMutableMap()
        updated[number] = System.currentTimeMillis()
        val encoded = updated.entries.joinToString(ENTRY_DELIMITER) { (num, ts) -> "$num$FIELD_DELIMITER$ts" }
        prefs.edit().putString(KEY_ADDED, encoded).apply()
    }

    fun addedTimestamps(context: Context): Map<String, Long> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ADDED, null) ?: return emptyMap()
        return raw.split(ENTRY_DELIMITER)
            .mapNotNull { entry ->
                val parts = entry.split(FIELD_DELIMITER)
                val timestamp = parts.lastOrNull()?.toLongOrNull() ?: return@mapNotNull null
                val number = parts.dropLast(1).joinToString(FIELD_DELIMITER)
                if (number.isBlank()) null else number to timestamp
            }
            .toMap()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_ADDED).apply()
    }
}
