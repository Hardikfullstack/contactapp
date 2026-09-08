package com.example.contactapp.ui.features.onboarding

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contactapp.R
import com.example.contactapp.ads.InterstitialAdManager
import com.example.contactapp.ads.NativeAdTemplate
import com.example.contactapp.ads.NativeAdView
import com.example.contactapp.ui.components.animatedPulse
import com.example.contactapp.ui.theme.*
import com.example.contactapp.util.AnalyticsManager
import com.example.contactapp.util.LocaleChangeState
import com.example.contactapp.viewmodel.AppConfigViewModel
import java.util.Locale
import kotlin.math.abs

data class Language(
    val name: String,
    val nativeName: String,
    val code: String,
    val avatarChar: String,
    val avatarColor: Color
)

private val languageCodes = listOf("en", "hi", "ar", "fr", "de", "id", "it", "pt", "es")

// First letter of each language's own native name (English, हिन्दी, العربية, Français, Deutsch,
// Bahasa Indonesia, Italiano, Português, Español).
private val alphabetFirstLetterByCode = mapOf(
    "en" to "E",
    "hi" to "ह",
    "ar" to "ع",
    "fr" to "F",
    "de" to "D",
    "id" to "B",
    "it" to "I",
    "pt" to "P",
    "es" to "E"
)

private fun getAvatarColorForCode(code: String): Color {
    val colors = listOf(AvatarGreen, AvatarBlue, AvatarOrange, AvatarPink, AvatarPurple)
    val index = abs(code.hashCode()) % colors.size
    return colors[index]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    onDone: () -> Unit,
    isFirstRun: Boolean = false,
    onBackClick: (() -> Unit)? = null
) {
    val systemDefaultTitle = stringResource(R.string.system_default)
    val dynamicLanguages = remember(systemDefaultTitle) {
        val systemLocale = Locale.getDefault()
        val systemDefaultLanguage = Language(
            name = systemDefaultTitle,
            nativeName = "(${systemLocale.getDisplayLanguage(Locale.ENGLISH)})",
            code = "",
            avatarChar = "S",
            avatarColor = AvatarGreen
        )

        val list = languageCodes.map { code ->
            val locale = Locale.forLanguageTag(code)
            val nativeName = locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase() }
            Language(
                name = locale.getDisplayLanguage(Locale.ENGLISH),
                nativeName = "($nativeName)",
                code = code,
                avatarChar = alphabetFirstLetterByCode[code] ?: nativeName.take(1),
                avatarColor = getAvatarColorForCode(code)
            )
        }
        listOf(systemDefaultLanguage) + list
    }

    var selectedLanguageCode by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { "" })
    }

    // This is the last onboarding step — the system back gesture/button would otherwise pop
    // back to AdvancedPermissionScreen, letting the user re-enter a completed permission flow.
    // Swallow it here instead; there's no UI back arrow shown in this mode either (onBackClick
    // stays null for first-run callers), so the only way forward is picking a language and Done.
    BackHandler(enabled = isFirstRun) {}

    // Shares the same AppConfigViewModel instance created in MainActivity (Activity-scoped).
    val context = LocalContext.current
    val appConfigViewModel: AppConfigViewModel = viewModel(context as ComponentActivity)
    val adConfig by appConfigViewModel.appResponse.collectAsState()
    val bigNativeAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.native_1_on_off == "on") {
            result.native_1?.takeIf { it.isNotBlank() }
        } else null
    }
    // First-run only — shown right after "Done" is tapped, on the way into the app.
    val languageDoneInterstitialAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.interstitial_1_on_off == "on") {
            result.interstitial_1?.takeIf { it.isNotBlank() }
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.select_app_language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick, modifier = Modifier.padding(start = 2.dp).size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            actions = {
                Button(
                    onClick = {
                        val appLocale: LocaleListCompat = if (selectedLanguageCode.isEmpty()) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(selectedLanguageCode)
                        }
                        // setApplicationLocales() recreates MainActivity to refresh strings —
                        // without this flag, that recreate replays the splash screen from scratch.
                        val activity = context as? Activity
                        val applyLanguageAndNavigate = {
                            LocaleChangeState.skipNextSplash = true
                            AppCompatDelegate.setApplicationLocales(appLocale)
                            AnalyticsManager.logEventWithAction(
                                "language_changed",
                                "LanguageSelectionScreen",
                                selectedLanguageCode.ifEmpty { "system" },
                                mapOf("first_run" to isFirstRun)
                            )
                            onDone()
                            if (!isFirstRun) {
                                // Delay recreate slightly so navigation completes and locale async save finishes
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    activity?.recreate()
                                }, 250)
                            }
                        }
                        
                        if (isFirstRun && activity != null && languageDoneInterstitialAdUnitId != null &&
                            InterstitialAdManager.isReady(languageDoneInterstitialAdUnitId)
                        ) {
                            InterstitialAdManager.show(activity, languageDoneInterstitialAdUnitId) {
                                applyLanguageAndNavigate()
                            }
                        } else {
                            applyLanguageAndNavigate()
                        }
                    },
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .then(
                            if (isFirstRun) {
                                Modifier.animatedPulse(MaterialTheme.colorScheme.primary, maxAlpha = 0.25f)
                            } else {
                                Modifier
                            }
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.done),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        HorizontalDivider(thickness = 1.dp, color = Color(0xFFCDCDCD))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 4.dp)
        ) {
            items(dynamicLanguages) { language ->
                LanguageItem(
                    language = language,
                    isSelected = selectedLanguageCode == language.code,
                    onClick = { selectedLanguageCode = language.code }
                )
            }
        }

        if (bigNativeAdUnitId != null) {
            NativeAdView(
                adUnitId = bigNativeAdUnitId,
                template = NativeAdTemplate.MEDIUM,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(language.avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = language.avatarChar,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = language.nativeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Radio Button
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}
