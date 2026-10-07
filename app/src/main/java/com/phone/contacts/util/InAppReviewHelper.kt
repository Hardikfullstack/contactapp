package com.phone.contacts.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.play.core.review.ReviewManagerFactory

/** Play In-App Review for the 4-5 star rating path. Google quotas this sheet, so it's only
 * attempted once per install — after that, and whenever the sheet can't be shown, the user goes
 * straight to the Play Store listing (same behavior as the reference app). */
object InAppReviewHelper {
    private const val PREFS = "contact_app_prefs"
    private const val KEY_ATTEMPTED = "in_app_review_attempted"

    fun requestOrOpenStore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val activity = context.findActivity()
        if (prefs.getBoolean(KEY_ATTEMPTED, false) || activity == null) {
            RateUsHelper.openPlayStoreListing(context)
            return
        }
        prefs.edit().putBoolean(KEY_ATTEMPTED, true).apply()
        AnalyticsManager.logEventWithAction(AnalyticsEvents.IN_APP_REVIEW, AnalyticsEvents.SCREEN_REVIEW, AnalyticsEvents.ACTION_REQUESTED)

        val reviewManager = ReviewManagerFactory.create(context)
        reviewManager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Play doesn't reveal whether the user actually rated, so "flow launched" counts as done.
                reviewManager.launchReviewFlow(activity, task.result)
            } else {
                RateUsHelper.openPlayStoreListing(context)
            }
        }
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}
