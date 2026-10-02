package com.phone.contacts.service

import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.util.CallUtils
import com.phone.contacts.util.DefaultDialerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    /** Whether a third call can be dialed right now — only ever true with exactly one call up
     * (no secondary yet) that itself supports being put on hold, matching contactapp's own gate
     * (CAPABILITY_HOLD isn't reliably reported on a just-merged conference call on every telephony
     * stack, so callers needing the post-merge case fall back to "no secondary call pending"
     * instead of this flag — see ManageCallSheet). */
    private val _canAddCall = MutableStateFlow(false)
    val canAddCall: StateFlow<Boolean> = _canAddCall.asStateFlow()

    // Mute/audio-routing are InCallService-level operations, not Call-level — held weakly since
    // CallManager outlives any single call/service instance and must never keep it alive.
    private var serviceRef: WeakReference<InCallService>? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Caller info resolved once per call (name/photo lookup is async) and reused by every
    // subsequent notification re-render for that same call, matching how the on-screen caller
    // name/photo stays fixed for the life of the call.
    private var primaryCallerName: String? = null
    private var primaryCallerPhotoUri: String? = null
    private var primaryHasContactName: Boolean = false
    private var secondaryCallerName: String? = null
    private var secondaryCallerPhotoUri: String? = null
    private var secondaryHasContactName: Boolean = false

    private fun resolvePrimaryCallerInfo(context: Context, number: String, onResolved: () -> Unit) {
        primaryCallerName = number
        primaryCallerPhotoUri = null
        primaryHasContactName = false
        scope.launch {
            val contact = ContactRepository.findContactByNumber(context, number)
            if (contact?.name != null) {
                primaryCallerName = contact.name
                primaryHasContactName = true
            }
            primaryCallerPhotoUri = contact?.photoUri
            onResolved()
        }
    }

    private fun resolveSecondaryCallerInfo(context: Context, number: String, onResolved: () -> Unit) {
        secondaryCallerName = number
        secondaryCallerPhotoUri = null
        secondaryHasContactName = false
        scope.launch {
            val contact = ContactRepository.findContactByNumber(context, number)
            if (contact?.name != null) {
                secondaryCallerName = contact.name
                secondaryHasContactName = true
            }
            secondaryCallerPhotoUri = contact?.photoUri
            onResolved()
        }
    }

    private fun renderActiveNotification(context: Context, call: Call) {
        // Same fresh re-check as onCallAdded - never show/refresh the call notification unless
        // this app genuinely holds the default-dialer role right now.
        DefaultDialerState.refresh(context)
        if (!DefaultDialerState.isDefault.value) return
        CallNotificationManager.showActiveCallNotification(
            context,
            primaryCallerName ?: call.details.handle?.schemeSpecificPart ?: "Unknown",
            primaryCallerPhotoUri,
            callConnectedAtMillis = _connectedAtElapsedRealtime.value,
            hasContactName = primaryHasContactName,
            isConference = _conferenceChildren.value.size > 1
        )
    }

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
        _canAddCall.value = primary != null && secondary == null &&
            primary.details.can(Call.Details.CAPABILITY_HOLD)
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            _callState.value = state
            if (state == Call.STATE_ACTIVE && _connectedAtElapsedRealtime.value == null) {
                _connectedAtElapsedRealtime.value = android.os.SystemClock.elapsedRealtime()
            }
            recomputeCanMerge()

            val context = serviceRef?.get() ?: return
            when (state) {
                Call.STATE_ACTIVE, Call.STATE_HOLDING -> renderActiveNotification(context, call)
                Call.STATE_DISCONNECTED -> CallNotificationManager.cancelNotification(context)
            }
        }

        override fun onChildrenChanged(call: Call, children: MutableList<Call>) {
            _conferenceChildren.value = children.toList()
            if (children.isNotEmpty()) {
                // Telecom merged in place rather than handing back a new conference Call — the
                // separately-tracked secondary is now redundant/stale.
                clearSecondaryCall()
            }
            // Re-render immediately on a merge (not just on the next state transition) so the
            // "Conference call" icon/title switch shows up the moment it completes.
            if (call.state == Call.STATE_ACTIVE || call.state == Call.STATE_HOLDING) {
                serviceRef?.get()?.let { renderActiveNotification(it, call) }
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

            // Answered, held, or hung up before being answered - either way it's no longer
            // "waiting", so the stacked call-waiting pill no longer applies.
            if (state != Call.STATE_RINGING) {
                serviceRef?.get()?.let { CallNotificationManager.cancelCallWaitingNotification(it) }
            }
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
        val isFirstCall = _currentCall.value == null
        when {
            isConferenceCall -> promoteToConference(call)
            isFirstCall -> {
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

        val context = serviceRef?.get() ?: return
        // Re-queried fresh (not the cached DefaultDialerState.isDefault.value some screen last
        // set) - Telecom can still bind this InCallService and hand over a call in the brief
        // window right as the role is being granted, before any screen's own refresh() has run;
        // trusting a stale cached value here could wrongly skip the very first call's notification.
        DefaultDialerState.refresh(context)
        if (!DefaultDialerState.isDefault.value) return
        CallNotificationManager.ensureChannels(context)
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
        when {
            isConferenceCall -> {} // callCallback's onStateChanged/onChildrenChanged renders this once it's active.
            isFirstCall && call.state == Call.STATE_RINGING -> {
                resolvePrimaryCallerInfo(context, number) {
                    CallNotificationManager.showIncomingCallNotification(
                        context, primaryCallerName ?: number, number, primaryCallerPhotoUri, primaryHasContactName
                    )
                }
            }
            isFirstCall -> {
                // Outgoing call (dialing/connecting) - reuses the active-call notification since
                // there's nothing to Answer/Decline on our own outgoing call.
                resolvePrimaryCallerInfo(context, number) {
                    CallNotificationManager.showActiveCallNotification(
                        context,
                        primaryCallerName ?: number,
                        primaryCallerPhotoUri,
                        statusText = context.getString(com.phone.contacts.R.string.dialing),
                        callConnectedAtMillis = null,
                        hasContactName = primaryHasContactName
                    )
                }
            }
            call.state == Call.STATE_RINGING -> {
                // Call-waiting: a second call ringing in while already on a call.
                resolveSecondaryCallerInfo(context, number) {
                    CallNotificationManager.showCallWaitingNotification(
                        context, secondaryCallerName ?: number, number, secondaryCallerPhotoUri, secondaryHasContactName
                    )
                }
            }
        }
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

        // The surviving call's own resolved info takes over as "primary" for future re-renders.
        primaryCallerName = secondaryCallerName
        primaryCallerPhotoUri = secondaryCallerPhotoUri
        primaryHasContactName = secondaryHasContactName
        if (secondary.state == Call.STATE_ACTIVE || secondary.state == Call.STATE_HOLDING) {
            serviceRef?.get()?.let { renderActiveNotification(it, secondary) }
        }
    }

    fun onCallRemoved(call: Call) {
        call.unregisterCallback(callCallback)
        call.unregisterCallback(secondaryCallCallback)
        val context = serviceRef?.get()
        when {
            call === _secondaryCall.value -> {
                context?.let { CallNotificationManager.cancelCallWaitingNotification(it) }
                clearSecondaryCall()
            }
            call === _currentCall.value -> {
                if (_secondaryCall.value != null) {
                    promoteSecondaryToPrimary()
                } else {
                    _currentCall.value = null
                    _callState.value = Call.STATE_DISCONNECTED
                    _connectedAtElapsedRealtime.value = null
                    _conferenceChildren.value = emptyList()
                    context?.let { CallNotificationManager.cancelNotification(it) }
                    primaryCallerName = null
                    primaryCallerPhotoUri = null
                    primaryHasContactName = false
                }
            }
        }
        recomputeCanMerge()
    }

    fun onAudioStateChanged(state: CallAudioState) {
        _audioState.value = state
        // Keeps the notification's Mute/Speaker icons in sync - covers both a toggle tapped from
        // the notification itself and external route changes (e.g. a headset connecting).
        if (_callState.value == Call.STATE_ACTIVE) {
            serviceRef?.get()?.let { CallNotificationManager.refreshActiveCallNotification(it) }
        }
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

    /** Ends the in-progress call outright and answers the waiting one instead — the call-waiting
     * screen's "Answer & end other call" option, distinct from [answerSecondaryCall] which holds
     * (not disconnects) the primary. */
    fun answerSecondaryAndEndOther() {
        val secondary = _secondaryCall.value ?: return
        _currentCall.value?.disconnect()
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
