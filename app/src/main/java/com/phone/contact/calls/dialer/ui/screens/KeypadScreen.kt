package com.phone.contact.calls.dialer.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.phone.contact.calls.dialer.R
import com.phone.contact.calls.dialer.data.Contact
import com.phone.contact.calls.dialer.data.ContactRepository
import com.phone.contact.calls.dialer.ui.components.verticalScrollIndicator
import com.phone.contact.calls.dialer.ui.theme.primaryAccentColor
import com.phone.contact.calls.dialer.util.CallUtils
import com.phone.contact.calls.dialer.util.MessageUtils
import com.phone.contact.calls.dialer.util.KeypadTonePreferences
import com.phone.contact.calls.dialer.util.SpeedDialPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun KeypadScreen(
    onCreateNewContact: (String) -> Unit,
    onAddToContact: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    // Long-pressing a digit that has no speed dial assigned yet opens the same picker Settings >
    // Speed Dial uses for that key, matching the reference app's own Keypad long-press behavior.
    onAssignSpeedDial: (String) -> Unit,
    // Set when this screen is opened via a tel: dial intent (e.g. the system's "Call" action on
    // selected text) - pre-fills the number, selected end-to-end like the reference app, so the
    // user lands ready to just confirm instead of retyping it.
    initialNumber: String? = null
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        SpeedDialPreferences.initialize(context)
        KeypadTonePreferences.initialize(context)
    }
    val speedDialEntries by SpeedDialPreferences.entries
    // TextFieldValue (not a plain String) so the typed number has a real, movable cursor - tapping
    // anywhere in it repositions the cursor there (matches the reference app's own EditText-backed
    // display), and digit/backspace presses act at that position instead of always the end.
    var dialedNumberField by remember {
        mutableStateOf(
            initialNumber?.let { TextFieldValue(it, selection = TextRange(0, it.length)) } ?: TextFieldValue("")
        )
    }
    val dialedNumber = dialedNumberField.text
    // Confirmation step before handing off to the contact picker, matching Settings > Speed
    // Dial's own confirm-then-pick flow (SpeedDialScreen's confirmingKey) rather than jumping
    // straight to the picker on long-press.
    var confirmingSpeedDialKey by remember { mutableStateOf<String?>(null) }

    // Reference app's KeypadFragment.onResume() calls binding.v.clearFocus() every time the
    // screen resumes - including right after placing a call and coming back - so a selection made
    // before the call doesn't linger highlighted. Collapsing the selection (not the typed digits)
    // to a plain cursor at the end on every ON_RESUME matches that: the highlight and the
    // selection toolbar disappear, same as the reference app.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                dialedNumberField = dialedNumberField.copy(
                    selection = TextRange(dialedNumberField.text.length)
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // STREAM_DTMF is aliased to the Ring volume stream outside of an active call, so it goes
    // silent under Silent/Vibrate mode or a low ring volume — matching the symptom reported (no
    // sound despite the in-app toggle being on). STREAM_MUSIC follows the Media volume instead,
    // which is what lets the reference app's own keypad tone stay audible in that exact case.
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME * 4 / 5) }
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
        // Match list or Action list occupies the space above the number display + keypad.
        if (dialedNumber.isNotEmpty()) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .verticalScrollIndicator(listState, MaterialTheme.colorScheme.primary)
            ) {
                item {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        KeypadActionRow(
                            icon = Icons.Outlined.PersonAdd,
                            text = stringResource(R.string.create_new_contact),
                            onClick = { onCreateNewContact(dialedNumber) }
                        )
                        KeypadActionRow(
                            icon = Icons.Outlined.PersonAdd, // Using PersonAdd for "Add to a contact"
                            flipIcon = true,
                            text = stringResource(R.string.add_to_a_contact),
                            onClick = { onAddToContact(dialedNumber) }
                        )
                        KeypadActionRow(
                            icon = Icons.AutoMirrored.Filled.Message,
                            text = stringResource(R.string.send_a_message),
                            onClick = { onSendMessage(dialedNumber) }
                        )
                    }
                }
                if (matches.isNotEmpty()) {
                    items(matches, key = { it.id }) { contact ->
                        DialMatchRow(
                            contact = contact,
                            onCall = { CallUtils.placeCall(context, contact.number) },
                            onMessage = { MessageUtils.sendMessage(context, contact.number) }
                        )
                    }
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
                .padding(bottom = 4.dp),
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
                    // A real editable field (not a plain Text) so tapping anywhere in the typed
                    // number moves the cursor/selection there, matching the reference app's own
                    // EditText-backed display - readOnly = true is the only reliable way to block
                    // the system keyboard (digits come from the dialpad below, not the IME; the
                    // CompositionLocalProvider(LocalTextInputService provides null) trick tried
                    // earlier does nothing on this Compose version's text field implementation).
                    // readOnly blocks typed edits and the keyboard, but NOT touch-driven selection
                    // or the native long-press copy/cut toolbar, so both still work. The one real
                    // cost of readOnly is that Compose's own built-in cursor stops rendering, so a
                    // blinking cursor is drawn here by hand, positioned every recomposition from
                    // onTextLayout's getCursorRect() at the field's actual selection - not just
                    // nailed to the end - so it still tracks wherever the user last tapped/typed.
                    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "cursor_blink")
                    val cursorAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(durationMillis = 500),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "cursor_alpha"
                    )

                    // Long-pressing within the number to select text uses Compose's default text
                    // selection colors (tied to MaterialTheme.colorScheme.primary) unless overridden
                    // - primary is a dark, saturated blue that's low-contrast directly on a dark
                    // background (see primaryAccentColor's own doc comment), so the selection
                    // highlight/handles looked wrong in dark mode. primaryAccentColor() already
                    // swaps in the lighter dark-mode variant everywhere else in this app.
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
                            androidx.compose.foundation.text.selection.TextSelectionColors(
                                handleColor = primaryAccentColor(),
                                backgroundColor = primaryAccentColor().copy(alpha = 0.4f)
                            )
                    ) {
                        BasicTextField(
                            value = dialedNumberField,
                            onValueChange = { dialedNumberField = it },
                            readOnly = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                            ),
                            onTextLayout = { textLayoutResult = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    textLayoutResult?.let { layout ->
                        // Clamped against the LAYOUT's own text length, not dialedNumberField's -
                        // onTextLayout lags one frame behind a state change (layout happens after
                        // composition), so right after typing/deleting a digit this `layout` can
                        // still be for the previous (shorter) text while dialedNumberField.selection
                        // already reflects the new, longer one. getCursorRect() throws if the offset
                        // is past what it was actually laid out for - this crashed on every digit.
                        val cursorOffset = dialedNumberField.selection.start
                            .coerceIn(0, layout.layoutInput.text.length)
                        val cursorRect = layout.getCursorRect(cursorOffset)
                        // The text field is vertically centered in this 36dp box (contentAlignment
                        // above) but cursorRect.top is relative to the text field's OWN top edge,
                        // not the box's - this re-adds that centering gap so the cursor lines up
                        // with the actual glyph row instead of sitting too high.
                        val boxHeightPx = with(density) { 36.dp.toPx() }
                        val verticalCenterOffsetPx = (boxHeightPx - layout.size.height) / 2f
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        x = cursorRect.left.toInt(),
                                        y = (verticalCenterOffsetPx + cursorRect.top).toInt()
                                    )
                                }
                                .width(2.dp)
                                .height(with(density) { (cursorRect.bottom - cursorRect.top).toDp() })
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha))
                        )
                    }
                }
            }

            DialPad(
                showBackspace = dialedNumber.isNotEmpty(),
                onDigit = { digit ->
                    playDialFeedback(toneGenerator, vibrator, digit)
                    dialedNumberField = insertAtCursor(dialedNumberField, digit)
                },
                onLongPressDigit = { digit ->
                    // Classic speed dial only fires from a blank dial field — once the user is
                    // already typing a different number, a long-press just acts as a normal digit
                    // press instead of hijacking it into an unrelated call.
                    if (dialedNumber.isEmpty()) {
                        val entry = speedDialEntries[digit]
                        if (entry != null) {
                            CallUtils.placeCall(context, entry.number)
                        } else {
                            // No assignment yet for this key — matches the reference app's own
                            // Keypad long-press: confirm first (same dialog Settings > Speed Dial
                            // itself shows), then open the assign-a-contact picker on "Set".
                            confirmingSpeedDialKey = digit
                        }
                    } else {
                        playDialFeedback(toneGenerator, vibrator, digit)
                        dialedNumberField = insertAtCursor(dialedNumberField, digit)
                    }
                },
                onBackspace = {
                    if (dialedNumberField.text.isNotEmpty()) {
                        dialedNumberField = deleteAtCursor(dialedNumberField)
                    }
                },
                onCall = { CallUtils.placeCall(context, dialedNumber) }
            )
        }
    }

    confirmingSpeedDialKey?.let { key ->
        AlertDialog(
            onDismissRequest = { confirmingSpeedDialKey = null },
            title = { Text(stringResource(R.string.speed_dial_title)) },
            text = { Text(stringResource(R.string.set_speed_dial_number_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingSpeedDialKey = null
                        onAssignSpeedDial(key)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = primaryAccentColor())
                ) {
                    Text(stringResource(R.string.action_set))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmingSpeedDialKey = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = primaryAccentColor())
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Inserts [text] at the cursor position. If text is selected (highlighted), collapses selection
 * to its end and appends/inserts the digit instead of overwriting/erasing the selected digits. */
private fun insertAtCursor(current: TextFieldValue, text: String): TextFieldValue {
    val insertPos = current.selection.max
    val newText = current.text.substring(0, insertPos) + text + current.text.substring(insertPos)
    return current.copy(text = newText, selection = TextRange(insertPos + text.length))
}

/** Deletes the selection if there is one, otherwise the single character before the cursor —
 * matching a real EditText's backspace, not just always dropping the last character. */
private fun deleteAtCursor(current: TextFieldValue): TextFieldValue {
    val start = current.selection.min
    val end = current.selection.max
    if (start != end) {
        val newText = current.text.removeRange(start, end)
        return current.copy(text = newText, selection = TextRange(start))
    }
    if (start == 0) return current
    val newText = current.text.removeRange(start - 1, start)
    return current.copy(text = newText, selection = TextRange(start - 1))
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
    if (KeypadTonePreferences.enabled.value) {
        toneGenerator.startTone(tone, 150)
    }
    // 15ms at amplitude 40 was too short/weak to actually be felt on most hardware - many haptic
    // motors have a 20-30ms spin-up latency before they produce any perceptible pulse at all, so
    // that call technically ran but the vibration was effectively invisible to the user.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createOneShot(25, 80))
    } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(25)
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
    onLongPressDigit: (String) -> Unit,
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
                        DialButton(
                            digit = digit,
                            letters = letters,
                            onClick = { onDigit(digit) },
                            onLongClick = { onLongPressDigit(digit) }
                        )
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
                    val coroutineScope = rememberCoroutineScope()
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            // Holding this repeatedly deletes (matching the reference app's own
                            // keypad) instead of only ever removing one character per tap - the
                            // repeat starts after a short hold so a quick tap still just deletes once.
                            .pointerInput(onBackspace) {
                                detectTapGestures(
                                    onPress = {
                                        onBackspace()
                                        val repeatJob = coroutineScope.launch {
                                            delay(400)
                                            while (true) {
                                                onBackspace()
                                                delay(100)
                                            }
                                        }
                                        tryAwaitRelease()
                                        repeatJob.cancel()
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialButton(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                // The asterisk glyph sits much smaller than a digit at the same font size in most
                // fonts — bumped up so "*" doesn't look tiny next to its neighbors. Its ink also
                // sits higher in the glyph's own box than a digit's does, so it needs a small
                // downward nudge too or it reads as "not centered" even though the Text itself is.
                fontSize = if (digit == "*") 30.sp else 23.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = if (digit == "*") Modifier.offset(y = 4.dp) else Modifier
            )
            // Always reserve this line (even blank) so every digit sits at the same height —
            // otherwise a button with no letters centers on one line while its neighbors center on
            // two, making its digit look shifted down relative to them.
            Text(
                text = letters.ifEmpty { " " },
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
                maxLines = 1
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
            tint = primaryAccentColor(),
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onCall)
        )
    }
}

@Composable
private fun KeypadActionRow(icon: ImageVector, flipIcon: Boolean = false, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).let { if (flipIcon) it.scale(scaleX = -1f, scaleY = 1f) else it }
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

