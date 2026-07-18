package ps.palpay.tracker.service

import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import ps.palpay.tracker.PalPayApplication
import ps.palpay.tracker.core.PhoneNumberUtils
import ps.palpay.tracker.data.local.db.TransactionEntity
import ps.palpay.tracker.data.repository.TransactionRepository
import java.util.regex.Pattern

class PalPayNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: TransactionRepository

    companion object {
        const val PKG_PALPAY = "com.pcnc.wallet"
        const val PKG_JAWWAL_PAY = "ps.Jawwal.JawwalPayNewPlus"
    }

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

            val packageName = sbn.packageName
            val extras = sbn.notification.extras ?: return@launch
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            when (packageName) {
                PKG_PALPAY -> parsePalPay(sbn.key, title, text)
                PKG_JAWWAL_PAY -> parseJawwalPay(sbn.key, title, text)
            }
        }
    }

    private fun parsePalPay(key: String, title: String, text: String) {
        val arRegex = "تحويل دفع لصديق:\\s*(.*?)\\s*,\\s*بمبلغ\\s*(?:ILS\\s*)?([0-9.]+)"
        val enRegex = "Transfer Pay-to-Friend:\\s*(.*?)\\s*,\\s*amount of\\s*([0-9.]+)"
        
        val arMatcher = Pattern.compile(arRegex, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE).matcher(text)
        val enMatcher = Pattern.compile(enRegex, Pattern.CASE_INSENSITIVE).matcher(text)

        when {
            arMatcher.find() -> save(key, null, arMatcher.group(1), arMatcher.group(2), "PalPay", title, text)
            enMatcher.find() -> save(key, null, enMatcher.group(1), enMatcher.group(2), "PalPay", title, text)
        }
    }

    private fun parseJawwalPay(key: String, title: String, text: String) {
        // نستخدم regex أكثر مرونة لالتقاط الاسم أو الرقم
        // لقد استلمت حركه تحويل اموال بقيمة: ILS 4.00 من [اسم أو رقم] . الرصيد الحالي...
        val regex = "بقيمة:\\s*(?:ILS\\s*)?([0-9.]+)\\s*من\\s*(.*?)\\s*\\.\\s*الرصيد.*المرجع\\s*:\\s*\\((.*?)\\)"
        val matcher = Pattern.compile(regex, Pattern.UNICODE_CASE or Pattern.DOTALL).matcher(text)

        if (matcher.find()) {
            val amount = matcher.group(1)
            val senderRaw = matcher.group(2)
            val reference = matcher.group(3)
            
            // تنظيف المرسل إذا كان رقماً
            val finalSender = if (PhoneNumberUtils.isPhoneNumber(senderRaw ?: "")) {
                PhoneNumberUtils.cleanPhoneNumber(senderRaw ?: "")
            } else {
                senderRaw?.trim() ?: "JawwalPay User"
            }
            
            save(key, reference, finalSender, amount, "JawwalPay", title, text)
        }
    }

    private fun save(key: String, ref: String?, sender: String?, amountStr: String?, source: String, title: String, text: String) {
        val amount = amountStr?.toDoubleOrNull() ?: return
        serviceScope.launch {
            val entity = TransactionEntity(
                notificationKey = key,
                transactionReference = ref,
                senderName = sender?.trim() ?: "Unknown",
                amount = amount,
                paymentType = title,
                timestamp = System.currentTimeMillis(),
                rawText = text,
                rawTitle = title,
                walletSource = source
            )
            repository.insertTransaction(entity)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
