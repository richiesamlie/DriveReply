package com.drivereply.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drivereply.app.DriveReplyApplication
import com.drivereply.app.service.DriveReplyService
import com.drivereply.app.util.DebugEventLogger
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        val app = context.applicationContext as DriveReplyApplication
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isServiceEnabled = app.preferencesManager.isServiceEnabled.first()
                val isActivityEnabled = app.preferencesManager.activityRecognitionEnabled.first()
                DriveReplyService.isActivityRecognitionEnabled = isActivityEnabled
                if (!isServiceEnabled || !isActivityEnabled) {
                    DebugEventLogger.log(
                        "ActivityTransitionRx",
                        "Ignoring activity transition (serviceEnabled=$isServiceEnabled activityEnabled=$isActivityEnabled)"
                    )
                    return@launch
                }

                for (event in result.transitionEvents) {
                    if (event.activityType != DetectedActivity.IN_VEHICLE) continue

                    when (event.transitionType) {
                        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                            DriveReplyService.onActivityTransitionEnter()
                        }

                        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                            DriveReplyService.onActivityTransitionExit()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugEventLogger.w("ActivityTransitionRx", "Failed to process activity transition", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
