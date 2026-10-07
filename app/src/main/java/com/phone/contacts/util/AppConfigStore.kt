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
            json.decodeFromString(AppResponse.serializer(), cached)
        } catch (_: Exception) {
            null
        }
    }

    /** Network call - run off the main thread. Failures keep the cached config as it is. */
    suspend fun refresh(context: Context) {
        try {
            val response = pinLocalVersion(ApiClient.fetchAppConfig())
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
}
