package com.phone.contacts.ui.features.call

import androidx.compose.ui.graphics.Color

/** One face (accept or decline) of a call-button style — a solid color (one entry) or a
 * top-to-bottom gradient (two entries), plus the phone-icon color drawn on top. Matches the
 * reference app's own accept/decline icon assets, sampled directly from its APK. */
data class ButtonFace(val background: List<Color>, val iconColor: Color)

/** A selectable accept/decline button look, matching the reference app's "Call button styles"
 * setting. The reference app gates most of these behind a rewarded-ad unlock; this app has no
 * ads SDK, so every style here is simply free. [SLIDER] isn't a color style at all — it's this
 * app's existing swipe-to-answer incoming-call UI (see CallScreen's SlideToAnswer), kept as one
 * of the selectable options alongside the tap-button styles rather than replaced by them.
 *
 * Only [STYLE_6] (the 7th item in the grid, counting SLIDER as the 1st) gets the animated
 * up-chevron hint and the drag-up-to-answer/decline gesture — every other tap style is plain
 * tap-only, no chevron. */
enum class CallButtonStyle(val accept: ButtonFace?, val decline: ButtonFace?, val hasSwipeGesture: Boolean = false) {
    SLIDER(null, null),
    STYLE_1(ButtonFace(listOf(Color(0xFF7CBF35)), Color.White), ButtonFace(listOf(Color(0xFFEE5637)), Color.White)),
    STYLE_2(
        ButtonFace(listOf(Color(0xFF83DC4D), Color(0xFF379101)), Color.White),
        ButtonFace(listOf(Color(0xFFFF9900), Color(0xFFFF4F35)), Color.White)
    ),
    STYLE_3(
        ButtonFace(listOf(Color(0xFF6D9C30), Color(0xFFCFF547)), Color.White),
        ButtonFace(listOf(Color(0xFFB50E1F), Color(0xFFFF615C)), Color.White)
    ),
    STYLE_4(ButtonFace(listOf(Color.White), Color(0xFF00B000)), ButtonFace(listOf(Color.White), Color(0xFFED1C24))),
    STYLE_5(ButtonFace(listOf(Color.White), Color(0xFF29CC00)), ButtonFace(listOf(Color.White), Color(0xFFFF3B30))),
    STYLE_6(ButtonFace(listOf(Color(0xFF39B54A)), Color.White), ButtonFace(listOf(Color(0xFFED1C24)), Color.White), hasSwipeGesture = true),
    STYLE_7(ButtonFace(listOf(Color(0xFF7ED54A)), Color.White), ButtonFace(listOf(Color(0xFFFF4545)), Color.White)),
    STYLE_8(
        ButtonFace(listOf(Color(0xFF44E654), Color(0xFF1A9963)), Color.White),
        ButtonFace(listOf(Color(0xFFF03C3C), Color(0xFFB31212)), Color.White)
    ),
    STYLE_9(ButtonFace(listOf(Color(0xFF2AD171)), Color.White), ButtonFace(listOf(Color(0xFFFF274C)), Color.White)),
    STYLE_10(ButtonFace(listOf(Color(0xFF00BD23)), Color.White), ButtonFace(listOf(Color(0xFFFF002D)), Color.White)),
    STYLE_11(ButtonFace(listOf(Color(0xFF00B10C)), Color.White), ButtonFace(listOf(Color(0xFFFF0066)), Color.White)),
    STYLE_12(ButtonFace(listOf(Color(0xFF3C9606)), Color.White), ButtonFace(listOf(Color(0xFFFF0000)), Color.White)),
    STYLE_13(ButtonFace(listOf(Color(0xFF009E1D)), Color.White), ButtonFace(listOf(Color(0xFFFF0049)), Color.White)),
    STYLE_14(ButtonFace(listOf(Color(0xFF4AA02A)), Color.White), ButtonFace(listOf(Color(0xFFC21C1C)), Color.White)),
    STYLE_15(
        ButtonFace(listOf(Color.White), Color(0xFF11998E)),
        ButtonFace(listOf(Color.White), Color(0xFFE52D27))
    ),
    STYLE_16(ButtonFace(listOf(Color(0xFF46D85C)), Color.White), ButtonFace(listOf(Color(0xFFFB3E2B)), Color.White));

    val isSlider: Boolean get() = this == SLIDER
}
