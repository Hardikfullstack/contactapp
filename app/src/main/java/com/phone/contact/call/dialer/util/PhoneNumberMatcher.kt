package com.phone.contact.call.dialer.util

/**
 * Country-agnostic phone-number matching key — digits only, then the last [MIN_MATCH_DIGITS]
 * significant digits. [MIN_MATCH_DIGITS] (7) matches android.telephony.PhoneNumberUtils' own
 * MIN_MATCH constant, the minimum digit count Android itself treats as enough to call two numbers
 * "the same" regardless of country code, leading zero, or formatting differences.
 *
 * This app used to take the last 10 digits everywhere, which only reliably matches a 10-digit
 * numbering plan (India, the US, ...) — an 11-digit China number or a 9-digit Gulf-state number
 * could silently fail to match (or, with a country code still attached, match the wrong number).
 * Every place in this app that groups/compares phone numbers (blocking, spam detection,
 * favorites, call history, contact lookup) should key off this same function so a number matches
 * consistently everywhere, not just in one feature.
 */
object PhoneNumberMatcher {
    private const val MIN_MATCH_DIGITS = 7

    fun normalize(number: String?): String {
        if (number.isNullOrEmpty()) return ""
        return number.replace(Regex("[^0-9]"), "").takeLast(MIN_MATCH_DIGITS)
    }
}
