package com.phone.contacts

import android.app.Application
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
    }
}
