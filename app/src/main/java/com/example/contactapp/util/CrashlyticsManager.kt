package com.example.contactapp.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Central place for crash/non-fatal-exception reporting via Firebase Crashlytics. Once
 * initialized, uncaught (fatal) exceptions are captured automatically — no further wiring needed
 * for those. [recordException] is there for selectively reporting exceptions the app already
 * catches and swallows, so they show up in the dashboard instead of only Logcat.
 */
object CrashlyticsManager {
    private var instance: FirebaseCrashlytics? = null

    /** Enables crash collection. Should be called once at app startup. */
    fun init() {
        instance = FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(true)
        }
    }

    /** Reports a caught exception as a non-fatal, so it's visible in the Crashlytics dashboard. */
    fun recordException(throwable: Throwable) {
        instance?.recordException(throwable)
    }
}
