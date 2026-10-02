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
    val durationSeconds: Long = 0L,
    // Only set for calls where the carrier itself withheld the number (CallLog's own
    // NUMBER_PRESENTATION) - "Private number"/"Payphone"/"Unknown", matching what every stock
    // dialer labels these as. Null for a normal, presentation-ALLOWED call, even if NUMBER itself
    // came back blank from the provider - that's a data gap, not a withheld-ID case, so it's left
    // for the UI to fall back to showing nothing rather than inventing a label for it.
    val presentationLabel: String? = null
)
