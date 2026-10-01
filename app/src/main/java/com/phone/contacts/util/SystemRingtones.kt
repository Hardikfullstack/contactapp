package com.phone.contacts.util

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SystemRingtoneItem(val title: String, val uri: Uri)

/** Every ringtone the device ships with / has registered with [RingtoneManager] — the same stock
 * tones the system's own ringtone picker lists, matching how contactapp's own Set Ringtone screen
 * enumerates them (no MediaStore scan, no extra permission needed). */
suspend fun querySystemRingtones(context: Context): List<SystemRingtoneItem> = withContext(Dispatchers.IO) {
    val results = mutableListOf<SystemRingtoneItem>()
    try {
        val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_RINGTONE) }
        val cursor = manager.cursor
        while (cursor.moveToNext()) {
            val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            val uri = manager.getRingtoneUri(cursor.position)
            results.add(SystemRingtoneItem(title, uri))
        }
    } catch (e: Exception) {
        // Fall through with whatever was gathered so far.
    }
    results
}
