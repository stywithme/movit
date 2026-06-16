package com.movit.core.data.repository

import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitBillingApi
import com.movit.core.network.dto.CancelSubscriptionRequest
import com.movit.core.network.dto.CancelSubscriptionResponse
import com.movit.core.network.dto.CreateCheckoutRequest
import com.movit.core.network.dto.SubscriptionCheckoutDto
import com.movit.core.network.dto.SubscriptionPlanDto
import com.movit.core.network.dto.SubscriptionStatusDto
import com.movit.core.network.dto.VerifyGooglePlayRequest
import com.movit.core.network.dto.VerifyGooglePlayResponse
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

    suspend fun getPlans(): AppResult<List<SubscriptionPlanDto>> {
        val res = api.fetchPlans(auth()).getOrElse {
            return AppResult.Failure(it.message ?: "Failed to load plans.")
        }
        if (!res.success) return AppResult.Failure(res.error ?: "Failed to load plans.")
        return AppResult.Success(res.data.orEmpty())
    }

    suspend fun getStatus(): AppResult<SubscriptionStatusDto> {
        val res = api.fetchStatus(auth()).getOrElse {
            return AppResult.Failure(it.message ?: "Failed to load subscription status.")
        }
        if (!res.success) return AppResult.Failure(res.error ?: "Failed to load subscription status.")
        return res.data?.let { AppResult.Success(it) } ?: AppResult.Failure("Empty status response.")
    }

    suspend fun createCheckout(request: CreateCheckoutRequest): AppResult<SubscriptionCheckoutDto> {
        val res = api.createCheckout(request, auth()).getOrElse {
            return AppResult.Failure(it.message ?: "Checkout failed.")
        }
        if (!res.success) return AppResult.Failure(res.error ?: "Checkout failed.")
        return res.data?.let { AppResult.Success(it) } ?: AppResult.Failure("Empty checkout response.")
    }

    suspend fun getCheckout(checkoutId: String): AppResult<SubscriptionCheckoutDto> {
        val res = api.fetchCheckout(checkoutId, auth()).getOrElse {
            return AppResult.Failure(it.message ?: "Checkout lookup failed.")
        }
        if (!res.success) return AppResult.Failure(res.error ?: "Checkout lookup failed.")
        return res.data?.let { AppResult.Success(it) } ?: AppResult.Failure("Empty checkout response.")
    }

    suspend fun verifyGooglePlay(request: VerifyGooglePlayRequest): AppResult<VerifyGooglePlayResponse> {
        val res = api.verifyGooglePlay(request, auth()).getOrElse {
            return AppResult.Failure(it.message ?: "Purchase verification failed.")
        }
        if (!res.success) return AppResult.Failure(res.error ?: "Purchase verification failed.")
        return res.data?.let { AppResult.Success(it) } ?: AppResult.Failure("Empty verification response.")
    }

    suspend fun cancel(request: CancelSubscriptionRequest): AppResult<CancelSubscriptionResponse> {
        val res = api.cancel(request, auth()).getOrElse {
            return AppResult.Failure(it.message ?: "Cancel failed.")
        }
        if (!res.success) return AppResult.Failure(res.error ?: "Cancel failed.")
        return res.data?.let { AppResult.Success(it) } ?: AppResult.Failure("Empty cancel response.")
    }
}
