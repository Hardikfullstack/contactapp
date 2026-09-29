package com.phone.contacts.util

import android.content.Context

object DeviceUtils {
    /** True when the device is set to gesture navigation (no visible nav bar, edge swipes for
     * back/home) rather than 2/3-button navigation. */
    fun isGestureNavigationEnabled(context: Context): Boolean {
        return try {
            val resourceId = context.resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
            resourceId > 0 && context.resources.getInteger(resourceId) == 2
        } catch (e: Exception) {
            false
        }
    }
}
