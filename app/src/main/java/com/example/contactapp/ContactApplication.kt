package com.example.contactapp

import android.app.Application
import android.util.Log
import com.example.contactapp.service.FakeCallConnectionService
import com.example.contactapp.util.AfterCallState
import com.example.contactapp.util.AnalyticsManager
import com.example.contactapp.util.CrashlyticsManager
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ContactApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("FakeCallDebug", "ContactApplication.onCreate: process started")
        AnalyticsManager.init()
        CrashlyticsManager.init()
        AfterCallState.applyPersistedMode(this)

        // Off the main thread — MobileAds.initialize() does blocking I/O internally.
        Thread { MobileAds.initialize(this) }.start()

        // Off the main thread too — both do real IPC/disk I/O (Telecom registration, a CallLog
        // ContentProvider delete), and Application.onCreate() must fully finish before any other
        // component (including ContactCallService, which launches InCallActivity for a real call)
        // can run. On a cold start triggered by an actual incoming/outgoing call — the process was
        // killed in the background and Android is starting it fresh just to handle that call —
        // blocking here on the main thread was adding real, user-visible delay before the call
        // screen could appear. Neither of these needs to block anything: FakeCallReceiver only
        // needs the phone account by the time a scheduled fake-call alarm actually fires (well
        // after this), and the Call Log purge is just best-effort housekeeping.
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
