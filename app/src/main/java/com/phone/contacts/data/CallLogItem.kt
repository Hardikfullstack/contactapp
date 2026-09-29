package com.phone.contacts.data

enum class CallType {
    INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, OTHER
}

data class CallLogItem(
    val id: Long,
    val name: String?,
    val number: String,
    val type: CallType,
    val timestamp: Long,
    val callCount: Int = 1,
    val durationSeconds: Long = 0L
)
