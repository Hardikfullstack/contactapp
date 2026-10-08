package com.phone.contacts.util

import android.content.Context
import com.phone.contacts.data.model.AppResponse
import com.phone.contacts.data.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

/** Fetches the remote app config once per app launch and caches the last good response, so the
 * app still has its config when offline (same prefs name and key as the sibling contactapp). */
object AppConfigStore {
    private const val PREFS_NAME = "app_config"
    private const val KEY_CACHED = "cached_app_config"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** Live config for Compose screens — seeded from the cache, updated after each successful fetch. */
    val config = MutableStateFlow<AppResponse?>(null)

    fun readCached(context: Context): AppResponse? {
        val cached = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHED, null) ?: return null
        return try {
            injectTestAds(json.decodeFromString(AppResponse.serializer(), cached))
        } catch (_: Exception) {
            null
        }
    }

    /** Network call - run off the main thread. Failures keep the cached config as it is. */
    suspend fun refresh(context: Context) {
        try {
            val response = pinLocalVersion(injectTestAds(ApiClient.fetchAppConfig()))
            if (response.status == 200) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_CACHED, json.encodeToString(AppResponse.serializer(), response))
                    .apply()
                config.value = response
            }
        } catch (e: Exception) {
            // Offline or server error - the cached config stays in place.
            CrashlyticsManager.recordException(e)
        }
    }

    /** Local overrides, regardless of what the panel sends:
     * - extra_data_2_message (the "latest version" the in-app update prompt compares against) is
     *   pinned to "1.0.0", so the update prompt never fires for this build.
     * - extra_data_1_on_off (maintenance kill switch) is forced "on", so the maintenance screen
     *   blocks the app.
     * Delete these overrides (and the call site in refresh) to let the panel control them again. */
    private fun pinLocalVersion(response: AppResponse): AppResponse {
        val result = response.result ?: return response
        return response.copy(
            result = result.copy(
                extra_data_2_message = "1.0.0",
                extra_data_1_on_off = "off"
            )
        )
    }

    /**
     * Temporary: forces every ad slot (banner, native, interstitial, app open) onto Google's
     * official test ad unit IDs and "on", regardless of what the panel actually returns — so ad
     * placements across the app can be built and verified before this app has real, approved ad
     * units. Applied here (both [readCached] and [refresh]) instead of in each screen, so every
     * screen just calls the real `AdPlacements.adUnitId(..., AdType.X, slot = N)` as if the panel
     * already had real ids wired up. Delete this one function (and its two call sites above) once
     * real ad unit ids are approved — no screen needs to change.
     */
    private fun injectTestAds(response: AppResponse): AppResponse {
        val result = response.result ?: return response
        val testNative = "ca-app-pub-3940256099942544/2247696110"
        val testBanner = "ca-app-pub-3940256099942544/6300978111"
        val testInterstitial = "ca-app-pub-3940256099942544/1033173712"
        val testAppOpen = "ca-app-pub-3940256099942544/9257395921"
        return response.copy(
            result = result.copy(
                google_ads_on_off = "on",

                native_1 = testNative, native_1_on_off = "on",
                native_2 = testNative, native_2_on_off = "on",
                native_3 = testNative, native_3_on_off = "on",
                native_4 = testNative, native_4_on_off = "on",
                native_5 = testNative, native_5_on_off = "on",
                native_6 = testNative, native_6_on_off = "on",
                native_7 = testNative, native_7_on_off = "on",
                native_8 = testNative, native_8_on_off = "on",
                native_9 = testNative, native_9_on_off = "on",
                native_10 = testNative, native_10_on_off = "on",
                native_11 = testNative, native_11_on_off = "on",
                native_12 = testNative, native_12_on_off = "on",
                native_13 = testNative, native_13_on_off = "on",
                native_14 = testNative, native_14_on_off = "on",
                native_15 = testNative, native_15_on_off = "on",
                native_16 = testNative, native_16_on_off = "on",

                banner_1 = testBanner, banner_1_on_off = "on",
                banner_2 = testBanner, banner_2_on_off = "on",
                banner_3 = testBanner, banner_3_on_off = "on",
                banner_4 = testBanner, banner_4_on_off = "on",
                banner_5 = testBanner, banner_5_on_off = "on",
                banner_6 = testBanner, banner_6_on_off = "on",
                banner_7 = testBanner, banner_7_on_off = "on",
                banner_8 = testBanner, banner_8_on_off = "on",
                banner_9 = testBanner, banner_9_on_off = "on",
                banner_10 = testBanner, banner_10_on_off = "on",

                interstitial_1 = testInterstitial, interstitial_1_on_off = "on",
                interstitial_2 = testInterstitial, interstitial_2_on_off = "on",
                interstitial_3 = testInterstitial, interstitial_3_on_off = "on",
                interstitial_4 = testInterstitial, interstitial_4_on_off = "on",
                interstitial_5 = testInterstitial, interstitial_5_on_off = "on",
                interstitial_6 = testInterstitial, interstitial_6_on_off = "on",
                interstitial_7 = testInterstitial, interstitial_7_on_off = "on",

                app_open_1 = testAppOpen, app_open_1_on_off = "on",
                app_open_2 = testAppOpen, app_open_2_on_off = "on",
                app_open_3 = testAppOpen, app_open_3_on_off = "on",
                app_open_4 = testAppOpen, app_open_4_on_off = "on"
            )
        )
    }
}
