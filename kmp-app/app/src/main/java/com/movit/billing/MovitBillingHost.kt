package com.movit.billing

import android.content.Context
import com.movit.core.data.MovitData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Billing platform host backed entirely by the KMP data layer ([MovitData]) — no legacy `poc` auth.
 *
 * Tokens / base URL come from `core:data` ([com.movit.core.data.platform.AndroidMovitPlatform] →
 * secure session store). Post-purchase refresh re-syncs the profile (isPro / expiry) through the
 * shared Ktor account API; the Ktor client auto-refreshes the access token, so no manual
 * refresh-token round trip is needed here.
 */
class MovitBillingHost(
    private val context: Context,
) : BillingHost {
    override val applicationId: String
        get() = context.packageName

    override suspend fun refreshSessionAfterPurchase() {
        if (!MovitData.isInstalled) return
        withContext(Dispatchers.IO) {
            // fetchProfile() persists the refreshed isPro / subscriptionExpiry into the shared session.
            runCatching { MovitData.account.fetchProfile() }
            // P2.11: after Pro purchase, refresh reports dashboard.
            if (MovitData.requirePlatform().isProUser()) {
                runCatching { MovitData.reports.syncDashboard() }
            }
        }
    }
}

fun installBillingHost(context: Context) {
    Billing.install(MovitBillingHost(context.applicationContext))
}
