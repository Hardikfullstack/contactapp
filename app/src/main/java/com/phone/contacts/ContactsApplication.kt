package com.phone.contacts

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import com.phone.contacts.ads.AdConnectivityRetry
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
        // Without this, AdConnectivityRetry's NetworkCallback is never registered, so its `tick`
        // never bumps and a native ad that failed to load while offline sits failed forever
        // instead of retrying the moment connectivity comes back.
        AdConnectivityRetry.start(this)

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
