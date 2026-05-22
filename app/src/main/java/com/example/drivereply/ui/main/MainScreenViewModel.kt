package com.example.drivereply.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.data.MessageTemplate
import com.example.drivereply.service.DriveReplyService
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
    val hasNotificationPermission: Boolean = false,
    val isBatteryOptimized: Boolean = false,
    val hasBluetoothConnect: Boolean = false,
    val hasFineLocation: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val preferencesManager = app.preferencesManager
    private val messageTemplateDao = app.database.messageTemplateDao()
    private val replyLogDao = app.database.replyLogDao()

    private val _permissionState = MutableStateFlow(PermissionSnapshot())
    val permissionState: StateFlow<PermissionSnapshot> = _permissionState.asStateFlow()

    val uiState: StateFlow<MainUiState> = combine(
        preferencesManager.isServiceEnabled,
        DriveReplyService.isDriving,
        messageTemplateDao.getActive(),
        replyLogDao.getAll(),
        _permissionState
    ) { enabled, driving, activeTemplate, logs, permissions ->
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        MainUiState(
            isServiceEnabled = enabled,
            isDriving = driving,
            activeTemplate = activeTemplate,
            repliesToday = logs.count { it.timestamp >= todayStart },
            hasActivityRecognition = permissions.activityRecognition,
            hasNotificationListener = permissions.notificationListener,
            hasNotificationPermission = permissions.notification,
            isBatteryOptimized = permissions.batteryOptimized,
            hasBluetoothConnect = permissions.bluetoothConnect,
            hasFineLocation = permissions.fineLocation,
            hasBackgroundLocation = permissions.backgroundLocation,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun refreshPermissions() {
        val context = getApplication<Application>()
        _permissionState.value = PermissionSnapshot(
            activityRecognition = PermissionHelper.hasActivityRecognitionPermission(context),
            notificationListener = PermissionHelper.hasNotificationListenerPermission(context),
            notification = PermissionHelper.hasNotificationPermission(context),
            batteryOptimized = PermissionHelper.isBatteryOptimizationExempt(context),
            bluetoothConnect = PermissionHelper.hasBluetoothConnectPermission(context),
            fineLocation = PermissionHelper.hasFineLocationPermission(context),
            backgroundLocation = PermissionHelper.hasBackgroundLocationPermission(context),
        )
    }

    fun toggleService(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setServiceEnabled(enabled)
            val context = getApplication<Application>()
            if (enabled) {
                DriveReplyService.start(context)
            } else {
                DriveReplyService.stop(context)
            }
        }
    }

    fun openNotificationListenerSettings() {
        PermissionHelper.openNotificationListenerSettings(getApplication())
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
