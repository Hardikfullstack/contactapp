package com.phone.contacts.ads

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phone.contacts.R
import kotlinx.coroutines.launch

/**
 * Preloads [adUnitId]'s interstitial as soon as this is called, and returns a function that shows
 * it when invoked — matching the reference app's pattern of an interstitial on a given action
 * (back, Done, etc). If the ad isn't ready yet, a brief bounded wait (with [AdLoadingLottie] shown
 * over a non-dismissible dialog, same as Splash's own App Open/Interstitial wait) gives it a real
 * chance to finish loading instead of silently skipping it. No ad unit id just calls [onFinished]
 * immediately, same as a load that never became ready in time.
 */
@Composable
fun rememberInterstitialTrigger(adUnitId: String?): (onFinished: () -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLoading by remember { mutableStateOf(false) }

    LaunchedEffect(adUnitId) {
        if (adUnitId != null) {
            InterstitialAdManager.preload(context, adUnitId)
        }
    }

    if (showLoading) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AdLoadingLottie(modifier = Modifier.size(140.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ad_is_loading),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    return { onFinished ->
        val activity = context as? Activity
        if (adUnitId == null || activity == null) {
            onFinished()
        } else if (InterstitialAdManager.isReady(adUnitId)) {
            InterstitialAdManager.show(activity, adUnitId) { onFinished() }
        } else {
            showLoading = true
            scope.launch {
                waitUntilAdReady { InterstitialAdManager.isReady(adUnitId) }
                showLoading = false
                if (InterstitialAdManager.isReady(adUnitId)) {
                    InterstitialAdManager.show(activity, adUnitId) { onFinished() }
                } else {
                    onFinished()
                }
            }
        }
    }
}

/**
 * Matches the reference app's own pattern: several screens show a preloaded interstitial right
 * when the user leaves (system back gesture/button, or the screen's own back arrow), then
 * continue on to [onBack] either way.
 *
 * Returns a lambda to use as the screen's own back-arrow IconButton's onClick, so both exit paths
 * (system back and the in-screen arrow) go through the same ad check.
 */
@Composable
fun rememberBackWithInterstitial(adUnitId: String?, onBack: () -> Unit): () -> Unit {
    val trigger = rememberInterstitialTrigger(adUnitId)
    val backWithAd: () -> Unit = { trigger(onBack) }
    BackHandler(onBack = backWithAd)
    return backWithAd
}
