package dev.anonymous.transfers_ledger.domain.model

import dev.anonymous.transfers_ledger.core.JawwalPayMode

data class AppStatus(
    val isReady: Boolean = false,
    val listenerConnected: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val trackingEnabled: Boolean = true,
    val selectedPeriod: SummaryPeriod = SummaryPeriod.DAILY,
    val isCustomPeriod: Boolean = false,
    val summaryTitle: String = "",
    val listTitle: String = "",
    val stats: DashboardStats = DashboardStats(),
    val jawwalPayMode: JawwalPayMode = JawwalPayMode.SMS
)
