package com.phone.contact.call.dialer.ads

import android.content.Context

enum class ColdStartAdType { NONE, APP_OPEN, INTERSTITIAL }

/**
 * Tracks how many times the app has been cold-started **after initial setup** (fresh process,
 * not background→foreground within the same session) and decides which ad — if any — Splash
 * should show that time. The true "very first ever open" (fresh install, finishing onboarding)
 * never reaches this counter at all — completing setup navigates straight to Recents without
 * passing back through Splash — so this counter's count=1 already represents the user's first
 * *return* to the app (kill+reopen), which is exactly where the cadence should start.
 *
 * Cadence (matches the Messages app's own "every 3rd cold start, not every one" pacing): only
 * every 3rd cold start is "due" at all — the 1st/2nd of each 3-cycle show nothing, so a user who
 * force-kills and reopens often doesn't see an ad every time. 3rd and 6th due cycles show App
 * Open; the 9th shows Interstitial instead. Pattern then repeats every 9 opens: 12th/15th → App
 * Open, 18th → Interstitial.
 *
 * A due attempt that couldn't actually show (no connectivity, or the ad just didn't finish
 * loading within the bounded wait) sets [markRetryPending] instead of silently giving up on that
 * cycle — the very next cold start (regardless of where it falls in the cadence) gets one more
 * attempt at the same ad type, cleared once an ad is actually shown. Without this, a single
 * offline moment on exactly the 3rd/6th/9th... open would skip that whole cycle's impression
 * until the next due cold start.
 */
object AppOpenCounter {
    private const val PREFS_NAME = "ad_open_prefs"
    private const val KEY_OPEN_COUNT = "cold_start_open_count"
    private const val KEY_RETRY_PENDING_TYPE = "app_open_ad_retry_pending_type"

    fun incrementAndGet(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = prefs.getInt(KEY_OPEN_COUNT, 0) + 1
        prefs.edit().putInt(KEY_OPEN_COUNT, next).apply()
        return next
    }

    /** Reads the current stored count without incrementing it — for callers that just need to
     * know "which open is this?" without advancing the cadence. */
    fun currentCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_OPEN_COUNT, 0)
    }

    fun adTypeFor(context: Context, openCount: Int): ColdStartAdType {
        retryPendingType(context)?.let { return it }

        if (openCount <= 0 || openCount % 3 != 0) return ColdStartAdType.NONE
        val dueCycle = openCount / 3
        val offset = (dueCycle - 1) % 3
        return if (offset == 2) ColdStartAdType.INTERSTITIAL else ColdStartAdType.APP_OPEN
    }

    private fun retryPendingType(context: Context): ColdStartAdType? {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RETRY_PENDING_TYPE, null) ?: return null
        return try {
            ColdStartAdType.valueOf(stored)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Call when a due attempt actually failed to show (ad never became ready) — the next cold
     * start retries the same ad [type] regardless of the cadence. */
    fun markRetryPending(context: Context, type: ColdStartAdType) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_RETRY_PENDING_TYPE, type.name).apply()
    }

    /** Call once an attempt actually shows the ad — clears any pending retry so the cadence goes
     * back to just the regular schedule. */
    fun clearRetryPending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_RETRY_PENDING_TYPE).apply()
    }
}
