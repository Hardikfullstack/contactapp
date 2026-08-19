package com.example.contactapp.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Contact(
    val id: String,
    val name: String,
    val number: String,
    val isFavorite: Boolean = false,
    val photoUri: String? = null,
    val isBlocked: Boolean = false
)
