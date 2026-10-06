package com.phone.contact.call.dialer.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.phone.contact.call.dialer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceAudioFile(val uri: Uri, val title: String, val durationMs: Long)

/** Scans every audio file on the device via MediaStore, so any local song or sound can be picked as a
 * ringtone. Needs READ_MEDIA_AUDIO (API 33+) or READ_EXTERNAL_STORAGE (older). Without it the query
 * returns nothing instead of throwing. */
suspend fun queryDeviceAudioFiles(context: Context): List<DeviceAudioFile> = withContext(Dispatchers.IO) {
    val results = mutableListOf<DeviceAudioFile>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.DURATION
    )
    try {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                results.add(
                    DeviceAudioFile(
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        title = cursor.getString(titleCol) ?: context.getString(R.string.unknown),
                        durationMs = cursor.getLong(durationCol)
                    )
                )
            }
        }
    } catch (_: Exception) {
        // Permission denied or query failed - return whatever was gathered so far.
    }
    results
}
