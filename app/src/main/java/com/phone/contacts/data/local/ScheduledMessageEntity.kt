package com.phone.contacts.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** An SMS queued from the After Call screen to be sent at [timeMillis]. Removed once it has been sent. */
@Entity(tableName = "scheduled_messages")
data class ScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val name: String,
    val body: String,
    val timeMillis: Long
)
