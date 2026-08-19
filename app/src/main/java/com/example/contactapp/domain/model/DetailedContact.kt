package com.example.contactapp.domain.model

data class DetailedContact(
    val name: String,
    val phoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val addresses: List<String> = emptyList(),
    val organizations: List<String> = emptyList(),
    val jobTitle: String? = null,
    val department: String? = null,
    val notes: String? = null,
    val birthday: String? = null,
    val websites: List<String> = emptyList(),
    val nicknames: List<String> = emptyList(),
    val relations: List<String> = emptyList(),
    val ims: List<String> = emptyList()
)
