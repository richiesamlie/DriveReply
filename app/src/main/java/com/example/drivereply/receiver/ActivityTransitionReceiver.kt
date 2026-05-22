package com.example.drivereply.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.example.drivereply.service.DriveReplyService
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityTransitionReceiver : BroadcastReceiver() {

    companion object {
        private const val EXIT_DEBOUNCE_MS = 2 * 60 * 1000L // 2 minutes
        private val handler = Handler(Looper.getMainLooper())
        private var exitRunnable: Runnable? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            if (event.activityType != DetectedActivity.IN_VEHICLE) continue

            when (event.transitionType) {
                ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    // Cancel any pending exit debounce
                    exitRunnable?.let { handler.removeCallbacks(it) }
                    exitRunnable = null

                    DriveReplyService.setDrivingState(true)
                    DriveReplyService.clearRepliedContacts()

                    updateServiceNotification(context, "Driving detected — auto-reply active 🚗")
                }

                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                    // Debounce exit to avoid false positives (e.g., brief stop at traffic light)
                    exitRunnable?.let { handler.removeCallbacks(it) }
                    exitRunnable = Runnable {
                        DriveReplyService.setDrivingState(false)
                        updateServiceNotification(context, "Driving stopped — auto-reply paused")
                        exitRunnable = null
                    }
                    handler.postDelayed(exitRunnable!!, EXIT_DEBOUNCE_MS)
                }
            }
        }
    }

    private fun updateServiceNotification(context: Context, text: String) {
        // The service updates its own notification via the companion state;
        // we trigger it indirectly by sending a broadcast or calling the service directly.
        // Since DriveReplyService is a running foreground service, we can use a direct reference.
        // For simplicity, we post an intent to the service.
        val serviceIntent = Intent(context, DriveReplyService::class.java).apply {
            action = "UPDATE_NOTIFICATION"
            putExtra("notification_text", text)
        }
        context.startService(serviceIntent)
    }
}
