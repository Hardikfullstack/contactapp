package com.phone.contacts

import android.app.Application
import com.phone.contacts.util.AnalyticsManager
import com.phone.contacts.util.CrashlyticsManager

class ContactsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AnalyticsManager.init()
        CrashlyticsManager.init()
    }
}
