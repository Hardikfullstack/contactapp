package com.example.contactapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Entity(tableName = "after_call_reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val contactName: String? = null,
    val reminderTimeMillis: Long,
    val note: String = "",
    val colorIndex: Int = 0
) {
    val formattedTime: String
        get() {
            val dateTime = Instant.ofEpochMilli(reminderTimeMillis).atZone(ZoneId.systemDefault())
            return dateTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
        }

    /** [today]/[tomorrow] are passed in (rather than hardcoded) so this stays localized —
     * an entity has no Context/stringResource access of its own. */
    fun formattedDayLabel(today: String, tomorrow: String): String {
        val date = Instant.ofEpochMilli(reminderTimeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val now = java.time.LocalDate.now()
        return when (java.time.temporal.ChronoUnit.DAYS.between(now, date)) {
            0L -> today
            1L -> tomorrow
            else -> date.format(DateTimeFormatter.ofPattern("MMM dd", Locale.getDefault()))
        }
    }
}
