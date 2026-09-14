package dev.anonymous.transfers_ledger.domain.model

enum class TransactionDirection {
    INCOMING,
    OUTGOING
}

enum class DirectionSource {
    AUTO,
    MANUAL,
    DEFAULT
}

enum class IdentifierType {
    PHONE,
    NAME
}
