package com.example.contactapp.ui.features.aftercall

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.contactapp.MainActivity
import com.example.contactapp.ads.AppOpenBackgroundReturnTrigger
import com.example.contactapp.ui.theme.ContactAppTheme
import com.example.contactapp.util.AfterCallNotificationHelper
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Full-screen quick-actions shown right after a call ends — launched by [AfterCallReceiver]
 * (in service/). AppCompatActivity (not plain ComponentActivity) so per-app locale
 * (AppCompatDelegate.setApplicationLocales, used by the Language screen) applies here too. */
@AndroidEntryPoint
class AfterCallActivity : AppCompatActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    companion object {
        /** True while this Activity is started (between onStart/onStop) — lets AfterCallReceiver
         * check, after a short delay, whether its startActivity() call actually resulted in this
         * screen showing, so it knows whether the full-screen-intent-notification fallback is
         * needed. */
        var isVisible = false

        /** Set right before AfterCallMoreTab's Send Mail/Calendar/Web actions launch an external
         * app — those also trigger onUserLeaveHint, but per product decision should leave this
         * screen alive behind them (so back from the launched app returns here). Add Contact and
         * Message are NOT covered by this — they should close After Call like Home/Recents does.
         * Consumed (reset to false) on the very next leave-hint. */
        var suppressNextLeaveFinish = false
    }

    private var number by mutableStateOf("")
    private var callInfoLine1 by mutableStateOf("")
    private var callInfoLine2 by mutableStateOf("")
    private var contactName by mutableStateOf<String?>(null)

    // Every close path routes through finish() — overriding it here guarantees the flag is set
    // before MainActivity resumes underneath, so an App Open ad never sneaks in right as the
    // user returns from this screen.
    override fun finish() {
        AppOpenBackgroundReturnTrigger.isAdPaused = true
        super.finish()
    }

    override fun onStart() {
        super.onStart()
        isVisible = true
    }

    override fun onStop() {
        super.onStop()
        isVisible = false
    }

    // Home/Recents pressed while this screen is showing should close it, matching how other
    // apps' after-call screens behave — it shouldn't linger in the background/task switcher.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (suppressNextLeaveFinish) {
            suppressNextLeaveFinish = false
        } else if (!isFinishing) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (!updateFromIntent(intent)) return

        setContent {
            val appTheme by preferenceManager.themeFlow.collectAsState(initial = preferenceManager.getAppTheme())
            val systemInDark = isSystemInDarkTheme()
            val isDarkTheme = remember(appTheme, systemInDark) {
                when (appTheme) {
                    "Dark" -> true
                    "Light" -> false
                    else -> systemInDark
                }
            }
            SideEffect {
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
            }
            ContactAppTheme(darkTheme = isDarkTheme) {
                AfterCallScreen(
                    number = number,
                    displayName = contactName,
                    isKnownContact = contactName != null,
                    callInfoLine1 = callInfoLine1,
                    callInfoLine2 = callInfoLine2,
                    onFinish = { finish() },
                    onOpenMainApp = { tab ->
                        // skip_splash bypasses MainActivity's own cold-start SplashScreen (and
                        // the App Open ad it can show) — the user already just came from this
                        // app's own UI, showing a splash/ad on top of that would be jarring.
                        // finish() (below) sets isAdPaused too, so the separate background-return
                        // App Open trigger doesn't fire either.
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("skip_splash", true)
                            putExtra("open_tab", tab)
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    // A second call ending while this screen is still showing for a previous one reuses this same
    // instance (FLAG_ACTIVITY_SINGLE_TOP) instead of creating a new one.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateFromIntent(intent)
    }

    private fun updateFromIntent(intent: Intent): Boolean {
        val newNumber = intent.getStringExtra("number")
        if (newNumber == null) {
            finish()
            return false
        }
        number = newNumber
        callInfoLine1 = intent.getStringExtra("callInfoLine1") ?: ""
        callInfoLine2 = intent.getStringExtra("callInfoLine2") ?: ""
        contactName = intent.getStringExtra("contactName")
        AfterCallNotificationHelper.cancelAfterCallFullScreenNotification(this)
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
