package ps.palpay.tracker.domain.model

data class AppStatus(
    val isReady: Boolean = false,
    val listenerConnected: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val trackingEnabled: Boolean = true,
    
    val palpayCount: Int = 0,
    val palpayTotal: Double = 0.0,
    
    val jawwalPayCount: Int = 0,
    val jawwalPayTotal: Double = 0.0
) {
    val totalCount: Int get() = palpayCount + jawwalPayCount
    val totalAmount: Double get() = palpayTotal + jawwalPayTotal
}
