package com.example.contactapp.service

import android.telecom.Call
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallManager {
    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall = _currentCall.asStateFlow()

    private val _callState = MutableStateFlow<Int>(Call.STATE_DISCONNECTED)
    val callState = _callState.asStateFlow()

    private val _isSpam = MutableStateFlow(false)
    val isSpam = _isSpam.asStateFlow()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            _callState.value = state
        }
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

    fun mute(shouldMute: Boolean) {
        // This is typically handled via InCallService.setAudioRoute or similar, 
        // but simple toggle is often done via AudioManager
    }

    fun setSpeaker(shouldActivate: Boolean) {
        // Handled via InCallService.setAudioRoute
    }
}
