package com.movit.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mobile subscription / billing contracts (Ktor + kotlinx.serialization) — the single source of
 * truth shared between Android (Play Billing + MyFatoorah) and iOS (StoreKit 2).
 * Replaces the legacy Retrofit/Gson models under `feature:billing/network`.
 *
 * `name` / `description` / `features` stay as raw [JsonElement] because the backend returns them
 * polymorphically (plain string, `{en, ar}` object, or localized array).
 */
@Serializable
data class SubscriptionApiEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: String? = null,
)

@Serializable
data class MobilePlansEnvelope(
    val success: Boolean = false,
    val data: List<SubscriptionPlanDto>? = null,
    val error: String? = null,
)

@Serializable
data class SubscriptionPlanDto(
    val id: String,
    val name: JsonElement? = null,
    val description: JsonElement? = null,
    val monthlyPrice: Double = 0.0,
    val yearlyPrice: Double = 0.0,
    val currency: String? = "EGP",
    val discount: Double? = 0.0,
    val maxExercisesLimit: Int = 0,
    @SerialName("maxWorkoutTemplatesLimit")
    val maxWorkoutTemplatesLimit: Int = 0,
    val freeDoctorSessionsLimit: Int = 0,
    val monthlyGooglePlayProductId: String? = null,
    val yearlyGooglePlayProductId: String? = null,
    val monthlyAppStoreProductId: String? = null,
    val yearlyAppStoreProductId: String? = null,
    val features: JsonElement? = null,
    val isActive: Boolean = true,
)

@Serializable
data class SubscriptionStatusDto(
    val isPro: Boolean = false,
    val isFree: Boolean = false,
    val subscriptionExpiry: String? = null,
    val activeSubscription: SubscriptionRowDto? = null,
    val pendingCheckouts: List<SubscriptionCheckoutDto> = emptyList(),
)

@Serializable
data class SubscriptionRowDto(
    val id: String,
    val userId: String? = null,
    val planId: String = "",
    val status: String? = null,
    val billingPeriod: String? = null,
    val gateway: String? = null,
    val amountPaid: Double? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val plan: SubscriptionPlanDto? = null,
)

@Serializable
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

@Serializable
data class CreateCheckoutRequest(
    val planId: String,
    val billingPeriod: String,
    val gateway: String = "myfatoorah",
    val idempotencyKey: String? = null,
    val replaceSubscriptionId: String? = null,
)

@Serializable
data class VerifyGooglePlayRequest(
    val planId: String,
    val billingPeriod: String,
    val productId: String,
    val purchaseToken: String,
    val packageName: String? = null,
    val orderId: String? = null,
    val linkedPurchaseToken: String? = null,
)

@Serializable
data class CancelSubscriptionRequest(
    val subscriptionId: String? = null,
    val immediate: Boolean? = false,
    val reason: String? = null,
)

@Serializable
data class VerifyGooglePlayResponse(
    val subscription: SubscriptionRowDto? = null,
    val status: SubscriptionStatusDto? = null,
)

@Serializable
data class VerifyAppStoreRequest(
    val planId: String,
    val billingPeriod: String,
    val productId: String,
    val transactionId: String,
    val originalTransactionId: String,
    val signedTransactionInfo: String,
)

@Serializable
data class VerifyAppStoreResponse(
    val subscription: SubscriptionRowDto? = null,
    val status: SubscriptionStatusDto? = null,
)

@Serializable
data class CancelSubscriptionResponse(
    val subscription: SubscriptionRowDto? = null,
    val status: SubscriptionStatusDto? = null,
)
