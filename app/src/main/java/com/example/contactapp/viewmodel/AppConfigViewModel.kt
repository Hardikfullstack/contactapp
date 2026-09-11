package com.example.contactapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.data.model.AppResponse
import com.example.contactapp.data.network.ApiClient
import com.example.contactapp.util.AnalyticsManager
import com.example.contactapp.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class AppConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_config", Context.MODE_PRIVATE)
    private val defaultAppName = "Contacts"

    companion object {
        /** Reads the same cached config this ViewModel uses, for callers that can't hold a
         * ViewModel instance (e.g. a BroadcastReceiver) — used to kick off an ad preload as
         * early as possible before the owning screen even composes. */
        fun readCachedResult(context: Context): com.example.contactapp.data.model.AppResult? {
            val cachedPrefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
            val cachedJson = cachedPrefs.getString("cached_app_config", null) ?: return null
            return try {
                Json { ignoreUnknownKeys = true; coerceInputValues = true }
                    .decodeFromString<AppResponse>(cachedJson).result
            } catch (e: Exception) {
                null
            }
        }
    }

    private val _appResponse = MutableStateFlow(loadCachedResponse())
    val appResponse: StateFlow<AppResponse?> = _appResponse

    private val _dynamicAppName = MutableStateFlow(
        _appResponse.value?.result?.app_name?.takeIf { it.isNotBlank() }
            ?: prefs.getString("dynamic_app_name", defaultAppName)?.takeIf { it.isNotBlank() } ?: defaultAppName
    )
    val dynamicAppName: StateFlow<String> = _dynamicAppName

    // Drives an "you're offline" dialog and re-triggers a fetch once connectivity returns.
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline

    init {
        fetchAppData()
        observeNetwork()
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            // NetworkMonitor emits the current status immediately on subscription — init{} already
            // did the initial fetchAppData(), so skip that first emission and only refetch on an
            // actual offline→online transition (real reconnect), not the startup snapshot.
            var isFirstEmission = true
            NetworkMonitor(getApplication()).isOnline.collectLatest { online ->
                _isOnline.value = online
                if (!isFirstEmission && online && (_appResponse.value == null || _appResponse.value?.title == "Loaded from cache")) {
                    fetchAppData()
                }
                isFirstEmission = false
            }
        }
    }

    private fun loadCachedResponse(): AppResponse? {
        val cachedJson = prefs.getString("cached_app_config", null) ?: return null
        return try {
            val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            jsonParser.decodeFromString<AppResponse>(cachedJson).copy(title = "Loaded from cache")
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchAppData() {
        viewModelScope.launch {
            try {
                val rawResponse = ApiClient.fetchAppConfig()
                val response = injectTestAds(rawResponse)
                if (response.status == 200) {
                    _appResponse.value = response

                    response.result?.let { result ->
                        val editor = prefs.edit()

                        result.app_name?.takeIf { it.isNotBlank() }?.let {
                            editor.putString("dynamic_app_name", it)
                            _dynamicAppName.value = it
                        }
                        try {
                            val jsonString = Json { ignoreUnknownKeys = true }
                                .encodeToString(AppResponse.serializer(), response)
                            editor.putString("cached_app_config", jsonString)
                        } catch (e: Exception) {
                            // Cache-write failure — in-memory state above is already updated.
                        }

                        editor.apply()
                    }
                }
            } catch (e: Exception) {
                // Offline or server error — cached/default state (already loaded) stays as-is.
            }
        }
    }

    /**
     * Temporary: forces every ad slot except Rewarded onto Google's official test ad unit IDs
     * and "on", regardless of what the panel actually returned — so ad placements can be built
     * and verified before this app has real, approved ad units. Rewarded is left untouched so it
     * keeps whatever the server sends. Remove once real ad unit IDs are wired up.
     */
    private fun injectTestAds(response: AppResponse): AppResponse {
        val res = response.result ?: return response
        val testResult = res.copy(
            google_ads_on_off = "on",
            app_name = "Contacts",
            native_1 = "ca-app-pub-3940256099942544/2247696110",
            native_2 = "ca-app-pub-3940256099942544/2247696110",
            native_3 = "ca-app-pub-3940256099942544/2247696110",
            native_4 = "ca-app-pub-3940256099942544/2247696110",
            native_5 = "ca-app-pub-3940256099942544/2247696110",
            native_6 = "ca-app-pub-3940256099942544/2247696110",
            native_7 = "ca-app-pub-3940256099942544/2247696110",
            native_8 = "ca-app-pub-3940256099942544/2247696110",
            native_9 = "ca-app-pub-3940256099942544/2247696110",
            native_10 = "ca-app-pub-3940256099942544/2247696110",
            native_11 = "ca-app-pub-3940256099942544/2247696110",
            native_12 = "ca-app-pub-3940256099942544/2247696110",
            native_13 = "ca-app-pub-3940256099942544/2247696110",
            native_14 = "ca-app-pub-3940256099942544/2247696110",
            native_15 = "ca-app-pub-3940256099942544/2247696110",
            native_1_on_off = "on",
            native_2_on_off = "on",
            native_3_on_off = "on",
            native_4_on_off = "on",
            native_5_on_off = "on",
            native_6_on_off = "on",
            native_7_on_off = "on",
            native_8_on_off = "on",
            native_9_on_off = "on",
            native_10_on_off = "on",
            native_11_on_off = "on",
            native_12_on_off = "on",
            native_13_on_off = "on",
            native_14_on_off = "on",
            native_15_on_off = "on",
            extra_data_2_message = "1.0.0",

            banner_1 = "ca-app-pub-3940256099942544/6300978111",
            banner_2 = "ca-app-pub-3940256099942544/6300978111",
            banner_3 = "ca-app-pub-3940256099942544/6300978111",
            banner_4 = "ca-app-pub-3940256099942544/6300978111",
            banner_5 = "ca-app-pub-3940256099942544/6300978111",
            banner_6 = "ca-app-pub-3940256099942544/6300978111",
            banner_7 = "ca-app-pub-3940256099942544/6300978111",
            banner_8 = "ca-app-pub-3940256099942544/6300978111",
            banner_9 = "ca-app-pub-3940256099942544/6300978111",
            banner_10 = "ca-app-pub-3940256099942544/6300978111",
            banner_1_on_off = "on",
            banner_2_on_off = "on",
            banner_3_on_off = "on",
            banner_4_on_off = "on",
            banner_5_on_off = "on",
            banner_6_on_off = "on",
            banner_7_on_off = "on",
            banner_8_on_off = "on",
            banner_9_on_off = "on",
            banner_10_on_off = "on",

            interstitial_1 = "ca-app-pub-3940256099942544/1033173712",
            interstitial_2 = "ca-app-pub-3940256099942544/1033173712",
            interstitial_3 = "ca-app-pub-3940256099942544/1033173712",
            interstitial_4 = "ca-app-pub-3940256099942544/1033173712",
            interstitial_5 = "ca-app-pub-3940256099942544/1033173712",
            interstitial_6 = "ca-app-pub-3940256099942544/1033173712",
            interstitial_7 = "ca-app-pub-3940256099942544/1033173712",
            interstitial_1_on_off = "on",
            interstitial_2_on_off = "on",
            interstitial_3_on_off = "on",
            interstitial_4_on_off = "on",
            interstitial_5_on_off = "on",
            interstitial_6_on_off = "on",
            interstitial_7_on_off = "on",

            app_open_1 = "ca-app-pub-3940256099942544/9257395921",
            app_open_2 = "ca-app-pub-3940256099942544/9257395921",
            app_open_3 = "ca-app-pub-3940256099942544/9257395921",
            app_open_4 = "ca-app-pub-3940256099942544/9257395921",
            app_open_1_on_off = "on",
            app_open_2_on_off = "on",
            app_open_3_on_off = "on",
            app_open_4_on_off = "on",

            back_click = "1"
        )
        return response.copy(result = testResult)
    }
}
