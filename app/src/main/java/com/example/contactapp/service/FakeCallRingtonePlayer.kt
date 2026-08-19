package com.example.contactapp.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.example.contactapp.domain.repository.ContactRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the actual ring sound for a fake call. Telecom auto-rings calls it manages itself, but
 * explicitly does NOT do this for self-managed connections (see FakeCallConnectionService) — a
 * self-managed app is expected to own its own ringing audio entirely, same as it owns its own
 * incoming-call UI via onShowIncomingCallUi(). Since this app owns the whole ring pipeline here
 * (unlike real calls, which the system Ringer handles), it also honors a per-contact ringtone
 * override when the fake caller's number matches a real contact.
 */
@Singleton
class FakeCallRingtonePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository
) {
    private var mediaPlayer: MediaPlayer? = null
    private var ringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startRinging(number: String? = null) {
        if (mediaPlayer != null || ringJob != null) return // already ringing / resolving which ringtone to use

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            // Respect Silent/Vibrate, same as a real incoming call would.
            return
        }

        ringJob = scope.launch {
            val contactOverride = number?.let { contactRepository.getContactRingtone(it) }
            val uri = when {
                contactOverride == Uri.EMPTY -> null // contact explicitly set to Silent
                contactOverride != null -> contactOverride
                else -> RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            } ?: return@launch // Silent — either the contact's explicit choice, or no default set at all

            withContext(Dispatchers.Main) {
                playUri(uri)
            }
        }
    }

    private fun playUri(uri: Uri) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("FakeCallDebug", "startRinging: FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun stopRinging() {
        ringJob?.cancel()
        ringJob = null
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (e: Exception) {
                // Already stopped/released — nothing to do.
            }
            player.release()
        }
        mediaPlayer = null
    }
}
