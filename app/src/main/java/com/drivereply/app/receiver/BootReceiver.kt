package com.drivereply.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drivereply.app.DriveReplyApplication
import com.drivereply.app.service.DriveReplyService
import com.drivereply.app.util.DebugEventLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val app = context.applicationContext as DriveReplyApplication
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isEnabled = app.preferencesManager.isServiceEnabled.first()
                if (isEnabled) {
                    DriveReplyService.start(context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugEventLogger.w("BootReceiver", "Failed to process ${intent.action}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
