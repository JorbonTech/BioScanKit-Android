package com.bioscankit.android.paywall

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PaywallStore 行为测试。
 *
 * 重点验证与 iOS `PaywallStore` 逐条一致的三件事：
 * 1. 事件序列（事件名 + 关键字段）
 * 2. `failureStage` 能区分商店环节与额度入账环节
 * 3. 额度入账幂等，且 `CreditAccounting.BillingClient` 不重复入账
 */
class PaywallStoreTest {

    private val catalog = PurchaseCatalog(
        lifetime = PaywallProduct(
            id = "lifetime",
            kind = PaywallProductKind.Lifetime,
            title = "Lifetime",
            subtitle = "",
        ),
        consumables = listOf(
            PaywallProduct(
                id = "scans5",
                kind = PaywallProductKind.Consumable,
                title = "5 Scans",
                subtitle = "",
                creditCount = 5,
            ),
        ),
        defaultProductId = "scans5",
    )

    private fun store(
        client: BillingClient,
        ledger: CreditLedger? = null,
        creditAccounting: CreditAccounting = CreditAccounting.Ledger,
        events: MutableList<PaywallEvent> = mutableListOf(),
    ): Pair<PaywallStore, MutableList<PaywallEvent>> {
        val configuration = PaywallConfiguration(
            style = PaywallStyle.CardSelection,
            catalog = catalog,
            copy = PaywallCopy(heroTitle = "", heroSubtitle = "", heroFootnote = ""),
            creditAccounting = creditAccounting,
        )
        return PaywallStore(
            configuration = configuration,
            billingClient = client,
            creditLedger = ledger,
            actions = PaywallActions(track = { events += it }),
        ) to events
    }

    @Test
    fun shownIsTrackedOnlyOnce() {
        val (store, events) = store(FakeBillingClient())
        store.appeared()
        store.appeared()
        assertEquals(1, events.size)
        assertEquals(PaywallEvent.Shown(PaywallStyle.CardSelection), events.first())
    }

    @Test
    fun loadTracksProductsLoadedWithState() = runBlocking {
        val (store, events) = store(
            FakeBillingClient(
                products = listOf(
                    BillingProduct("scans5", "5 Scans", "", "$0.99"),
                    BillingProduct("lifetime", "Lifetime", "", "$27.99"),
                ),
            )
        )
        store.load()

        assertEquals(2, store.state.products.size)
        assertTrue(events.contains(PaywallEvent.ProductsLoaded(count = 2, state = "loaded")))
    }

    @Test
    fun loadFailureTracksProductLoadFailed() = runBlocking {
        val (store, events) = store(FakeBillingClient(throwOnLoad = IllegalStateException("boom")))
        store.load()

        assertEquals(1, events.size)
        assertTrue(events.first() is PaywallEvent.ProductsLoadFailed)
        assertTrue(store.state.operation is BillingOperation.Failed)
    }

    @Test
    fun consumablePurchaseDeliversCreditsOnce() = runBlocking {
        val ledger = CreditLedger(MemoryCreditStorage())
        val (store, events) = store(
            FakeBillingClient(
                products = listOf(BillingProduct("scans5", "5 Scans", "", "$0.99")),
                purchaseResult = PurchaseResult(transactionId = "order-1"),
            ),
            ledger = ledger,
        )
        store.load()
        store.purchaseSelectedProduct()

        assertEquals(5, store.state.creditBalance.purchased)
        assertEquals(BillingOperation.Succeeded, store.state.operation)
        assertTrue(events.contains(PaywallEvent.PurchaseStarted("scans5")))
        assertTrue(events.contains(PaywallEvent.PurchaseSucceeded("scans5")))
    }

    @Test
    fun duplicateTransactionDoesNotDoubleDeliver() = runBlocking {
        val ledger = CreditLedger(MemoryCreditStorage())
        val client = FakeBillingClient(
            products = listOf(BillingProduct("scans5", "5 Scans", "", "$0.99")),
            purchaseResult = PurchaseResult(transactionId = "order-1"),
        )
        val (store, _) = store(client, ledger = ledger)
        store.load()

        store.purchaseSelectedProduct()
        store.purchaseSelectedProduct()

        assertEquals(5, store.state.creditBalance.purchased)
    }

    @Test
    fun creditDeliveryFailureUsesCreditDeliveryStage() = runBlocking {
        // 未提供 transactionId 时，CreditLedger 会拒绝入账，模拟「额度入账失败」。
        val ledger = CreditLedger(MemoryCreditStorage())
        val (store, events) = store(
            FakeBillingClient(
                products = listOf(BillingProduct("scans5", "5 Scans", "", "$0.99")),
                purchaseResult = PurchaseResult(transactionId = null),
            ),
            ledger = ledger,
        )
        store.load()
        store.purchaseSelectedProduct()

        val failure = events.filterIsInstance<PaywallEvent.PurchaseFailed>().single()
        assertEquals(PaywallStore.STAGE_CREDIT_DELIVERY, failure.failureStage)
        assertEquals(0, store.state.creditBalance.purchased)
        assertFalse(events.contains(PaywallEvent.PurchaseSucceeded("scans5")))
    }

    @Test
    fun billingClientAccountingSkipsLedgerDelivery() = runBlocking {
        val ledger = CreditLedger(MemoryCreditStorage())
        val (store, _) = store(
            FakeBillingClient(
                products = listOf(BillingProduct("scans5", "5 Scans", "", "$0.99")),
                purchaseResult = PurchaseResult(transactionId = "order-1"),
            ),
            ledger = ledger,
            creditAccounting = CreditAccounting.BillingClient,
        )
        store.load()
        store.purchaseSelectedProduct()

        // BillingClient 模式下组件不入账，余额保持 0。
        assertEquals(0, store.state.creditBalance.purchased)
        assertEquals(BillingOperation.Succeeded, store.state.operation)
    }

    @Test
    fun cancelledPurchaseTracksCancellation() = runBlocking {
        val (store, events) = store(
            FakeBillingClient(
                products = listOf(BillingProduct("scans5", "5 Scans", "", "$0.99")),
                purchaseResult = PurchaseResult(wasCancelled = true),
            )
        )
        store.load()
        store.purchaseSelectedProduct()

        assertEquals(BillingOperation.Cancelled, store.state.operation)
        assertTrue(events.contains(PaywallEvent.PurchaseCancelled("scans5")))
        assertNull(store.state.notice)
    }

    @Test
    fun purchaseBeforeProductsLoadIsBlocked() = runBlocking {
        val (store, events) = store(FakeBillingClient())
        store.purchaseSelectedProduct()

        assertEquals(
            PaywallEvent.PurchaseBlocked(reason = "products_not_ready"),
            events.first(),
        )
        assertEquals(BillingOperation.Idle, store.state.operation)
    }

    @Test
    fun lifetimePurchaseAppliesEntitlement() = runBlocking {
        val ledger = CreditLedger(MemoryCreditStorage())
        val (store, _) = store(
            FakeBillingClient(
                products = listOf(BillingProduct("lifetime", "Lifetime", "", "$27.99")),
                purchaseResult = PurchaseResult(
                    transactionId = "order-2",
                    entitlement = EntitlementState.Lifetime,
                ),
            ),
            ledger = ledger,
        )
        store.select("lifetime")
        store.load()
        store.purchaseSelectedProduct()

        assertEquals(EntitlementState.Lifetime, store.state.entitlement)
        assertTrue(store.state.creditBalance.hasUnlimitedAccess)
    }

    @Test
    fun restoreNothingFoundProducesNoticeAndEvent() = runBlocking {
        val (store, events) = store(
            FakeBillingClient(restoredEntitlement = EntitlementState.Standard)
        )
        store.restorePurchases()

        assertTrue(events.contains(PaywallEvent.RestoreStarted))
        assertTrue(events.contains(PaywallEvent.RestoreNothingFound))
        assertEquals(BillingOperation.Idle, store.state.operation)
        assertTrue(store.state.notice != null)
    }

    @Test
    fun restoreFailureTracksErrorType() = runBlocking {
        val (store, events) = store(
            FakeBillingClient(throwOnRestore = IllegalStateException("network"))
        )
        store.restorePurchases()

        val failure = events.filterIsInstance<PaywallEvent.RestoreFailed>().single()
        assertEquals("IllegalStateException", failure.errorType)
    }

    @Test
    fun selectIgnoresUnknownProduct() {
        val (store, events) = store(FakeBillingClient())
        store.select("does-not-exist")
        assertTrue(events.isEmpty())
    }
}

private class FakeBillingClient(
    private val products: List<BillingProduct> = emptyList(),
    private val purchaseResult: PurchaseResult = PurchaseResult(transactionId = "order"),
    private val restoredEntitlement: EntitlementState = EntitlementState.Standard,
    private val throwOnLoad: Throwable? = null,
    private val throwOnRestore: Throwable? = null,
) : BillingClient {
    override suspend fun loadProducts(productIds: List<String>): List<BillingProduct> {
        throwOnLoad?.let { throw it }
        return products
    }

    override suspend fun purchase(productId: String): PurchaseResult = purchaseResult

    override suspend fun refreshEntitlements(): EntitlementState = EntitlementState.Standard

    override suspend fun restorePurchases(): EntitlementState {
        throwOnRestore?.let { throw it }
        return restoredEntitlement
    }
}
