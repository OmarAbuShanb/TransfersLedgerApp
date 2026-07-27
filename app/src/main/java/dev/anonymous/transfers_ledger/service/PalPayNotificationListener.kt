package dev.anonymous.transfers_ledger.service

import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import dev.anonymous.transfers_ledger.core.JawwalPayMode
import dev.anonymous.transfers_ledger.core.PaymentSources
import kotlinx.coroutines.launch
import dev.anonymous.transfers_ledger.PalPayApplication
import dev.anonymous.transfers_ledger.core.TextNormalizer
import dev.anonymous.transfers_ledger.core.notification.ParsedPaymentNotification
import dev.anonymous.transfers_ledger.core.notification.PaymentNotificationParser
import dev.anonymous.transfers_ledger.data.local.db.TransactionEntity
import dev.anonymous.transfers_ledger.data.repository.TransactionRepository

class PalPayNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: TransactionRepository

    override fun onCreate() {
        super.onCreate()
        repository = (application as PalPayApplication).repository
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch { repository.setListenerConnected(true) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        serviceScope.launch { repository.setListenerConnected(false) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, PalPayNotificationListener::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        serviceScope.launch {
            if (!repository.isTrackingEnabled.first()) return@launch

            // Retrieve user's JawwalPay mode preference
            val mode = repository.jawwalPayMode.first()
            // Skip notifications that don't match the selected source mode
            if ((mode == JawwalPayMode.APP && sbn.packageName == PaymentSources.JAWWAL_PAY_SMS) ||
                (mode == JawwalPayMode.SMS && sbn.packageName == PaymentSources.JAWWAL_PAY_PACKAGE)) {
                return@launch
            }

            val title = sbn.notification.extras.getString("android.title").orEmpty()
            val text = sbn.notification.extras.getCharSequence("android.text")?.toString().orEmpty()
            val bigText = sbn.notification.extras.getCharSequence("android.bigText")?.toString().orEmpty()
            val combinedText = listOf(title, text, bigText)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")

            PaymentNotificationParser.parse(sbn.packageName, combinedText)?.let { parsed ->
                save(sbn.key, title, combinedText, parsed)
            }
        }
    }

    private suspend fun save(
        key: String,
        title: String,
        text: String,
        parsed: ParsedPaymentNotification
    ) {
        val normalizedAmount = TextNormalizer.normalizeDigits(parsed.amount)
            .replace(',', '.')
            .toDoubleOrNull()
            ?: return
        val finalSender = parsed.sender.trim().ifBlank { "Unknown" }

        repository.insertTransaction(
            TransactionEntity(
                notificationKey = key,
                transactionReference = parsed.reference,
                senderName = finalSender,
                normalizedSender = TextNormalizer.normalize(finalSender),
                amount = normalizedAmount,
                direction = parsed.direction,
                directionSource = parsed.directionSource,
                paymentType = title,
                timestamp = System.currentTimeMillis(),
                rawText = text,
                rawTitle = title,
                walletSource = parsed.sourceName
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
