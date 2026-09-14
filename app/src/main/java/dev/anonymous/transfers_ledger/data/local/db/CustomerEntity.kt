package dev.anonymous.transfers_ledger.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val defaultOutgoing: Boolean = false,
    val defaultExcluded: Boolean = false
)
