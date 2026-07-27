package dev.anonymous.transfers_ledger.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class TransactionWithCustomer(
    @Embedded val transaction: TransactionEntity,
    @ColumnInfo(name = "customerDisplayName") val customerDisplayName: String?
) {
    val displayName: String get() = customerDisplayName ?: transaction.senderName
}
