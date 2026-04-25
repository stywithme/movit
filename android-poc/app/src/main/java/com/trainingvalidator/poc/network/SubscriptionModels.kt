package com.trainingvalidator.poc.network

import com.google.gson.JsonElement

/**
 * Mobile subscription API payloads (matches Nest mobile/subscriptions + mobile/plans).
 */

data class SubscriptionApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
)

data class MobilePlansEnvelope(
    val success: Boolean,
    val data: List<SubscriptionPlanDto>? = null,
    val error: String? = null,
)

data class SubscriptionPlanDto(
    val id: String,
    val name: JsonElement? = null,
    val description: JsonElement? = null,
    val monthlyPrice: Double = 0.0,
    val yearlyPrice: Double = 0.0,
    val currency: String? = "EGP",
    val discount: Double? = 0.0,
    val monthlyGooglePlayProductId: String? = null,
    val yearlyGooglePlayProductId: String? = null,
    val features: JsonElement? = null,
    val isActive: Boolean = true,
)

data class SubscriptionStatusDto(
    val isPro: Boolean,
    val isFree: Boolean,
    val subscriptionExpiry: String? = null,
    val activeSubscription: SubscriptionRowDto? = null,
    val pendingCheckouts: List<SubscriptionCheckoutDto> = emptyList(),
)

data class SubscriptionRowDto(
    val id: String,
    val userId: String? = null,
    val planId: String,
    val status: String? = null,
    val billingPeriod: String? = null,
    val gateway: String? = null,
    val amountPaid: Double? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val plan: SubscriptionPlanDto? = null,
)

data class SubscriptionCheckoutDto(
    val id: String,
    val userId: String? = null,
    val planId: String? = null,
    val subscriptionId: String? = null,
    val gateway: String? = null,
    val billingPeriod: String? = null,
    val status: String? = null,
    val currency: String? = null,
    val amount: Double? = null,
    val paymentUrl: String? = null,
    val paidAt: String? = null,
    val plan: SubscriptionPlanDto? = null,
    val subscription: SubscriptionRowDto? = null,
)

data class CreateCheckoutRequest(
    val planId: String,
    val billingPeriod: String,
    val gateway: String = "myfatoorah",
    val idempotencyKey: String? = null,
    val replaceSubscriptionId: String? = null,
)

data class VerifyGooglePlayRequest(
    val planId: String,
    val billingPeriod: String,
    val productId: String,
    val purchaseToken: String,
    val packageName: String? = null,
    val orderId: String? = null,
    val linkedPurchaseToken: String? = null,
)

data class CancelSubscriptionRequest(
    val subscriptionId: String? = null,
    val immediate: Boolean? = false,
    val reason: String? = null,
)

data class VerifyGooglePlayResponse(
    val subscription: SubscriptionRowDto? = null,
    val status: SubscriptionStatusDto? = null,
)

data class CancelSubscriptionResponse(
    val subscription: SubscriptionRowDto? = null,
    val status: SubscriptionStatusDto? = null,
)
