package dev.hossain.trmnl.network

import com.slack.eithernet.ApiResult
import dev.hossain.trmnl.network.model.TrmnlDisplayResponse
import dev.hossain.trmnl.network.model.TrmnlLogResponse
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * API service interface for TRMNL.
 *
 * This interface defines the endpoints for the TRMNL API.
 *
 * See:
 * - https://docs.usetrmnl.com/go
 */
interface TrmnlApiService {
    /**
     * Retrieve TRMNL image data, device-free.
     */
    @GET("api/display")
    suspend fun getDisplayData(
        @Header("access-token") accessToken: String,
    ): ApiResult<TrmnlDisplayResponse, Unit>

    @GET("api/log")
    suspend fun getLog(
        @Header("ID") id: String,
        @Header("Access-Token") accessToken: String,
    ): ApiResult<TrmnlLogResponse, Unit>
}
