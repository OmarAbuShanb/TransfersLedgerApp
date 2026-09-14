package dev.anonymous.transfers_ledger.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unprocessed_notifications")
data class UnprocessedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)
