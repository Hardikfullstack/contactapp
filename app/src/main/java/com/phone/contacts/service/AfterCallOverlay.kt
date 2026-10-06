package com.phone.contacts.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * A fully transparent 1px SYSTEM_ALERT_WINDOW view, added the moment a call ends and removed right
 * before AfterCallActivity starts. Same idea as contactapp's AfterCallMiniOverlay: holding a real
 * overlay window keeps the process out of the freeze/kill window some OEMs apply right after a call.
 */
object AfterCallOverlay {
    private const val TAG = "AfterCallOverlay"
    private var view: View? = null

    fun show(context: Context) {
        hide()
        if (!Settings.canDrawOverlays(context)) return
        val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val overlay = View(context.applicationContext)
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            windowManager.addView(overlay, params)
            view = overlay
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        try {
            (current.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.removeView(current)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove overlay", e)
        }
    }
}
