package com.example.drivereply.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "drivereply_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_SERVICE_ENABLED = booleanPreferencesKey("is_service_enabled")
        private val KEY_REPLY_IN_GROUP_CHATS = booleanPreferencesKey("reply_in_group_chats")
        private val KEY_LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")
        private val KEY_BLUETOOTH_DEVICES = stringSetPreferencesKey("bluetooth_devices")
        private val KEY_SPEED_ACTIVATION_THRESHOLD = intPreferencesKey("speed_activation_threshold")
        private val KEY_DEBUG_LOGS_ENABLED = booleanPreferencesKey("debug_logs_enabled")
    }

    val isServiceEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVICE_ENABLED] ?: false
    }

    val replyInGroupChats: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_REPLY_IN_GROUP_CHATS] ?: false
    }

    val logRetentionDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LOG_RETENTION_DAYS] ?: 7
    }

    val bluetoothDevices: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLUETOOTH_DEVICES] ?: emptySet()
    }

    val speedActivationThreshold: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_SPEED_ACTIVATION_THRESHOLD] ?: 0 // 0 means disabled
    }

    val debugLogsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_LOGS_ENABLED] ?: true
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setReplyInGroupChats(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REPLY_IN_GROUP_CHATS] = enabled
        }
    }

    suspend fun setLogRetentionDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOG_RETENTION_DAYS] = days
        }
    }

    suspend fun setBluetoothDevices(devices: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLUETOOTH_DEVICES] = devices
        }
    }

    suspend fun setSpeedActivationThreshold(threshold: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SPEED_ACTIVATION_THRESHOLD] = threshold
        }
    }

    suspend fun setDebugLogsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEBUG_LOGS_ENABLED] = enabled
        }
    }
}
