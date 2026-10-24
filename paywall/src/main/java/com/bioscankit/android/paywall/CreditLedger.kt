package com.bioscankit.android.paywall

/**
 * Persistence boundary for [CreditLedger].
 *
 * The bonus/lifetime/seed/migration methods have defaults so existing app
 * implementations keep compiling. Hosts that want the full BioScanKit
 * behaviour should override them.
 */
interface CreditStorage {
    fun readFreeCredits(): Int
    fun writeFreeCredits(value: Int)
    fun readPurchasedCredits(): Int
    fun writePurchasedCredits(value: Int)
    fun containsTransaction(transactionId: String): Boolean
    fun recordTransaction(transactionId: String)

    fun readBonusCredits(): Int = 0
    fun writeBonusCredits(value: Int) = Unit
    fun readLifetimeCache(): Boolean = false
    fun writeLifetimeCache(value: Boolean) = Unit

    /** Whether the initial free allowance has already been seeded. */
    fun hasSeededFreeCredits(): Boolean = true
    fun markFreeCreditsSeeded() = Unit

    /** Whether a free-credit value already exists from a previous build. */
    fun hasStoredFreeCredits(): Boolean = true

    /** Legacy bonus balance that should be folded into purchased credits once. */
    fun readLegacyBonusCredits(): Int = 0
    fun clearLegacyBonusCredits() = Unit
    fun isLegacyBonusMigrated(): Boolean = true
    fun markLegacyBonusMigrated() = Unit
}

sealed interface CreditDeliveryResult {
    data class Delivered(val balance: CreditBalance) : CreditDeliveryResult
    data class AlreadyDelivered(val balance: CreditBalance) : CreditDeliveryResult
}

/**
 * Seed/migration options for [CreditLedger].
 *
 * Defaults are intentionally conservative: no seeding and no legacy migration,
 * so existing hosts keep their current balance semantics.
 */
data class CreditLedgerConfiguration(
    val initialFreeCredits: Int? = null,
    val migrateLegacyBonusCredits: Boolean = false,
)

class CreditLedger(
    private val storage: CreditStorage,
    private val configuration: CreditLedgerConfiguration = CreditLedgerConfiguration(),
) {
    init {
        seedFreeCreditsIfNeeded()
        migrateLegacyBonusCreditsIfNeeded()
    }

    @Synchronized
    fun balance(): CreditBalance = CreditBalance(
        free = storage.readFreeCredits().coerceAtLeast(0),
        bonus = storage.readBonusCredits().coerceAtLeast(0),
        purchased = storage.readPurchasedCredits().coerceAtLeast(0),
        hasUnlimitedAccess = storage.readLifetimeCache(),
    )

    @Synchronized
    fun deliverPurchasedCredits(
        transactionId: String?,
        credits: Int,
    ): CreditDeliveryResult {
        val normalizedTransactionId = transactionId?.trim().orEmpty()
        require(normalizedTransactionId.isNotEmpty()) { "A transaction ID is required for idempotent delivery." }
        require(credits > 0) { "Credit delivery must be positive." }
        if (storage.containsTransaction(normalizedTransactionId)) {
            return CreditDeliveryResult.AlreadyDelivered(balance())
        }
        val current = storage.readPurchasedCredits().coerceAtLeast(0)
        val updated = current.toLong() + credits
        require(updated <= Int.MAX_VALUE) { "Credit balance overflow." }
        storage.writePurchasedCredits(updated.toInt())
        storage.recordTransaction(normalizedTransactionId)
        return CreditDeliveryResult.Delivered(balance())
    }

    /**
     * Grants a one-time recovery bonus. The campaign ID is used as an
     * idempotency key, matching the iOS `grantRecoveryCredits` behaviour.
     */
    @Synchronized
    fun grantRecoveryCredits(
        campaignId: String,
        credits: Int,
    ): CreditDeliveryResult {
        val normalizedCampaignId = campaignId.trim()
        require(normalizedCampaignId.isNotEmpty()) { "A campaign ID is required for bonus delivery." }
        require(credits > 0) { "Credit delivery must be positive." }
        val transactionId = "recovery:$normalizedCampaignId"
        if (storage.containsTransaction(transactionId)) {
            return CreditDeliveryResult.AlreadyDelivered(balance())
        }
        val current = storage.readBonusCredits().coerceAtLeast(0)
        val updated = current.toLong() + credits
        require(updated <= Int.MAX_VALUE) { "Credit balance overflow." }
        storage.writeBonusCredits(updated.toInt())
        storage.recordTransaction(transactionId)
        return CreditDeliveryResult.Delivered(balance())
    }

    @Synchronized
    fun setLifetime(active: Boolean) {
        storage.writeLifetimeCache(active)
    }

    /**
     * Consumes one credit in the iOS order: free -> bonus -> purchased.
     * Lifetime access never consumes a credit.
     */
    @Synchronized
    fun consumeOne(): Boolean {
        if (storage.readLifetimeCache()) return true

        val free = storage.readFreeCredits().coerceAtLeast(0)
        if (free > 0) {
            storage.writeFreeCredits(free - 1)
            return true
        }

        val bonus = storage.readBonusCredits().coerceAtLeast(0)
        if (bonus > 0) {
            storage.writeBonusCredits(bonus - 1)
            return true
        }

        val purchased = storage.readPurchasedCredits().coerceAtLeast(0)
        if (purchased > 0) {
            storage.writePurchasedCredits(purchased - 1)
            return true
        }
        return false
    }

    fun seedFreeCreditsOnce(initialAllowance: Int) {
        require(initialAllowance >= 0)
        if (storage.hasSeededFreeCredits()) return
        // Upgrade compatibility: never refill an existing balance.
        if (!storage.hasStoredFreeCredits()) {
            storage.writeFreeCredits(initialAllowance)
        }
        storage.markFreeCreditsSeeded()
    }

    fun migrateLegacyBonusCreditsOnce() {
        if (storage.isLegacyBonusMigrated()) return
        val legacyBonus = storage.readLegacyBonusCredits().coerceAtLeast(0)
        val current = storage.readPurchasedCredits().coerceAtLeast(0)
        val updated = current.toLong() + legacyBonus
        if (updated > Int.MAX_VALUE) return
        storage.writePurchasedCredits(updated.toInt())
        storage.clearLegacyBonusCredits()
        storage.markLegacyBonusMigrated()
    }

    private fun seedFreeCreditsIfNeeded() {
        configuration.initialFreeCredits?.let(::seedFreeCreditsOnce)
    }

    private fun migrateLegacyBonusCreditsIfNeeded() {
        if (configuration.migrateLegacyBonusCredits) migrateLegacyBonusCreditsOnce()
    }
}
