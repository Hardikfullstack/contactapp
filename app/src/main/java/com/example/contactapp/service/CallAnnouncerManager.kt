package com.example.contactapp.service

import android.content.Context
import android.speech.tts.TextToSpeech
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
            isInitialized = true
        }
    }

    fun announceCall(name: String) {
        if (!preferenceManager.isCallAnnouncerEnabled()) return

        val repeatCount = preferenceManager.getAnnouncerRepeatCount()
        val message = context.getString(R.string.incoming_call_from, name)

        scope.launch {
            // TTS init is async and this manager is typically first touched right as a
            // call arrives (e.g. when ContactCallService is freshly created), so the
            // engine may not be ready yet — wait briefly instead of silently dropping
            // the announcement, which is what made this look like it "just doesn't work".
            var waited = 0L
            while (!isInitialized && waited < 3000L) {
                delay(100)
                waited += 100
            }
            if (!isInitialized) return@launch

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
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CallAnnouncer")
    }

    fun shutdown() {
        tts?.shutdown()
        scope.cancel()
    }
}
