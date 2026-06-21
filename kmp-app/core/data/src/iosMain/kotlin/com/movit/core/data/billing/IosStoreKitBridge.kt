package com.movit.core.data.billing

/**
 * Swift `MovitStoreKitBridge` implements this protocol (StoreKit 2).
 * Registered via [installIosStoreKitBridge] from `iosApp` before subscription UI is shown.
 */
interface IosStoreKitBridge {
    val isAvailable: Boolean

    fun purchase(productId: String, handler: IosStoreKitPurchaseResultHandler)

    fun restorePurchases(handler: IosStoreKitRestoreResultHandler)

    /** Call after backend verify succeeds so StoreKit can close the transaction. */
    fun finishTransaction(transactionId: String)
}

data class IosStoreKitTransaction(
    val productId: String,
    val transactionId: String,
    val originalTransactionId: String,
    val signedTransactionInfo: String,
    val expiresAtEpochMs: Long = 0L,
)

/** Async callback — Swift calls when a StoreKit purchase finishes. */
interface IosStoreKitPurchaseResultHandler {
    fun onCompleted(transaction: IosStoreKitTransaction?, errorMessage: String?)
}

/** Async callback — Swift calls with active entitlements from StoreKit restore. */
interface IosStoreKitRestoreResultHandler {
    fun onCompleted(transactions: List<IosStoreKitTransaction>)
}

object IosStoreKitBridgeRegistry {
    private var bridge: IosStoreKitBridge? = null

    fun current(): IosStoreKitBridge? = bridge

    internal fun install(bridge: IosStoreKitBridge?) {
        this.bridge = bridge
    }
}
