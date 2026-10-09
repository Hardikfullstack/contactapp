package com.phone.contacts.ads

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * Preloads [adUnitId]'s interstitial as soon as this is called, and returns a function that shows
 * it when invoked — matching the reference app's pattern of an interstitial on a given action
 * (back, Done, etc). If the ad isn't ready yet, a brief bounded wait (with [AdLoadingScreen] shown
 * full-screen over the current content) gives it a real chance to finish loading instead of
 * silently skipping it. No ad unit id just calls [onFinished] immediately, same as a load that
 * never became ready in time.
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
        // A Dialog, not an inline composable: this is called before the caller screen's own
        // content (e.g. LanguageSelectionScreen's Scaffold), so an inline AdLoadingScreen() here
        // would be drawn first and then painted over — invisible even while showLoading is true.
        // A Dialog renders in its own Android Window, always on top regardless of composition
        // order, which an inline composable can't guarantee.
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            AdLoadingScreen()
        }
    }

    return { onFinished ->
        val activity = context as? Activity
        if (adUnitId == null || activity == null) {
            onFinished()
        } else if (InterstitialAdManager.isReady(adUnitId)) {
            // Already loaded — show it straight away, no loading screen flash.
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
