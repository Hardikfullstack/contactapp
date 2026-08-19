package com.example.contactapp.ui.features.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.example.contactapp.R
import com.example.contactapp.ui.theme.*
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

// First letter of each language's own alphabet/script (not the first letter of its name) —
// e.g. English/French/German/etc. all use the Latin alphabet, which starts with "A".
private val alphabetFirstLetterByCode = mapOf(
    "en" to "A",
    "hi" to "अ",
    "ar" to "ا",
    "fr" to "A",
    "de" to "A",
    "id" to "A",
    "it" to "A",
    "pt" to "A",
    "es" to "A"
)

private fun getAvatarColorForCode(code: String): Color {
    val colors = listOf(AvatarGreen, AvatarBlue, AvatarOrange, AvatarPink, AvatarPurple)
    val index = abs(code.hashCode()) % colors.size
    return colors[index]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    onDone: () -> Unit
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.select_app_language),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            actions = {
                Button(
                    onClick = {
                        val appLocale: LocaleListCompat = if (selectedLanguageCode.isEmpty()) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(selectedLanguageCode)
                        }
                        AppCompatDelegate.setApplicationLocales(appLocale)
                        onDone()
                    },
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

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(dynamicLanguages) { language ->
                LanguageItem(
                    language = language,
                    isSelected = selectedLanguageCode == language.code,
                    onClick = { selectedLanguageCode = language.code }
                )
            }
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
