package com.example.contactapp.domain.repository

import com.example.contactapp.domain.model.CallLogItem
import kotlinx.coroutines.flow.Flow

interface CallLogRepository {
    fun fetchCallLogs(): Flow<List<CallLogItem>>
    fun fetchCallHistory(phoneNumber: String): Flow<List<CallLogItem>>

    /**
     * Lean one-shot fetch for aggregate stats: skips the per-row contact-info
     * ContentResolver lookup that [fetchCallLogs] does for every row (fine for a
     * short recent list, far too slow across a whole call history), using only the
     * CACHED_NAME the call log already stores. Callers that need a display name/photo
     * for a specific number (e.g. top callers) should resolve it separately via
     * [resolveContactDisplayInfo] for just that handful of numbers.
     */
    suspend fun fetchCallLogsForAnalytics(): List<CallLogItem>

    suspend fun resolveContactDisplayInfo(number: String): Pair<String?, String?>

    fun deleteCallLog(id: Long)
    suspend fun deleteCallLogsByNumber(number: String)
    suspend fun deleteCallLogsByNumbers(numbers: List<String>)
    suspend fun blockNumber(number: String, block: Boolean)
    fun isBlocked(number: String): Flow<Boolean>
    fun getBlockedNumbers(): Flow<List<String>>
}
