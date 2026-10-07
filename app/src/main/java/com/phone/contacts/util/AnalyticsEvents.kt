package com.phone.contacts.util

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

    // Screens / sources
    const val SCREEN_CALL = "CallUtils"
    const val SCREEN_ADD_CONTACT = "AddContact"
    const val SCREEN_CONTACTS = "Contacts"
    const val SCREEN_CONTACT_DETAIL = "ContactDetail"
    const val SCREEN_APP_UPDATE = "AppUpdatePrompt"
    const val SCREEN_REVIEW = "InAppReviewHelper"

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

    // Params
    const val PARAM_RESULT = "result"
    const val PARAM_TYPE = "type"
    const val PARAM_COUNT = "count"
    const val PARAM_STARRED = "starred"

    // User properties
    const val USER_IS_DEFAULT_DIALER = "is_default_dialer"
    const val USER_APP_THEME = "app_theme"
    const val USER_APP_LANGUAGE = "app_language"
}
