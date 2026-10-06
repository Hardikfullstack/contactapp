package com.phone.contacts.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A "call back later" reminder created from the After Call screen. [timeMillis] is the wall-clock
 * time the reminder should fire; [colorIndex] indexes the palette shown in the reminder form. */
@Entity(tableName = "call_reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val name: String,
    val note: String,
    val timeMillis: Long,
    val colorIndex: Int = 0
)
