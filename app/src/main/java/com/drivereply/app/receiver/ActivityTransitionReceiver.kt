package com.drivereply.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drivereply.app.service.DriveReplyService
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return

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
    }
}
