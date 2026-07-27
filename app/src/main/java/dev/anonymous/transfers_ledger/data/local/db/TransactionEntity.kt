package dev.anonymous.transfers_ledger.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.anonymous.transfers_ledger.domain.model.DirectionSource
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["notificationKey"], unique = true),
        Index(value = ["transactionReference"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["walletSource"]),
        Index(value = ["direction"]),
        Index(value = ["customerId"]),
        Index(value = ["normalizedSender"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String,
    val transactionReference: String? = null,
    val senderName: String,
    val normalizedSender: String = "",
    val customerId: Long? = null,
    val amount: Double,
    val direction: TransactionDirection = TransactionDirection.INCOMING,
    val directionSource: DirectionSource = DirectionSource.AUTO,
    val paymentType: String,
    val timestamp: Long,
    val rawText: String,
    val rawTitle: String,
    val walletSource: String
)
