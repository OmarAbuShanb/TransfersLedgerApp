package dev.anonymous.transfers_ledger.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import dev.anonymous.transfers_ledger.service.PalPayNotificationListener
import dev.anonymous.transfers_ledger.service.TrackingForegroundService

/**
 * يستقبل إشعار إقلاع النظام (BOOT_COMPLETED) أو تحديث التطبيق (MY_PACKAGE_REPLACED)
 * ويُشغّل الخدمة الأمامية فوراً بدون أي عمليات ثقيلة.
 *
 * لماذا لا نقرأ DataStore هنا؟
 * - عمر الـ BroadcastReceiver قصير جداً (10 ثوانٍ كحد أقصى).
 * - بعد الإقلاع مباشرة، النظام (خاصة MIUI) قد يقتل العملية قبل اكتمال القراءة.
 * - الخدمة نفسها تقرأ isTrackingEnabled وتوقف نفسها إذا كان التتبع معطلاً.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            // 1. طلب إعادة ربط خدمة مراقبة الإشعارات
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    NotificationListenerService.requestRebind(
                        ComponentName(context, PalPayNotificationListener::class.java)
                    )
                } catch (_: Exception) { }
            }

            // 2. تشغيل الخدمة الأمامية فوراً — الخدمة نفسها ستتحقق من حالة التتبع
            val serviceIntent = Intent(context, TrackingForegroundService::class.java).apply {
                action = TrackingForegroundService.ACTION_BOOT_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}

