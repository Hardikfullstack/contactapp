package com.phone.contacts.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One pattern-based block rule from Blocking > Number Series - e.g. "block any number starting
 * with 5656" to silently reject a whole range (telemarketer prefix, spam series) in one go,
 * instead of blocking exact numbers one at a time. Enforced by
 * [com.phone.contacts.service.ContactsCallScreeningService] alongside the explicit Blocked
 * Numbers list. [matchType] is one of [NumberSeriesMatchType]'s names. */
@Entity(tableName = "number_series")
data class NumberSeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchType: String,
    val pattern: String,
    val addedAt: Long = System.currentTimeMillis()
)

enum class NumberSeriesMatchType { STARTS_WITH, CONTAINS, ENDS_WITH }
