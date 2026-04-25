package com.trainingvalidator.poc.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SubscriptionApi {

    @GET("api/mobile/plans")
    suspend fun getPlans(): Response<MobilePlansEnvelope>

    @GET("api/mobile/subscriptions/status")
    suspend fun getStatus(): Response<SubscriptionApiEnvelope<SubscriptionStatusDto>>

    @GET("api/mobile/subscriptions/mine")
    suspend fun getMine(): Response<SubscriptionApiEnvelope<List<SubscriptionRowDto>>>

    @POST("api/mobile/subscriptions/checkout")
    suspend fun createCheckout(
        @Body body: CreateCheckoutRequest,
    ): Response<SubscriptionApiEnvelope<SubscriptionCheckoutDto>>

    @GET("api/mobile/subscriptions/checkout/{id}")
    suspend fun getCheckout(
        @Path("id") id: String,
    ): Response<SubscriptionApiEnvelope<SubscriptionCheckoutDto>>

    @POST("api/mobile/subscriptions/google-play/verify")
    suspend fun verifyGooglePlay(
        @Body body: VerifyGooglePlayRequest,
    ): Response<SubscriptionApiEnvelope<VerifyGooglePlayResponse>>

    @POST("api/mobile/subscriptions/cancel")
    suspend fun cancel(
        @Body body: CancelSubscriptionRequest,
    ): Response<SubscriptionApiEnvelope<CancelSubscriptionResponse>>
}
