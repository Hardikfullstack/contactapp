package com.phone.contacts.ui.features.onboarding

data class Language(
    val code: String,
    val flagEmoji: String,
    val name: String,
    val nativeName: String
)

/**
 * Supported languages, in display order. [code] is a BCP-47 language tag consumed directly by
 * AppCompatDelegate.setApplicationLocales(). Keep this list in sync with res/values-<code>/ when
 * translations are added — an entry here with no matching values folder just falls back to English.
 *
 * Indonesian is the one exception: its [code] here is the correct modern BCP-47 tag "id", but its
 * resource folder is res/values-in/, not res/values-id/ - java.util.Locale has always normalized
 * language "id" to the old ISO 639 code "in" internally, so Android's runtime resource lookup for
 * a Locale built from "id" resolves against an "in"-qualified folder, not an "id"-qualified one. A
 * values-id/ folder present alongside is silently never matched (falls back to English), which is
 * exactly what happened here before this was caught.
 */
val supportedLanguages: List<Language> = listOf(
    Language("en", "🇺🇸", "English", "English"),
    Language("hi", "🇮🇳", "Hindi", "हिन्दी"),
    Language("ar", "🇸🇦", "Arabic", "العربية"),
    Language("fr", "🇫🇷", "French", "Français"),
    Language("de", "🇩🇪", "German", "Deutsch"),
    Language("id", "🇮🇩", "Indonesian", "Bahasa Indonesia"),
    Language("it", "🇮🇹", "Italian", "Italiano"),
    Language("es", "🇪🇸", "Spanish", "Español")
)

fun currentAppLanguageCode(): String =
    androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
