package dev.anonymous.transfers_ledger.core

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import dev.anonymous.transfers_ledger.service.PalPayNotificationListener

object SystemStatusUtils {
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val appContext = context.applicationContext
        val expectedComponent = ComponentName(appContext, PalPayNotificationListener::class.java)
        val enabledListeners = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        return enabledListeners
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { component ->
                component.packageName == expectedComponent.packageName &&
                    component.className == expectedComponent.className
            }
    }
}
