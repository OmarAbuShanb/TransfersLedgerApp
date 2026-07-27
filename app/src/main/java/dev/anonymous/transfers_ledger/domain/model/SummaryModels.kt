package dev.anonymous.transfers_ledger.domain.model

enum class SummaryPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    ALL
}

enum class TransactionFilter {
    ALL,
    INCOMING,
    OUTGOING
}

data class DateRange(
    val startAt: Long?,
    val endAt: Long?
)

data class WalletStats(
    val count: Int = 0,
    val incoming: Double = 0.0,
    val outgoing: Double = 0.0
) {
    val net: Double get() = incoming - outgoing
}

data class DashboardStats(
    val total: WalletStats = WalletStats(),
    val palpay: WalletStats = WalletStats(),
    val jawwalPay: WalletStats = WalletStats(),
    val bankOfPalestine: WalletStats = WalletStats()
)
