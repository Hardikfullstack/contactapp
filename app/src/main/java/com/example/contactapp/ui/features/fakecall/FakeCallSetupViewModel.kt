package com.example.contactapp.ui.features.fakecall

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.contactapp.service.ShakeDetectionService
import com.example.contactapp.service.ShakeWatchdogScheduler
import com.example.contactapp.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class FakeCallSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    fun getShakeCallerName(): String = preferenceManager.getShakeCallerName()
    fun getShakeCallerNumber(): String = preferenceManager.getShakeCallerNumber()
    fun isShakeTriggerEnabled(): Boolean = preferenceManager.isShakeTriggerEnabled()

    fun saveShakeProfile(name: String, number: String, enabled: Boolean) {
        preferenceManager.setShakeCallerProfile(name, number)
        preferenceManager.setShakeTriggerEnabled(enabled)
        if (enabled) {
            ShakeDetectionService.start(context)
            ShakeWatchdogScheduler.scheduleNext(context)
        } else {
            ShakeDetectionService.stop(context)
            ShakeWatchdogScheduler.cancel(context)
        }
    }
}
