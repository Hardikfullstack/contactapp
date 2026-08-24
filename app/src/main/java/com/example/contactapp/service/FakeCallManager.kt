package com.example.contactapp.service

import android.telecom.Call
import android.telecom.DisconnectCause
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for a fake call's state — mirrors [CallManager]'s pattern (same
 * [android.telecom.Call] STATE_* vocabulary) instead of the ad-hoc local Compose state fake
 * calls used to rely on, so anything (the call screen, a notification) can observe it uniformly.
 *
 * Bridges the self-managed Telecom [FakeCallConnection] (driven by the system — e.g. a Bluetooth
 * headset button, Android Auto, or another telecom-aware surface) and the in-app UI in
 * FakeCallActivity (driven by the swipe-up gestures). Whichever side acts first is the source of
 * truth; the other side is kept in sync through this object instead of talking to each other
 * directly, since a Connection and an Activity don't otherwise share a lifecycle.
 */
object FakeCallManager {
    data class FakeCallInfo(val name: String, val number: String, val photoUri: String?)

    private val _callState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val callState: StateFlow<Int> = _callState.asStateFlow()

    private val _callInfo = MutableStateFlow<FakeCallInfo?>(null)
    val callInfo: StateFlow<FakeCallInfo?> = _callInfo.asStateFlow()

    private var connection: FakeCallConnection? = null
    private var preAnswered: Boolean = false

    /**
     * Called by FakeCallActivity.onCreate() — the confirmed-reliable delivery path — every time
     * a fresh fake call screen is shown. Always resets prior state first, so a stale
     * DISCONNECTED/connection reference from a previous call can never leak into a new one.
     */
    fun startRinging(info: FakeCallInfo) {
        connection = null
        preAnswered = false
        _callInfo.value = info
        _callState.value = Call.STATE_RINGING
    }

    /**
     * Called by FakeCallConnectionService when Telecom does deliver a self-managed connection.
     * Purely additive — only retains the handle needed to relay answer/reject to Telecom; does
     * not re-derive ringing state, since startRinging() already owns that.
     */
    fun attachConnection(connection: FakeCallConnection) {
        this.connection = connection
        if (preAnswered) {
            // The user already answered via the UI before this connection existed — sync
            // Telecom's side to match instead of leaving it stuck on the setRinging() the
            // connection service just called. _callState is already ACTIVE from answerFromUi()
            // below, so only the Telecom-facing connection needs to catch up here.
            preAnswered = false
            connection.setActive()
        }
    }

    /**
     * The user answered via the in-app swipe-up gesture (or a notification's Answer action) —
     * always updates the UI state immediately, regardless of whether Telecom's self-managed
     * connection has arrived yet. On some devices/OEMs that connection can be delayed by a lot,
     * or — confirmed via logging elsewhere in this app — never arrive at all; the UI must never
     * block on it, or the call screen appears to hang with the answer gesture having done
     * nothing. If the connection does show up later, attachConnection() above reconciles it.
     */
    fun answerFromUi() {
        _callState.value = Call.STATE_ACTIVE
        val existingConnection = connection
        if (existingConnection != null) {
            existingConnection.setActive()
        } else {
            preAnswered = true
        }
    }

    /** The user declined or ended via the in-app UI — reflect that on the telecom side. */
    fun endFromUi() {
        connection?.let {
            it.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            it.destroy()
        }
        connection = null
        _callState.value = Call.STATE_DISCONNECTED
    }

    /** Telecom answered the call on our behalf (e.g. a Bluetooth headset) — reflect it in the UI. */
    fun notifyAnsweredByTelecom() {
        _callState.value = Call.STATE_ACTIVE
    }

    /** Telecom ended the call on our behalf (e.g. a Bluetooth reject) — reflect it in the UI. */
    fun notifyEndedByTelecom() {
        connection = null
        _callState.value = Call.STATE_DISCONNECTED
    }
}
