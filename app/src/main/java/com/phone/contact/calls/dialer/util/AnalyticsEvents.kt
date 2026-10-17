package com.phone.contact.calls.dialer.util

/** Single source of truth for analytics event names, actions and user-property keys, so the same
 * event isn't spelled two different ways across the codebase. Naming follows the sibling
 * contactapp: snake_case event name = feature, `screen`/`action` params = where and what happened. */
object AnalyticsEvents {
    // Events
    const val CALL_MADE = "call_made"
    const val CONTACT_CREATED = "contact_created"
    const val CONTACT_DELETED = "contact_deleted"
    const val CONTACT_UPDATED = "contact_updated"
    const val NUMBER_BLOCKED = "number_blocked"
    const val CALL_HISTORY_CLEARED = "call_history_cleared"
    const val APP_UPDATE_DIALOG = "app_update_dialog"
    const val IN_APP_REVIEW = "in_app_review"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val INCOMING_CALL = "incoming_call"
    const val DEFAULT_DIALER_PROMPT = "default_dialer_prompt"
    const val LANGUAGE_CHANGED = "language_changed"
    const val RATE_US_SHOWN = "rate_us_shown"
    const val RATE_US_RATING_GIVEN = "rate_us_rating_given"

    // Screens / sources
    const val SCREEN_CALL = "CallUtils"
    const val SCREEN_ADD_CONTACT = "AddContact"
    const val SCREEN_CONTACTS = "Contacts"
    const val SCREEN_CONTACT_DETAIL = "ContactDetail"
    const val SCREEN_APP_UPDATE = "AppUpdatePrompt"
    const val SCREEN_REVIEW = "InAppReviewHelper"
    const val SCREEN_CALL_MANAGER = "CallManager"
    const val SCREEN_RECENTS = "Recents"
    const val SCREEN_LANGUAGE = "Language"
    const val SCREEN_RATE_US = "RateUs"
    const val SCREEN_MAIN_NAVIGATION = "MainNavigation"

    // Actions
    const val ACTION_PLACED = "placed"
    const val ACTION_SUCCESS = "success"
    const val ACTION_SHOWN = "Shown"
    const val ACTION_UPDATE_ACCEPTED = "Update Accepted"
    const val ACTION_SOFT_UPDATE_DISMISSED = "Soft Update Cancelled/Dismissed"
    const val ACTION_FLOW_RESULT = "Immediate Update Flow Result"
    const val ACTION_REQUESTED = "requested"
    const val ACTION_FAVORITE_TOGGLED = "favorite_toggled"
    const val ACTION_RINGTONE_SET = "ringtone_set"
    const val ACTION_BLOCK = "block"
    const val ACTION_UNBLOCK = "unblock"
    const val ACTION_ANSWER = "answer"
    const val ACTION_DECLINE = "decline"
    const val ACTION_ANSWER_CALL_WAITING = "answer_call_waiting"
    const val ACTION_ANSWER_END_OTHER_CALL = "answer_end_other_call"
    const val ACTION_DECLINE_CALL_WAITING = "decline_call_waiting"
    const val ACTION_MERGE_CALLS = "merge_calls"
    const val ACTION_GRANTED = "granted"
    const val ACTION_DENIED = "denied"
    const val ACTION_FINISHED = "finished"
    const val ACTION_AUTO = "auto"

    // Params
    const val PARAM_RESULT = "result"
    const val PARAM_TYPE = "type"
    const val PARAM_COUNT = "count"
    const val PARAM_STARRED = "starred"
    const val PARAM_FIRST_RUN = "first_run"

    // User properties
    const val USER_IS_DEFAULT_DIALER = "is_default_dialer"
    const val USER_APP_THEME = "app_theme"
    const val USER_APP_LANGUAGE = "app_language"
    const val USER_LAST_SCREEN = "last_screen"
}
