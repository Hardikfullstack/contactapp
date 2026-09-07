package com.example.contactapp.util

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.provider.BlockedNumberContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalBlockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val prefs: SharedPreferences = context.getSharedPreferences("local_blocked_numbers", Context.MODE_PRIVATE)

    // Signal for local preference changes
    private val _localChangeSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // A single trigger stream (system ContentObserver + this app's own local changes) driving
    // ONE atomic read of both sources per trigger. Combining two independently-emitting flows
    // (one per source) let a stale snapshot from whichever source hadn't updated yet briefly
    // resurrect a number the other source had already correctly dropped, causing the blocked
    // list to visibly flicker (item removed, then briefly reappears, then removed again).
    // Reading both sources together in one step means every emission is internally consistent.
    private val triggerFlow: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }

        contentResolver.registerContentObserver(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            true,
            observer
        )

        val localTriggerJob = launch {
            _localChangeSignal.collect { trySend(Unit) }
        }

        trySend(Unit)
        awaitClose {
            contentResolver.unregisterContentObserver(observer)
            localTriggerJob.cancel()
        }
    }.flowOn(Dispatchers.IO)

    val blockedNumbersFlow: Flow<List<String>> = triggerFlow
        .map { (querySystemBlockedNumbers() + queryLocalBlockedNumbers()).distinct() }
        .flowOn(Dispatchers.IO)
        .conflate()

    fun isBlocked(number: String): Flow<Boolean> {
        val cleanTarget = number.replace(Regex("[^0-9]"), "")
        val last10Target = cleanTarget.takeLast(10)
        
        return blockedNumbersFlow.map { list ->
            list.any { 
                val cleanItem = it.replace(Regex("[^0-9]"), "")
                val last10Item = cleanItem.takeLast(10)
                last10Item == last10Target && last10Target.isNotEmpty()
            }
        }
    }

    fun setLocalBlocked(number: String, blocked: Boolean) {
        val cleanNumber = number.replace(Regex("[^0-9]"), "")
        val last10 = cleanNumber.takeLast(10)

        val editor = prefs.edit()
        if (blocked) {
            editor.putBoolean(number, true)
            if (last10.isNotEmpty()) {
                editor.putBoolean(last10, true)
            }
        } else {
            editor.remove(number)
            if (last10.isNotEmpty()) {
                editor.remove(last10)
                // The number may have been blocked via a differently-formatted string than the one
                // passed in here (e.g. blocked from a raw call-log number, unblocked via a contact's
                // stored number) — exact-string removal alone leaves that original key behind, and
                // it still normalizes to the same last-10 digits, so the number keeps showing as
                // blocked no matter how many times it's "unblocked". Purge every stored key that
                // matches by digits, not just the one exact string given here.
                prefs.all.keys
                    .filter { it.replace(Regex("[^0-9]"), "").takeLast(10) == last10 }
                    .forEach { editor.remove(it) }
            }
        }
        editor.apply()
        _localChangeSignal.tryEmit(Unit)
    }

    fun getBlockedNumbers(): Flow<List<String>> = blockedNumbersFlow

    private fun queryLocalBlockedNumbers(): List<String> =
        prefs.all.filter { it.value == true }.map { it.key }

    private fun querySystemBlockedNumbers(): List<String> {
        val blockedList = mutableListOf<String>()
        try {
            val cursor = contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null,
                null,
                null
            )
            cursor?.use {
                val index = it.getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
                if (index != -1) {
                    while (it.moveToNext()) {
                        it.getString(index)?.let { num -> blockedList.add(num) }
                    }
                }
            }
        } catch (e: Exception) {
            // Access denied or system-wide blocking not supported
        }
        return blockedList
    }
}
