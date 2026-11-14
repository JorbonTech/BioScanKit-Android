package com.bioscankit.android.paywall

import androidx.compose.runtime.Immutable
import com.bioscankit.android.design.BioScanTheme

enum class PaywallStyle { INature, CardSelection }
enum class PaywallProductKind { Lifetime, Consumable }

@Immutable
data class PaywallProduct(
    val id: String,
    val kind: PaywallProductKind,
    val title: String,
    val subtitle: String,
    val creditCount: Int? = null,
)

@Immutable
data class BillingProduct(
    val id: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val priceAmountMicros: Long? = null,
    val currencyCode: String? = null,
)

@Immutable
data class PaywallCopy(
    val heroTitle: String,
    val heroSubtitle: String,
    val heroFootnote: String,
    val restoreTitle: String = "RESTORE",
    val retryTitle: String = "Retry",
    val connectingTitle: String = "Connecting to Store",
    val connectingMessage: String = "Products are still loading. Please wait a moment and try again.",
    val pendingTitle: String = "Purchase Pending",
    val pendingMessage: String = "Your payment is being processed. Scans will be added once it completes.",
    val purchaseFailedTitle: String = "Purchase Failed",
    val restoreFailedTitle: String = "Restore Failed",
    val restoreNothingFoundMessage: String = "No previous Lifetime purchase found.",
)

@Immutable
data class PurchaseCatalog(
    val lifetime: PaywallProduct,
    val consumables: List<PaywallProduct> = emptyList(),
    val defaultProductId: String? = null,
) {
    /** 全部商品，顺序为 consumables + lifetime。 */
    val products: List<PaywallProduct> get() = consumables + lifetime

    fun product(id: String): PaywallProduct? = products.firstOrNull { it.id == id }
}

@Immutable
data class PaywallConfiguration(
    val theme: BioScanTheme = BioScanTheme.Neutral,
    val style: PaywallStyle = PaywallStyle.INature,
    val catalog: PurchaseCatalog,
    val copy: PaywallCopy,
    val creditAccounting: CreditAccounting = CreditAccounting.Ledger,
)

/** 权益状态，对齐 iOS `EntitlementState`（iOS 用 `standard` 表示非终身）。 */
enum class EntitlementState { Unknown, Standard, Lifetime }

/**
 * 购买结果，对齐 iOS `PurchaseResult`。
 *
 * iOS 用 `wasCancelled` + `transactionID` + `entitlement` 三个平铺字段表达
 * 成功/取消/待定，这里保持一致，便于两端埋点与状态机逐条对照。
 */
@Immutable
data class PurchaseResult(
    val wasCancelled: Boolean = false,
    val transactionId: String? = null,
    val entitlement: EntitlementState = EntitlementState.Unknown,
    /** Google Play 的 PENDING 交易。iOS 无此状态，映射为「未取消但无额度」。 */
    val isPending: Boolean = false,
    val errorMessage: String? = null,
) {
    val isSuccess: Boolean get() = !wasCancelled && !isPending && errorMessage == null
}

/** 购买结果构造辅助，保持旧调用点（`PurchaseResult.Success` 等）可用。 */
@Suppress("FunctionName")
fun PurchaseResultSuccess(transactionId: String?): PurchaseResult =
    PurchaseResult(transactionId = transactionId, entitlement = EntitlementState.Lifetime)

@Suppress("FunctionName")
fun PurchaseResultError(message: String): PurchaseResult =
    PurchaseResult(errorMessage = message)

@Immutable
data class CreditBalance(
    val free: Int,
    val bonus: Int = 0,
    val purchased: Int,
    val hasUnlimitedAccess: Boolean = false,
) {
    val total: Int
        get() {
            if (hasUnlimitedAccess) return Int.MAX_VALUE
            val first = free.toLong() + bonus
            val sum = first + purchased
            return if (sum >= Int.MAX_VALUE) Int.MAX_VALUE else sum.toInt()
        }
}

/** 计费操作状态，对齐 iOS `BillingOperation`。 */
sealed interface BillingOperation {
    data object Idle : BillingOperation
    data object LoadingProducts : BillingOperation
    data class Purchasing(val productId: String) : BillingOperation
    data object Restoring : BillingOperation
    data object Succeeded : BillingOperation
    data object Cancelled : BillingOperation
    data class Failed(val message: String) : BillingOperation
}

/** 统一提示，对齐 iOS `PaywallNotice`。 */
@Immutable
data class PaywallNotice(
    val id: String,
    val title: String = "",
    val message: String = "",
    /** 组件内置的本地化文案；宿主可优先使用，避免各 App 重复维护。 */
    @androidx.annotation.StringRes val titleRes: Int? = null,
    @androidx.annotation.StringRes val messageRes: Int? = null,
)

/** 额度记账方式，对齐 iOS `CreditAccounting`。 */
enum class CreditAccounting {
    /** 由 BioScanKit 的 CreditLedger 在购买成功后入账（默认）。 */
    Ledger,

    /** 由注入的 BillingClient 自行入账，保持各 App 旧行为。 */
    BillingClient,
}

/**
 * Paywall 事件，逐条对齐 iOS `PaywallEvent`。
 *
 * 事件名与字段顺序也保持一致，App 侧埋点映射可以直接对照 iOS 实现。
 */
sealed interface PaywallEvent {
    data class Shown(val style: PaywallStyle) : PaywallEvent
    data class ProductsLoaded(val count: Int, val state: String) : PaywallEvent
    data class ProductsLoadFailed(val errorType: String) : PaywallEvent
    data class ProductSelected(val productId: String) : PaywallEvent
    data class PurchaseStarted(val productId: String) : PaywallEvent
    data class PurchaseBlocked(val reason: String) : PaywallEvent
    data class PurchaseSucceeded(val productId: String) : PaywallEvent
    data class PurchaseCancelled(val productId: String) : PaywallEvent
    data class PurchaseFailed(
        val productId: String,
        val errorType: String,
        val failureStage: String,
    ) : PaywallEvent
    data object RestoreStarted : PaywallEvent
    data object RestoreSucceeded : PaywallEvent
    data object RestoreNothingFound : PaywallEvent
    data class RestoreFailed(val errorType: String) : PaywallEvent
}

/** Paywall 动作集合，对齐 iOS `PaywallActions`。 */
data class PaywallActions(
    val dismiss: () -> Unit = {},
    val track: (PaywallEvent) -> Unit = {},
    val syncCreditBalance: () -> Unit = {},
)
