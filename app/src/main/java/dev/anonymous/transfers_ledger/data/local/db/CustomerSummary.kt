package dev.anonymous.transfers_ledger.data.local.db

data class CustomerSummary(
    val id: Long,
    val displayName: String,
    val createdAt: Long,
    val accountCount: Int,
    val incomingTotal: Double,
    val outgoingTotal: Double
)
