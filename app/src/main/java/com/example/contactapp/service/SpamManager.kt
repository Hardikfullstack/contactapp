package com.example.contactapp.service

import android.content.Context
import com.example.contactapp.domain.repository.CallLogRepository
import com.example.contactapp.util.PreferenceManager
import com.example.contactapp.util.SpamDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpamManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callLogRepository: CallLogRepository,
    private val preferenceManager: PreferenceManager
) {

    /**
     * Checks if a number should be flagged as spam based on:
     * 1. Manual user reporting (local blacklist from PreferenceManager).
     * 2. Automatic pattern detection (history analysis).
     * 3. Contact lookup (known contacts are never spam).
     */
    suspend fun checkSpamStatus(number: String): SpamStatus = withContext(Dispatchers.IO) {
        val cleanNumber = number.replace(Regex("[^0-9]"), "").takeLast(10)
        if (cleanNumber.isEmpty()) return@withContext SpamStatus.NONE

        // 1. Manual Blacklist (Unified with Recents/Settings)
        if (preferenceManager.getSpamNumbers().contains(cleanNumber)) {
            return@withContext SpamStatus.MANUAL_SPAM
        }

        // 2. Contact Lookup (Known contacts are never spam)
        val contactInfo = callLogRepository.resolveContactDisplayInfo(number)
        if (contactInfo.first != null) {
            return@withContext SpamStatus.CONTACT
        }

        // 3. Automatic Pattern Detection — only when Caller ID & Spam protection is on
        // (Settings > Privacy & Data); manual reports above still apply regardless.
        if (preferenceManager.isCallerIdSpamProtectionEnabled()) {
            // Note: For real-time check, we'd ideally have an index, but history scan is fine for now.
            val logs = callLogRepository.fetchCallLogsForAnalytics()
            val detectedSpam = SpamDetector.detectSpamNumbers(logs)
            if (detectedSpam.contains(cleanNumber)) {
                return@withContext SpamStatus.DETECTED_SPAM
            }
        }

        SpamStatus.NONE
    }

    fun reportSpam(number: String, isSpam: Boolean) {
        val cleanNumber = number.replace(Regex("[^0-9]"), "").takeLast(10)
        if (cleanNumber.isNotEmpty()) {
            val current = preferenceManager.getSpamNumbers().toMutableSet()
            if (isSpam) current.add(cleanNumber) else current.remove(cleanNumber)
            preferenceManager.setSpamNumbers(current)
        }
    }

    enum class SpamStatus {
        NONE, CONTACT, DETECTED_SPAM, MANUAL_SPAM;
        
        fun isSpam() = this == DETECTED_SPAM || this == MANUAL_SPAM
    }
}
