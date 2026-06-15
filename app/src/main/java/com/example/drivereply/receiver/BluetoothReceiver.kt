package com.example.drivereply.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.service.DriveReplyService
import com.example.drivereply.util.DebugEventLogger
import com.example.drivereply.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BluetoothReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BluetoothReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // getParcelableExtra(String) is deprecated on API 33+; the typed
        // overload preserves the type argument while satisfying the
        // new platform contract.
        val device: BluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return

        val app = context.applicationContext as DriveReplyApplication
        val prefs = app.preferencesManager
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hasBluetoothPermission = PermissionHelper.hasBluetoothConnectPermission(context)
                val deviceAddress = if (hasBluetoothPermission) {
                    try {
                        device.address
                    } catch (_: SecurityException) {
                        null
                    }
                } else {
                    null
                }

                if (deviceAddress.isNullOrBlank()) {
                    DebugEventLogger.log(TAG, "Ignoring BT event without BLUETOOTH_CONNECT permission")
                    return@launch
                }

                val deviceLabel = if (hasBluetoothPermission) {
                    try {
                        device.name?.takeIf { it.isNotBlank() } ?: deviceAddress
                    } catch (_: SecurityException) {
                        deviceAddress
                    }
                } else {
                    deviceAddress
                }

                val savedDevices = prefs.bluetoothDevices.first()
                if (deviceAddress in savedDevices) {
                    when (action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> {
                            // Start service and set driving state to true
                            DriveReplyService.start(context)
                            DriveReplyService.setDrivingState(true)
                            DriveReplyService.clearRepliedContacts()
                            DebugEventLogger.log(TAG, "Matched BT connected $deviceLabel, driving mode enabled")

                            // Update persistent notification via the service's
                            // own state flow — no startService() from a
                            // background context (Android 12+ restriction).
                            DriveReplyService.setNotificationText(
                                "Driving detected via Bluetooth connection: $deviceLabel"
                            )
                        }
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            // End driving state
                            DriveReplyService.setDrivingState(false)
                            DebugEventLogger.log(TAG, "Matched BT disconnected $deviceLabel, driving mode disabled")
                            DriveReplyService.setNotificationText(
                                "Service active — waiting for driving detection"
                            )
                        }
                    }
                } else {
                    DebugEventLogger.log(TAG, "Ignoring BT device not in selected list: $deviceLabel")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
