package com.phone.contacts.util

import com.phone.contacts.R
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceAudioFile(val uri: Uri, val title: String, val durationMs: Long)

/** Scans every audio file on the device via MediaStore — ringtones, voice notes, music, anything —
 * matching the reference app's own unfiltered "Custom" ringtone list. Requires READ_MEDIA_AUDIO
 * (API 33+) / READ_EXTERNAL_STORAGE (older) already granted; without it, the query simply returns
 * only (or none of) the app's own files rather than throwing. */
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
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                results.add(
                    DeviceAudioFile(
                        uri = uri,
                        title = cursor.getString(titleCol) ?: context.getString(R.string.unknown),
                        durationMs = cursor.getLong(durationCol)
                    )
                )
            }
        }
    } catch (e: Exception) {
        // Permission denied or query failed — fall through with whatever was gathered so far.
    }
    results
}

fun formatAudioDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
