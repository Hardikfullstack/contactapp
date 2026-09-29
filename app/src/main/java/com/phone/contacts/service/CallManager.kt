package com.phone.contacts.service

import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.phone.contacts.util.CallUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Single source of truth for the current call(s) — fed by [ContactsCallService] (the
 * InCallService Telecom actually binds to) and read by [com.phone.contacts.ui.features.call.CallScreen].
 * Tracks up to two calls (primary + a second/call-waiting leg) plus a merged conference's
 * children, mirroring contactapp's own `CallManager` but without its 1s capability-polling
 * fallback or notification-manager integration — this app has neither.
 */
object CallManager {
    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall: StateFlow<Call?> = _currentCall.asStateFlow()

    private val _callState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val callState: StateFlow<Int> = _callState.asStateFlow()

    /** SystemClock.elapsedRealtime() at the moment the call became STATE_ACTIVE — null until
     * then. The on-screen timer recomputes elapsed time from this anchor rather than counting up
     * from a local 0, so it stays correct even if the call screen is recreated mid-call. */
    private val _connectedAtElapsedRealtime = MutableStateFlow<Long?>(null)
    val connectedAtElapsedRealtime: StateFlow<Long?> = _connectedAtElapsedRealtime.asStateFlow()

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audioState.asStateFlow()

    // --- Second call / conference state ---

    private val _secondaryCall = MutableStateFlow<Call?>(null)
    val secondaryCall: StateFlow<Call?> = _secondaryCall.asStateFlow()

    private val _secondaryCallState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val secondaryCallState: StateFlow<Int> = _secondaryCallState.asStateFlow()

    private val _secondaryConnectedAtElapsedRealtime = MutableStateFlow<Long?>(null)
    val secondaryConnectedAtElapsedRealtime: StateFlow<Long?> = _secondaryConnectedAtElapsedRealtime.asStateFlow()

    /** Populated from the primary call's [Call.getChildren] once Telecom merges two calls in
     * place (rather than handing back a brand-new conference [Call] via onCallAdded) — either
     * path can happen depending on the ConnectionService/OEM telephony stack. */
    private val _conferenceChildren = MutableStateFlow<List<Call>>(emptyList())
    val conferenceChildren: StateFlow<List<Call>> = _conferenceChildren.asStateFlow()

    /** Whether the primary+secondary calls can be merged right now — checked via both
     * [Call.getConferenceableCalls] and the CAPABILITY_MERGE_CONFERENCE bit on either call, since
     * either signal alone is unreliable on some OEM stacks (same OR-fallback contactapp uses). */
    private val _canMerge = MutableStateFlow(false)
    val canMerge: StateFlow<Boolean> = _canMerge.asStateFlow()

    // Mute/audio-routing are InCallService-level operations, not Call-level — held weakly since
    // CallManager outlives any single call/service instance and must never keep it alive.
    private var serviceRef: WeakReference<InCallService>? = null

    private fun recomputeCanMerge() {
        val primary = _currentCall.value
        val secondary = _secondaryCall.value
        _canMerge.value = if (primary == null || secondary == null) {
            false
        } else {
            primary.conferenceableCalls.isNotEmpty() || secondary.conferenceableCalls.isNotEmpty() ||
                primary.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) ||
                secondary.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            _callState.value = state
            if (state == Call.STATE_ACTIVE && _connectedAtElapsedRealtime.value == null) {
                _connectedAtElapsedRealtime.value = android.os.SystemClock.elapsedRealtime()
            }
            recomputeCanMerge()
        }

        override fun onChildrenChanged(call: Call, children: MutableList<Call>) {
            _conferenceChildren.value = children.toList()
            if (children.isNotEmpty()) {
                // Telecom merged in place rather than handing back a new conference Call — the
                // separately-tracked secondary is now redundant/stale.
                clearSecondaryCall()
            }
        }

        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) {
            recomputeCanMerge()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            recomputeCanMerge()
        }
    }

    private val secondaryCallCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            _secondaryCallState.value = state
            if (state == Call.STATE_ACTIVE && _secondaryConnectedAtElapsedRealtime.value == null) {
                _secondaryConnectedAtElapsedRealtime.value = android.os.SystemClock.elapsedRealtime()
            }
            recomputeCanMerge()
        }

        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) {
            recomputeCanMerge()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            recomputeCanMerge()
        }
    }

    fun bindService(service: InCallService) {
        serviceRef = WeakReference(service)
    }

    fun unbindService(service: InCallService) {
        if (serviceRef?.get() === service) serviceRef = null
    }

    fun onCallAdded(call: Call) {
        val isConferenceCall = call.children.isNotEmpty() || call.details.can(Call.Details.CAPABILITY_MANAGE_CONFERENCE)
        when {
            isConferenceCall -> promoteToConference(call)
            _currentCall.value == null -> {
                _currentCall.value = call
                _callState.value = call.state
                _connectedAtElapsedRealtime.value = if (call.state == Call.STATE_ACTIVE) {
                    android.os.SystemClock.elapsedRealtime()
                } else {
                    null
                }
                call.registerCallback(callCallback)
            }
            else -> addSecondaryCall(call)
        }
        recomputeCanMerge()
    }

    private fun addSecondaryCall(call: Call) {
        _secondaryCall.value?.unregisterCallback(secondaryCallCallback)
        _secondaryCall.value = call
        _secondaryCallState.value = call.state
        _secondaryConnectedAtElapsedRealtime.value = if (call.state == Call.STATE_ACTIVE) {
            android.os.SystemClock.elapsedRealtime()
        } else {
            null
        }
        call.registerCallback(secondaryCallCallback)
    }

    private fun clearSecondaryCall() {
        _secondaryCall.value?.unregisterCallback(secondaryCallCallback)
        _secondaryCall.value = null
        _secondaryCallState.value = Call.STATE_DISCONNECTED
        _secondaryConnectedAtElapsedRealtime.value = null
        recomputeCanMerge()
    }

    /** Telecom handed back a distinct merged conference Call — make it the new primary, the two
     * original legs (already removed/absorbed by Telecom) no longer need separate tracking. */
    private fun promoteToConference(call: Call) {
        _currentCall.value?.unregisterCallback(callCallback)
        clearSecondaryCall()
        _currentCall.value = call
        _callState.value = call.state
        _conferenceChildren.value = call.children.toList()
        _connectedAtElapsedRealtime.value = if (call.state == Call.STATE_ACTIVE) {
            android.os.SystemClock.elapsedRealtime()
        } else {
            null
        }
        call.registerCallback(callCallback)
    }

    /** The primary ended while a secondary/call-waiting call was still up — that secondary
     * becomes the new primary instead of the screen going blank. */
    private fun promoteSecondaryToPrimary() {
        val secondary = _secondaryCall.value ?: return
        secondary.unregisterCallback(secondaryCallCallback)
        _secondaryCall.value = null
        _currentCall.value = secondary
        _callState.value = secondary.state
        _connectedAtElapsedRealtime.value = _secondaryConnectedAtElapsedRealtime.value
        _secondaryCallState.value = Call.STATE_DISCONNECTED
        _secondaryConnectedAtElapsedRealtime.value = null
        secondary.registerCallback(callCallback)
        recomputeCanMerge()
    }

    fun onCallRemoved(call: Call) {
        call.unregisterCallback(callCallback)
        call.unregisterCallback(secondaryCallCallback)
        when {
            call === _secondaryCall.value -> clearSecondaryCall()
            call === _currentCall.value -> {
                if (_secondaryCall.value != null) {
                    promoteSecondaryToPrimary()
                } else {
                    _currentCall.value = null
                    _callState.value = Call.STATE_DISCONNECTED
                    _connectedAtElapsedRealtime.value = null
                    _conferenceChildren.value = emptyList()
                }
            }
        }
        recomputeCanMerge()
    }

    fun onAudioStateChanged(state: CallAudioState) {
        _audioState.value = state
    }

    fun answer() {
        _currentCall.value?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Declines a still-ringing call. Telecom requires reject(), not disconnect(), while ringing
     *  for the network to be reliably signaled that the call was declined. */
    fun reject() {
        _currentCall.value?.reject(false, null)
    }

    fun disconnect() {
        _currentCall.value?.disconnect()
    }

    /** Hold is single-call (Call.hold()/unhold()) — unlike Add call/merge/swap, it needs no
     * second-call/conference plumbing, so it's real here even though multi-call isn't built yet. */
    fun toggleHold() {
        val call = _currentCall.value ?: return
        if (_callState.value == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    /** Answers the waiting second call — Telecom does not auto-hold the first call, so the
     * primary is explicitly held first if it's currently active. */
    fun answerSecondaryCall() {
        val secondary = _secondaryCall.value ?: return
        if (_callState.value == Call.STATE_ACTIVE) {
            _currentCall.value?.hold()
        }
        secondary.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun rejectSecondaryCall() {
        _secondaryCall.value?.reject(false, null)
    }

    /** No dedicated Telecom "swap" API — this is just holding whichever call is currently active
     * and unholding the other one, exactly as contactapp's own CallManager does it. */
    fun swapCalls() {
        val primary = _currentCall.value ?: return
        val secondary = _secondaryCall.value ?: return
        if (_callState.value == Call.STATE_ACTIVE) {
            primary.hold()
            secondary.unhold()
        } else if (_callState.value == Call.STATE_HOLDING) {
            secondary.hold()
            primary.unhold()
        }
    }

    /** Standard Telecom `Call.conference(Call)` — Telecom then either hands back a new merged
     * Call via onCallAdded, or populates this call's children in place; both are handled by the
     * callbacks above. */
    fun mergeCalls() {
        val primary = _currentCall.value ?: return
        val secondary = _secondaryCall.value ?: return
        primary.conference(secondary)
    }

    fun disconnectConferenceParticipant(call: Call) {
        call.disconnect()
    }

    /** Places a second call while the first is active — if the primary is STATE_ACTIVE, waits for
     * it to actually reach STATE_HOLDING (or disconnect) before dialing, since calling hold() and
     * placing the new call back-to-back can race ahead of the modem on some devices (same ordering
     * contactapp's InCallActivity deliberately uses for its own Add-call flow). */
    fun addCall(context: Context, number: String) {
        val primary = _currentCall.value
        if (primary == null || _callState.value != Call.STATE_ACTIVE) {
            CallUtils.placeCall(context, number)
            return
        }
        lateinit var waitForHold: Call.Callback
        waitForHold = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_HOLDING || state == Call.STATE_DISCONNECTED) {
                    call.unregisterCallback(waitForHold)
                    CallUtils.placeCall(context, number)
                }
            }
        }
        primary.registerCallback(waitForHold)
        primary.hold()
    }

    fun setMuted(shouldMute: Boolean) {
        serviceRef?.get()?.setMuted(shouldMute)
    }

    fun toggleMute() {
        setMuted(!(_audioState.value?.isMuted ?: false))
    }

    fun setAudioRoute(route: Int) {
        serviceRef?.get()?.setAudioRoute(route)
    }

    /** Cycles speaker on/off — routes to speaker, or back to whatever the earpiece/wired/BT
     *  default would be, without the caller needing to know Telecom's exact route bitmask. */
    fun toggleSpeaker() {
        val current = _audioState.value ?: return
        if (current.route == CallAudioState.ROUTE_SPEAKER) {
            val fallback = when {
                current.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0 -> CallAudioState.ROUTE_BLUETOOTH
                current.supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET != 0 -> CallAudioState.ROUTE_WIRED_HEADSET
                else -> CallAudioState.ROUTE_EARPIECE
            }
            setAudioRoute(fallback)
        } else {
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
        }
    }

    fun playDtmfTone(digit: Char) {
        _currentCall.value?.playDtmfTone(digit)
    }

    fun stopDtmfTone() {
        _currentCall.value?.stopDtmfTone()
    }
}
