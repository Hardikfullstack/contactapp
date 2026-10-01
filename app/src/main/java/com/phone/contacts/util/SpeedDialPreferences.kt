package com.phone.contacts.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

private const val PREFS_NAME = "speed_dial_prefs"
private const val KEY_PREFIX = "key_"

/** Every assignable dial-pad key, in the same order the Keypad itself lays them out. */
val speedDialKeys: List<String> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

data class SpeedDialEntry(val contactId: String, val name: String, val number: String, val photoUri: String?)

/** A fixed, distinct color per dial-pad key for the badge shown in both the grid and the Speed
 * dial list — purely visual, so each slot stays easy to tell apart at a glance. */
fun speedDialColorFor(key: String): Color {
    val palette = listOf(
        Color(0xFF26A69A), Color(0xFFE57373), Color(0xFFFFB74D), Color(0xFF42A5F5), Color(0xFFAB47BC),
        Color(0xFF5C6BC0), Color(0xFF8D6E63), Color(0xFFFF7043), Color(0xFF66BB6A), Color(0xFF78909C),
        Color(0xFFEC407A), Color(0xFF26C6DA)
    )
    val index = speedDialKeys.indexOf(key).let { if (it == -1) 0 else it }
    return palette[index.mod(palette.size)]
}

private fun encode(entry: SpeedDialEntry): String =
    "${entry.contactId}|${entry.name}|${entry.number}|${entry.photoUri.orEmpty()}"

private fun decode(raw: String): SpeedDialEntry? {
    val parts = raw.split("|", limit = 4)
    if (parts.size != 4) return null
    return SpeedDialEntry(
        contactId = parts[0],
        name = parts[1],
        number = parts[2],
        photoUri = parts[3].ifEmpty { null }
    )
}

/** Every dial-pad key (0-9, *, #) is assignable to a contact, matching the reference app's own
 * full dialpad-style grid. SharedPreferences-backed, same lightweight pattern as the reference
 * app's own SpeedDialPrefs — this is a tiny 12-entry map, not worth a Room table. */
object SpeedDialPreferences {
    val entries = mutableStateOf<Map<String, SpeedDialEntry>>(emptyMap())

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        entries.value = speedDialKeys.mapNotNull { key ->
            prefs.getString("$KEY_PREFIX$key", null)?.let { raw -> decode(raw)?.let { key to it } }
        }.toMap()
    }

    /** [entry] null clears that key's assignment. */
    fun setKey(context: Context, key: String, entry: SpeedDialEntry?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (entry == null) remove("$KEY_PREFIX$key") else putString("$KEY_PREFIX$key", encode(entry))
        }.apply()
        entries.value = if (entry == null) entries.value - key else entries.value + (key to entry)
    }
}
