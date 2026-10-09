package com.phone.contacts.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phone.contacts.R

/**
 * Full-screen (edge to edge — no app bar, covers the status/nav bar area) loading state shown
 * while we briefly wait for an App Open/Interstitial ad to finish loading (see
 * [waitUntilAdReady]) — the [AdLoadingLottie] animation with "Ad is loading…" beneath it, same
 * moment the reference app shows it for, but styled with this app's own theme instead of fixed
 * colors, so it matches the Light/Dark toggle like every other screen.
 */
@Composable
fun AdLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-40).dp)
        ) {
            AdLoadingLottie(modifier = Modifier.size(280.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.ad_is_loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}
