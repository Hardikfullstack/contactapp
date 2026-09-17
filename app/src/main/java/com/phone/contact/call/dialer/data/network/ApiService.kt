package com.phone.contact.call.dialer.data.network

import com.phone.contact.call.dialer.data.model.AppResponse
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("api/getApp")
    suspend fun getAppData(
        @Part("package_name") packageName: RequestBody
    ): AppResponse
}
