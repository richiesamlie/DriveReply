package com.drivereply.app.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.drivereply.app.DriveReplyApplication
import com.drivereply.app.service.DriveReplyService
import com.drivereply.app.util.DebugEventLogger
import com.drivereply.app.util.PermissionHelper
import kotlinx.coroutines.CancellationException
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
                            if (!prefs.isServiceEnabled.first()) {
                                DebugEventLogger.log(TAG, "Ignoring BT connect because monitoring service is disabled")
                                return@launch
                            }
                            val started = DriveReplyService.start(context)
                            if (!started) {
                                DebugEventLogger.w(
                                    TAG,
                                    "Could not start foreground service on BT connect device=${DebugEventLogger.redact(deviceLabel)}"
                                )
                                return@launch
                            }
                            DriveReplyService.onBluetoothDeviceConnected(deviceAddress)
                            DebugEventLogger.log(
                                TAG,
                                "Matched selected BT device connected device=${DebugEventLogger.redact(deviceLabel)}"
                            )

                            DriveReplyService.setNotificationText(
                                "Driving detected via Bluetooth connection: $deviceLabel",
                                logMessage = "Driving detected via Bluetooth connection: ${DebugEventLogger.redact(deviceLabel)}"
                            )
                        }
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            DriveReplyService.onBluetoothDeviceDisconnected(deviceAddress)
                            DebugEventLogger.log(
                                TAG,
                                "Matched selected BT device disconnected device=${DebugEventLogger.redact(deviceLabel)}"
                            )
                            if (!DriveReplyService.isDriving.value) {
                                DriveReplyService.setNotificationText(
                                    "Service active — waiting for driving detection"
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugEventLogger.w(TAG, "Failed to handle Bluetooth broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
