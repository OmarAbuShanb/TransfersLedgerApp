package dev.anonymous.transfers_ledger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import dev.anonymous.transfers_ledger.MainActivity
import dev.anonymous.transfers_ledger.PalPayApplication
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.core.SystemStatusUtils
import dev.anonymous.transfers_ledger.core.TransactionStatsCalculator
import dev.anonymous.transfers_ledger.data.local.db.TransactionWithCustomer
import dev.anonymous.transfers_ledger.data.repository.TransactionRepository
import dev.anonymous.transfers_ledger.domain.model.DateRange
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection
import java.util.Calendar
import java.util.Locale

class TrackingForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var statusMonitorJob: Job? = null
    private var isForegroundStarted = false

    private val channelId = "wallet_tracker_silent_channel_v4"
    private val notificationId = 101

    // A channel used to push a "date changed" signal from the BroadcastReceiver into
    // the coroutine flow. Channel.CONFLATED ensures that if several signals arrive
    // before the coroutine processes them, only the latest matters.
    private val dateChangedChannel = Channel<Unit>(Channel.CONFLATED)

    /**
     * Receives ACTION_DATE_CHANGED and ACTION_TIME_CHANGED from the system.
     *
     * Why both?
     * - ACTION_DATE_CHANGED fires when the calendar date changes (midnight rollover).
     * - ACTION_TIME_CHANGED fires when the user or NTP adjusts the clock. A large
     *   forward adjustment could jump past midnight without triggering DATE_CHANGED.
     *
     * These intents are not delivered to manifest receivers (since Android 3.1) so we
     * register/unregister dynamically in onCreate/onDestroy.
     */
    private val dateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIME_CHANGED -> dateChangedChannel.trySend(Unit)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        registerReceiver(dateChangeReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = (application as PalPayApplication).repository

        if (intent?.action == ACTION_MARK_OUTGOING) {
            val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
            if (transactionId > 0L) {
                serviceScope.launch {
                    repository.updateDirection(transactionId, TransactionDirection.OUTGOING)
                }
            }
        }

        // عند بدء التشغيل من الإقلاع: نعرض إشعاراً خفيفاً فوراً ثم نتحقق من حالة التتبع
        val isBootStart = intent?.action == ACTION_BOOT_START
        if (isBootStart) {
            ensureBootForegroundStarted()
            serviceScope.launch {
                val trackingEnabled = repository.isTrackingEnabled.first()
                if (!trackingEnabled) {
                    stopForegroundCompat()
                    stopSelf()
                    return@launch
                }
                // التتبع مفعّل — نحدّث الإشعار للعرض الطبيعي ونبدأ المراقبة
                ensureForegroundStarted(repository)
                startStatusMonitorIfNeeded(repository)
            }
            return START_STICKY
        }

        ensureForegroundStarted(repository)

        startStatusMonitorIfNeeded(repository)

        return START_STICKY
    }

    /**
     * عند الإقلاع: نعرض إشعاراً خفيفاً فوراً ("جاري إعادة تشغيل الخدمة") حتى يكتمل
     * شرط startForeground خلال 5 ثوانٍ — بدون أي عمليات ثقيلة كقراءة قاعدة البيانات.
     */
    private fun ensureBootForegroundStarted() {
        if (isForegroundStarted) return
        startForeground(
            notificationId,
            createNotification(
                getString(R.string.service_boot_starting_title),
                getString(R.string.service_monitoring),
                null
            )
        )
        isForegroundStarted = true
    }

    private fun startStatusMonitorIfNeeded(repository: TransactionRepository) {
        if (statusMonitorJob?.isActive != true) {
            statusMonitorJob = serviceScope.launch {
                combine(
                    repository.isTrackingEnabled,
                    todayRangeFlow().flatMapLatest { repository.getTransactionsForStats(it) },
                    repository.getLatestConvertibleTransaction(),
                    notificationListenerEnabledFlow()
                ) { trackingEnabled, transactions, convertible, listenerEnabled ->
                    ServiceNotificationState(
                        trackingEnabled = trackingEnabled,
                        listenerEnabled = listenerEnabled,
                        stats = TransactionStatsCalculator.calculate(transactions),
                        convertibleTransaction = convertible
                    )
                }
                    .distinctUntilChanged()
                    .collect { state -> updateNotification(state) }
            }
        }
    }

    private fun notificationListenerEnabledFlow() = flow {
        while (true) {
            emit(SystemStatusUtils.isNotificationListenerEnabled(this@TrackingForegroundService))
            delay(NOTIFICATION_PERMISSION_CHECK_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    /**
     * Emits a [DateRange] representing today starting at midnight.
     *
     * Two independent mechanisms drive re-emission so neither alone is a single point
     * of failure:
     *
     * 1. **Timer-based**: Calculates the exact milliseconds until the next midnight and
     *    delays until then. This handles the normal case accurately.
     *
     * 2. **BroadcastReceiver-based**: [dateChangedChannel] receives a signal whenever
     *    the system fires ACTION_DATE_CHANGED or ACTION_TIME_CHANGED. This covers:
     *    - Doze mode waking up after midnight (the delay may fire late or not at all).
     *    - Manual clock adjustments that jump past midnight.
     *    - DST transitions.
     *
     * `distinctUntilChanged()` ensures that if both mechanisms fire around the same
     * time, only one downstream re-subscription happens.
     */
    private fun todayRangeFlow() = merge(
        timerBasedDayFlow(),
        dateChangedChannel.receiveAsFlow()
    )
        .map { DateRange(startOfDay(), null) }
        .distinctUntilChanged()

    // Ticker flow that emits once immediately, then once per day at midnight.
    private fun timerBasedDayFlow() = flow<Unit> {
        emit(Unit)
        while (true) {
            delay(millisUntilNextDay())
            emit(Unit)
        }
    }


    private fun ensureForegroundStarted(repository: TransactionRepository) {
        if (isForegroundStarted) return

        startForeground(
            notificationId,
            createNotification(getString(R.string.service_running_title), currentDailySummary(repository), null)
        )
        isForegroundStarted = true
    }

    private fun currentDailySummary(repository: TransactionRepository): String {
        val stats = runCatching {
            runBlocking(Dispatchers.IO) {
                TransactionStatsCalculator.calculate(
                    repository.getTransactionsForStatsOnce(DateRange(startOfDay(), null))
                )
            }
        }.getOrNull()

        return if (stats != null) {
            dailySummary(
                palpay = stats.palpay.incoming,
                jawwalPay = stats.jawwalPay.incoming,
                bankOfPalestine = stats.bankOfPalestine.incoming,
                net = stats.total.net
            )
        } else {
            dailySummary(0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun dailySummary(
        palpay: Double,
        jawwalPay: Double,
        bankOfPalestine: Double,
        net: Double
    ): String {
        return String.format(
            Locale.getDefault(),
            getString(R.string.notif_summary_template),
            palpay,
            jawwalPay,
            bankOfPalestine,
            net
        )
    }

    private fun updateNotification(state: ServiceNotificationState) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notification = when {
            !state.trackingEnabled -> {
                notificationManager.cancel(notificationId)
                stopForegroundCompat()
                stopSelf()
                return
            }

            !state.listenerEnabled -> createNotification(
                getString(R.string.service_blocked_title),
                getString(R.string.service_notification_access_blocked),
                null
            )

            else -> {
                val content = dailySummary(
                    palpay = state.stats.palpay.incoming,
                    jawwalPay = state.stats.jawwalPay.incoming,
                    bankOfPalestine = state.stats.bankOfPalestine.incoming,
                    net = state.stats.total.net
                )
                createNotification(getString(R.string.service_running_title), content, state.convertibleTransaction)
            }
        }

        notificationManager.notify(notificationId, notification)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        isForegroundStarted = false
    }

    private fun createNotification(
        title: String,
        content: String,
        convertibleTransaction: TransactionWithCustomer?
    ): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSound(null)

        val latestLine = convertibleTransaction?.let {
            getString(
                R.string.service_latest_transfer_template,
                it.displayName,
                it.transaction.amount,
                it.transaction.walletSource
            )
        }

        if (latestLine != null && convertibleTransaction != null) {
            builder
                .setStyle(NotificationCompat.BigTextStyle().bigText("$content\n$latestLine"))
                .addAction(
                    0,
                    getString(R.string.mark_as_outgoing_action),
                    markOutgoingIntent(convertibleTransaction.transaction.id)
                )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(content))
        }

        return builder.build()
    }

    private fun markOutgoingIntent(transactionId: Long): PendingIntent {
        val intent = Intent(this, TrackingForegroundService::class.java).apply {
            action = ACTION_MARK_OUTGOING
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
        }
        return PendingIntent.getService(
            this,
            transactionId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.deleteNotificationChannel("tracking_channel")
            notificationManager.deleteNotificationChannel("tracking_channel_silent")
            notificationManager.deleteNotificationChannel("tracking_channel_silent_v2")
            notificationManager.deleteNotificationChannel("wallet_tracker_silent_channel_v3")

            val channel = NotificationChannel(
                channelId,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.service_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dateChangeReceiver)
        isForegroundStarted = false
        serviceScope.cancel()
    }

    private fun startOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Returns the number of milliseconds until the next midnight.
     *
     * A minimum of 60 seconds is enforced so that if this is called very close to
     * midnight (e.g. at 23:59:59.999) the delay is long enough to cross into the new
     * day rather than firing again in the same second.
     */
    private fun millisUntilNextDay(): Long {
        val now = System.currentTimeMillis()
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return (nextMidnight - now).coerceAtLeast(60_000L)
    }

    private data class ServiceNotificationState(
        val trackingEnabled: Boolean,
        val listenerEnabled: Boolean,
        val stats: dev.anonymous.transfers_ledger.domain.model.DashboardStats,
        val convertibleTransaction: TransactionWithCustomer?
    )

    companion object {
        const val ACTION_MARK_OUTGOING = "ps.palpay.tracker.action.MARK_OUTGOING"
        const val ACTION_BOOT_START = "ps.palpay.tracker.action.BOOT_START"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        private const val NOTIFICATION_PERMISSION_CHECK_INTERVAL_MS = 5000L
    }
}
