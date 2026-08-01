package dev.anonymous.transfers_ledger.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import dev.anonymous.transfers_ledger.service.TransfersLedgerNotificationListener

object IntentUtils {

    fun getNotificationListenerSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                val componentName = ComponentName(context, TransfersLedgerNotificationListener::class.java)
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName.flattenToString())
            }
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }

    /**
     * نستخدم ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS بدلاً من ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     * لضمان قبول التطبيق في جوجل بلاي (سياسة Play Policy).
     */
    fun getBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
