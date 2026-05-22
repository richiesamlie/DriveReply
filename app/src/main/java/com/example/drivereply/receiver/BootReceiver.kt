package com.example.drivereply.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.service.DriveReplyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as DriveReplyApplication
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isEnabled = app.preferencesManager.isServiceEnabled.first()
                if (isEnabled) {
                    DriveReplyService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
