package dev.hossain.trmnl.network

import dev.hossain.trmnl.network.model.TrmnlDisplayResponse
import retrofit2.http.GET
import retrofit2.http.Header

interface TrmnlApiService {
    @GET("api/display")
    suspend fun getDisplayData(
        @Header("access-token") accessToken: String,
    ): TrmnlDisplayResponse
}
