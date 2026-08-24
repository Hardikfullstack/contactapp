package com.example.contactapp.service

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

object CallManager {
    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall = _currentCall.asStateFlow()

    private val _callState = MutableStateFlow<Int>(Call.STATE_DISCONNECTED)
    val callState = _callState.asStateFlow()

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

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            _callState.value = state
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
        if (call == null) {
            _isSpam.value = false
        }
        call?.registerCallback(callCallback)
    }

    fun setSpam(isSpam: Boolean) {
        _isSpam.value = isSpam
    }

    fun answer() {
        _currentCall.value?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Declines a still-ringing call. Telecom requires reject(), not disconnect(), while ringing
     *  for the network to be reliably signaled that the call was declined. */
    fun reject() {
        _currentCall.value?.reject(false, null)
    }

    /** Ends a call that's already dialing/active. */
    fun disconnect() {
        _currentCall.value?.disconnect()
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
    }

    /** Whether "Add call" should be offered right now — requires an existing call that can be
     *  held (so the second call can be placed) and no second call already in progress. Whether
     *  a second call can actually be *placed and answered* still depends on carrier/SIM support
     *  for multi-party calls. */
    fun canAddCall(): Boolean {
        val call = _currentCall.value ?: return false
        return _secondaryCall.value == null && call.details.can(Call.Details.CAPABILITY_HOLD)
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
    }

    fun clearSecondaryCall() {
        _secondaryCall.value?.unregisterCallback(secondaryCallCallback)
        _secondaryCall.value = null
        _secondaryCallState.value = Call.STATE_DISCONNECTED
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
