package com.movit.core.network

import com.movit.core.network.dto.CancelSubscriptionRequest
import com.movit.core.network.dto.CancelSubscriptionResponse
import com.movit.core.network.dto.CreateCheckoutRequest
import com.movit.core.network.dto.MobilePlansEnvelope
import com.movit.core.network.dto.SubscriptionApiEnvelope
import com.movit.core.network.dto.SubscriptionCheckoutDto
import com.movit.core.network.dto.SubscriptionStatusDto
import com.movit.core.network.dto.VerifyAppStoreRequest
import com.movit.core.network.dto.VerifyAppStoreResponse
import com.movit.core.network.dto.VerifyGooglePlayRequest
import com.movit.core.network.dto.VerifyGooglePlayResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Ktor billing API — mobile subscription endpoints (Play Billing verify, MyFatoorah checkout,
 * status, cancel). Shares the authed [HttpClient] with [MovitMobileApi]; auth is passed per call.
 */
class MovitBillingApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String,
) {
    private fun base(path: String): String {
        val root = baseUrlProvider().trimEnd('/')
        return "$root/${path.removePrefix("/")}"
    }

    private fun HttpRequestBuilder.bearer(authorization: String?) {
        authorization?.let { header("Authorization", it) }
    }

    suspend fun fetchPlans(authorization: String?): Result<MobilePlansEnvelope> = runCatching {
        val response = client.get(base("api/mobile/plans")) { bearer(authorization) }
        if (!response.status.isSuccess()) error("Plans request failed (${response.status.value})")
        response.body<MobilePlansEnvelope>()
    }

    suspend fun fetchStatus(authorization: String?): Result<SubscriptionApiEnvelope<SubscriptionStatusDto>> = runCatching {
        val response = client.get(base("api/mobile/subscriptions/status")) { bearer(authorization) }
        if (!response.status.isSuccess()) error("Status request failed (${response.status.value})")
        response.body<SubscriptionApiEnvelope<SubscriptionStatusDto>>()
    }

    suspend fun createCheckout(
        request: CreateCheckoutRequest,
        authorization: String?,
    ): Result<SubscriptionApiEnvelope<SubscriptionCheckoutDto>> = runCatching {
        val response = client.post(base("api/mobile/subscriptions/checkout")) {
            bearer(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) error("Checkout request failed (${response.status.value})")
        response.body<SubscriptionApiEnvelope<SubscriptionCheckoutDto>>()
    }

    suspend fun fetchCheckout(
        checkoutId: String,
        authorization: String?,
    ): Result<SubscriptionApiEnvelope<SubscriptionCheckoutDto>> = runCatching {
        val response = client.get(base("api/mobile/subscriptions/checkout/$checkoutId")) { bearer(authorization) }
        if (!response.status.isSuccess()) error("Checkout status request failed (${response.status.value})")
        response.body<SubscriptionApiEnvelope<SubscriptionCheckoutDto>>()
    }

    suspend fun verifyGooglePlay(
        request: VerifyGooglePlayRequest,
        authorization: String?,
    ): Result<SubscriptionApiEnvelope<VerifyGooglePlayResponse>> = runCatching {
        val response = client.post(base("api/mobile/subscriptions/google-play/verify")) {
            bearer(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) error("Google Play verify failed (${response.status.value})")
        response.body<SubscriptionApiEnvelope<VerifyGooglePlayResponse>>()
    }

    suspend fun verifyAppStore(
        request: VerifyAppStoreRequest,
        authorization: String?,
    ): Result<SubscriptionApiEnvelope<VerifyAppStoreResponse>> = runCatching {
        val response = client.post(base("api/mobile/subscriptions/app-store/verify")) {
            bearer(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) error("App Store verify failed (${response.status.value})")
        response.body<SubscriptionApiEnvelope<VerifyAppStoreResponse>>()
    }

    suspend fun cancel(
        request: CancelSubscriptionRequest,
        authorization: String?,
    ): Result<SubscriptionApiEnvelope<CancelSubscriptionResponse>> = runCatching {
        val response = client.post(base("api/mobile/subscriptions/cancel")) {
            bearer(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) error("Cancel request failed (${response.status.value})")
        response.body<SubscriptionApiEnvelope<CancelSubscriptionResponse>>()
    }
}
