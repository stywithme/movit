import Foundation
import MovitApp
import StoreKit
import UIKit

/// StoreKit 2 bridge for KMP iOS subscription purchase / restore.
/// Registers via `IosStoreKitBridgeInstallKt.installIosStoreKitBridge` from `iOSApp` init.
final class MovitStoreKitBridge: NSObject, IosStoreKitBridge {
    private var pendingTransactions: [String: Transaction] = [:]
    private var updatesTask: Task<Void, Never>?

    override init() {
        super.init()
        startTransactionUpdatesListener()
    }

    deinit {
        updatesTask?.cancel()
    }

    var isAvailable: Bool { true }

    func purchase(productId: String, handler: IosStoreKitPurchaseResultHandler) {
        Task { @MainActor in
            do {
                let products = try await Product.products(for: [productId])
                guard let product = products.first else {
                    handler.onCompleted(transaction: nil, errorMessage: "Product not found")
                    return
                }
                let purchaseResult = try await product.purchase()
                switch purchaseResult {
                case .success(let verification):
                    let transaction = try Self.verifiedTransaction(verification)
                    let mapped = Self.mapTransaction(transaction, jws: verification.jwsRepresentation)
                    pendingTransactions[mapped.transactionId] = transaction
                    handler.onCompleted(transaction: mapped, errorMessage: nil)
                case .userCancelled:
                    handler.onCompleted(transaction: nil, errorMessage: nil)
                case .pending:
                    handler.onCompleted(transaction: nil, errorMessage: "Purchase pending approval")
                @unknown default:
                    handler.onCompleted(transaction: nil, errorMessage: "Unknown purchase result")
                }
            } catch {
                handler.onCompleted(transaction: nil, errorMessage: error.localizedDescription)
            }
        }
    }

    func restorePurchases(handler: IosStoreKitRestoreResultHandler) {
        Task {
            var restored: [IosStoreKitTransaction] = []
            for await result in Transaction.currentEntitlements {
                if case .verified(let transaction) = result {
                    restored.append(
                        Self.mapTransaction(transaction, jws: result.jwsRepresentation)
                    )
                }
            }
            handler.onCompleted(transactions: restored)
        }
    }

    func finishTransaction(transactionId: String) {
        Task {
            guard let transaction = pendingTransactions.removeValue(forKey: transactionId) else { return }
            await transaction.finish()
        }
    }

    private func startTransactionUpdatesListener() {
        updatesTask = Task {
            for await result in Transaction.updates {
                guard case .verified(let transaction) = result else { continue }
                let mapped = Self.mapTransaction(transaction, jws: result.jwsRepresentation)
                pendingTransactions[mapped.transactionId] = transaction
                IosStoreKitRenewalNotifierKt.onIosStoreKitRenewal(transaction: mapped)
            }
        }
    }

    private static func verifiedTransaction<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified:
            throw NSError(
                domain: "MovitStoreKit",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Transaction verification failed"]
            )
        case .verified(let safe):
            return safe
        }
    }

    private static func mapTransaction(
        _ transaction: Transaction,
        jws: String
    ) -> IosStoreKitTransaction {
        let expiresMs: Int64 = {
            guard let expiry = transaction.expirationDate else { return 0 }
            return Int64(expiry.timeIntervalSince1970 * 1000.0)
        }()
        return IosStoreKitTransaction(
            productId: transaction.productID,
            transactionId: String(transaction.id),
            originalTransactionId: String(transaction.originalID),
            signedTransactionInfo: jws,
            expiresAtEpochMs: expiresMs
        )
    }
}
