package ps.palpay.tracker.data.local.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    companion object {
        val LISTENER_CONNECTED = booleanPreferencesKey("listener_connected")
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
    }

    val isListenerConnected: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LISTENER_CONNECTED] ?: false
    }

    val isTrackingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TRACKING_ENABLED] ?: true
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
}
