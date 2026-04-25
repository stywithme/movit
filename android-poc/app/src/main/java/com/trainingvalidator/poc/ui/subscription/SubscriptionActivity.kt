package com.trainingvalidator.poc.ui.subscription

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
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
import com.trainingvalidator.poc.BuildConfig
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivitySubscriptionBinding
import com.trainingvalidator.poc.databinding.ItemSubscriptionPlanBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.CancelSubscriptionRequest
import com.trainingvalidator.poc.network.CreateCheckoutRequest
import com.trainingvalidator.poc.network.RefreshTokenRequest
import com.trainingvalidator.poc.network.SubscriptionPlanDto
import com.trainingvalidator.poc.network.VerifyGooglePlayRequest
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.ui.utils.currentLanguage
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
 * Plans, MyFatoorah checkout (Custom Tabs), Google Play Billing verify, and cancel renewal.
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
        ApiClient.init(applicationContext)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            binding.scrollContent.canScrollVertically(-1)
        }
        binding.swipeRefresh.setOnRefreshListener { loadAll(showRefreshIndicator = true) }

        binding.chipBillingPeriod.setOnCheckedStateChangeListener { _, _ ->
            renderPlanCards()
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
                val plansRes = withContext(Dispatchers.IO) { ApiClient.subscriptionApi.getPlans() }
                val statusRes = withContext(Dispatchers.IO) { ApiClient.subscriptionApi.getStatus() }

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

    private fun applyStatus(data: com.trainingvalidator.poc.network.SubscriptionStatusDto?) {
        if (data == null) return
        val title = when {
            data.isPro -> getString(R.string.subscription_status_pro)
            data.pendingCheckouts.isNotEmpty() -> getString(R.string.subscription_status_pending)
            else -> getString(R.string.subscription_status_free)
        }
        binding.tvStatusTitle.text = title
        val expiry = data.subscriptionExpiry
        binding.tvStatusDetail.text = when {
            data.isPro && !expiry.isNullOrBlank() -> getString(R.string.subscription_expires, formatIsoDate(expiry))
            data.pendingCheckouts.isNotEmpty() -> getString(R.string.subscription_payment_pending)
            else -> ""
        }
        val active = data.activeSubscription
        activeSubscriptionId = active?.id
        binding.btnCancelSubscription.isVisible =
            data.isPro && active != null && (active.gateway == "myfatoorah" || active.gateway == "google_play")
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
        val lang = currentLanguage
        for (plan in plans) {
            val cardBinding = ItemSubscriptionPlanBinding.inflate(inflater, binding.containerPlans, false)
            cardBinding.tvPlanTitle.text = localizedText(plan.name, lang, plan.id)
            val desc = localizedText(plan.description, lang, "")
            if (desc.isNotBlank()) {
                cardBinding.tvPlanDesc.isVisible = true
                cardBinding.tvPlanDesc.text = desc
            } else {
                cardBinding.tvPlanDesc.isVisible = false
            }
            val currency = plan.currency ?: "EGP"
            val price = if (billingPeriod() == "yearly") plan.yearlyPrice else plan.monthlyPrice
            val suffix = if (billingPeriod() == "yearly") "/yr" else "/mo"
            cardBinding.tvPlanPrice.text = String.format(Locale.getDefault(), "%.2f %s %s", price, currency, suffix)

            cardBinding.root.setOnClickListener {
                selectedPlan = plan
                updateSelectionStrokes()
            }
            cardBinding.root.tag = plan.id
            binding.containerPlans.addView(cardBinding.root)
        }
        updateSelectionStrokes()
    }

    private fun updateSelectionStrokes() {
        for (i in 0 until binding.containerPlans.childCount) {
            val child = binding.containerPlans.getChildAt(i)
            val card = child as? com.google.android.material.card.MaterialCardView ?: continue
            val id = child.tag as? String
            val selected = id != null && id == selectedPlan?.id
            card.strokeWidth = if (selected) 2 else 0
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
                val res = withContext(Dispatchers.IO) { ApiClient.subscriptionApi.createCheckout(body) }
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
        lifecycleScope.launch {
            try {
                val body = CancelSubscriptionRequest(
                    subscriptionId = activeSubscriptionId,
                    immediate = false,
                    reason = "user_app",
                )
                val res = withContext(Dispatchers.IO) { ApiClient.subscriptionApi.cancel(body) }
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
                packageName = BuildConfig.APPLICATION_ID,
                orderId = purchase.orderId,
            )
            val res = withContext(Dispatchers.IO) { ApiClient.subscriptionApi.verifyGooglePlay(req) }
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
                val res = withContext(Dispatchers.IO) { ApiClient.subscriptionApi.getCheckout(checkoutId) }
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
        try {
            val ctx = applicationContext
            val refreshToken = AuthManager.getRefreshToken(ctx) ?: return
            val refreshRes = withContext(Dispatchers.IO) {
                ApiClient.authApi.refreshToken(RefreshTokenRequest(refreshToken))
            }
            if (refreshRes.isSuccessful && refreshRes.body()?.success == true) {
                val tokens = refreshRes.body()?.data ?: return
                AuthManager.saveNewTokens(ctx, tokens.accessToken, tokens.refreshToken, tokens.expiresIn.toLong())
            }
            val authHeader = AuthManager.getAuthHeader(ctx) ?: return
            val profileRes = withContext(Dispatchers.IO) {
                ApiClient.authApi.getProfile(authHeader)
            }
            if (profileRes.isSuccessful && profileRes.body()?.success == true) {
                profileRes.body()?.data?.let { AuthManager.updateUser(ctx, it) }
            }
        } catch (_: Exception) {
            // Tokens/profile refresh best-effort
        }
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
                is com.trainingvalidator.poc.network.SubscriptionApiEnvelope<*> -> body.error
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
