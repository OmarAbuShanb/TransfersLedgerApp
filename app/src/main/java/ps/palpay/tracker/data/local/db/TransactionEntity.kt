package ps.palpay.tracker.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["notificationKey"], unique = true),
        Index(value = ["transactionReference"], unique = true) // ضمان عدم تكرار نفس المرجع المالي
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String,
    val transactionReference: String? = null, // المرجع الخاص بجوال باي أو غيره
    val senderName: String,
    val amount: Double,
    val paymentType: String,
    val timestamp: Long,
    val rawText: String,
    val rawTitle: String,
    val walletSource: String
)
