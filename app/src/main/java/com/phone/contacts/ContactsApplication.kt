package com.phone.contacts

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import com.phone.contacts.util.AnalyticsManager
import com.phone.contacts.util.CrashlyticsManager

class ContactsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AnalyticsManager.init()
        CrashlyticsManager.init()
        // Without this, the ads SDK never loads and every ad request silently does nothing — no
        // error, no callback, just never serves. Off the main thread — MobileAds.initialize() does
        // blocking I/O internally.
        Thread { MobileAds.initialize(this) }.start()

        // onStop fires once whenever the whole app (not just one Activity) leaves the foreground -
        // home button, app switch, screen lock. There's no callback for an actual uninstall (no app
        // code runs at that point), so this is the closest real signal for "the screen the user was
        // last on" without sending a Firebase call on every single navigation.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                AnalyticsManager.flushLastScreenProperty()
            }
        })
    }
}
