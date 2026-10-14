package com.phone.contacts.ads

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
 */
object AppOpenCounter {
    private const val PREFS_NAME = "ad_open_prefs"
    private const val KEY_OPEN_COUNT = "cold_start_open_count"

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

    fun shouldShowAppOpenAd(openCount: Int): Boolean = openCount % 3 == 0
}
