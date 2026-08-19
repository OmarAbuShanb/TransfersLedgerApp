package dev.anonymous.transfers_ledger.core

import dev.anonymous.transfers_ledger.data.local.db.TransactionEntity
import dev.anonymous.transfers_ledger.domain.model.DashboardStats
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection
import dev.anonymous.transfers_ledger.domain.model.WalletStats

object TransactionStatsCalculator {
    fun calculate(transactions: List<TransactionEntity>): DashboardStats {
        val total = MutableWalletStats()
        val palpay = MutableWalletStats()
        val jawwalPay = MutableWalletStats()
        val bankOfPalestine = MutableWalletStats()

        transactions.forEach { transaction ->
            total.add(transaction)
            when (transaction.walletSource) {
                PaymentSources.PALPAY -> palpay.add(transaction)
                PaymentSources.JAWWAL_PAY -> jawwalPay.add(transaction)
                PaymentSources.BANK_OF_PALESTINE -> bankOfPalestine.add(transaction)
            }
        }

        return DashboardStats(
            total = total.toWalletStats(),
            palpay = palpay.toWalletStats(),
            jawwalPay = jawwalPay.toWalletStats(),
            bankOfPalestine = bankOfPalestine.toWalletStats()
        )
    }

    private class MutableWalletStats {
        var count: Int = 0
        var incoming: Double = 0.0
        var outgoing: Double = 0.0

        fun add(transaction: TransactionEntity) {
            count++
            if (transaction.direction == TransactionDirection.OUTGOING) {
                outgoing += transaction.amount
            } else {
                incoming += transaction.amount
            }
        }

        fun toWalletStats(): WalletStats = WalletStats(count, incoming, outgoing)
    }

}
