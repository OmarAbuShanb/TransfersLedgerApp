package dev.anonymous.transfers_ledger.core

import androidx.annotation.ColorRes
import dev.anonymous.transfers_ledger.R

object PaymentSources {
    const val PALPAY_PACKAGE = "com.pcnc.wallet"
    const val JAWWAL_PAY_PACKAGE = "ps.Jawwal.JawwalPayNewPlus"
    const val BANK_OF_PALESTINE_PACKAGE = "com.pcnc.bop"
    const val QUDS_BANK_PACKAGE = "com.icsfs.qb.test"
    const val ARAB_ISLAMIC_BANK_PACKAGE = "com.pcnc.aib"
    const val PIB_PACKAGE = "com.icsfs.pibank"

    val trackedPackages = listOf(
        PALPAY_PACKAGE, JAWWAL_PAY_PACKAGE, BANK_OF_PALESTINE_PACKAGE,
        QUDS_BANK_PACKAGE, ARAB_ISLAMIC_BANK_PACKAGE, PIB_PACKAGE
    )

    const val PALPAY = "PalPay"
    const val JAWWAL_PAY = "JawwalPay"
    const val BANK_OF_PALESTINE = "Bank of Palestine"
    const val JAWWAL_PAY_SMS = "jawwalpay.sms.virtual"

    val quickManualOutgoingSources = listOf(PALPAY, BANK_OF_PALESTINE)

    fun badgeText(sourceName: String): String {
        return when (sourceName) {
            PALPAY -> "P"
            JAWWAL_PAY -> "J"
            BANK_OF_PALESTINE -> "B"
            else -> sourceName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        }
    }

    @ColorRes
    fun badgeColorRes(sourceName: String): Int {
        return when (sourceName) {
            PALPAY -> R.color.palpay_purple
            JAWWAL_PAY -> R.color.jawwal_green
            BANK_OF_PALESTINE -> R.color.bop_red
            else -> R.color.text_secondary
        }
    }
}

enum class JawwalPayMode {
    APP,
    SMS
}
