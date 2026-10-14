package com.phone.contacts.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.phone.contacts.R

/** Routing for the Rate Us dialog's star tap, matching the reference app's own split: 1-3 stars
 * go to a private feedback email instead of a public store review, 4-5 go straight to the Play
 * Store listing. Also tracks the automatic (3rd-cold-open) prompt shown from RecentsScreen, so
 * both that entry point and the manual Settings one stay in sync via the same prefs. */
object RateUsHelper {
    private const val PREFS_NAME = "rate_us_prefs"
    private const val KEY_INTERACTED = "rate_us_interacted"
    private const val KEY_AUTO_SHOWN = "rate_us_auto_shown"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once the user has tapped a star rating anywhere (Settings or the automatic prompt). */
    fun hasInteracted(context: Context): Boolean = prefs(context).getBoolean(KEY_INTERACTED, false)

    /** True once the automatic (3rd-cold-open) prompt has been shown - it only ever shows once. */
    fun hasAutoShown(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_SHOWN, false)

    fun markAutoShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_AUTO_SHOWN, true).apply()
        AnalyticsManager.logEventWithAction(AnalyticsEvents.RATE_US_SHOWN, AnalyticsEvents.SCREEN_RECENTS, AnalyticsEvents.ACTION_AUTO)
    }

    fun handleRating(context: Context, stars: Int) {
        prefs(context).edit().putBoolean(KEY_INTERACTED, true).apply()
        AnalyticsManager.logEventWithAction(AnalyticsEvents.RATE_US_RATING_GIVEN, AnalyticsEvents.SCREEN_RATE_US, stars.toString())
        if (stars in 1..3) {
            sendFeedbackEmail(context, stars)
        } else {
            InAppReviewHelper.requestOrOpenStore(context)
        }
    }

    /** Settings' own standalone "Feedback" row - same destination email as the low-star rating
     * path, just without a star count attached since the user came here directly, not via a rating. */
    fun openFeedbackEmail(context: Context) = sendFeedbackEmail(context, stars = null)

    private fun sendFeedbackEmail(context: Context, stars: Int?) {
        // Always English - read by the developer, not shown as app UI.
        val appName = context.getString(R.string.app_name)
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }
        val subject = if (stars != null) {
            "App Feedback: $appName (Rating: $stars Stars)"
        } else {
            "App Feedback: $appName"
        }
        val body = "Please write your feedback here...\n\n\n" +
            "---------------------------------\n" +
            "System Information (for developer):\n" +
            "- Device: ${android.os.Build.MODEL}\n" +
            "- Android: ${android.os.Build.VERSION.RELEASE}\n" +
            "- App: $appName\n" +
            "- Version: $versionName" +
            (stars?.let { "\n- Rating: $it stars" } ?: "")
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("parth@aavakar.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        // Skip the chooser when Gmail is present - smoother than making the user pick every time.
        emailIntent.setPackage("com.google.android.gm")
        try {
            context.startActivity(emailIntent)
        } catch (_: ActivityNotFoundException) {
            emailIntent.setPackage(null)
            try {
                context.startActivity(Intent.createChooser(emailIntent, context.getString(R.string.action_send_feedback)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, context.getString(R.string.toast_no_email_app), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openPlayStoreListing(context: Context) {
        val packageName = context.packageName
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            )
        }
    }
}
