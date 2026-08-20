package com.example.contactapp.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class CallReminder(
    val id: Long,
    val contactName: String,
    val contactNumber: String,
    val photoUri: String?,
    val timeMillis: Long
)

object CallReminderCodec {
    private val gson = Gson()
    private val listType = object : TypeToken<List<CallReminder>>() {}.type

    fun encode(reminders: List<CallReminder>): String = gson.toJson(reminders)

    fun decode(raw: String?): List<CallReminder> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<CallReminder>>(raw, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
