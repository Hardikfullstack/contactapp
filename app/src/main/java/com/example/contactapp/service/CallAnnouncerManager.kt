package com.example.contactapp.service

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.contactapp.R
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallAnnouncerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            // Without this, speak() plays over the (often-muted) Music stream — route it to the
            // ring stream instead, tying volume to the ringer like a real incoming call.
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            isInitialized = true
        } else {
            Log.e("CallAnnouncerDebug", "TTS init FAILED — status=$status (no TTS engine installed/enabled on this device?)")
        }
    }

    fun announceCall(name: String) {
        if (!preferenceManager.isCallAnnouncerEnabled()) return

        val repeatCount = preferenceManager.getAnnouncerRepeatCount()
        val message = context.getString(R.string.incoming_call_from, name)

        scope.launch {
            // TTS init is async and often not ready yet when a call first arrives — wait briefly
            // instead of silently dropping the announcement.
            var waited = 0L
            while (!isInitialized && waited < 3000L) {
                delay(100)
                waited += 100
            }
            if (!isInitialized) {
                Log.e("CallAnnouncerDebug", "announceCall: giving up — TTS never finished initializing after ${waited}ms")
                return@launch
            }
            if (repeatCount == 0) { // Continuous
                while (isActive) {
                    speak(message)
                    delay(3000)
                }
            } else {
                repeat(repeatCount) {
                    speak(message)
                    delay(3000)
                }
            }
        }
    }

    fun stopAnnouncing() {
        scope.coroutineContext.cancelChildren()
        tts?.stop()
    }

    private fun speak(text: String) {
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CallAnnouncer")
        if (result != TextToSpeech.SUCCESS) {
            Log.e("CallAnnouncerDebug", "speak: TextToSpeech.speak() returned $result (expected ${TextToSpeech.SUCCESS})")
        }
    }

    fun shutdown() {
        tts?.shutdown()
        scope.cancel()
    }
}
