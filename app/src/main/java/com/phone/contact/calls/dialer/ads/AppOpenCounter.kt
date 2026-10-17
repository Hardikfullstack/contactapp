package com.phone.contact.calls.dialer.ads

import android.content.Context

/**
 * Tracks how many times the app has been cold-started **after onboarding was already done**
 * (fresh process, not background→foreground within the same session - that's
 * [AppOpenBackgroundReturnTrigger]'s own separate cadence). The true first-ever open (fresh
 * install, still inside onboarding) never increments this at all - MainActivity only calls
 * [incrementAndGet] when the user had already finished onboarding before this cold start - so
 * count=1 already represents the user's first return (kill+reopen) after setup, not the very
 * first install.
 *
 * Cadence: the App Open ad shows only on every 3rd cold start (3, 6, 9, ...) - the 1st and 2nd of
 * each cycle show nothing, so a user who force-kills and reopens often doesn't see it every time.
 *
 * A due attempt that couldn't actually show (no connectivity, or the ad just didn't finish
 * loading within the bounded wait) sets [markRetryPending] instead of silently giving up on that
 * cycle - the very next cold start (regardless of where it falls in the %3 cadence) gets one more
 * attempt, cleared once an ad is actually shown. Without this, a single offline moment on exactly
 * the 3rd/6th/9th... open would skip that whole cycle's impression until the next multiple of 3.
 */
object AppOpenCounter {
    private const val PREFS_NAME = "ad_open_prefs"
    private const val KEY_OPEN_COUNT = "cold_start_open_count"
    private const val KEY_RETRY_PENDING = "app_open_ad_retry_pending"

    fun incrementAndGet(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = prefs.getInt(KEY_OPEN_COUNT, 0) + 1
        prefs.edit().putInt(KEY_OPEN_COUNT, next).apply()
        return next
    }

    /** Reads the current stored count without incrementing it - for callers (like the auto Rate Us
     * prompt) that just need to know "which open is this?" without advancing the cadence
     * themselves (incrementAndGet already ran once for this cold start, from MainActivity). */
    fun currentCount(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_OPEN_COUNT, 0)

    fun shouldShowAppOpenAd(context: Context, openCount: Int): Boolean =
        openCount % 3 == 0 || isRetryPending(context)

    private fun isRetryPending(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_RETRY_PENDING, false)

    /** Call when a due attempt actually failed to show (ad never became ready) - the next cold
     * start retries regardless of the %3 cadence. */
    fun markRetryPending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_RETRY_PENDING, true).apply()
    }

    /** Call once an attempt actually shows the ad - clears any pending retry so the cadence goes
     * back to just the regular %3 schedule. */
    fun clearRetryPending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_RETRY_PENDING, false).apply()
    }
}
