package com.example.contactapp.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.contactapp.ui.features.call.InCallActivity

/**
 * Shows an App Open ad when the app returns to the foreground after being backgrounded (user
 * switched to another app / Home, then came back) — separate from any cold-start (kill+reopen)
 * cadence, since a plain background→foreground return doesn't re-enter onboarding/splash. Only
 * every 2nd such return shows the ad, so quick app-switches (dialer, share sheet) don't interrupt
 * every single time. Call [init] once (e.g. from MainActivity) when config is ready.
 */
object AppOpenBackgroundReturnTrigger : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private var isInitialized = false
    private var isColdStart = true
    private var returnCount = 0
    private var currentActivity: Activity? = null
    private var adUnitId: String? = null

    /**
     * One-shot skip for the very next return-to-foreground — set this to true right before
     * intentionally sending the user to system Settings for something unrelated to "switching
     * away from the app" (e.g. a battery-optimization/autostart shortcut), so that return doesn't
     * get treated as an app-switch-back and show an ad right when they're just fixing a setting.
     * Consumed (reset to false) the next time onStart fires.
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

        // Don't interrupt onboarding/permission-granting — bouncing to system Settings for
        // battery/autostart/MIUI permissions and back would otherwise count as "returns" too.
        if (!isOnboardingCompleted(activity)) return

        // Don't interrupt an in-progress call screen with a full-screen ad.
        if (activity is InCallActivity) return

        if (AppOpenAdManager.isReady()) {
            AppOpenAdManager.show(activity, unitId) {}
        }
    }

    private fun isOnboardingCompleted(context: Context): Boolean {
        return context.getSharedPreferences("contact_app_prefs", Context.MODE_PRIVATE)
            .getBoolean("onboarding_completed", false)
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
