package com.example.contactapp.ads

import android.content.Context

enum class ColdStartAdType { NONE, APP_OPEN, INTERSTITIAL }

/**
 * Tracks how many times the app has been cold-started **after initial setup** (fresh process,
 * not background→foreground within the same session) and decides which ad — if any — Splash
 * should show that time. The true "very first ever open" (fresh install, finishing onboarding)
 * never reaches this counter at all — completing setup navigates straight to Recents without
 * passing back through Splash — so this counter's count=1 already represents the user's first
 * *return* to the app (kill+reopen), which is exactly where the cadence should start:
 *
 * - 1st, 2nd kill+reopen: APP_OPEN.
 * - 3rd kill+reopen (and every 3rd after): INTERSTITIAL.
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

    /** Reads the current stored count without incrementing it — for callers that just need to
     * know "which open is this?" without advancing the cadence. */
    fun currentCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_OPEN_COUNT, 0)
    }

    fun adTypeFor(openCount: Int): ColdStartAdType {
        if (openCount <= 0) return ColdStartAdType.NONE
        val offset = (openCount - 1) % 3
        return if (offset == 2) ColdStartAdType.INTERSTITIAL else ColdStartAdType.APP_OPEN
    }
}
