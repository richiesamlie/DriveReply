package com.example.drivereply.ui.settings

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.service.SupportedMessagingPackages
import com.example.drivereply.service.WhatsAppNotificationListener
import com.example.drivereply.service.DriveReplyService
import com.example.drivereply.util.DebugEventLogger
import com.example.drivereply.util.GitHubReleaseChecker
import com.example.drivereply.util.PermissionHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UpdateCheckUiState(
    val installedTag: String = "",
    val isChecking: Boolean = false,
    val latestTag: String? = null,
    val hasUpdate: Boolean = false,
    val downloadUrl: String? = null,
    val releaseUrl: String? = null,
    val message: String? = null
)

data class SettingsSupportedAppDetection(
    val label: String,
    val packageName: String,
    val isInstalled: Boolean
)

data class SetupState(
    val hasActivityRecognition: Boolean = false,
    val hasNotificationListener: Boolean = false,
    val isNotificationListenerConnected: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isBatteryOptimizationExempt: Boolean = false,
    val hasBluetoothConnect: Boolean = false,
    val hasFineLocation: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val supportedApps: List<SettingsSupportedAppDetection> = emptyList()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val preferencesManager = app.preferencesManager
    private val _updateCheckState = MutableStateFlow(
        UpdateCheckUiState(
            installedTag = computeInstalledGitHubStyleTag()
        )
    )
    private val _setupState = MutableStateFlow(SetupState())
    val updateCheckState: StateFlow<UpdateCheckUiState> = _updateCheckState.asStateFlow()
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

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

    fun refreshSetupState() {
        val supportedApps = detectSupportedApps()
        _setupState.value = SetupState(
            hasActivityRecognition = PermissionHelper.hasActivityRecognitionPermission(app),
            hasNotificationListener = PermissionHelper.hasNotificationListenerPermission(app),
            isNotificationListenerConnected = WhatsAppNotificationListener.isListenerConnected.value,
            hasNotificationPermission = PermissionHelper.hasNotificationPermission(app),
            isBatteryOptimizationExempt = PermissionHelper.isBatteryOptimizationExempt(app),
            hasBluetoothConnect = PermissionHelper.hasBluetoothConnectPermission(app),
            hasFineLocation = PermissionHelper.hasFineLocationPermission(app),
            hasBackgroundLocation = PermissionHelper.hasBackgroundLocationPermission(app),
            supportedApps = supportedApps
        )
    }

    fun setDebugLogsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDebugLogsEnabled(enabled)
        }
    }

    fun checkForUpdates() {
        val installedTag = _updateCheckState.value.installedTag
        _updateCheckState.value = _updateCheckState.value.copy(
            isChecking = true,
            message = null
        )

        viewModelScope.launch {
            val result = GitHubReleaseChecker.checkForUpdates(installedTag)
            result.fold(
                onSuccess = { update ->
                    _updateCheckState.value = _updateCheckState.value.copy(
                        isChecking = false,
                        latestTag = update.latestTag,
                        hasUpdate = update.hasUpdate,
                        downloadUrl = update.apkDownloadUrl,
                        releaseUrl = update.releaseUrl,
                        message = if (update.hasUpdate) {
                            "New version available."
                        } else {
                            "You are on the latest version."
                        }
                    )
                },
                onFailure = { error ->
                    _updateCheckState.value = _updateCheckState.value.copy(
                        isChecking = false,
                        message = "Update check failed: ${error.message ?: "Unknown error"}"
                    )
                }
            )
        }
    }

    suspend fun buildDiagnosticsSnapshot(): String {
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val installedApps = SupportedMessagingPackages.supportedApps.joinToString(separator = ",") { supported ->
            "${supported.label}:${isPackageInstalled(supported.packageName)}"
        }

        return buildString {
            appendLine("[DriveReply Diagnostics]")
            appendLine("generatedAt=$generatedAt")
            appendLine("installedTag=${_updateCheckState.value.installedTag}")
            appendLine("serviceEnabled=${preferencesManager.isServiceEnabled.first()}")
            appendLine("driving=${DriveReplyService.isDriving.value}")
            appendLine("listenerPermission=${PermissionHelper.hasNotificationListenerPermission(app)}")
            appendLine("listenerConnected=${WhatsAppNotificationListener.isListenerConnected.value}")
            appendLine("activityRecognitionPermission=${PermissionHelper.hasActivityRecognitionPermission(app)}")
            appendLine("notificationPermission=${PermissionHelper.hasNotificationPermission(app)}")
            appendLine("batteryOptimizationExempt=${PermissionHelper.isBatteryOptimizationExempt(app)}")
            appendLine("bluetoothConnectPermission=${PermissionHelper.hasBluetoothConnectPermission(app)}")
            appendLine("fineLocationPermission=${PermissionHelper.hasFineLocationPermission(app)}")
            appendLine("backgroundLocationPermission=${PermissionHelper.hasBackgroundLocationPermission(app)}")
            appendLine("replyInGroupChats=${preferencesManager.replyInGroupChats.first()}")
            appendLine("speedThresholdKmh=${preferencesManager.speedActivationThreshold.first()}")
            appendLine("debugLogsEnabled=${preferencesManager.debugLogsEnabled.first()}")
            appendLine("supportedApps=$installedApps")
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                app.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun computeInstalledGitHubStyleTag(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.packageManager.getPackageInfo(
                    app.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                app.packageManager.getPackageInfo(app.packageName, 0)
            }
            val versionName = (packageInfo.versionName ?: "0.0").removePrefix("v").removePrefix("V")
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            val segments = versionName.split('.').filter { it.isNotBlank() }
            if (segments.size >= 3) {
                "v$versionName"
            } else {
                "v$versionName.$versionCode"
            }
        } catch (_: Exception) {
            "v0.0.0"
        }
    }

    private fun detectSupportedApps(): List<SettingsSupportedAppDetection> {
        val pm = app.packageManager
        return SupportedMessagingPackages.supportedApps.map { appDef ->
            SettingsSupportedAppDetection(
                label = appDef.label,
                packageName = appDef.packageName,
                isInstalled = isPackageInstalled(pm, appDef.packageName)
            )
        }.sortedWith(
            compareByDescending<SettingsSupportedAppDetection> { it.isInstalled }
                .thenBy { it.label }
        )
    }

    private fun isPackageInstalled(packageManager: PackageManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
