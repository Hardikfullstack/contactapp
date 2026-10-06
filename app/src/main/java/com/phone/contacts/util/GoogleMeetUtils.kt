package com.phone.contacts.util

import com.phone.contacts.R
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

private const val GOOGLE_MEET_PACKAGE = "com.google.android.apps.tachyon"
// Confirmed directly from the reference app's own compiled strings (found inside its classes.dex)
// - not a guess. "https://duo.google.com/invite/..." and "https://meet.google.com/new" both go
// through a generic web/sign-in path that shows Google's OS-level "choose an account" picker since
// neither is tied to a specific call; this custom action is Duo/Meet's own native per-number call
// API, which runs inside the app's already-signed-in session instead - no account picker - and
// shows Meet's own "can't receive calls - share a link instead" fallback if the number isn't
// reachable, matching the reference app's behavior exactly.
private const val MEET_CALL_ACTION = "com.google.android.apps.tachyon.action.CALL"

object GoogleMeetUtils {
    fun isInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(GOOGLE_MEET_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun launchMeetCall(context: Context, number: String) {
        try {
            context.startActivity(
                Intent(MEET_CALL_ACTION).apply {
                    setPackage(GOOGLE_MEET_PACKAGE)
                    data = Uri.fromParts("tel", number, null)
                }
            )
            return
        } catch (_: Exception) {
            // Fall through to the Play Store listing below.
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GOOGLE_MEET_PACKAGE"))
            )
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_google_meet_missing), Toast.LENGTH_SHORT).show()
        }
    }
}
