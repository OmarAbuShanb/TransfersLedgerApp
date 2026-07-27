package dev.anonymous.transfers_ledger.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.anonymous.transfers_ledger.domain.model.IdentifierType

@Entity(
    tableName = "customer_identifiers",
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["normalizedValue"]),
        Index(value = ["type", "normalizedValue"], unique = true)
    ]
)
data class CustomerIdentifierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val type: IdentifierType,
    val value: String,
    val normalizedValue: String,
    val createdAt: Long
)
