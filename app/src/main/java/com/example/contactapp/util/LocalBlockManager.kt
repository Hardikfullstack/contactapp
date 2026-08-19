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

    // Flow of local blocked numbers
    private val localBlockedFlow: Flow<List<String>> = _localChangeSignal
        .onStart { emit(Unit) }
        .map { 
            prefs.all.filter { it.value == true }.map { it.key }
        }

    // Live stream of blocked numbers from the system source of truth
    private val systemBlockedFlow: Flow<List<String>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(querySystemBlockedNumbers())
            }
        }

        contentResolver.registerContentObserver(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            true,
            observer
        )

        trySend(querySystemBlockedNumbers())
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    // Hybrid flow: combined system and local blocks
    val blockedNumbersFlow: Flow<List<String>> = combine(
        systemBlockedFlow,
        localBlockedFlow
    ) { system, local ->
        (system + local).distinct()
    }.conflate()

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
            }
        }
        editor.apply()
        _localChangeSignal.tryEmit(Unit)
    }

    fun getBlockedNumbers(): Flow<List<String>> = blockedNumbersFlow

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
