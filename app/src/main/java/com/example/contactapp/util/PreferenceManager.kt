package com.example.contactapp.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Timestamp StateFlow to ensure every setting change is broadcast instantly
    private val _preferenceUpdateEvent = MutableStateFlow<Long>(System.currentTimeMillis())
    val preferencesFlow: Flow<Long> = _preferenceUpdateEvent.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    // Granular flows for specific preferences to prevent redundant updates
    val themeFlow: Flow<String> = _preferenceUpdateEvent
        .map { getAppTheme() }
        .distinctUntilChanged()
        .onStart { emit(getAppTheme()) }

    val sortOrderFlow: Flow<String> = _preferenceUpdateEvent
        .map { getContactSortOrder() }
        .distinctUntilChanged()
        .onStart { emit(getContactSortOrder()) }

    fun isOnboardingCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    fun getContactSortOrder(): String {
        return sharedPreferences.getString(KEY_CONTACT_SORT_ORDER, "First Name") ?: "First Name"
    }

    fun setContactSortOrder(order: String) {
        sharedPreferences.edit().putString(KEY_CONTACT_SORT_ORDER, order).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    fun getAppTheme(): String {
        return sharedPreferences.getString(KEY_APP_THEME, "System") ?: "System"
    }

    fun setAppTheme(theme: String) {
        sharedPreferences.edit().putString(KEY_APP_THEME, theme).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    fun isCallAnnouncerEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_CALL_ANNOUNCER_ENABLED, false)
    }

    fun setCallAnnouncerEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_CALL_ANNOUNCER_ENABLED, enabled).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    fun getAnnouncerRepeatCount(): Int {
        return sharedPreferences.getInt(KEY_ANNOUNCER_REPEAT_COUNT, 1)
    }

    fun setAnnouncerRepeatCount(count: Int) {
        sharedPreferences.edit().putInt(KEY_ANNOUNCER_REPEAT_COUNT, count).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    fun isFlashAlertEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_FLASH_ALERT_ENABLED, false)
    }

    fun setFlashAlertEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_FLASH_ALERT_ENABLED, enabled).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    fun getFlashBlinkSpeed(): Long {
        // Must match one of the three selectable speeds in FlashAlertScreen (800/400/150) —
        // Medium is the sensible default. A value that isn't one of those three leaves none of
        // the radio options selected until the user explicitly picks one.
        return sharedPreferences.getLong(KEY_FLASH_BLINK_SPEED, 400L)
    }

    fun setFlashBlinkSpeed(speed: Long) {
        sharedPreferences.edit().putLong(KEY_FLASH_BLINK_SPEED, speed).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    val wallpaperSelectionFlow: Flow<WallpaperSelection> = _preferenceUpdateEvent
        .map { getCallWallpaperSelection() }
        .distinctUntilChanged()
        .onStart { emit(getCallWallpaperSelection()) }

    fun getCallWallpaperSelection(): WallpaperSelection {
        val raw = sharedPreferences.getString(KEY_CALL_WALLPAPER_URI, null)
        val (selection, wasLegacy) = WallpaperCodec.decode(raw)
        if (wasLegacy) {
            sharedPreferences.edit().putString(KEY_CALL_WALLPAPER_URI, WallpaperCodec.encode(selection)).apply()
        }
        return selection
    }

    fun setCallWallpaperSelection(selection: WallpaperSelection) {
        sharedPreferences.edit().putString(KEY_CALL_WALLPAPER_URI, WallpaperCodec.encode(selection)).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    val callAccentColorFlow: Flow<String> = _preferenceUpdateEvent
        .map { getCallAccentColorId() }
        .distinctUntilChanged()
        .onStart { emit(getCallAccentColorId()) }

    fun getCallAccentColorId(): String {
        return sharedPreferences.getString(KEY_CALL_ACCENT_COLOR, "green") ?: "green"
    }

    fun setCallAccentColorId(id: String) {
        sharedPreferences.edit().putString(KEY_CALL_ACCENT_COLOR, id).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    val callButtonShapeFlow: Flow<String> = _preferenceUpdateEvent
        .map { getCallButtonShapeName() }
        .distinctUntilChanged()
        .onStart { emit(getCallButtonShapeName()) }

    fun getCallButtonShapeName(): String {
        return sharedPreferences.getString(KEY_CALL_BUTTON_SHAPE, CallButtonShape.CIRCLE.name) ?: CallButtonShape.CIRCLE.name
    }

    fun setCallButtonShape(shape: CallButtonShape) {
        sharedPreferences.edit().putString(KEY_CALL_BUTTON_SHAPE, shape.name).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    fun isAutoReplyEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_REPLY_ENABLED, false)
    }

    fun setAutoReplyEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_REPLY_ENABLED, enabled).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    fun getAutoReplyMessage(): String {
        return sharedPreferences.getString(KEY_AUTO_REPLY_MESSAGE, DEFAULT_AUTO_REPLY_MESSAGE) ?: DEFAULT_AUTO_REPLY_MESSAGE
    }

    fun setAutoReplyMessage(message: String) {
        sharedPreferences.edit().putString(KEY_AUTO_REPLY_MESSAGE, message).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    val spamNumbersFlow: Flow<Set<String>> = _preferenceUpdateEvent
        .map { getSpamNumbers() }
        .distinctUntilChanged()
        .onStart { emit(getSpamNumbers()) }

    /** Normalized (last-10-digit) numbers flagged by SpamDetector — persisted here so a ringing
     *  call can be classified with a cheap synchronous read instead of re-querying the call log. */
    fun getSpamNumbers(): Set<String> {
        return sharedPreferences.getStringSet(KEY_SPAM_NUMBERS, emptySet()) ?: emptySet()
    }

    fun setSpamNumbers(numbers: Set<String>) {
        // This is written from a live call-log flow that can re-fire on every content-observer
        // tick — skip the write (and the shared _preferenceUpdateEvent bump every other
        // preference flow in this file keys off) when the set hasn't actually changed.
        if (numbers == getSpamNumbers()) return
        sharedPreferences.edit().putStringSet(KEY_SPAM_NUMBERS, numbers).apply()
        _preferenceUpdateEvent.value = System.currentTimeMillis()
    }

    companion object {
        private const val PREF_NAME = "contact_app_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_CONTACT_SORT_ORDER = "contact_sort_order"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_CALL_ANNOUNCER_ENABLED = "call_announcer_enabled"
        private const val KEY_ANNOUNCER_REPEAT_COUNT = "announcer_repeat_count"
        private const val KEY_FLASH_ALERT_ENABLED = "flash_alert_enabled"
        private const val KEY_FLASH_BLINK_SPEED = "flash_blink_speed"
        private const val KEY_CALL_WALLPAPER_URI = "call_wallpaper_uri"
        private const val KEY_CALL_ACCENT_COLOR = "call_accent_color_id"
        private const val KEY_CALL_BUTTON_SHAPE = "call_button_shape"
        private const val KEY_AUTO_REPLY_ENABLED = "auto_reply_enabled"
        private const val KEY_AUTO_REPLY_MESSAGE = "auto_reply_message"
        private const val DEFAULT_AUTO_REPLY_MESSAGE = "Can't talk right now, I'll call you back."
        private const val KEY_SPAM_NUMBERS = "spam_numbers"
    }
}
