package dev.anonymous.transfers_ledger.data.local.db

import androidx.room.TypeConverter
import dev.anonymous.transfers_ledger.domain.model.DirectionSource
import dev.anonymous.transfers_ledger.domain.model.IdentifierType
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection

class Converters {
    @TypeConverter
    fun toDirection(value: String): TransactionDirection = TransactionDirection.valueOf(value)

    @TypeConverter
    fun fromDirection(value: TransactionDirection): String = value.name

    @TypeConverter
    fun toDirectionSource(value: String): DirectionSource = DirectionSource.valueOf(value)

    @TypeConverter
    fun fromDirectionSource(value: DirectionSource): String = value.name

    @TypeConverter
    fun toIdentifierType(value: String): IdentifierType = IdentifierType.valueOf(value)

    @TypeConverter
    fun fromIdentifierType(value: IdentifierType): String = value.name
}
