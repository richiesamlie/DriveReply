package com.example.drivereply.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.data.MessageTemplate
import com.example.drivereply.service.DriveReplyService
import com.example.drivereply.service.WhatsAppNotificationListener
import com.example.drivereply.util.DebugEventLogger
import com.example.drivereply.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class MainUiState(
    val isServiceEnabled: Boolean = false,
    val isDriving: Boolean = false,
    val activeTemplate: MessageTemplate? = null,
    val repliesToday: Int = 0,
    val hasActivityRecognition: Boolean = false,
    val hasNotificationListener: Boolean = false,
    val isNotificationListenerConnected: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isBatteryOptimized: Boolean = false,
    val hasBluetoothConnect: Boolean = false,
    val hasFineLocation: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
)

private data class MainCoreState(
    val isServiceEnabled: Boolean,
    val isDriving: Boolean,
    val isNotificationListenerConnected: Boolean,
    val activeTemplate: MessageTemplate?,
    val repliesToday: Int,
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainScreenVM"
    }

    private val app = application as DriveReplyApplication
    private val preferencesManager = app.preferencesManager
    private val messageTemplateDao = app.database.messageTemplateDao()
    private val replyLogDao = app.database.replyLogDao()

    private val _permissionState = MutableStateFlow(PermissionSnapshot())
    val permissionState: StateFlow<PermissionSnapshot> = _permissionState.asStateFlow()
    private var lastListenerRebindAttemptMs: Long = 0

    private val coreState: StateFlow<MainCoreState> = combine(
        preferencesManager.isServiceEnabled,
        DriveReplyService.isDriving,
        WhatsAppNotificationListener.isListenerConnected,
        messageTemplateDao.getActive(),
        replyLogDao.getAll()
    ) { enabled, driving, listenerConnected, activeTemplate, logs ->
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        MainCoreState(
            isServiceEnabled = enabled,
            isDriving = driving,
            isNotificationListenerConnected = listenerConnected,
            activeTemplate = activeTemplate,
            repliesToday = logs.count { it.timestamp >= todayStart },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MainCoreState(
            isServiceEnabled = false,
            isDriving = false,
            isNotificationListenerConnected = false,
            activeTemplate = null,
            repliesToday = 0
        )
    )

    val uiState: StateFlow<MainUiState> = combine(coreState, _permissionState) { core, permissions ->
        MainUiState(
            isServiceEnabled = core.isServiceEnabled,
            isDriving = core.isDriving,
            activeTemplate = core.activeTemplate,
            repliesToday = core.repliesToday,
            hasActivityRecognition = permissions.activityRecognition,
            hasNotificationListener = permissions.notificationListener,
            isNotificationListenerConnected = core.isNotificationListenerConnected,
            hasNotificationPermission = permissions.notification,
            isBatteryOptimized = permissions.batteryOptimized,
            hasBluetoothConnect = permissions.bluetoothConnect,
            hasFineLocation = permissions.fineLocation,
            hasBackgroundLocation = permissions.backgroundLocation,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun refreshPermissions() {
        val context = getApplication<Application>()
        val snapshot = PermissionSnapshot(
            activityRecognition = PermissionHelper.hasActivityRecognitionPermission(context),
            notificationListener = PermissionHelper.hasNotificationListenerPermission(context),
            notification = PermissionHelper.hasNotificationPermission(context),
            batteryOptimized = PermissionHelper.isBatteryOptimizationExempt(context),
            bluetoothConnect = PermissionHelper.hasBluetoothConnectPermission(context),
            fineLocation = PermissionHelper.hasFineLocationPermission(context),
            backgroundLocation = PermissionHelper.hasBackgroundLocationPermission(context),
        )
        _permissionState.value = snapshot
        val listenerConnected = WhatsAppNotificationListener.isListenerConnected.value
        DebugEventLogger.log(
            TAG,
            "Permission refresh listenerGranted=${snapshot.notificationListener}, " +
                "listenerConnected=$listenerConnected, " +
                "serviceEnabled=${uiState.value.isServiceEnabled}, driving=${uiState.value.isDriving}"
        )

        if (snapshot.notificationListener && !listenerConnected) {
            attemptNotificationListenerRebind(manual = false)
        }
    }

    fun toggleService(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setServiceEnabled(enabled)
            val context = getApplication<Application>()
            if (enabled) {
                DriveReplyService.start(context)
                DebugEventLogger.log(TAG, "Service toggle ON by user")
            } else {
                DriveReplyService.stop(context)
                DebugEventLogger.log(TAG, "Service toggle OFF by user")
            }
        }
    }

    fun toggleDrivingState() {
        if (uiState.value.isServiceEnabled) {
            val nextDriving = !uiState.value.isDriving
            DriveReplyService.setDrivingState(
                nextDriving,
                DriveReplyService.DrivingStateSource.MANUAL_SIMULATION
            )
            DebugEventLogger.log(TAG, "Manual simulation toggled -> $nextDriving")
        } else {
            DebugEventLogger.log(TAG, "Manual simulation tap ignored because service is OFF")
        }
    }

    fun openNotificationListenerSettings() {
        PermissionHelper.openNotificationListenerSettings(getApplication())
    }

    fun rebindNotificationListener() {
        attemptNotificationListenerRebind(manual = true)
    }

    private fun attemptNotificationListenerRebind(manual: Boolean) {
        val now = System.currentTimeMillis()
        if (!manual && (now - lastListenerRebindAttemptMs) < 30_000L) return

        lastListenerRebindAttemptMs = now
        val context = getApplication<Application>()
        PermissionHelper.requestNotificationListenerRebind(context)
        DebugEventLogger.log(TAG, "Requested notification listener rebind (manual=$manual)")
    }

    fun openBatteryOptimizationSettings() {
        PermissionHelper.openBatteryOptimizationSettings(
            getApplication(),
            getApplication<Application>().packageName
        )
    }
}

data class PermissionSnapshot(
    val activityRecognition: Boolean = false,
    val notificationListener: Boolean = false,
    val notification: Boolean = false,
    val batteryOptimized: Boolean = false,
    val bluetoothConnect: Boolean = false,
    val fineLocation: Boolean = false,
    val backgroundLocation: Boolean = false,
)
