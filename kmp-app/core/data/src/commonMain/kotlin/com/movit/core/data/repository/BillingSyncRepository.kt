package com.movit.core.data.repository

import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitBillingApi
import com.movit.core.network.dto.CancelSubscriptionRequest
import com.movit.core.network.dto.CancelSubscriptionResponse
import com.movit.core.network.dto.CreateCheckoutRequest
import com.movit.core.network.dto.SubscriptionCheckoutDto
import com.movit.core.network.dto.SubscriptionPlanDto
import com.movit.core.network.dto.SubscriptionStatusDto
import com.movit.core.network.dto.VerifyAppStoreRequest
import com.movit.core.network.dto.VerifyAppStoreResponse
import com.movit.core.network.dto.VerifyGooglePlayRequest
import com.movit.core.network.dto.VerifyGooglePlayResponse
import com.movit.resources.localizedString
import com.movit.shared.AppResult

/**
 * KMP billing repository — wraps [MovitBillingApi] and unwraps the `{success,data,error}` envelope
 * into [AppResult]. Auth comes from the shared secure session via the platform bindings.
 */
class BillingSyncRepository(
    private val api: MovitBillingApi,
    private val platform: () -> MovitPlatformBindings,
) {
    private fun auth(): String? = platform().authHeader()

    private suspend fun billingError(key: String, fallback: String? = null): String =
        fallback?.takeIf { it.isNotBlank() }
            ?: localizedString(platform().preferredLanguage(), key)

    suspend fun getPlans(): AppResult<List<SubscriptionPlanDto>> {
        val res = api.fetchPlans(auth()).getOrElse {
            return AppResult.Failure(billingError("billing_load_plans_failed", it.message))
        }
        if (!res.success) return AppResult.Failure(billingError("billing_load_plans_failed", res.error))
        return AppResult.Success(res.data.orEmpty())
    }

    suspend fun getStatus(): AppResult<SubscriptionStatusDto> {
        val res = api.fetchStatus(auth()).getOrElse {
            return AppResult.Failure(billingError("billing_load_status_failed", it.message))
        }
        if (!res.success) return AppResult.Failure(billingError("billing_load_status_failed", res.error))
        return res.data?.let { AppResult.Success(it) }
            ?: AppResult.Failure(billingError("billing_empty_status_response"))
    }

    suspend fun createCheckout(request: CreateCheckoutRequest): AppResult<SubscriptionCheckoutDto> {
        val res = api.createCheckout(request, auth()).getOrElse {
            return AppResult.Failure(billingError("billing_checkout_failed", it.message))
        }
        if (!res.success) return AppResult.Failure(billingError("billing_checkout_failed", res.error))
        return res.data?.let { AppResult.Success(it) }
            ?: AppResult.Failure(billingError("billing_empty_checkout_response"))
    }

    suspend fun getCheckout(checkoutId: String): AppResult<SubscriptionCheckoutDto> {
        val res = api.fetchCheckout(checkoutId, auth()).getOrElse {
            return AppResult.Failure(billingError("billing_checkout_lookup_failed", it.message))
        }
        if (!res.success) return AppResult.Failure(billingError("billing_checkout_lookup_failed", res.error))
        return res.data?.let { AppResult.Success(it) }
            ?: AppResult.Failure(billingError("billing_empty_checkout_response"))
    }

    suspend fun verifyGooglePlay(request: VerifyGooglePlayRequest): AppResult<VerifyGooglePlayResponse> {
        val res = api.verifyGooglePlay(request, auth()).getOrElse {
            return AppResult.Failure(billingError("billing_purchase_verification_failed", it.message))
        }
        if (!res.success) {
            return AppResult.Failure(billingError("billing_purchase_verification_failed", res.error))
        }
        return res.data?.let { AppResult.Success(it) }
            ?: AppResult.Failure(billingError("billing_empty_verification_response"))
    }

    suspend fun verifyAppStore(request: VerifyAppStoreRequest): AppResult<VerifyAppStoreResponse> {
        val res = api.verifyAppStore(request, auth()).getOrElse {
            return AppResult.Failure(billingError("billing_purchase_verification_failed", it.message))
        }
        if (!res.success) {
            return AppResult.Failure(billingError("billing_purchase_verification_failed", res.error))
        }
        return res.data?.let { AppResult.Success(it) }
            ?: AppResult.Failure(billingError("billing_empty_verification_response"))
    }

    suspend fun cancel(request: CancelSubscriptionRequest): AppResult<CancelSubscriptionResponse> {
        val res = api.cancel(request, auth()).getOrElse {
            return AppResult.Failure(billingError("billing_cancel_failed", it.message))
        }
        if (!res.success) return AppResult.Failure(billingError("billing_cancel_failed", res.error))
        return res.data?.let { AppResult.Success(it) }
            ?: AppResult.Failure(billingError("billing_empty_cancel_response"))
    }
}
