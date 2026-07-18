package ps.palpay.tracker.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ps.palpay.tracker.PalPayApplication
import ps.palpay.tracker.service.PalPayNotificationListener
import ps.palpay.tracker.service.TrackingForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
            // 1. طلب إعادة ربط خدمة مراقبة الإشعارات
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationListenerService.requestRebind(
                    ComponentName(context, PalPayNotificationListener::class.java)
                )
            }

            // 2. إعادة تشغيل الخدمة الأمامية إذا كان التتبع مفعلاً
            val repository = (context.applicationContext as PalPayApplication).repository
            CoroutineScope(Dispatchers.IO).launch {
                if (repository.isTrackingEnabled.first()) {
                    val serviceIntent = Intent(context, TrackingForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
