package com.example.drivereply.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.service.DriveReplyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val deviceAddress = device.address ?: return

        val app = context.applicationContext as DriveReplyApplication
        val prefs = app.preferencesManager

        CoroutineScope(Dispatchers.IO).launch {
            val savedDevices = prefs.bluetoothDevices.first()
            if (deviceAddress in savedDevices) {
                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        // Start service and set driving state to true
                        DriveReplyService.start(context)
                        DriveReplyService.setDrivingState(true)
                        DriveReplyService.clearRepliedContacts()
                        
                        // Update persistent notification
                        val serviceIntent = Intent(context, DriveReplyService::class.java).apply {
                            this.action = "UPDATE_NOTIFICATION"
                            putExtra("notification_text", "Driving detected via Bluetooth connection: ${device.name ?: deviceAddress}")
                        }
                        context.startService(serviceIntent)
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        // End driving state
                        DriveReplyService.setDrivingState(false)
                        val serviceIntent = Intent(context, DriveReplyService::class.java).apply {
                            this.action = "UPDATE_NOTIFICATION"
                            putExtra("notification_text", "Service active — waiting for driving detection")
                        }
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
