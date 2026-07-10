package com.movit.feature.shell

import com.movit.core.data.MovitData
import com.movit.core.data.billing.IosStoreKitBridgeRegistry
import com.movit.core.data.billing.IosStoreKitPurchaseResultHandler
import com.movit.core.data.billing.IosStoreKitRestoreResultHandler
import com.movit.core.data.billing.IosStoreKitRenewalNotifier
import com.movit.core.data.billing.IosStoreKitTransaction
import com.movit.core.network.dto.SubscriptionPlanDto
import com.movit.core.network.dto.VerifyAppStoreRequest
import com.movit.shared.AppResult
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * iOS subscription flow — loads plans from [MovitData.billing], purchases via StoreKit 2 bridge,
 * verifies on the backend, then refreshes the profile (mirrors Android `SubscriptionActivity`).
 */
object IosSubscriptionCoordinator {

    private val renewalScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var renewalHandlerInstalled = false

    fun ensureRenewalHandlerInstalled() {
        if (renewalHandlerInstalled) return
        renewalHandlerInstalled = true
        IosStoreKitRenewalNotifier.setHandler { transaction ->
            renewalScope.launch {
                handleRenewalTransaction(transaction)
            }
        }
    }

    private suspend fun handleRenewalTransaction(transaction: IosStoreKitTransaction) {
        val plans = when (val res = MovitData.billing.getPlans()) {
            is AppResult.Success -> res.value.filter { it.isActive }
            is AppResult.Failure -> return
        }
        val match = plans.firstOrNull { plan ->
            plan.monthlyAppStoreProductId == transaction.productId ||
                plan.yearlyAppStoreProductId == transaction.productId
        } ?: return
        val period = when (transaction.productId) {
            match.yearlyAppStoreProductId -> "yearly"
            else -> "monthly"
        }
        verifyTransaction(match, period, transaction, showSuccessAlert = false)
    }

    suspend fun launchSubscriptionFlow(
        billingPeriod: String = "monthly",
        restorePurchases: Boolean = false,
    ): Boolean {
        val bridge = IosStoreKitBridgeRegistry.current()
        if (bridge == null || !bridge.isAvailable) {
            showIosBillingAlert("In-app subscriptions are not available on this device.")
            return false
        }

        val plans = when (val res = MovitData.billing.getPlans()) {
            is AppResult.Success -> res.value.filter { it.isActive }
            is AppResult.Failure -> {
                showIosBillingAlert(res.message.ifBlank { "Failed to load plans." })
                return false
            }
        }
        val plan = plans.firstOrNull()
        if (plan == null) {
            showIosBillingAlert("No subscription plans are available.")
            return false
        }

        if (restorePurchases) {
            return restoreAndVerify(plans)
        }

        val productId = planAppStoreProductId(plan, billingPeriod)
        if (productId.isNullOrBlank()) {
            showIosBillingAlert("This plan is not configured for App Store billing yet.")
            return false
        }

        val transaction = purchase(bridge, productId) ?: return false
        return verifyTransaction(plan, billingPeriod, transaction)
    }

    private suspend fun restoreAndVerify(plans: List<SubscriptionPlanDto>): Boolean {
        val bridge = IosStoreKitBridgeRegistry.current() ?: return false
        val transactions = restore(bridge)
        if (transactions.isEmpty()) {
            showIosBillingAlert("No active App Store subscriptions were found for this Apple ID.")
            return false
        }

        var verifiedAny = false
        for (transaction in transactions) {
            val match = plans.firstOrNull { plan ->
                plan.monthlyAppStoreProductId == transaction.productId ||
                    plan.yearlyAppStoreProductId == transaction.productId
            } ?: continue
            val period = when (transaction.productId) {
                match.yearlyAppStoreProductId -> "yearly"
                else -> "monthly"
            }
            if (verifyTransaction(match, period, transaction)) {
                verifiedAny = true
            }
        }
        if (!verifiedAny) {
            showIosBillingAlert("Could not match restored purchases to an active plan.")
        }
        return verifiedAny
    }

    private suspend fun verifyTransaction(
        plan: SubscriptionPlanDto,
        billingPeriod: String,
        transaction: IosStoreKitTransaction,
        showSuccessAlert: Boolean = true,
    ): Boolean {
        val request = VerifyAppStoreRequest(
            planId = plan.id,
            billingPeriod = billingPeriod,
            productId = transaction.productId,
            transactionId = transaction.transactionId,
            originalTransactionId = transaction.originalTransactionId,
            signedTransactionInfo = transaction.signedTransactionInfo,
        )
        return when (val res = MovitData.billing.verifyAppStore(request)) {
            is AppResult.Success -> {
                IosStoreKitBridgeRegistry.current()?.finishTransaction(transaction.transactionId)
                runCatching { MovitData.account.fetchProfile() }
                if (MovitData.requirePlatform().isProUser()) {
                    runCatching { MovitData.reports.syncDashboard() }
                }
                if (showSuccessAlert) {
                    showIosBillingAlert("Subscription activated successfully.")
                }
                true
            }
            is AppResult.Failure -> {
                if (showSuccessAlert) {
                    showIosBillingAlert(res.message.ifBlank { "Purchase verification failed." })
                }
                false
            }
        }
    }

    private suspend fun purchase(
        bridge: com.movit.core.data.billing.IosStoreKitBridge,
        productId: String,
    ): IosStoreKitTransaction? =
        suspendCancellableCoroutine { cont ->
            bridge.purchase(
                productId,
                object : IosStoreKitPurchaseResultHandler {
                    override fun onCompleted(
                        transaction: IosStoreKitTransaction?,
                        errorMessage: String?,
                    ) {
                        if (!cont.isActive) return
                        when {
                            transaction != null -> cont.resume(transaction)
                            !errorMessage.isNullOrBlank() -> {
                                showIosBillingAlert(errorMessage)
                                cont.resume(null)
                            }
                            else -> cont.resume(null)
                        }
                    }
                },
            )
        }

    private suspend fun restore(
        bridge: com.movit.core.data.billing.IosStoreKitBridge,
    ): List<IosStoreKitTransaction> =
        suspendCancellableCoroutine { cont ->
            bridge.restorePurchases(
                object : IosStoreKitRestoreResultHandler {
                    override fun onCompleted(transactions: List<IosStoreKitTransaction>) {
                        if (cont.isActive) cont.resume(transactions)
                    }
                },
            )
        }

    private fun planAppStoreProductId(plan: SubscriptionPlanDto, billingPeriod: String): String? =
        if (billingPeriod == "yearly") {
            plan.yearlyAppStoreProductId
        } else {
            plan.monthlyAppStoreProductId
        }
}
