package com.example.contactapp.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort call-audio recorder using MediaRecorder.AudioSource.VOICE_CALL. There is no
 * public, reliable Android API for third-party call recording — since roughly Android 10, most
 * OEMs (and stock AOSP) block non-system apps from actually capturing the other party's audio
 * through this source, so start() can throw outright, or succeed while only capturing silence,
 * entirely depending on the device. This is the same ceiling every non-system call-recorder app
 * hits; the UI surfaces that caveat (and the legal one — consent-to-record laws vary by
 * jurisdiction) before recording starts rather than presenting this as guaranteed to work.
 */
@Singleton
class CallRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds = _elapsedSeconds.asStateFlow()

    /** Returns true if recording actually started, false if this device/OS rejected it. */
    fun start(callerLabel: String): Boolean {
        if (_isRecording.value) return true

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallRecordings").apply { mkdirs() }
        val safeLabel = callerLabel.filter { it.isLetterOrDigit() }.ifEmpty { "call" }
        val fileName = "${safeLabel}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
        val file = File(dir, fileName)

        return try {
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            _isRecording.value = true
            _elapsedSeconds.value = 0
            timerJob = scope.launch {
                while (true) {
                    delay(1000)
                    _elapsedSeconds.value++
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "start: FAILED — likely blocked by this device's OS/OEM (VOICE_CALL source is commonly restricted) — ${e.javaClass.simpleName}: ${e.message}", e)
            releaseQuietly()
            false
        }
    }

    /** Returns the saved file, or null if nothing was recording. */
    fun stop(): File? {
        if (!_isRecording.value) return null
        val file = outputFile
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "stop: FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
        }
        releaseQuietly()
        return file
    }

    private fun releaseQuietly() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            // Already in a bad state — nothing more to do.
        }
        recorder = null
        outputFile = null
        _isRecording.value = false
        timerJob?.cancel()
        timerJob = null
    }

    private companion object {
        const val TAG = "CallRecorder"
    }
}
