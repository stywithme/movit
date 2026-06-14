package com.movit.billing

import android.content.Context
import com.trainingvalidator.poc.BuildConfig
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.ApiConfig
import com.trainingvalidator.poc.network.RefreshTokenRequest
import com.trainingvalidator.poc.storage.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges billing to legacy auth/session until WS-2 (B1/B5) completes.
 * This is an intentional platform host — not dead legacy UI.
 */
class LegacyBillingHost(
    private val context: Context,
) : BillingHost {
    override val applicationId: String
        get() = BuildConfig.APPLICATION_ID

    override fun apiBaseUrl(): String = ApiConfig.getEffectiveBaseUrl()

    override fun authHeader(): String? = AuthManager.getAuthHeader(context)

    override suspend fun refreshSessionAfterPurchase() {
        withContext(Dispatchers.IO) {
            try {
                val refreshToken = AuthManager.getRefreshToken(context) ?: return@withContext
                val refreshRes = ApiClient.authApi.refreshToken(RefreshTokenRequest(refreshToken))
                if (refreshRes.isSuccessful && refreshRes.body()?.success == true) {
                    val tokens = refreshRes.body()?.data ?: return@withContext
                    AuthManager.saveNewTokens(
                        context,
                        tokens.accessToken,
                        tokens.refreshToken,
                        tokens.expiresIn.toLong(),
                    )
                }
                val authHeader = AuthManager.getAuthHeader(context) ?: return@withContext
                val profileRes = ApiClient.authApi.getProfile(authHeader)
                if (profileRes.isSuccessful && profileRes.body()?.success == true) {
                    profileRes.body()?.data?.let { AuthManager.updateUser(context, it) }
                }
            } catch (_: Exception) {
                // Best-effort refresh after purchase
            }
        }
    }
}

fun installBillingHost(context: Context) {
    Billing.install(LegacyBillingHost(context.applicationContext))
}
