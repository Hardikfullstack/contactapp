package com.example.contactapp.util

import com.example.contactapp.domain.model.CallLogItem
import com.example.contactapp.domain.model.CallType

/**
 * Local, on-device spam/robocall heuristic — there's no external caller-ID/spam database here
 * (that would require a paid service like Truecaller's), so this only flags *patterns* typical
 * of robocalls and "ring-and-cut" scams: repeated calls from a number that isn't a saved
 * contact, where most of those calls were never actually talked through. It cannot identify a
 * specific caller as spam the way a crowd-sourced database could — it's a pattern match, not a
 * verified identification, and can have false positives (e.g. a new contact you haven't saved
 * yet who happens to call a few times while you're unavailable).
 */
object SpamDetector {
    private const val MIN_CALLS = 3
    private const val SHORT_CALL_THRESHOLD_SECONDS = 3L
    private const val SHORT_CALL_RATIO_THRESHOLD = 0.7

    fun normalizeNumber(number: String): String = number.replace(Regex("[^0-9]"), "").takeLast(10)

    /** Returns the normalized numbers of unknown callers matching the spam pattern. */
    fun detectSpamNumbers(logs: List<CallLogItem>): Set<String> {
        return logs
            .asSequence()
            .filter { it.name.isNullOrBlank() && (it.type == CallType.INCOMING || it.type == CallType.MISSED) }
            .groupBy { normalizeNumber(it.number) }
            .filter { (number, calls) -> number.isNotEmpty() && calls.size >= MIN_CALLS }
            .filter { (_, calls) ->
                val shortCalls = calls.count { it.durationSeconds <= SHORT_CALL_THRESHOLD_SECONDS }
                shortCalls.toDouble() / calls.size >= SHORT_CALL_RATIO_THRESHOLD
            }
            .keys
    }
}
