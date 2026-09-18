package com.phone.contact.call.dialer.service

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.phone.contact.call.dialer.util.AnalyticsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

object CallManager {
    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall = _currentCall.asStateFlow()

    private val _callState = MutableStateFlow<Int>(Call.STATE_DISCONNECTED)
    val callState = _callState.asStateFlow()

    // Telecom often grants CAPABILITY_HOLD via onDetailsChanged, not a state change — exposing the
    // actual computed booleans as their own StateFlow (instead of a side-channel "version" counter
    // callers were expected to collect-but-ignore just to force a recompose) means whichever
    // composable collects .value directly gets recomposed the moment Telecom updates the
    // capability, regardless of which composition scope that collection happens to sit in.
    /** Whether the current call can be put on hold — false on some carriers/SIMs even for an
     *  otherwise-normal active call, so the Hold button should hide/disable rather than assume. */
    private val _canHold = MutableStateFlow(false)
    val canHold = _canHold.asStateFlow()

    /** Whether "Add call" should be offered right now — requires an existing call that can be
     *  held (so the second call can be placed) and no second call already in progress. Whether
     *  a second call can actually be *placed and answered* still depends on carrier/SIM support
     *  for multi-party calls. */
    private val _canAddCall = MutableStateFlow(false)
    val canAddCall = _canAddCall.asStateFlow()

    /** Whether the two simultaneous calls can be merged into a conference — only meaningful once
     *  a secondary call already exists, and still depends on carrier/SIM conference support. */
    private val _canMerge = MutableStateFlow(false)
    val canMerge = _canMerge.asStateFlow()

    /** Public re-check, called on a short poll from the UI while two calls are up — capability
     *  flags like CAPABILITY_MERGE_CONFERENCE are supposed to trigger onDetailsChanged when they
     *  change, but that event has been observed to not always fire promptly (or at all) on some
     *  OEM/carrier telephony stacks, which would otherwise leave Merge stuck disabled even once
     *  the network genuinely supports it. */
    fun refreshCapabilities() = recomputeCapabilities()

    private fun recomputeCapabilities() {
        val call = _currentCall.value
        val secondary = _secondaryCall.value
        _canHold.value = call?.details?.can(Call.Details.CAPABILITY_HOLD) == true
        _canAddCall.value = call != null && secondary == null &&
            call.details.can(Call.Details.CAPABILITY_HOLD)
        // Call.conferenceableCalls (with its own dedicated onConferenceableCallsChanged callback)
        // is the API AOSP's own Dialer checks for "can these two calls merge" — CAPABILITY_
        // MERGE_CONFERENCE is a separate, unreliable signal that some telephony stacks never set
        // even when a real network-side merge is genuinely available, which left Merge stuck
        // disabled despite both calls being fully connected. Kept as a fallback OR, not a
        // replacement, since the reverse gap (capability set, conferenceableCalls empty) is
        // possible on other stacks.
        _canMerge.value = call != null && secondary != null &&
            (call.conferenceableCalls.contains(secondary) ||
                secondary.conferenceableCalls.contains(call) ||
                call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) ||
                secondary.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE))
    }

    private val _isSpam = MutableStateFlow(false)
    val isSpam = _isSpam.asStateFlow()

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    /** Current mute/speaker/route state, pushed by ContactCallService.onCallAudioStateChanged —
     *  null until the InCallService has reported at least one state (e.g. before a call exists). */
    val audioState = _audioState.asStateFlow()

    // Mute/audio-routing are InCallService-level operations, not Call-level, so CallManager needs
    // a live reference to the bound service to carry them out — held weakly since CallManager
    // outlives any single call/service instance and must never be the thing keeping it alive.
    private var serviceRef: WeakReference<InCallService>? = null

    /** Populated once the current call becomes a merged conference — Telecom reports the
     *  individual participants as this call's children rather than as a separate top-level call,
     *  which is also the signal used to detect that a merge actually went through. */
    private val _conferenceChildren = MutableStateFlow<List<Call>>(emptyList())
    /** Empty whenever there's no conference — callers derive "is this a conference" from
     *  `.isNotEmpty()` directly rather than a separate boolean flow. */
    val conferenceChildren = _conferenceChildren.asStateFlow()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            _callState.value = state
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            recomputeCapabilities()
        }

        override fun onChildrenChanged(call: Call, children: MutableList<Call>) {
            _conferenceChildren.value = children.toList()
            // Some ConnectionServices merge by populating the EXISTING primary call's children
            // directly instead of delivering a brand-new "conference" Call via onCallAdded — in
            // that path promoteToConference() never runs, so without this, the just-merged
            // secondary call would keep sitting in _secondaryCall as a stale, separately-tracked
            // call alongside the new conference display.
            if (children.isNotEmpty() && _secondaryCall.value != null) {
                _secondaryCall.value?.unregisterCallback(secondaryCallCallback)
                _secondaryCall.value = null
                _secondaryCallState.value = Call.STATE_DISCONNECTED
            }
            recomputeCapabilities()
        }

        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) {
            recomputeCapabilities()
        }
    }

    fun attachService(service: InCallService) {
        serviceRef = WeakReference(service)
    }

    fun detachService(service: InCallService) {
        if (serviceRef?.get() === service) {
            serviceRef = null
        }
    }

    fun onAudioStateChanged(state: CallAudioState) {
        _audioState.value = state
    }

    fun updateCall(call: Call?) {
        _currentCall.value?.unregisterCallback(callCallback)
        _currentCall.value = call
        _callState.value = call?.state ?: Call.STATE_DISCONNECTED
        // Seeded immediately from the call's own current children, not left to wait for the
        // first onChildrenChanged callback — a call that's already a conference by the time it's
        // assigned here (e.g. promoteToConference) would otherwise show no participants at all
        // until Telecom happens to fire that callback again.
        _conferenceChildren.value = call?.children?.toList() ?: emptyList()
        if (call == null) {
            _isSpam.value = false
        }
        call?.registerCallback(callCallback)
        recomputeCapabilities()
    }

    fun setSpam(isSpam: Boolean) {
        _isSpam.value = isSpam
    }

    fun answer() {
        _currentCall.value?.answer(VideoProfile.STATE_AUDIO_ONLY)
        AnalyticsManager.logEventWithAction("incoming_call", "CallManager", "answer")
    }

    /** Declines a still-ringing call. Telecom requires reject(), not disconnect(), while ringing
     *  for the network to be reliably signaled that the call was declined. */
    fun reject() {
        _currentCall.value?.reject(false, null)
        AnalyticsManager.logEventWithAction("incoming_call", "CallManager", "decline")
    }

    /** Ends a call that's already dialing/active. */
    fun disconnect() {
        _currentCall.value?.disconnect()
    }

    /** Same as [disconnect]/[toggleHold] but targeted at an explicit [Call] rather than always
     *  [_currentCall] — used when the screen is displaying the secondary call as the "front" call
     *  (e.g. a just-placed Add Call leg while the original call sits on hold) and its End/Hold
     *  buttons need to act on that one instead of whichever Telecom itself still tracks as primary. */
    fun disconnectCall(targetCall: Call) {
        targetCall.disconnect()
    }

    fun toggleHoldForCall(targetCall: Call) {
        if (targetCall.state == Call.STATE_HOLDING) targetCall.unhold() else targetCall.hold()
    }

    /** Toggles hold on the current call — Telecom exposes hold/unhold as separate calls, not a
     *  single flip, so this just picks the right one based on the call's current state. */
    fun toggleHold() {
        val call = _currentCall.value ?: return
        if (call.state == Call.STATE_HOLDING) call.unhold() else call.hold()
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

    private val _secondaryCall = MutableStateFlow<Call?>(null)
    /** The second simultaneous call from "Add call" — null whenever there's only one call. */
    val secondaryCall = _secondaryCall.asStateFlow()

    private val _secondaryCallState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val secondaryCallState = _secondaryCallState.asStateFlow()

    private val secondaryCallCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            _secondaryCallState.value = state
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            // CAPABILITY_MERGE_CONFERENCE is frequently granted to the SECONDARY call, not the
            // primary one — without this, canMerge never recomputes when that capability actually
            // arrives (it only reacts to the primary call's own onDetailsChanged), leaving Merge
            // permanently disabled even once the carrier genuinely supports it.
            recomputeCapabilities()
        }

        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) {
            recomputeCapabilities()
        }
    }

    /** Registers the second simultaneous call placed via "Add call". Telecom itself decides
     *  whether two calls can coexist on this SIM/carrier — this just tracks whichever call
     *  isn't the primary once it exists. */
    fun addSecondaryCall(call: Call) {
        if (call === _currentCall.value) return
        _secondaryCall.value?.unregisterCallback(secondaryCallCallback)
        _secondaryCall.value = call
        _secondaryCallState.value = call.state
        call.registerCallback(secondaryCallCallback)
        recomputeCapabilities()
    }

    fun clearSecondaryCall() {
        _secondaryCall.value?.unregisterCallback(secondaryCallCallback)
        _secondaryCall.value = null
        _secondaryCallState.value = Call.STATE_DISCONNECTED
        recomputeCapabilities()
    }

    /** Answers a genuine call-waiting call (secondary call still ringing) — holds the primary
     *  first since Telecom doesn't automatically do this for a second simultaneous call. */
    fun answerSecondaryCall() {
        val secondary = _secondaryCall.value ?: return
        _currentCall.value?.takeIf { it.state == Call.STATE_ACTIVE }?.hold()
        secondary.answer(VideoProfile.STATE_AUDIO_ONLY)
        AnalyticsManager.logEventWithAction("incoming_call", "CallManager", "answer_call_waiting")
    }

    /** The other stock-dialer call-waiting option — ends the current call outright instead of
     *  holding it, then answers the waiting one. Disconnecting first (rather than after) means
     *  ContactCallService's own onCallRemoved() cleanup naturally promotes this secondary call to
     *  primary once the old one actually finishes disconnecting, without any extra bookkeeping here. */
    fun answerAndEndOtherCall() {
        val waiting = _secondaryCall.value ?: return
        _currentCall.value?.disconnect()
        waiting.answer(VideoProfile.STATE_AUDIO_ONLY)
        AnalyticsManager.logEventWithAction("incoming_call", "CallManager", "answer_end_other_call")
    }

    /** Declines a still-ringing secondary (call-waiting) call — reject(), not disconnect(), same
     *  reason as the primary's reject(): Telecom needs this to signal "declined" to the network. */
    fun rejectSecondaryCall() {
        _secondaryCall.value?.reject(false, null)
        AnalyticsManager.logEventWithAction("incoming_call", "CallManager", "decline_call_waiting")
    }

    /** Swaps which of the two simultaneous calls is active vs held. */
    fun swapCalls() {
        val primary = _currentCall.value ?: return
        val secondary = _secondaryCall.value ?: return
        when (primary.state) {
            Call.STATE_ACTIVE -> {
                primary.hold()
                secondary.unhold()
            }
            Call.STATE_HOLDING -> {
                secondary.hold()
                primary.unhold()
            }
        }
    }

    /** Merges the two simultaneous calls into a conference — Telecom's own convention is to call
     *  conference() on whichever call reports CAPABILITY_MERGE_CONFERENCE (checked via canMerge). */
    fun mergeCalls() {
        val primary = _currentCall.value ?: return
        val secondary = _secondaryCall.value ?: return
        primary.conference(secondary)
        AnalyticsManager.logEventWithAction("incoming_call", "CallManager", "merge_calls")
    }

    /** Called when Telecom hands over the merged conference call itself (detected via
     *  CAPABILITY_MANAGE_CONFERENCE or a non-empty children list) — the two individual legs are
     *  now this call's children, not separate top-level calls, so the old secondary-call tracking
     *  is cleared in favor of it. */
    fun promoteToConference(call: Call) {
        _secondaryCall.value?.unregisterCallback(secondaryCallCallback)
        _secondaryCall.value = null
        _secondaryCallState.value = Call.STATE_DISCONNECTED
        updateCall(call)
    }

    /** Disconnects a single participant out of the current conference. */
    fun disconnectConferenceParticipant(call: Call) {
        call.disconnect()
    }

    /** The primary call ended while a secondary one was still up — promote the survivor so the
     *  UI has exactly one primary call again. */
    fun promoteSecondaryToPrimary() {
        val secondary = _secondaryCall.value ?: return
        _secondaryCall.value?.unregisterCallback(secondaryCallCallback)
        _secondaryCall.value = null
        _secondaryCallState.value = Call.STATE_DISCONNECTED
        updateCall(secondary)
    }
}
