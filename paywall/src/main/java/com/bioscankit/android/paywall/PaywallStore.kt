package com.bioscankit.android.paywall

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Paywall 状态，对齐 iOS `PaywallStore` 的 `@Published` 字段集合。
 *
 * 与 iOS 的对应关系：
 * - `products` → `[String: BillingProduct]`
 * - `entitlement` / `creditBalance` / `operation` / `notice` / `selectedProductId` 同名同义
 */
data class PaywallState(
    val products: Map<String, BillingProduct> = emptyMap(),
    val entitlement: EntitlementState = EntitlementState.Unknown,
    val creditBalance: CreditBalance = CreditBalance(free = 0, purchased = 0),
    val operation: BillingOperation = BillingOperation.Idle,
    val notice: PaywallNotice? = null,
    val selectedProductId: String? = null,
)

/**
 * Paywall 状态机，对齐 iOS `PaywallStore`。
 *
 * 设计要点（与 iOS 逐条一致）：
 * 1. 购买编排在 Store 内完成，App 只提供 `BillingClient` 与 `CreditLedger`。
 * 2. `failureStage` 区分 `store_purchase`（商店环节）与 `credit_delivery`（额度入账环节）。
 * 3. 额度记账方式由 `CreditAccounting` 决定；`Ledger` 模式下入账失败视为购买失败。
 * 4. 所有事件经 `PaywallActions.track` 抛出，组件内不硬编码埋点名。
 *
 * 线程模型：Android 侧不强制主线程，宿主应在主线程调用（与 Compose 状态读取一致）。
 */
class PaywallStore(
    val configuration: PaywallConfiguration,
    private val billingClient: BillingClient,
    private val creditLedger: CreditLedger?,
    private val actions: PaywallActions = PaywallActions(),
) {
    /**
     * 当前状态。使用 Compose 的 `mutableStateOf` 以便 UI 能观察到变化
     * （对应 iOS 的 `@Published`）。
     */
    var state: PaywallState by mutableStateOf(
        PaywallState(
            entitlement = creditLedger?.balance()?.let { balance ->
                if (balance.hasUnlimitedAccess) EntitlementState.Lifetime else EntitlementState.Unknown
            } ?: EntitlementState.Unknown,
            creditBalance = creditLedger?.balance() ?: CreditBalance(free = 0, purchased = 0),
            selectedProductId = configuration.catalog.defaultProductId,
        )
    )
        private set

    private var didTrackAppearance = false

    val selectedProduct: PaywallProduct?
        get() = state.selectedProductId?.let { configuration.catalog.product(it) }

    val isBusy: Boolean
        get() = when (state.operation) {
            BillingOperation.LoadingProducts,
            is BillingOperation.Purchasing,
            BillingOperation.Restoring,
            -> true
            else -> false
        }

    fun productDetails(productId: String): BillingProduct? = state.products[productId]

    /** 首次展示埋点，只上报一次（对应 iOS `didTrackAppearance`）。 */
    fun appeared() {
        if (didTrackAppearance) return
        didTrackAppearance = true
        actions.track(PaywallEvent.Shown(configuration.style))
    }

    fun select(productId: String) {
        if (configuration.catalog.product(productId) == null) return
        if (state.selectedProductId == productId) return
        state = state.copy(selectedProductId = productId)
        actions.track(PaywallEvent.ProductSelected(productId))
    }

    fun dismiss() {
        actions.dismiss()
    }

    suspend fun load() {
        state = state.copy(notice = null, operation = BillingOperation.LoadingProducts)
        try {
            val productIds = configuration.catalog.products.map(PaywallProduct::id)
            val loaded = billingClient.loadProducts(productIds)
            val entitlement = billingClient.refreshEntitlements()

            state = state.copy(
                products = loaded.associateBy(BillingProduct::id),
            )
            applyEntitlement(entitlement)
            state = state.copy(operation = BillingOperation.Idle)

            val expected = configuration.catalog.products.size
            val productsState = when {
                state.products.isEmpty() -> "empty"
                state.products.size == expected -> "loaded"
                else -> "partial"
            }
            actions.track(
                PaywallEvent.ProductsLoaded(
                    count = state.products.size,
                    state = productsState,
                )
            )
        } catch (error: Throwable) {
            state = state.copy(
                operation = BillingOperation.Failed(error.message.orEmpty()),
            )
            actions.track(PaywallEvent.ProductsLoadFailed(errorType(error)))
        }
    }

    suspend fun purchaseSelectedProduct() {
        val product = selectedProduct ?: run {
            actions.track(PaywallEvent.PurchaseBlocked(reason = "no_product_selected"))
            return
        }
        if (state.products[product.id] == null) {
            actions.track(PaywallEvent.PurchaseBlocked(reason = "products_not_ready"))
            state = state.copy(operation = BillingOperation.Idle)
            state = state.copy(
                notice = PaywallNotice(
                    id = noticeId(),
                    title = configuration.copy.connectingTitle,
                    message = configuration.copy.connectingMessage,
                    titleRes = R.string.bioscan_paywall_connecting_title,
                    messageRes = R.string.bioscan_paywall_connecting_message,
                ),
            )
            return
        }

        state = state.copy(
            notice = null,
            operation = BillingOperation.Purchasing(product.id),
        )
        actions.track(PaywallEvent.PurchaseStarted(product.id))
        var failureStage = STAGE_STORE_PURCHASE

        try {
            val result = billingClient.purchase(product.id)

            if (result.wasCancelled) {
                state = state.copy(operation = BillingOperation.Cancelled)
                actions.track(PaywallEvent.PurchaseCancelled(product.id))
                return
            }
            if (result.isPending) {
                // Google Play 待定交易：既非成功也非失败，保持 idle 并提示。
                state = state.copy(
                    operation = BillingOperation.Idle,
                    notice = PaywallNotice(
                        id = noticeId(),
                        title = configuration.copy.pendingTitle,
                        message = configuration.copy.pendingMessage,
                        titleRes = R.string.bioscan_paywall_pending_title,
                        messageRes = R.string.bioscan_paywall_pending_message,
                    ),
                )
                return
            }
            if (result.errorMessage != null) {
                throw IllegalStateException(result.errorMessage)
            }

            when (product.kind) {
                PaywallProductKind.Lifetime -> applyEntitlement(EntitlementState.Lifetime)
                PaywallProductKind.Consumable -> {
                    val credits = product.creditCount ?: 0
                    if (configuration.creditAccounting == CreditAccounting.Ledger) {
                        failureStage = STAGE_CREDIT_DELIVERY
                        val ledger = creditLedger
                            ?: throw IllegalStateException(
                                "CreditAccounting.Ledger requires a CreditLedger."
                            )
                        val delivery = ledger.deliverPurchasedCredits(
                            transactionId = result.transactionId,
                            credits = credits,
                        )
                        // Delivered / AlreadyDelivered 都算入账成功（幂等）。
                        state = state.copy(
                            creditBalance = when (delivery) {
                                is CreditDeliveryResult.Delivered -> delivery.balance
                                is CreditDeliveryResult.AlreadyDelivered -> delivery.balance
                            },
                        )
                    } else {
                        // BillingClient 模式：App 已自行入账，仅刷新余额。
                        refreshCreditBalance()
                    }
                }
            }

            if (result.entitlement == EntitlementState.Lifetime) {
                applyEntitlement(EntitlementState.Lifetime)
            }

            state = state.copy(operation = BillingOperation.Succeeded)
            actions.track(PaywallEvent.PurchaseSucceeded(product.id))
        } catch (error: Throwable) {
            state = state.copy(
                operation = BillingOperation.Idle,
                notice = PaywallNotice(
                    id = noticeId(),
                    title = configuration.copy.purchaseFailedTitle,
                    titleRes = R.string.bioscan_paywall_purchase_failed_title,
                    message = error.message.orEmpty(),
                ),
            )
            actions.track(
                PaywallEvent.PurchaseFailed(
                    productId = product.id,
                    errorType = errorType(error),
                    failureStage = failureStage,
                )
            )
        }
    }

    suspend fun restorePurchases() {
        state = state.copy(notice = null)
        actions.track(PaywallEvent.RestoreStarted)
        state = state.copy(operation = BillingOperation.Restoring)

        try {
            val restored = billingClient.restorePurchases()
            applyEntitlement(restored)
            if (restored == EntitlementState.Lifetime) {
                state = state.copy(operation = BillingOperation.Succeeded)
                actions.track(PaywallEvent.RestoreSucceeded)
            } else {
                state = state.copy(
                    operation = BillingOperation.Idle,
                    notice = PaywallNotice(
                        id = noticeId(),
                        title = configuration.copy.restoreTitle,
                        message = configuration.copy.restoreNothingFoundMessage,
                        messageRes = R.string.bioscan_paywall_restore_nothing_message,
                    ),
                )
                actions.track(PaywallEvent.RestoreNothingFound)
            }
        } catch (error: Throwable) {
            state = state.copy(
                operation = BillingOperation.Idle,
                notice = PaywallNotice(
                    id = noticeId(),
                    title = configuration.copy.restoreFailedTitle,
                    titleRes = R.string.bioscan_paywall_restore_failed_title,
                    message = error.message.orEmpty(),
                ),
            )
            actions.track(PaywallEvent.RestoreFailed(errorType(error)))
        }
    }

    fun refreshCreditBalance() {
        state = state.copy(creditBalance = creditLedger?.balance() ?: state.creditBalance)
    }

    fun clearTransientOperation() {
        state = when (state.operation) {
            BillingOperation.Succeeded,
            BillingOperation.Cancelled,
            is BillingOperation.Failed,
            -> state.copy(operation = BillingOperation.Idle)
            else -> state
        }
    }

    fun clearNotice() {
        state = state.copy(notice = null)
    }

    private fun applyEntitlement(value: EntitlementState) {
        creditLedger?.setLifetime(value == EntitlementState.Lifetime)
        state = state.copy(
            entitlement = value,
            creditBalance = creditLedger?.balance() ?: state.creditBalance,
        )
    }

    private fun noticeId(): String = "notice-${System.identityHashCode(this)}-${noticeSeq++}"

    private var noticeSeq = 0

    private fun errorType(error: Throwable): String =
        error::class.simpleName ?: "unknown"

    companion object {
        /** 商店（RevenueCat / Play Billing）环节失败。 */
        const val STAGE_STORE_PURCHASE = "store_purchase"

        /** 额度入账环节失败，与 iOS `failureStage` 取值一致。 */
        const val STAGE_CREDIT_DELIVERY = "credit_delivery"
    }
}
