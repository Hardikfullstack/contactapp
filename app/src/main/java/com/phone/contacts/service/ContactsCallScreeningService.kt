package com.phone.contacts.service

import android.provider.BlockedNumberContract
import android.telecom.Call
import android.telecom.CallScreeningService
import com.phone.contacts.data.BlockRepository
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.data.local.AppDatabase
import com.phone.contacts.data.local.NumberSeriesMatchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Makes the Blocking screen's "Block calls from unidentified callers" toggle - previously
 * UI/preference-only, see [BlockRepository]'s own doc comment - and Number Series actually reject
 * those calls, the same way the reference app's own CallScreeningService does. Decompiling the
 * reference app's `SimpleCallScreeningService.onScreenCall` confirmed this exact decision tree:
 * - No number at all (private/withheld caller) -> always allowed, never blocked.
 * - The explicit Blocked Numbers list and Number Series patterns apply regardless of the toggle.
 * - The toggle on -> a number that isn't a saved contact is rejected; a saved contact never is,
 *   regardless of the toggle.
 */
class ContactsCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart

        if (number.isNullOrBlank()) {
            // No number to match against anything - let it ring normally, same as the reference
            // app (a withheld/private call is never auto-blocked here).
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        scope.launch {
            val isExplicitlyBlocked = try {
                BlockedNumberContract.isBlocked(this@ContactsCallScreeningService, number)
            } catch (_: Exception) {
                false
            }
            if (isExplicitlyBlocked) {
                respondToCall(callDetails, block())
                return@launch
            }

            val matchesNumberSeries = try {
                AppDatabase.getInstance(this@ContactsCallScreeningService).numberSeriesDao().getAllOnce().any { entry ->
                    when (NumberSeriesMatchType.valueOf(entry.matchType)) {
                        NumberSeriesMatchType.STARTS_WITH -> number.startsWith(entry.pattern)
                        NumberSeriesMatchType.CONTAINS -> number.contains(entry.pattern)
                        NumberSeriesMatchType.ENDS_WITH -> number.endsWith(entry.pattern)
                    }
                }
            } catch (_: Exception) {
                false
            }
            if (matchesNumberSeries) {
                respondToCall(callDetails, block())
                return@launch
            }

            if (BlockRepository.isBlockUnknownCallersEnabled(this@ContactsCallScreeningService)) {
                val isSavedContact = try {
                    ContactRepository.findContactByNumber(this@ContactsCallScreeningService, number) != null
                } catch (_: Exception) {
                    // Can't confirm either way (e.g. READ_CONTACTS revoked) - don't block on an
                    // unconfirmed guess, a missed real call is worse than an unscreened spam one.
                    true
                }
                if (!isSavedContact) {
                    respondToCall(callDetails, block())
                    return@launch
                }
            }

            respondToCall(callDetails, CallResponse.Builder().build())
        }
    }

    private fun block(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(true)
            .setSkipNotification(true)
            .build()
}
