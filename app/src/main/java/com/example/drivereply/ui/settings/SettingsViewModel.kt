package com.example.drivereply.ui.settings

import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.util.DebugEventLogger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val preferencesManager = app.preferencesManager

    val replyInGroupChats: StateFlow<Boolean> = preferencesManager.replyInGroupChats
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val logRetentionDays: StateFlow<Int> = preferencesManager.logRetentionDays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 7
        )

    val bluetoothDevices: StateFlow<Set<String>> = preferencesManager.bluetoothDevices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    val speedActivationThreshold: StateFlow<Int> = preferencesManager.speedActivationThreshold
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val debugLogsEnabled: StateFlow<Boolean> = preferencesManager.debugLogsEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    val debugLogText: StateFlow<String> = DebugEventLogger.entries
        .map { entries ->
            if (entries.isEmpty()) {
                "No debug logs yet. Reproduce the issue, then return here and tap Copy Logs."
            } else {
                entries.joinToString(separator = "\n")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "No debug logs yet. Reproduce the issue, then return here and tap Copy Logs."
        )

    fun setReplyInGroupChats(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setReplyInGroupChats(enabled)
        }
    }

    fun setLogRetentionDays(days: Int) {
        viewModelScope.launch {
            preferencesManager.setLogRetentionDays(days)
        }
    }

    fun setBluetoothDevices(devices: Set<String>) {
        viewModelScope.launch {
            preferencesManager.setBluetoothDevices(devices)
        }
    }

    fun toggleBluetoothDevice(address: String) {
        viewModelScope.launch {
            val current = bluetoothDevices.value.toMutableSet()
            if (current.contains(address)) {
                current.remove(address)
            } else {
                current.add(address)
            }
            preferencesManager.setBluetoothDevices(current)
        }
    }

    fun setSpeedActivationThreshold(threshold: Int) {
        viewModelScope.launch {
            preferencesManager.setSpeedActivationThreshold(threshold)
        }
    }

    fun getPairedBluetoothDevices(): List<Pair<String, String>> {
        val bluetoothManager = app.getSystemService(Application.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.map { (it.name ?: "Unknown Device") to it.address }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun clearDebugLogs() {
        DebugEventLogger.clear()
    }

    fun setDebugLogsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDebugLogsEnabled(enabled)
        }
    }
}
