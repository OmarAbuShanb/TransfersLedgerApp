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
import android.provider.Telephony
import android.util.Log
import dev.anonymous.transfers_ledger.core.JawwalPayMode
import dev.anonymous.transfers_ledger.core.PaymentSources
import kotlinx.coroutines.launch
import dev.anonymous.transfers_ledger.app.TransfersLedgerApplication
import dev.anonymous.transfers_ledger.core.TextNormalizer
import dev.anonymous.transfers_ledger.core.notification.ParsedPaymentNotification
import dev.anonymous.transfers_ledger.core.notification.PaymentNotificationParser
import dev.anonymous.transfers_ledger.data.local.db.TransactionEntity
import dev.anonymous.transfers_ledger.data.repository.TransactionRepository

class TransfersLedgerNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: TransactionRepository

    companion object {
        private const val TAG = "TransfersLedgerNotificationListener"
    }

    override fun onCreate() {
        super.onCreate()
        repository = (application as TransfersLedgerApplication).repository
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch { repository.setListenerConnected(true) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        serviceScope.launch { repository.setListenerConnected(false) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, TransfersLedgerNotificationListener::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        serviceScope.launch {
            if (!repository.isTrackingEnabled.first()) return@launch

            // Retrieve user's JawwalPay mode preference
            val mode = repository.jawwalPayMode.first()

            val title = sbn.notification.extras.getString("android.title").orEmpty()
            val text = sbn.notification.extras.getCharSequence("android.text")?.toString().orEmpty()
            val bigText = sbn.notification.extras.getCharSequence("android.bigText")?.toString().orEmpty()

            Log.d(TAG, "=== Notification Received ===")
            Log.d(TAG, "Package: ${sbn.packageName}")
            Log.d(TAG, "Title: [$title]")
            Log.d(TAG, "Text: [${text.take(150)}]")
            if (bigText.isNotBlank()) Log.d(TAG, "BigText: [${bigText.take(150)}]")

            val combinedText = listOf(title, text, bigText)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")

            // Resolve package name (if it's an SMS from JawwalPay, map it to the virtual package)
            var resolvedPackageName = sbn.packageName
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this@TransfersLedgerNotificationListener)
            val isFromDefaultSmsApp = sbn.packageName == defaultSmsPackage

            val cleanTitle = title.replace(Regex("[\\u200e\\u200f\\u202a-\\u202e\\ufeff]"), "").trim()
            val isJawwalPaySmsSender = cleanTitle.equals("Jawwal Pay", ignoreCase = true) ||
                    cleanTitle.equals("JawwalPay", ignoreCase = true) ||
                    cleanTitle.equals("جوال باي", ignoreCase = true)

            val isNotAppPackage = sbn.packageName != PaymentSources.JAWWAL_PAY_PACKAGE

            Log.d(TAG, "DefaultSmsApp: $defaultSmsPackage | IsFromSmsApp: $isFromDefaultSmsApp | CleanTitle: '$cleanTitle' | IsJawwalPaySender: $isJawwalPaySmsSender")

            if (isNotAppPackage && isJawwalPaySmsSender) {
                resolvedPackageName = PaymentSources.JAWWAL_PAY_SMS
                Log.d(TAG, "-> Resolved as JawwalPay SMS (virtual package)")
            }

            Log.d(TAG, "ResolvedPackage: $resolvedPackageName | JawwalPayMode: $mode")

            // Skip notifications that don't match the selected source mode
            if ((mode == JawwalPayMode.APP && resolvedPackageName == PaymentSources.JAWWAL_PAY_SMS) ||
                (mode == JawwalPayMode.SMS && resolvedPackageName == PaymentSources.JAWWAL_PAY_PACKAGE)) {
                Log.d(TAG, "Skipped: filtered by JawwalPay mode ($mode)")
                return@launch
            }

            val parsed = PaymentNotificationParser.parse(resolvedPackageName, combinedText)
            if (parsed != null) {
                Log.d(TAG, "Parsed OK: rule=${parsed.matchedRuleName}, sender=${parsed.sender}, amount=${parsed.amount}, dir=${parsed.direction}, ref=${parsed.reference}")
                val uniqueKey = "${sbn.key}_${sbn.postTime}"
                save(uniqueKey, title, combinedText, parsed)
            } else {
                Log.w(TAG, "Parse FAILED | package=$resolvedPackageName")
                Log.w(TAG, "CombinedText: $combinedText")
                
                if (PaymentSources.trackedPackages.contains(resolvedPackageName)) {
                    val notifText = text.ifBlank { bigText }
                    if (title.isNotBlank() && notifText.isNotBlank()) {
                        try {
                            repository.insertUnprocessedNotification(
                                dev.anonymous.transfers_ledger.data.local.db.UnprocessedNotificationEntity(
                                    packageName = resolvedPackageName,
                                    title = title,
                                    text = notifText,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save unprocessed notification", e)
                        }
                    }
                }
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
        if (normalizedAmount == null) {
            Log.w(TAG, "Save SKIPPED: amount parse failed for '${parsed.amount}'")
            return
        }
        val finalSender = parsed.sender.trim().ifBlank { "Unknown" }

        Log.d(TAG, "Saving: sender=$finalSender, amount=$normalizedAmount, dir=${parsed.direction}, key=${key.take(50)}")

        try {
            val insertedId = repository.insertTransaction(
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
            if (insertedId == -1L) {
                Log.w(TAG, "Save SKIPPED: Duplicate transaction in Room DB (key or ref already exists)")
            } else {
                Log.d(TAG, "Save SUCCESS: Inserted rowId=$insertedId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save FAILED: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
