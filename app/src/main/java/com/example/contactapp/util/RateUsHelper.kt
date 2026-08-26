package com.example.contactapp.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.contactapp.BuildConfig
import com.example.contactapp.R

/**
 * Shared routing for the Rate Us dialog's star tap — used from both the manual entry point
 * (Settings) and the automatic one (shown once on the user's 2nd app open). Single source of
 * truth so both call sites stay in sync and [hasInteracted] is always accurate.
 */
object RateUsHelper {
    private const val PREFS_NAME = "contact_app_prefs"
    private const val KEY_INTERACTED = "rate_us_interacted"
    private const val KEY_AUTO_SHOWN = "rate_us_auto_shown"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once the user has tapped a star rating anywhere (Settings or the automatic prompt). */
    fun hasInteracted(context: Context): Boolean = prefs(context).getBoolean(KEY_INTERACTED, false)

    /** True once the automatic (2nd-app-open) prompt has been shown — it only ever shows once. */
    fun hasAutoShown(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_SHOWN, false)

    fun markAutoShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_AUTO_SHOWN, true).apply()
        AnalyticsManager.logEventWithAction("rate_us_shown", "Recents", "auto")
    }

    /** 1-3 stars -> feedback email; 4-5 stars -> in-app review (once per install) or Play Store. */
    fun handleRating(context: Context, stars: Int) {
        prefs(context).edit().putBoolean(KEY_INTERACTED, true).apply()
        AnalyticsManager.logEventWithAction("rate_us_rating_given", "RateUs", stars.toString())
        if (stars in 1..3) {
            sendFeedbackEmail(context, stars)
        } else {
            requestInAppReviewOrPlayStore(context)
        }
    }

    private fun sendFeedbackEmail(context: Context, stars: Int) {
        // Subject/body are always English — this is read by the developer, not shown as app UI,
        // so it shouldn't follow the user's app-language setting.
        val appName = context.getString(R.string.app_name)
        val subject = "App Feedback: $appName (Rating: $stars Stars)"
        val body = "Please write your feedback here...\n\n\n" +
            "---------------------------------\n" +
            "System Information (for developer):\n" +
            "- Device: ${android.os.Build.MODEL}\n" +
            "- Android: ${android.os.Build.VERSION.RELEASE}\n" +
            "- App: $appName\n" +
            "- Version: ${BuildConfig.VERSION_NAME}\n" +
            "- Rating: $stars stars"
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("parth@aavakar.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        // Skip the chooser when Gmail is present — smoother than making the user pick every time.
        emailIntent.setPackage("com.google.android.gm")
        try {
            context.startActivity(emailIntent)
        } catch (e: Exception) {
            emailIntent.setPackage(null)
            try {
                context.startActivity(Intent.createChooser(emailIntent, context.getString(R.string.help_feedback_label)))
            } catch (e2: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_no_email_app), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestInAppReviewOrPlayStore(context: Context) {
        // In-app review has an undisclosed Play quota — once used, a later request can silently
        // no-op (task succeeds but no sheet ever shows), leaving the button looking dead. So we
        // only ever attempt it once per install; every rating after that goes straight to Play
        // Store, which always visibly does something.
        val reviewPrefs = prefs(context)
        val alreadyAttempted = reviewPrefs.getBoolean("in_app_review_attempted", false)
        val activity = context.findActivity()
        if (alreadyAttempted || activity == null) {
            openPlayStoreListing(context)
        } else {
            reviewPrefs.edit().putBoolean("in_app_review_attempted", true).apply()
            val reviewManager = com.google.android.play.core.review.ReviewManagerFactory.create(context)
            val request = reviewManager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // launchReviewFlow's own completion doesn't reliably signal whether the user
                    // actually rated (Play policy), so we treat "flow launched" as success.
                    reviewManager.launchReviewFlow(activity, task.result)
                } else {
                    openPlayStoreListing(context)
                }
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

    private fun openPlayStoreListing(context: Context) {
        val uri = Uri.parse("market://details?id=${context.packageName}")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                )
            )
        }
    }
}
