package com.example.contactapp.util

/**
 * Set right before AppCompatDelegate.setApplicationLocales() — that call recreates every running
 * Activity to refresh string resources for the new language, and does so via an explicit
 * programmatic recreate rather than a normal configuration-change signal. That means it bypasses
 * android:configChanges="locale" on MainActivity entirely, so onCreate() has no built-in way to
 * tell "recreated because the language just changed" apart from a genuine cold launch — without
 * this flag it would replay the full splash/branding animation right after picking a language,
 * which is exactly the jarring flash this exists to prevent. Consumed (reset to false) the moment
 * MainActivity reads it, so it only ever suppresses the one splash it was set for.
 */
object LocaleChangeState {
    var skipNextSplash = false
}
