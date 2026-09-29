package com.phone.contacts.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.phone.contacts.data.Contact
import com.phone.contacts.data.ContactRepository
import com.phone.contacts.ui.components.verticalScrollIndicator
import com.phone.contacts.util.CallUtils
import com.phone.contacts.util.MessageUtils

@Composable
fun KeypadScreen(onAddNumberClick: (String) -> Unit) {
    val context = LocalContext.current
    var dialedNumber by remember { mutableStateOf("") }

    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, ToneGenerator.MAX_VOLUME / 3) }
    DisposableEffect(Unit) { onDispose { toneGenerator.release() } }
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(Vibrator::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    var allContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            ContactRepository.fetchContacts(context).collect { allContacts = it }
        }
    }

    val matches = remember(dialedNumber, allContacts) {
        val digits = dialedNumber.filter { it.isDigit() }
        if (digits.isEmpty()) {
            emptyList()
        } else {
            allContacts.filter { it.number.filter { c -> c.isDigit() }.contains(digits) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Match list occupies the space above the number display + keypad, scrolling
        // independently — matches the reference layout where results sit above "123".
        if (dialedNumber.isNotEmpty() && matches.isNotEmpty()) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .verticalScrollIndicator(listState, MaterialTheme.colorScheme.primary)
            ) {
                items(matches, key = { it.id }) { contact ->
                    DialMatchRow(
                        contact = contact,
                        onCall = { CallUtils.placeCall(context, contact.number) },
                        onMessage = { MessageUtils.sendMessage(context, contact.number) }
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // One unified card holds the typed number + "Add Number" (when present) and the keypad
        // itself, so they read as a single panel rather than two stacked cards.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Always reserved (not conditionally added/removed) so typing the first digit
            // doesn't suddenly push the keypad down — the space for the number and "Add
            // Number" is there from the start, they just render invisible until there's a
            // number to show.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (dialedNumber.isEmpty()) {
                        // Blinking cursor — marks where the typed number will land, so the
                        // reserved empty space doesn't just look like a dead gap.
                        val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
                        val cursorAlpha by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 600),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "cursor_alpha"
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha))
                        )
                    } else {
                        Text(
                            text = dialedNumber,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Text(
                    text = "Add Number",
                    color = if (dialedNumber.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .then(
                            if (dialedNumber.isNotEmpty()) {
                                Modifier.clickable { onAddNumberClick(dialedNumber) }
                            } else {
                                Modifier
                            }
                        )
                )
            }

            DialPad(
                showBackspace = dialedNumber.isNotEmpty(),
                onDigit = { digit ->
                    playDialFeedback(toneGenerator, vibrator, digit)
                    dialedNumber += digit
                },
                onBackspace = { if (dialedNumber.isNotEmpty()) dialedNumber = dialedNumber.dropLast(1) },
                onCall = { CallUtils.placeCall(context, dialedNumber) }
            )
        }
    }
}

private fun playDialFeedback(toneGenerator: ToneGenerator, vibrator: Vibrator?, digit: String) {
    val tone = when (digit) {
        "0" -> ToneGenerator.TONE_DTMF_0
        "1" -> ToneGenerator.TONE_DTMF_1
        "2" -> ToneGenerator.TONE_DTMF_2
        "3" -> ToneGenerator.TONE_DTMF_3
        "4" -> ToneGenerator.TONE_DTMF_4
        "5" -> ToneGenerator.TONE_DTMF_5
        "6" -> ToneGenerator.TONE_DTMF_6
        "7" -> ToneGenerator.TONE_DTMF_7
        "8" -> ToneGenerator.TONE_DTMF_8
        "9" -> ToneGenerator.TONE_DTMF_9
        "*" -> ToneGenerator.TONE_DTMF_S
        "#" -> ToneGenerator.TONE_DTMF_P
        else -> ToneGenerator.TONE_DTMF_0
    }
    toneGenerator.startTone(tone, 60)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createOneShot(15, 40))
    } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(15)
    }
}

private val dialKeys = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to ""
)

@Composable
private fun DialPad(
    showBackspace: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        dialKeys.chunked(3).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                row.forEach { (digit, letters) ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        DialButton(digit = digit, letters = letters, onClick = { onDigit(digit) })
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    onClick = onCall,
                    shape = CircleShape,
                    color = Color(0xFF1DA463),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Call, contentDescription = "Call", tint = Color.White)
                    }
                }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (showBackspace) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBackspace)
                    )
                }
            }
        }
    }
}

@Composable
private fun DialButton(digit: String, letters: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                fontSize = 23.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            // Always reserve the letters line (even blank) so every digit sits at the same
            // height — otherwise a button with no letters (1, *, #) centers on one line while
            // its neighbors center on two, making its digit look shifted down relative to them.
            Text(
                text = letters.ifEmpty { " " },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
private fun DialMatchRow(contact: Contact, onCall: () -> Unit, onMessage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(contact = contact, size = 40.dp)
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = contact.number,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Message,
            contentDescription = "Message",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onMessage)
        )
        Spacer(modifier = Modifier.size(18.dp))
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = "Call",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onCall)
        )
    }
}
