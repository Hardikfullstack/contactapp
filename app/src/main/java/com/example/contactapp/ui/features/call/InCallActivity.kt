package com.example.contactapp.ui.features.call

import android.os.Bundle
import android.telecom.Call
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.example.contactapp.domain.repository.ContactRepository
import com.example.contactapp.service.AutoReplyManager
import com.example.contactapp.service.CallManager
import com.example.contactapp.service.SpamManager
import com.example.contactapp.ui.theme.ContactAppTheme
import com.example.contactapp.util.CallAccentColors
import com.example.contactapp.util.CallButtonShape
import com.example.contactapp.util.CallTheme
import com.example.contactapp.util.ContactCallBackgroundManager
import com.example.contactapp.util.PreferenceManager
import com.example.contactapp.util.WallpaperSelection
import com.example.contactapp.util.isDarkOnCallScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var contactCallBackgroundManager: ContactCallBackgroundManager

    @Inject
    lateinit var contactRepository: ContactRepository

    @Inject
    lateinit var autoReplyManager: AutoReplyManager

    @Inject
    lateinit var spamManager: SpamManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        enableEdgeToEdge()

        setContent {
            val callState by CallManager.callState.collectAsState()
            val call by CallManager.currentCall.collectAsState()
            val rawNumber = call?.details?.handle?.schemeSpecificPart

            // The number Telecom hands back (call.details.handle) can be formatted
            // differently from what's stored on the contact (country code, spacing, etc.),
            // so resolve it through the same PhoneLookup-backed contact matching the rest
            // of the app already trusts, rather than comparing raw strings directly.
            var resolvedNumber by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(rawNumber) {
                resolvedNumber = rawNumber?.let { raw ->
                    contactRepository.findContactByNumber(raw)?.number ?: raw
                }
            }

            val globalSelection by preferenceManager.wallpaperSelectionFlow.collectAsState(
                initial = preferenceManager.getCallWallpaperSelection()
            )
            // This caller's own "calling card" (set on their contact detail page) takes
            // priority over the app-wide Tools > Call Wallpaper pick when present.
            val callingCard by remember(resolvedNumber) {
                if (resolvedNumber != null) contactCallBackgroundManager.getBackgroundFlow(resolvedNumber!!) else flowOf(WallpaperSelection.None)
            }.collectAsState(initial = WallpaperSelection.None)

            val selection = if (callingCard !is WallpaperSelection.None) callingCard else globalSelection

            val callAccentColorId by preferenceManager.callAccentColorFlow.collectAsState(
                initial = preferenceManager.getCallAccentColorId()
            )
            val callButtonShapeName by preferenceManager.callButtonShapeFlow.collectAsState(
                initial = preferenceManager.getCallButtonShapeName()
            )
            val callTheme = CallTheme(
                accentColor = CallAccentColors.findById(callAccentColorId).color,
                buttonShape = CallButtonShape.safeValueOf(callButtonShapeName)
            )

            LaunchedEffect(callState) {
                if (callState == Call.STATE_DISCONNECTED) {
                    // Plain finish() can leave an empty task card behind in the system
                    // app-switcher for a singleInstance activity like this one — remove it
                    // outright instead, since there's nothing to return to in this task.
                    finishAndRemoveTask()
                }
            }

            val insetsController = remember { WindowCompat.getInsetsController(window, window.decorView) }
            SideEffect {
                insetsController.isAppearanceLightStatusBars = !selection.isDarkOnCallScreen()
            }

            ContactAppTheme {
                val isSpamByCallManager by CallManager.isSpam.collectAsState()
                
                InCallScreen(
                    onHangup = { CallManager.disconnect() },
                    onDecline = {
                        CallManager.reject()
                        resolvedNumber?.let { autoReplyManager.sendReplyIfEnabled(it) }
                    },
                    onAnswer = { CallManager.answer() },
                    isSpam = isSpamByCallManager,
                    onReportSpam = { num -> spamManager.reportSpam(num, true) },
                    selection = selection,
                    theme = callTheme
                )
            }
        }
    }
}
