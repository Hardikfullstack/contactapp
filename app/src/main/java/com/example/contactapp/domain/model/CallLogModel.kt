package com.example.contactapp.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class CallLogItem(
    val id: Long,
    val name: String?,
    val number: String,
    val type: CallType,
    val timestamp: Long,
    val duration: String,
    val durationSeconds: Long = 0,
    val photoUri: String? = null,
    val isBlocked: Boolean = false
)

enum class CallType {
    INCOMING, OUTGOING, MISSED, REJECTED, VOICEMAIL, BLOCKED, SPAM, OTHER
}
