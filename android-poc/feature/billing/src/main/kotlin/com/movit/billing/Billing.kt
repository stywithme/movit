package com.movit.billing

/**
 * Platform host contract for the Android billing component (Play Billing + MyFatoorah).
 * Implemented by the app shell; keeps subscription networking out of legacy `poc` packages.
 */
interface BillingHost {
    val applicationId: String

    suspend fun refreshSessionAfterPurchase()
}

object Billing {
    @Volatile
    private var host: BillingHost? = null

    fun install(host: BillingHost) {
        this.host = host
    }

    fun requireHost(): BillingHost =
        checkNotNull(host) { "Billing.install() must be called before using billing APIs" }
}
