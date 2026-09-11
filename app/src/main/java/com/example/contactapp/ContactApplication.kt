package com.example.contactapp

import android.app.Application
import android.util.Log
import com.example.contactapp.ads.AdConnectivityRetry
import com.example.contactapp.service.FakeCallConnectionService
import com.example.contactapp.util.AfterCallState
import com.example.contactapp.util.AnalyticsManager
import com.example.contactapp.util.CrashlyticsManager
import com.example.contactapp.util.PreferenceManager
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ContactApplication : Application() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onCreate() {
        super.onCreate()
        Log.d("FakeCallDebug", "ContactApplication.onCreate: process started")
        AnalyticsManager.init()
        CrashlyticsManager.init()
        AfterCallState.applyPersistedMode(this)
        // As early as possible — the OS-level night-mode resolution (values-night/ resources,
        // including themes.xml's native windowBackground) needs to match this app's own in-app
        // Light/Dark/System theme choice from the very first frame, not just once Compose gets to
        // run ContactAppTheme moments later.
        preferenceManager.applyNightMode()
        AdConnectivityRetry.start(this)

        // Off the main thread — MobileAds.initialize() does blocking I/O internally.
        Thread { MobileAds.initialize(this) }.start()

        // Off the main thread — both do real IPC/disk I/O and would otherwise delay
        // Application.onCreate() finishing, which blocks the call screen appearing on a cold start.
        Thread {
            // Registering is idempotent and cheap — doing it on every process start (including
            // the one triggered by a scheduled fake-call alarm waking a killed app) guarantees the
            // account exists before FakeCallReceiver ever needs to place a call through it.
            FakeCallConnectionService.registerPhoneAccount(this)

            // Defense in depth: sweeps up any fake-call Call Log rows left behind by a crash or a
            // termination path from before this cleanup existed, so they never linger in Recents.
            FakeCallConnectionService.purgeCallLogEntries(this)
        }.start()
    }
}
