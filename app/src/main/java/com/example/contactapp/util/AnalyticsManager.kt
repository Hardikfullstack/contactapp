package com.example.contactapp.util

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics

/**
 * AnalyticsManager is a common utility to log events to Firebase Analytics. It provides a central
 * place to manage all event names and parameters.
 */
object AnalyticsManager {
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private const val enabled = true

    /** Initializes Firebase Analytics. Should be called once at app startup. */
    fun init() {
        if (!enabled) return
        if (firebaseAnalytics == null) {
            firebaseAnalytics = Firebase.analytics
        }
    }

    /** Logs a general event with optional parameters. */
    fun logEvent(eventName: String, params: Bundle? = null) {
        if (!enabled) return
        firebaseAnalytics?.logEvent(eventName, params)
    }

    fun logAdEvent(adType: String, placement: String, action: String) {
        val bundle = Bundle().apply {
            putString("ad_type", adType)
            putString("placement", placement)
            putString("action", action) // e.g., "request", "loaded", "failed_to_load", "shown", "failed_to_show", "dismissed", "clicked"
        }
        logEvent("ad_performance", bundle)
    }

    /** Sets a Firebase user property (segment users by it in the console) — e.g. app_theme,
     * app_language, is_default_dialer. Persists across sessions until changed again. */
    fun setUserProperty(name: String, value: String?) {
        if (!enabled) return
        firebaseAnalytics?.setUserProperty(name, value)
    }

    /** Logs Firebase's standard screen_view event — call on every screen/destination change. */
    fun logScreenView(screenName: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    /** Logs an event with a flexible payload map. */
    fun logEventWithAction(
        eventName: String,
        screenName: String,
        action: String,
        extraParams: Map<String, Any>? = null
    ) {
        val bundle = Bundle().apply {
            putString("screen", screenName)
            putString("action", action)
            extraParams?.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
        logEvent(eventName, bundle)
    }
}
