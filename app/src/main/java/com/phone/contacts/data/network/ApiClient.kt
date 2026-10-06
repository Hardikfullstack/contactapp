package com.phone.contacts.data.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.phone.contacts.data.model.AppResponse
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://panel.aavakar.com/"

    // Panel package identifier used to fetch this app's remote config.
    private const val API_PACKAGE_NAME = "common_dev"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // Logging stays off: request/response bodies are not written to logcat in any build.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit by lazy {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    /** Fetches the remote app config for this app. */
    suspend fun fetchAppConfig(): AppResponse {
        val packageNameBody = API_PACKAGE_NAME.toRequestBody("text/plain".toMediaTypeOrNull())
        return apiService.getAppData(packageNameBody)
    }
}
