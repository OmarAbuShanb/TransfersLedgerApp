package ps.palpay.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ps.palpay.tracker.MainActivity
import ps.palpay.tracker.PalPayApplication
import ps.palpay.tracker.R
import java.util.Locale

class TrackingForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // استخدام ID جديد تماماً لضمان إعادة ضبط إعدادات القناة في النظام
    private val channelId = "wallet_tracker_silent_channel_v3"
    private val notificationId = 101

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = (application as PalPayApplication).repository

        startForeground(
            notificationId, 
            createNotification(
                getString(R.string.service_running_title), 
                getString(R.string.service_monitoring)
            )
        )

        serviceScope.launch {
            repository.getTodayTransactions().collect { transactions ->
                val palpayTotal = transactions.filter { it.walletSource == "PalPay" }.sumOf { it.amount }
                val jawwalTotal = transactions.filter { it.walletSource == "JawwalPay" }.sumOf { it.amount }
                val total = palpayTotal + jawwalTotal
                
                updateNotification(palpayTotal, jawwalTotal, total)
            }
        }

        return START_STICKY
    }

    private fun updateNotification(pTotal: Double, jTotal: Double, total: Double) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val content = String.format(Locale.getDefault(), getString(R.string.notif_summary_template), pTotal, jTotal, total)
        val notification = createNotification(getString(R.string.service_running_title), content)
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotification(title: String, content: String): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // تحديث الإشعار بدون صوت أو اهتزاز
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSound(null) // التأكد برمجياً من عدم وجود صوت
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            // حذف القنوات القديمة التي قد تسبب صوتاً
            notificationManager.deleteNotificationChannel("tracking_channel")
            notificationManager.deleteNotificationChannel("tracking_channel_silent")
            notificationManager.deleteNotificationChannel("tracking_channel_silent_v2")
            
            val channel = NotificationChannel(
                channelId,
                "خدمة التتبع الصامتة",
                NotificationManager.IMPORTANCE_MIN // أدنى مستوى أهمية: لا صوت، لا اهتزاز، لا منبثق
            ).apply {
                description = "قناة هادئة تماماً لخدمة تتبع الحوالات"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null) // حذف الصوت من القناة نهائياً
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
