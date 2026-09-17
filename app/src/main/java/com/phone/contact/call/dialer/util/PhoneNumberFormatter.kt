package com.phone.contact.call.dialer.util

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Formats a phone number for on-screen display WITH its country code (e.g. "+91 98765 43210")
 * instead of showing it however it happened to arrive — Telecom/CallLog frequently hand over a
 * bare local number with no country code at all, so a notification showing just raw digits gives
 * no way to tell an unfamiliar-country call apart from a local one at a glance. Falls back to the
 * number as-is if formatting fails, rather than showing something broken.
 */
object PhoneNumberFormatter {
    fun withCountryCode(context: Context, number: String): String {
        if (number.isBlank()) return number
        return try {
            PhoneNumberUtils.formatNumber(number, deviceCountryIso(context)) ?: number
        } catch (e: Exception) {
            number
        }
    }

    private fun deviceCountryIso(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simCountry = telephonyManager?.simCountryIso?.takeIf { it.isNotBlank() }
        val networkCountry = telephonyManager?.networkCountryIso?.takeIf { it.isNotBlank() }
        return (simCountry ?: networkCountry ?: Locale.getDefault().country).uppercase(Locale.ROOT)
    }
}
