package com.phone.contacts.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.phone.contacts.ui.features.call.CallActivity
import com.phone.contacts.util.OnboardingPreferences

/**
 * Shows an App Open ad when the app returns to the foreground after being backgrounded (user
 * switched to another app / Home, then came back) — separate from any cold-start (kill+reopen)
 * cadence, since a plain background→foreground return doesn't re-enter the Language/onboarding
 * screen. Only every 2nd such return shows the ad, so quick app-switches (dialer, share sheet)
 * don't interrupt every single time. Matches contactapp's own trigger of the same name. Call
 * [init] once (e.g. from MainActivity) when config is ready.
 */
object AppOpenBackgroundReturnTrigger : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private var isInitialized = false
    private var isColdStart = true
    private var returnCount = 0
    private var currentActivity: Activity? = null
    private var adUnitId: String? = null

    /**
     * One-shot skip for the very next return-to-foreground — set this to true right before
     * intentionally sending the user to system Settings or another app for something unrelated
     * to "switching away from the app" (e.g. a file/photo picker, the overlay-permission
     * shortcut), so that return doesn't get treated as an app-switch-back and show an ad right
     * when they're just finishing an in-app action. Consumed (reset to false) the next time
     * onStart fires.
     */
    var isAdPaused = false

    fun init(application: Application, adUnitId: String) {
        this.adUnitId = adUnitId
        AppOpenAdManager.preload(application, adUnitId)
        if (isInitialized) return
        isInitialized = true
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (isColdStart) {
            // The app's own first launch — not a "returned from background" moment.
            isColdStart = false
            return
        }
        if (isAdPaused) {
            isAdPaused = false
            return
        }
        returnCount++
        if (returnCount % 2 != 0) return

        val activity = currentActivity ?: return
        val unitId = adUnitId ?: return

        // Don't interrupt the first-run language picker.
        if (!OnboardingPreferences.isLanguageSelected(activity)) return

        // Don't interrupt an in-progress call screen or the after-call screen with a full-screen ad.
        if (activity is CallActivity || activity is com.phone.contacts.ui.features.aftercall.AfterCallActivity) return

        if (AppOpenAdManager.isReady()) {
            AppOpenAdManager.show(activity, unitId) {}
        }
    }

    // Application.ActivityLifecycleCallbacks — only currentActivity tracking is needed here.
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) { currentActivity = activity }
    override fun onActivityResumed(activity: Activity) { currentActivity = activity }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
