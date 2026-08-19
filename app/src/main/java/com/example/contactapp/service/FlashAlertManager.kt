package com.example.contactapp.service

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlashAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null
    private var isBlinking = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var blinkJob: Job? = null

    init {
        try {
            cameraId = cameraManager.cameraIdList.find { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            // Camera access error
        }
    }

    fun startBlinking() {
        if (!preferenceManager.isFlashAlertEnabled() || cameraId == null || isBlinking) return

        isBlinking = true
        val speed = preferenceManager.getFlashBlinkSpeed()

        blinkJob = scope.launch {
            while (isActive && isBlinking) {
                try {
                    toggleFlash(true)
                    delay(speed)
                    toggleFlash(false)
                    delay(speed)
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    fun stopBlinking() {
        isBlinking = false
        blinkJob?.cancel()
        blinkJob = null
        scope.launch {
            try {
                toggleFlash(false)
            } catch (e: Exception) { /* Ignore */ }
        }
    }

    private suspend fun toggleFlash(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                cameraId?.let {
                    cameraManager.setTorchMode(it, enabled)
                }
            } catch (e: Exception) {
                // Torch mode not available
            }
        }
    }
}
