package com.movit.billing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.google.gson.JsonElement
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.movit.billing.network.BillingApiClient
import com.movit.billing.network.CancelSubscriptionRequest
import com.movit.billing.network.CreateCheckoutRequest
import com.movit.billing.network.SubscriptionApiEnvelope
import com.movit.billing.network.SubscriptionPlanDto
import com.movit.billing.network.SubscriptionStatusDto
import com.movit.billing.network.VerifyGooglePlayRequest
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivitySubscriptionBinding
import com.trainingvalidator.poc.databinding.ItemSubscriptionPlanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import java.text.DateFormat
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Android platform billing UI: MyFatoorah (Custom Tabs), Google Play Billing verify, cancel renewal.
 * Retained intentionally as a platform component — not legacy dead code (WS-3 / Gate G5).
 */
class SubscriptionActivity : AppCompatActivity(), PurchasesUpdatedListener {

    private lateinit var binding: ActivitySubscriptionBinding
    private var plans: List<SubscriptionPlanDto> = emptyList()
    private var selectedPlan: SubscriptionPlanDto? = null
    private var billingClient: BillingClient? = null
    private var billingReady = false
    private var pendingGooglePlan: SubscriptionPlanDto? = null
    private var pendingGoogleBillingPeriod: String = "monthly"
    private var activeSubscriptionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            binding.scrollContent.canScrollVertically(-1)
        }
        binding.swipeRefresh.setOnRefreshListener { loadAll(showRefreshIndicator = true) }
        binding.btnRefreshSubscription.setOnClickListener { loadAll(showRefreshIndicator = true) }

        binding.chipBillingPeriod.setOnCheckedStateChangeListener { _, _ ->
            renderPlanCards()
            renderIncludedFeatures()
            updatePaymentOptions()
            updateSelectionStrokes()
        }

        binding.btnPayMyFatoorah.setOnClickListener { startMyFatoorahCheckout() }
        binding.btnPayGooglePlay.setOnClickListener { startGooglePlayCheckout() }
        binding.btnCancelSubscription.setOnClickListener { cancelRenewal() }

        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                billingReady = result.responseCode == BillingClient.BillingResponseCode.OK
            }

            override fun onBillingServiceDisconnected() {
                billingReady = false
            }
        })

        loadAll(showRefreshIndicator = false)
        handleReturnIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReturnIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val pendingId = pendingCheckoutId()
        if (!pendingId.isNullOrBlank()) {
            lifecycleScope.launch {
                pollCheckoutOnce(pendingId)
            }
        }
    }

    override fun onDestroy() {
        billingClient?.endConnection()
        billingClient = null
        super.onDestroy()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Toast.makeText(this, result.debugMessage.ifBlank { getString(R.string.subscription_checkout_failed) }, Toast.LENGTH_LONG).show()
            return
        }
        val purchase = purchases?.firstOrNull() ?: return
        val plan = pendingGooglePlan ?: return
        val period = pendingGoogleBillingPeriod
        pendingGooglePlan = null
        lifecycleScope.launch {
            val ok = ensureAcknowledged(purchase)
            if (!ok) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SubscriptionActivity, R.string.subscription_checkout_failed, Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val productId = purchase.products.firstOrNull() ?: return@launch
            verifyOnServer(plan, period, productId, purchase)
        }
    }

    private fun billingPeriod(): String =
        if (binding.chipYearly.isChecked) "yearly" else "monthly"

    private fun loadAll(showRefreshIndicator: Boolean) {
        lifecycleScope.launch {
            if (showRefreshIndicator) binding.swipeRefresh.isRefreshing = true
            try {
                val plansRes = withContext(Dispatchers.IO) { BillingApiClient.api.getPlans() }
                val statusRes = withContext(Dispatchers.IO) { BillingApiClient.api.getStatus() }

                if (plansRes.isSuccessful && plansRes.body()?.success == true) {
                    plans = plansRes.body()?.data.orEmpty().filter { it.isActive }
                } else {
                    Toast.makeText(this@SubscriptionActivity, R.string.subscription_load_error, Toast.LENGTH_LONG).show()
                }

                if (selectedPlan == null || plans.none { it.id == selectedPlan?.id }) {
                    selectedPlan = plans.firstOrNull()
                }

                if (statusRes.isSuccessful && statusRes.body()?.success == true) {
                    applyStatus(statusRes.body()?.data)
                }

                renderPlanCards()
                updateSelectionStrokes()
            } catch (e: Exception) {
                Toast.makeText(this@SubscriptionActivity, R.string.subscription_load_error, Toast.LENGTH_LONG).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun applyStatus(data: SubscriptionStatusDto?) {
        if (data == null) return
        val lang = billingLanguage
        val pill = when {
            data.isPro -> getString(R.string.subscription_status_pro)
            data.pendingCheckouts.isNotEmpty() -> getString(R.string.subscription_status_pending)
            else -> getString(R.string.subscription_status_free)
        }
        binding.tvStatusPill.text = pill
        binding.tvStatusTitle.text = when {
            data.isPro -> localizedText(data.activeSubscription?.plan?.name, lang, getString(R.string.pro_membership))
            data.pendingCheckouts.isNotEmpty() -> getString(R.string.subscription_status_pending)
            else -> getString(R.string.subscription_free_headline)
        }
        val expiry = data.subscriptionExpiry
        binding.tvStatusDetail.text = when {
            data.isPro && !expiry.isNullOrBlank() -> getString(R.string.subscription_pro_detail, formatIsoDate(expiry))
            data.pendingCheckouts.isNotEmpty() -> getString(R.string.subscription_pending_detail)
            else -> getString(R.string.subscription_free_detail)
        }
        val active = data.activeSubscription
        activeSubscriptionId = active?.id
        val canManage = data.isPro && active != null && (active.gateway == "myfatoorah" || active.gateway == "google_play")
        binding.cardManageSubscription.isVisible = canManage
        binding.btnCancelSubscription.isVisible = canManage
        binding.tvManageSubscriptionDetail.text =
            if (!expiry.isNullOrBlank()) getString(R.string.subscription_manage_detail, formatIsoDate(expiry))
            else getString(R.string.subscription_cancel_at_period_end)
    }

    private fun formatIsoDate(iso: String): String {
        return try {
            val cleaned = iso.take(19).replace('T', ' ')
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val d = parser.parse(cleaned) ?: return iso
            DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(d)
        } catch (_: Exception) {
            iso
        }
    }

    private fun renderPlanCards() {
        binding.containerPlans.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val lang = billingLanguage
        for (plan in plans) {
            val cardBinding = ItemSubscriptionPlanBinding.inflate(inflater, binding.containerPlans, false)
            cardBinding.tvPlanTitle.text = localizedText(plan.name, lang, plan.id)
            cardBinding.tvPlanBadge.isVisible = plan.id == selectedPlan?.id
            val desc = localizedText(plan.description, lang, "")
            if (desc.isNotBlank()) {
                cardBinding.tvPlanDesc.isVisible = true
                cardBinding.tvPlanDesc.text = desc
            } else {
                cardBinding.tvPlanDesc.isVisible = false
            }
            val currency = plan.currency ?: "EGP"
            val price = if (billingPeriod() == "yearly") plan.yearlyPrice else plan.monthlyPrice
            val suffix = if (billingPeriod() == "yearly") {
                getString(R.string.subscription_period_year)
            } else {
                getString(R.string.subscription_period_month)
            }
            cardBinding.tvPlanPrice.text = String.format(Locale.getDefault(), "%.2f %s", price, currency)
            cardBinding.tvPlanPeriod.text = suffix
            cardBinding.tvPlanFeatures.text = planFeatureLines(plan).take(3).joinToString("\n")

            cardBinding.root.setOnClickListener {
                selectedPlan = plan
                renderPlanCards()
                renderIncludedFeatures()
                updateSelectionStrokes()
            }
            cardBinding.root.tag = plan.id
            binding.containerPlans.addView(cardBinding.root)
        }
        renderIncludedFeatures()
        updatePaymentOptions()
        updateSelectionStrokes()
    }

    private fun updateSelectionStrokes() {
        for (i in 0 until binding.containerPlans.childCount) {
            val child = binding.containerPlans.getChildAt(i)
            val card = child as? com.google.android.material.card.MaterialCardView ?: continue
            val id = child.tag as? String
            val selected = id != null && id == selectedPlan?.id
            card.strokeWidth = if (selected) 2 else 0
            card.setCardBackgroundColor(
                ContextCompat.getColor(
                    this,
                    if (selected) R.color.surface_elevated else R.color.surface,
                ),
            )
        }
    }

    private fun renderIncludedFeatures() {
        val plan = selectedPlan
        binding.tvIncludedFeatures.text =
            if (plan == null) defaultFeatureLines().joinToString("\n")
            else planFeatureLines(plan).joinToString("\n")
    }

    private fun updatePaymentOptions() {
        val plan = selectedPlan
        val googleProductId = if (billingPeriod() == "yearly") {
            plan?.yearlyGooglePlayProductId
        } else {
            plan?.monthlyGooglePlayProductId
        }
        binding.btnPayMyFatoorah.isEnabled = plan != null
        binding.btnPayGooglePlay.isVisible = !googleProductId.isNullOrBlank()
        binding.tvPaymentSubtitle.text = if (plan == null) {
            getString(R.string.subscription_payment_subtitle)
        } else {
            val name = localizedText(plan.name, billingLanguage, getString(R.string.pro_membership))
            val price = if (billingPeriod() == "yearly") plan.yearlyPrice else plan.monthlyPrice
            val currency = plan.currency ?: "EGP"
            getString(R.string.subscription_payment_selected, name, String.format(Locale.getDefault(), "%.2f %s", price, currency))
        }
    }

    private fun planFeatureLines(plan: SubscriptionPlanDto): List<String> {
        val localized = localizedFeatureLines(plan.features)
        val lines = mutableListOf<String>()
        if (localized.isNotEmpty()) lines += localized
        if (plan.maxWorkoutsLimit > 0) lines += "✓ ${getString(R.string.subscription_feature_workouts, plan.maxWorkoutsLimit)}"
        if (plan.maxExercisesLimit > 0) lines += "✓ ${getString(R.string.subscription_feature_exercises, plan.maxExercisesLimit)}"
        if (plan.freeDoctorSessionsLimit > 0) {
            lines += "✓ ${getString(R.string.subscription_feature_doctor_sessions, plan.freeDoctorSessionsLimit)}"
        }
        if (lines.isEmpty()) lines += defaultFeatureLines()
        return lines.distinct()
    }

    private fun defaultFeatureLines(): List<String> = listOf(
        "✓ ${getString(R.string.subscription_feature_ai)}",
        "✓ ${getString(R.string.subscription_feature_reports)}",
        "✓ ${getString(R.string.subscription_feature_programs)}",
    )

    private fun localizedFeatureLines(features: JsonElement?): List<String> {
        if (features == null || features.isJsonNull) return emptyList()
        val lang = billingLanguage
        return when {
            features.isJsonArray -> features.asJsonArray.mapNotNull { feature ->
                localizedText(feature, lang, "").takeIf { it.isNotBlank() }?.let { "✓ $it" }
            }
            features.isJsonObject -> {
                val obj = features.asJsonObject
                val localized = obj.get(if (lang.startsWith("ar")) "ar" else "en")
                when {
                    localized != null && localized.isJsonArray -> localized.asJsonArray.mapNotNull {
                        localizedText(it, lang, "").takeIf { value -> value.isNotBlank() }?.let { value -> "✓ $value" }
                    }
                    localized != null && localized.isJsonPrimitive -> listOf("✓ ${localized.asString}")
                    else -> emptyList()
                }
            }
            features.isJsonPrimitive -> listOf("✓ ${features.asString}")
            else -> emptyList()
        }
    }

    private fun localizedText(el: JsonElement?, lang: String, fallback: String): String {
        if (el == null || el.isJsonNull) return fallback
        if (el.isJsonPrimitive) return el.asString
        val obj = el.asJsonObject
        val key = if (lang.lowercase(Locale.getDefault()).startsWith("ar")) "ar" else "en"
        if (obj.has(key)) {
            val v = obj.get(key)
            if (v != null && v.isJsonPrimitive) return v.asString
        }
        val first = obj.entrySet().firstOrNull()?.value
        return if (first != null && first.isJsonPrimitive) first.asString else fallback
    }

    private fun startMyFatoorahCheckout() {
        val plan = selectedPlan
        if (plan == null) {
            Toast.makeText(this, R.string.subscription_select_plan_first, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val body = CreateCheckoutRequest(
                    planId = plan.id,
                    billingPeriod = billingPeriod(),
                    gateway = "myfatoorah",
                )
                val res = withContext(Dispatchers.IO) { BillingApiClient.api.createCheckout(body) }
                if (!res.isSuccessful || res.body()?.success != true) {
                    Toast.makeText(this@SubscriptionActivity, apiErrorMessage(res, R.string.subscription_checkout_failed), Toast.LENGTH_LONG).show()
                    return@launch
                }
                val checkout = res.body()?.data
                val url = checkout?.paymentUrl
                if (url.isNullOrBlank()) {
                    if (checkout?.status == "paid") {
                        refreshSessionFromServer()
                        loadAll(showRefreshIndicator = true)
                    } else {
                        Toast.makeText(this@SubscriptionActivity, R.string.subscription_checkout_failed, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                savePendingCheckout(checkout.id)
                withContext(Dispatchers.Main) {
                    CustomTabsIntent.Builder().build().launchUrl(this@SubscriptionActivity, Uri.parse(url))
                }
            } catch (_: Exception) {
                Toast.makeText(this@SubscriptionActivity, R.string.subscription_checkout_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleReturnIntent(source: Intent?) {
        val data = source?.data ?: return
        if (data.scheme != "waytofix" || data.host != "subscription") return
        val checkoutId = data.getQueryParameter("checkoutId")
        val status = data.getQueryParameter("status")

        lifecycleScope.launch {
            when (status) {
                "paid" -> {
                    clearPendingCheckout()
                    refreshSessionFromServer()
                    Toast.makeText(this@SubscriptionActivity, R.string.subscription_purchase_success, Toast.LENGTH_SHORT).show()
                    loadAll(showRefreshIndicator = true)
                }
                "pending" -> {
                    if (!checkoutId.isNullOrBlank()) {
                        savePendingCheckout(checkoutId)
                        pollCheckoutOnce(checkoutId)
                    }
                    Toast.makeText(this@SubscriptionActivity, R.string.subscription_payment_pending, Toast.LENGTH_LONG).show()
                    loadAll(showRefreshIndicator = true)
                }
                "failed" -> {
                    clearPendingCheckout()
                    Toast.makeText(this@SubscriptionActivity, R.string.subscription_checkout_failed, Toast.LENGTH_LONG).show()
                    loadAll(showRefreshIndicator = true)
                }
            }
        }
    }

    private fun startGooglePlayCheckout() {
        val plan = selectedPlan
        if (plan == null) {
            Toast.makeText(this, R.string.subscription_select_plan_first, Toast.LENGTH_SHORT).show()
            return
        }
        val period = billingPeriod()
        val productId = if (period == "yearly") plan.yearlyGooglePlayProductId else plan.monthlyGooglePlayProductId
        if (productId.isNullOrBlank()) {
            Toast.makeText(this, R.string.subscription_no_play_product, Toast.LENGTH_LONG).show()
            return
        }
        if (!billingReady) {
            Toast.makeText(this, R.string.subscription_checkout_failed, Toast.LENGTH_SHORT).show()
            return
        }
        pendingGooglePlan = plan
        pendingGoogleBillingPeriod = period
        val product = com.android.billingclient.api.QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        billingClient?.queryProductDetailsAsync(params) { billingResult: BillingResult, details: MutableList<ProductDetails> ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK || details.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(this, R.string.subscription_checkout_failed, Toast.LENGTH_LONG).show()
                }
                return@queryProductDetailsAsync
            }
            val productDetails = details[0]
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken.isNullOrBlank()) {
                runOnUiThread {
                    Toast.makeText(this, R.string.subscription_checkout_failed, Toast.LENGTH_LONG).show()
                }
                return@queryProductDetailsAsync
            }
            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                billingClient?.launchBillingFlow(this@SubscriptionActivity, flowParams)
            }
        }
    }

    private fun cancelRenewal() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.subscription_cancel_title)
            .setMessage(R.string.subscription_cancel_message)
            .setNegativeButton(R.string.subscription_keep_plan, null)
            .setPositiveButton(R.string.subscription_confirm_cancel) { _, _ ->
                performCancelRenewal()
            }
            .show()
    }

    private fun performCancelRenewal() {
        lifecycleScope.launch {
            try {
                val body = CancelSubscriptionRequest(
                    subscriptionId = activeSubscriptionId,
                    immediate = false,
                    reason = "user_app",
                )
                val res = withContext(Dispatchers.IO) { BillingApiClient.api.cancel(body) }
                withContext(Dispatchers.Main) {
                    if (res.isSuccessful && res.body()?.success == true) {
                        Toast.makeText(this@SubscriptionActivity, R.string.subscription_cancelled, Toast.LENGTH_SHORT).show()
                        refreshSessionFromServer()
                        loadAll(showRefreshIndicator = true)
                    } else {
                        Toast.makeText(this@SubscriptionActivity, apiErrorMessage(res, R.string.subscription_load_error), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(this@SubscriptionActivity, R.string.subscription_load_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun ensureAcknowledged(purchase: Purchase): Boolean {
        if (purchase.isAcknowledged) return true
        val client = billingClient ?: return false
        return suspendCancellableCoroutine { cont ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { result: BillingResult ->
                cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
        }
    }

    private suspend fun verifyOnServer(
        plan: SubscriptionPlanDto,
        billingPeriod: String,
        productId: String,
        purchase: Purchase,
    ) {
        try {
            val req = VerifyGooglePlayRequest(
                planId = plan.id,
                billingPeriod = billingPeriod,
                productId = productId,
                purchaseToken = purchase.purchaseToken,
                packageName = Billing.requireHost().applicationId,
                orderId = purchase.orderId,
            )
            val res = withContext(Dispatchers.IO) { BillingApiClient.api.verifyGooglePlay(req) }
            withContext(Dispatchers.Main) {
                if (res.isSuccessful && res.body()?.success == true) {
                    Toast.makeText(this@SubscriptionActivity, R.string.subscription_purchase_success, Toast.LENGTH_SHORT).show()
                    refreshSessionFromServer()
                    loadAll(showRefreshIndicator = true)
                } else {
                    Toast.makeText(this@SubscriptionActivity, apiErrorMessage(res, R.string.subscription_load_error), Toast.LENGTH_LONG).show()
                }
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SubscriptionActivity, R.string.subscription_load_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun pollCheckoutOnce(checkoutId: String) {
        try {
            repeat(3) { attempt ->
                val res = withContext(Dispatchers.IO) { BillingApiClient.api.getCheckout(checkoutId) }
                if (res.isSuccessful && res.body()?.success == true) {
                    val st = res.body()?.data?.status
                    if (st == "paid" || st == "failed") {
                        clearPendingCheckout()
                        refreshSessionFromServer()
                        withContext(Dispatchers.Main) {
                            loadAll(showRefreshIndicator = true)
                        }
                        return
                    }
                }
                if (attempt < 2) delay(1500)
            }
        } catch (_: Exception) {
            // Keep pending id for manual refresh
        }
    }

    private suspend fun refreshSessionFromServer() {
        Billing.requireHost().refreshSessionAfterPurchase()
    }

    private fun prefs() = getSharedPreferences(PREF_SUB_FLOW, Context.MODE_PRIVATE)

    private fun savePendingCheckout(id: String) {
        prefs().edit().putString(KEY_PENDING_CHECKOUT, id).apply()
    }

    private fun pendingCheckoutId(): String? = prefs().getString(KEY_PENDING_CHECKOUT, null)

    private fun clearPendingCheckout() {
        prefs().edit().remove(KEY_PENDING_CHECKOUT).apply()
    }

    private fun apiErrorMessage(response: Response<*>, fallbackResId: Int): String {
        response.body()?.let { body ->
            val error = when (body) {
                is SubscriptionApiEnvelope<*> -> body.error
                else -> null
            }
            if (!error.isNullOrBlank()) return error
        }

        val raw = response.errorBody()?.string()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val json = JSONObject(raw)
                val error = json.optString("error")
                val message = json.optString("message")
                if (error.isNotBlank()) return error
                if (message.isNotBlank()) return message
            }
        }
        return getString(fallbackResId)
    }

    companion object {
        private const val PREF_SUB_FLOW = "subscription_flow"
        private const val KEY_PENDING_CHECKOUT = "pending_checkout_id"
    }
}
