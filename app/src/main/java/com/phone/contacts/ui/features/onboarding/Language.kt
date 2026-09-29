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
