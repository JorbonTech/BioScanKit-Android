package com.bioscankit.android.paywall

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates recognition access and credit consumption for app recognition flows.
 *
 * The caller performs validation and starts the recognition operation; this
 * controller only consumes a credit after the operation completes successfully.
 */
class RecognitionAccessController(
    private val ledger: CreditLedger,
) {
    sealed class AccessError(message: String) : IllegalStateException(message) {
        data object NoCredits : AccessError("No identifications remaining.")

        data object Busy : AccessError("An identification is already in progress.")
    }

    private val busy = AtomicBoolean(false)

    fun balance(): CreditBalance = ledger.balance()

    fun hasAccess(): Boolean = ledger.balance().let {
        it.hasUnlimitedAccess || it.total > 0
    }

    suspend fun <Result> perform(operation: suspend () -> Result): Result {
        if (!hasAccess()) throw AccessError.NoCredits
        if (!busy.compareAndSet(false, true)) {
            throw AccessError.Busy
        }
        try {
            val result = operation()
            if (!ledger.consumeOne()) throw AccessError.NoCredits
            return result
        } finally {
            busy.set(false)
        }
    }
}
