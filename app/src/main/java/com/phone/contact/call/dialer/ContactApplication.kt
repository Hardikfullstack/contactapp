package com.phone.contact.call.dialer

import android.app.Application
import com.phone.contact.call.dialer.ads.AdConnectivityRetry
import com.phone.contact.call.dialer.service.FakeCallConnectionService
import com.phone.contact.call.dialer.util.AfterCallState
import com.phone.contact.call.dialer.util.AnalyticsManager
import com.phone.contact.call.dialer.util.CrashlyticsManager
import com.phone.contact.call.dialer.util.DefaultDialerState
import com.phone.contact.call.dialer.util.PreferenceManager
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ContactApplication : Application() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onCreate() {
        super.onCreate()
        AnalyticsManager.init()
        CrashlyticsManager.init()
        AfterCallState.applyPersistedMode(this)
        // As early as possible — the OS-level night-mode resolution (values-night/ resources,
        // including themes.xml's native windowBackground) needs to match this app's own in-app
        // Light/Dark/System theme choice from the very first frame, not just once Compose gets to
        // run ContactAppTheme moments later.
        preferenceManager.applyNightMode()
        AdConnectivityRetry.start(this)
        // As early as possible too — DefaultDialerState.isDefault starts out optimistically true
        // (no Context available at its own declaration to check for real), so without this, the
        // very first frame of Recents/MainNavigation after this app lost the default-dialer role
        // (e.g. the user set another app as default) briefly renders as if it still held it —
        // call log button enabled, bottom bar unlocked — before the screen's own LaunchedEffect
        // catches up and flips it to the locked "Set Default" state a moment later. A local
        // RoleManager check is fast/synchronous, safe to do here on the main thread.
        DefaultDialerState.refresh(this)

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
