package com.phone.contacts.ads

import com.phone.contacts.data.model.AppResult

/**
 * The ad types the app can show. Each one reads its unit id and on/off switch from the remote
 * config (AppResult), by numbered slot: banner_1..10, native_1..16, interstitial_1..7, app_open_1..4.
 *
 * INTERSTITIAL_ON_BACK is an interstitial shown when the user leaves a screen (back / exit). It uses
 * the interstitial slots too, so pass the slot the panel gives that placement.
 *
 * Nothing in the app calls this yet — no ad type is wired to a screen.
 */
enum class AdType { BANNER, NATIVE, INTERSTITIAL, INTERSTITIAL_ON_BACK, APP_OPEN }

object AdPlacements {
    /**
     * Returns the ad unit id for [type] in [slot], or null when Google ads are off, the slot's switch
     * is not "on", or the id is blank. The screens that use this decide when to load and show it.
     */
    fun adUnitId(result: AppResult?, type: AdType, slot: Int = 1): String? {

        if (result == null || result.google_ads_on_off != "on") return null
        val (adUnitId, switch) = when (type) {
            AdType.BANNER -> banner(result, slot)
            AdType.NATIVE -> native(result, slot)
            AdType.INTERSTITIAL, AdType.INTERSTITIAL_ON_BACK -> interstitial(result, slot)
            AdType.APP_OPEN -> appOpen(result, slot)
        }
        if (switch != "on") return null
        return adUnitId?.takeIf { it.isNotBlank() }
    }

    /** Interstitial shown on back / exit: a separate slot from the regular interstitials. Confirm
     * the actual panel slot for this placement before wiring it to a screen - 4 is a placeholder. */
    fun interstitialOnBackAdUnitId(result: AppResult?, slot: Int = 4): String? =
        adUnitId(result, AdType.INTERSTITIAL_ON_BACK, slot)

    private fun banner(result: AppResult, slot: Int): Pair<String?, String?> = when (slot) {
            1 -> result.banner_1 to result.banner_1_on_off
            2 -> result.banner_2 to result.banner_2_on_off
            3 -> result.banner_3 to result.banner_3_on_off
            4 -> result.banner_4 to result.banner_4_on_off
            5 -> result.banner_5 to result.banner_5_on_off
            6 -> result.banner_6 to result.banner_6_on_off
            7 -> result.banner_7 to result.banner_7_on_off
            8 -> result.banner_8 to result.banner_8_on_off
            9 -> result.banner_9 to result.banner_9_on_off
            10 -> result.banner_10 to result.banner_10_on_off
            else -> null to null
        }

    private fun native(result: AppResult, slot: Int): Pair<String?, String?> = when (slot) {
            1 -> result.native_1 to result.native_1_on_off
            2 -> result.native_2 to result.native_2_on_off
            3 -> result.native_3 to result.native_3_on_off
            4 -> result.native_4 to result.native_4_on_off
            5 -> result.native_5 to result.native_5_on_off
            6 -> result.native_6 to result.native_6_on_off
            7 -> result.native_7 to result.native_7_on_off
            8 -> result.native_8 to result.native_8_on_off
            9 -> result.native_9 to result.native_9_on_off
            10 -> result.native_10 to result.native_10_on_off
            11 -> result.native_11 to result.native_11_on_off
            12 -> result.native_12 to result.native_12_on_off
            13 -> result.native_13 to result.native_13_on_off
            14 -> result.native_14 to result.native_14_on_off
            15 -> result.native_15 to result.native_15_on_off
            16 -> result.native_16 to result.native_16_on_off
            17 -> result.native_17 to result.native_17_on_off
            18 -> result.native_18 to result.native_18_on_off
            19 -> result.native_19 to result.native_19_on_off
            20 -> result.native_20 to result.native_20_on_off
            21 -> result.native_21 to result.native_21_on_off
            else -> null to null
        }

    private fun interstitial(result: AppResult, slot: Int): Pair<String?, String?> = when (slot) {
            1 -> result.interstitial_1 to result.interstitial_1_on_off
            2 -> result.interstitial_2 to result.interstitial_2_on_off
            3 -> result.interstitial_3 to result.interstitial_3_on_off
            4 -> result.interstitial_4 to result.interstitial_4_on_off
            5 -> result.interstitial_5 to result.interstitial_5_on_off
            6 -> result.interstitial_6 to result.interstitial_6_on_off
            7 -> result.interstitial_7 to result.interstitial_7_on_off
            else -> null to null
        }

    private fun appOpen(result: AppResult, slot: Int): Pair<String?, String?> = when (slot) {
            1 -> result.app_open_1 to result.app_open_1_on_off
            2 -> result.app_open_2 to result.app_open_2_on_off
            3 -> result.app_open_3 to result.app_open_3_on_off
            4 -> result.app_open_4 to result.app_open_4_on_off
            else -> null to null
        }
}
