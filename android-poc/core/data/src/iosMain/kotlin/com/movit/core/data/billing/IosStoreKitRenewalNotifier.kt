package com.movit.core.data.billing

/**
 * Swift `Transaction.updates` forwards renewal transactions here for optional backend re-verify.
 * Registered from [IosSubscriptionCoordinator] on iOS shell startup.
 */
object IosStoreKitRenewalNotifier {
    private var handler: ((IosStoreKitTransaction) -> Unit)? = null

    fun setHandler(handler: ((IosStoreKitTransaction) -> Unit)?) {
        this.handler = handler
    }

    fun onRenewal(transaction: IosStoreKitTransaction) {
        handler?.invoke(transaction)
    }
}

/** Entry point for Swift — exported as `IosStoreKitRenewalNotifierKt`. */
fun onIosStoreKitRenewal(transaction: IosStoreKitTransaction) {
    IosStoreKitRenewalNotifier.onRenewal(transaction)
}
