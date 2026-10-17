package com.phone.contact.calls.dialer.data

data class Contact(
    val id: String,
    val name: String,
    val number: String,
    val photoUri: String? = null,
    val isStarred: Boolean = false,
    val lastTimeContacted: Long = 0L
)
