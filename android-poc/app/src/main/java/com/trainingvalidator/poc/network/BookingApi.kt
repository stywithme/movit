package com.trainingvalidator.poc.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * User booking endpoints (mobile auth).
 */
interface BookingApi {
    @GET("api/bookings/rules")
    suspend fun getBookingRules(
        @Header("Authorization") authorization: String,
    ): Response<AuthApiResponse<BookingRulesDto>>
}
