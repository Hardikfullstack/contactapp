package com.phone.contact.calls.dialer.ui.features.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.phone.contact.calls.dialer.R
import com.phone.contact.calls.dialer.ads.AdPlacements
import com.phone.contact.calls.dialer.ads.AdType
import com.phone.contact.calls.dialer.ads.NativeAdTemplate
import com.phone.contact.calls.dialer.ads.NativeAdView
import com.phone.contact.calls.dialer.ads.rememberInterstitialTrigger
import com.phone.contact.calls.dialer.util.AnalyticsEvents
import com.phone.contact.calls.dialer.util.AnalyticsManager
import com.phone.contact.calls.dialer.util.AppConfigStore

/** The device's own system locale language code (e.g. "hi"), independent of any per-app language
 * override. Used both to auto-skip this screen on first run when the system locale is already
 * supported, and as the fallback for which radio button to pre-select when no explicit per-app
 * override has ever been made. */
private fun systemLocaleCode(context: android.content.Context): String {
    val systemLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
        localeManager?.systemLocales?.takeIf { !it.isEmpty }?.get(0)
            ?: android.content.res.Resources.getSystem().configuration.locales[0]
    } else {
        android.content.res.Resources.getSystem().configuration.locales[0]
    }
    return systemLocale.language
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(onDone: () -> Unit, onBack: (() -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // currentAppLanguageCode() only reflects an explicit per-app override (one actually made via
    // AppCompatDelegate.setApplicationLocales) - it's empty whenever no such override was ever
    // made, even if the app's own text is showing in a non-English language purely because the
    // device's system locale matches it (ordinary Android resource-qualifier fallback, unrelated
    // to AppCompat's per-app override). Falling back to a hardcoded "en" in that case picked the
    // wrong radio button on this screen for exactly that device: Hindi system locale, Hindi app
    // text, but "English" shown as selected here. Fall back to the device's own system locale
    // instead, same as the first-run auto-skip below already does.
    var selectedCode by remember {
        mutableStateOf(
            currentAppLanguageCode().ifEmpty {
                val sysCode = systemLocaleCode(context)
                supportedLanguages.find { it.code == sysCode }?.code ?: "en"
            }
        )
    }
    val adConfig by AppConfigStore.config.collectAsState()

    // Separate ad units for the two ways to leave this screen - back (slot 1) and Done tap (slot 2).
    val backInterstitialAdUnitId = AdPlacements.adUnitId(adConfig?.result, AdType.INTERSTITIAL_ON_BACK, slot = 1)
    val triggerBackInterstitial = rememberInterstitialTrigger(backInterstitialAdUnitId)
    val doneInterstitialAdUnitId = AdPlacements.adUnitId(adConfig?.result, AdType.INTERSTITIAL_ON_BACK, slot = 2)
    val triggerDoneInterstitial = rememberInterstitialTrigger(doneInterstitialAdUnitId)

    val isFirstRun = onBack == null
    val shouldAutoSkip = remember(isFirstRun) {
        isFirstRun && supportedLanguages.any { it.code == systemLocaleCode(context) }
    }

    androidx.compose.runtime.LaunchedEffect(shouldAutoSkip) {
        if (shouldAutoSkip) {
            val matchCode = supportedLanguages.find { it.code == systemLocaleCode(context) }?.code ?: "en"

            val locales = LocaleListCompat.forLanguageTags(matchCode)
            AppCompatDelegate.setApplicationLocales(locales)
            AnalyticsManager.logEventWithAction(
                AnalyticsEvents.LANGUAGE_CHANGED,
                AnalyticsEvents.SCREEN_LANGUAGE,
                AnalyticsEvents.ACTION_AUTO,
                mapOf(AnalyticsEvents.PARAM_FIRST_RUN to isFirstRun)
            )

            triggerDoneInterstitial { onDone() }
        }
    }

    if (shouldAutoSkip) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    if (onBack != null) {
        BackHandler { triggerBackInterstitial(onBack) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_language),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    // Only shown when reached from Settings — the first-run onboarding call site
                    // doesn't pass onBack, since there's nothing to go back to yet at that point.
                    if (onBack != null) {
                        IconButton(onClick = { triggerBackInterstitial(onBack) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            triggerDoneInterstitial {
                                val locales = LocaleListCompat.forLanguageTags(selectedCode)
                                AppCompatDelegate.setApplicationLocales(locales)
                                AnalyticsManager.logEventWithAction(
                                    AnalyticsEvents.LANGUAGE_CHANGED,
                                    AnalyticsEvents.SCREEN_LANGUAGE,
                                    selectedCode,
                                    mapOf(AnalyticsEvents.PARAM_FIRST_RUN to isFirstRun)
                                )
                                onDone()
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 22.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.done),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            // Matches the reference app / contactapp's own convention: native_1 = Language screen.
            AdPlacements.adUnitId(adConfig?.result, AdType.NATIVE, slot = 1)?.let { adUnitId ->
                NativeAdView(
                    adUnitId = adUnitId,
                    template = NativeAdTemplate.MEDIUM,
                    modifier = Modifier.navigationBarsPadding(),
                     compact = true,
                    bottomEdge = true,
                    cacheKey = "language_native"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(supportedLanguages) { language ->
                LanguageRow(
                    language = language,
                    isSelected = selectedCode == language.code,
                    onClick = { selectedCode = language.code }
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = language.flagEmoji, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = language.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = language.nativeName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        }
    }
}
