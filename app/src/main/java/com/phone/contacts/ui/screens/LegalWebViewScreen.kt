package com.phone.contacts.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.phone.contacts.R
import com.phone.contacts.ui.theme.primaryAccentColor
import com.phone.contacts.util.AppConfigStore

/**
 * Loads the Privacy Policy or Terms & Conditions page in a plain WebView. The URL itself comes
 * from the panel's remote config, not a hardcoded link — `extra_data_3_message` for privacy,
 * `extra_data_4_message` for terms, matching the sibling contactapp's own LegalWebViewScreen.
 */
@Composable
fun LegalWebViewScreen(onBack: () -> Unit, type: String) {
    var isLoading by remember { mutableStateOf(true) }
    val adConfig by AppConfigStore.config.collectAsState()

    // Non-null on purpose - the config can still be mid-fetch (empty) when this screen first
    // composes; keeping url a plain String (not String?) lets the AndroidView mount immediately
    // below and its update{} block load the real address the moment adConfig catches up.
    val url = remember(adConfig, type) {
        val result = adConfig?.result
        (if (type == "privacy") result?.extra_data_3_message else result?.extra_data_4_message)
            ?.toString() ?: ""
    }

    val title = if (type == "privacy") {
        stringResource(R.string.privacy_policy_title)
    } else {
        stringResource(R.string.terms_and_conditions_title)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        var loadedUrl by remember { mutableStateOf("") }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isLoading) 0f else 1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, loadedUrl: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, loadedUrl, favicon)
                                isLoading = true
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val requestUrl = request?.url?.toString()
                                return handleExternalLink(view, requestUrl)
                            }

                            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                super.onPageFinished(view, loadedUrl)
                                isLoading = false
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            domStorageEnabled = true
                        }
                    }
                },
                update = { webView ->
                    // adConfig can arrive after this AndroidView has already been created (it's
                    // mounted immediately, before the remote config necessarily has a value) -
                    // this re-fires on every recomposition, so only load when url actually
                    // changed to something new, not on every unrelated recomposition (e.g. from
                    // isLoading flipping).
                    if (url.isNotBlank() && url != loadedUrl) {
                        loadedUrl = url
                        webView.loadUrl(url)
                    }
                }
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primaryAccentColor())
                }
            }
        }
    }
}

/** mailto:/tel:/sms:/intent:/market: links inside the page open their own app instead of loading
 * in the WebView. */
private fun handleExternalLink(view: WebView?, url: String?): Boolean {
    if (url != null && (url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("intent:") || url.startsWith("market:"))) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            view?.context?.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }
    return false
}
