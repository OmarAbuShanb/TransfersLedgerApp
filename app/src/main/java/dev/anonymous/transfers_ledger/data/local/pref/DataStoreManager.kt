package dev.anonymous.transfers_ledger.data.local.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import dev.anonymous.transfers_ledger.domain.model.SummaryPeriod
import dev.anonymous.transfers_ledger.core.JawwalPayMode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    companion object {
        val LISTENER_CONNECTED = booleanPreferencesKey("listener_connected")
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
        val FIRST_OPEN_AT = longPreferencesKey("first_open_at")
        val SUMMARY_PERIOD = stringPreferencesKey("summary_period")
        val EXPORT_NOTICE_SHOWN = booleanPreferencesKey("export_notice_shown")
        val JAWWAL_PAY_MODE = stringPreferencesKey("jawwal_pay_mode")
    }

    val isTrackingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TRACKING_ENABLED] ?: true
    }

    val firstOpenAt: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[FIRST_OPEN_AT] ?: 0L
    }

    val summaryPeriod: Flow<SummaryPeriod> = context.dataStore.data.map { preferences ->
        preferences[SUMMARY_PERIOD]?.let { runCatching { SummaryPeriod.valueOf(it) }.getOrNull() }
            ?: SummaryPeriod.DAILY
    }

    val jawwalPayMode: Flow<JawwalPayMode> = context.dataStore.data.map { preferences ->
        preferences[JAWWAL_PAY_MODE]?.let { runCatching { JawwalPayMode.valueOf(it) }.getOrNull() }
            ?: JawwalPayMode.SMS
    }

    suspend fun setListenerConnected(connected: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LISTENER_CONNECTED] = connected
        }
    }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TRACKING_ENABLED] = enabled
        }
    }

    suspend fun ensureFirstOpenAt(): Long {
        val existing = firstOpenAt.first()
        if (existing > 0L) return existing

        val now = System.currentTimeMillis()
        context.dataStore.edit { preferences ->
            if (preferences[FIRST_OPEN_AT] == null) {
                preferences[FIRST_OPEN_AT] = now
            }
        }
        return firstOpenAt.first().takeIf { it > 0L } ?: now
    }

    suspend fun setSummaryPeriod(period: SummaryPeriod) {
        context.dataStore.edit { preferences ->
            preferences[SUMMARY_PERIOD] = period.name
        }
    }

    suspend fun isExportNoticeShown(): Boolean {
        return context.dataStore.data.first()[EXPORT_NOTICE_SHOWN] ?: false
    }

    suspend fun setExportNoticeShown(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[EXPORT_NOTICE_SHOWN] = shown
        }
    }

    suspend fun setJawwalPayMode(mode: JawwalPayMode) {
        context.dataStore.edit { preferences ->
            preferences[JAWWAL_PAY_MODE] = mode.name
        }
    }
}
