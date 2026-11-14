package com.bioscankit.android.paywall

/**
 * 计费客户端，对齐 iOS `BillingClient`。
 *
 * 与 iOS 的方法命名保持一致（`refreshEntitlements` / `restorePurchases`），
 * 旧名 `entitlementState` / `restore` 保留为默认实现转发，避免破坏既有实现。
 */
interface BillingClient {
    suspend fun loadProducts(productIds: List<String>): List<BillingProduct>
    suspend fun purchase(productId: String): PurchaseResult
    suspend fun refreshEntitlements(): EntitlementState

    suspend fun restorePurchases(): EntitlementState {
        // 旧实现只需提供 refreshEntitlements，即视为恢复成功。
        return refreshEntitlements()
    }

    @Deprecated("Use refreshEntitlements()", ReplaceWith("refreshEntitlements()"))
    suspend fun entitlementState(): EntitlementState = refreshEntitlements()

    @Deprecated("Use restorePurchases()", ReplaceWith("restorePurchases()"))
    suspend fun restore(): PurchaseResult = PurchaseResult(
        wasCancelled = false,
        entitlement = restorePurchases(),
    )
}

class ClosureBillingClient(
    private val loadProductsBlock: suspend (List<String>) -> List<BillingProduct>,
    private val purchaseBlock: suspend (String) -> PurchaseResult,
    private val restoreBlock: suspend () -> EntitlementState,
    private val entitlementBlock: suspend () -> EntitlementState,
) : BillingClient {
    override suspend fun loadProducts(productIds: List<String>): List<BillingProduct> =
        loadProductsBlock(productIds)

    override suspend fun purchase(productId: String): PurchaseResult = purchaseBlock(productId)

    override suspend fun restorePurchases(): EntitlementState = restoreBlock()

    override suspend fun refreshEntitlements(): EntitlementState = entitlementBlock()
}
