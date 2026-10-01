package com.phone.contacts.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.phone.contacts.data.Contact

private const val PREFS_NAME = "display_options_prefs"
private const val KEY_SORT_ORDER = "contact_sort_order"
private const val KEY_NAME_FORMAT = "contact_name_format"

enum class ContactSortOrder(val label: String) {
    FIRST_NAME("First name"),
    LAST_NAME("Last name")
}

enum class ContactNameFormat(val label: String) {
    FIRST_NAME_FIRST("First name first"),
    LAST_NAME_FIRST("Last name first")
}

/** The key a contact sorts by — same naive "last word of the name" heuristic as the reference
 * app's own Sort-by-Last-Name, since this app (like the reference app) only stores one flat
 * display name, not separate given/family name fields. */
fun Contact.sortKey(order: ContactSortOrder): String = when (order) {
    ContactSortOrder.FIRST_NAME -> name.lowercase()
    ContactSortOrder.LAST_NAME -> name.trim().split(" ").last().lowercase()
}

/** Reorders a flat "First Middle Last" display name to "Last, First Middle" — single-word names
 * (nothing to reorder) are returned unchanged regardless of [format]. */
fun String.formattedForDisplay(format: ContactNameFormat): String {
    if (format == ContactNameFormat.FIRST_NAME_FIRST) return this
    val parts = trim().split(" ").filter { it.isNotBlank() }
    if (parts.size < 2) return this
    val last = parts.last()
    val rest = parts.dropLast(1).joinToString(" ")
    return "$last, $rest"
}

/** Persists the contacts list's sort order and name display format — same SharedPreferences-backed
 * singleton pattern as [AppThemePreferences]. Matches the reference app's "Display option" setting,
 * scoped to this app's own flat (non-structured) contact name. */
object DisplayOptionsPreferences {
    val sortOrder = mutableStateOf(ContactSortOrder.FIRST_NAME)
    val nameFormat = mutableStateOf(ContactNameFormat.FIRST_NAME_FIRST)

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sortOrder.value = runCatching {
            ContactSortOrder.valueOf(prefs.getString(KEY_SORT_ORDER, ContactSortOrder.FIRST_NAME.name)!!)
        }.getOrDefault(ContactSortOrder.FIRST_NAME)
        nameFormat.value = runCatching {
            ContactNameFormat.valueOf(prefs.getString(KEY_NAME_FORMAT, ContactNameFormat.FIRST_NAME_FIRST.name)!!)
        }.getOrDefault(ContactNameFormat.FIRST_NAME_FIRST)
    }

    fun setSortOrder(context: Context, order: ContactSortOrder) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SORT_ORDER, order.name)
            .apply()
        sortOrder.value = order
    }

    fun setNameFormat(context: Context, format: ContactNameFormat) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME_FORMAT, format.name)
            .apply()
        nameFormat.value = format
    }
}
