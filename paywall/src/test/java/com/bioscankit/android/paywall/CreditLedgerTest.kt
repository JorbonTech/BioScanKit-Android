package com.bioscankit.android.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditLedgerTest {
    @Test
    fun transactionDeliveryIsIdempotent() {
        val storage = MemoryCreditStorage()
        val ledger = CreditLedger(storage)

        assertTrue(ledger.deliverPurchasedCredits("order-1", 5) is CreditDeliveryResult.Delivered)
        assertTrue(ledger.deliverPurchasedCredits("order-1", 5) is CreditDeliveryResult.AlreadyDelivered)
        assertEquals(5, ledger.balance().purchased)
    }

    @Test
    fun creditsAreConsumedInFreeBonusThenPurchasedOrder() {
        val storage = MemoryCreditStorage(free = 1, bonus = 1, purchased = 1)
        val ledger = CreditLedger(storage)

        assertTrue(ledger.consumeOne())
        assertEquals(CreditBalance(free = 0, bonus = 1, purchased = 1), ledger.balance())
        assertTrue(ledger.consumeOne())
        assertEquals(CreditBalance(free = 0, bonus = 0, purchased = 1), ledger.balance())
        assertTrue(ledger.consumeOne())
        assertEquals(CreditBalance(free = 0, bonus = 0, purchased = 0), ledger.balance())
        assertFalse(ledger.consumeOne())
    }

    @Test
    fun lifetimeAccessDoesNotConsumeCredits() {
        val storage = MemoryCreditStorage(free = 1, bonus = 1, purchased = 1, lifetime = true)
        val ledger = CreditLedger(storage)

        assertTrue(ledger.consumeOne())
        assertEquals(CreditBalance(free = 1, bonus = 1, purchased = 1, hasUnlimitedAccess = true), ledger.balance())
    }

    @Test
    fun seedsInitialFreeCreditsOnlyOnceAndDoesNotOverwriteExistingBalance() {
        val fresh = MemoryCreditStorage(free = 0, hasStoredFreeCredits = false, seeded = false)
        CreditLedger(fresh, CreditLedgerConfiguration(initialFreeCredits = 5))
        assertEquals(5, fresh.readFreeCredits())

        val existing = MemoryCreditStorage(free = 3, hasStoredFreeCredits = true, seeded = false)
        CreditLedger(existing, CreditLedgerConfiguration(initialFreeCredits = 5))
        assertEquals(3, existing.readFreeCredits())
    }

    @Test
    fun migratesLegacyBonusCreditsIntoSecureBalanceOnce() {
        val storage = MemoryCreditStorage(
            purchased = 2,
            legacyBonus = 4,
            legacyMigrated = false,
        )
        val ledger = CreditLedger(
            storage,
            CreditLedgerConfiguration(migrateLegacyBonusCredits = true),
        )

        assertEquals(6, ledger.balance().purchased)
        assertEquals(0, storage.readLegacyBonusCredits())
        assertTrue(storage.isLegacyBonusMigrated())
    }

    @Test
    fun recoveryBonusIsIdempotentPerCampaign() {
        val storage = MemoryCreditStorage()
        val ledger = CreditLedger(storage)

        assertTrue(ledger.grantRecoveryCredits("welcome", 3) is CreditDeliveryResult.Delivered)
        assertTrue(ledger.grantRecoveryCredits("welcome", 3) is CreditDeliveryResult.AlreadyDelivered)
        assertEquals(3, ledger.balance().bonus)
    }
}

internal class MemoryCreditStorage(
    private var free: Int = 0,
    private var bonus: Int = 0,
    private var purchased: Int = 0,
    private var lifetime: Boolean = false,
    private var legacyBonus: Int = 0,
    private var legacyMigrated: Boolean = true,
    private var seeded: Boolean = true,
    private var hasStoredFreeCredits: Boolean = true,
) : CreditStorage {
    private val transactions = mutableSetOf<String>()

    override fun readFreeCredits(): Int = free
    override fun writeFreeCredits(value: Int) { free = value }
    override fun readPurchasedCredits(): Int = purchased
    override fun writePurchasedCredits(value: Int) { purchased = value }
    override fun containsTransaction(transactionId: String): Boolean = transactionId in transactions
    override fun recordTransaction(transactionId: String) { transactions += transactionId }

    override fun readBonusCredits(): Int = bonus
    override fun writeBonusCredits(value: Int) { bonus = value }
    override fun readLifetimeCache(): Boolean = lifetime
    override fun writeLifetimeCache(value: Boolean) { lifetime = value }

    override fun hasSeededFreeCredits(): Boolean = seeded
    override fun markFreeCreditsSeeded() { seeded = true }
    override fun hasStoredFreeCredits(): Boolean = hasStoredFreeCredits

    override fun readLegacyBonusCredits(): Int = legacyBonus
    override fun clearLegacyBonusCredits() { legacyBonus = 0 }
    override fun isLegacyBonusMigrated(): Boolean = legacyMigrated
    override fun markLegacyBonusMigrated() { legacyMigrated = true }
}
