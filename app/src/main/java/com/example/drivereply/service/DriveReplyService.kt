package com.example.drivereply.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.drivereply.MainActivity
import com.example.drivereply.receiver.ActivityTransitionReceiver
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DriveReplyService : Service() {

    companion object {
        private const val CHANNEL_ID = "drive_reply_service"
        private const val NOTIFICATION_ID = 1001
        private const val TRANSITION_PENDING_INTENT_REQUEST_CODE = 100

        private val _isDriving = MutableStateFlow(false)
        val isDriving: StateFlow<Boolean> = _isDriving.asStateFlow()

        private val _repliedContacts = mutableSetOf<String>()
        val repliedContacts: MutableSet<String> get() = _repliedContacts

        fun clearRepliedContacts() {
            _repliedContacts.clear()
        }

        fun setDrivingState(driving: Boolean) {
            _isDriving.value = driving
        }

        fun start(context: Context) {
            val intent = Intent(context, DriveReplyService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DriveReplyService::class.java)
            context.stopService(intent)
            _isDriving.value = false
        }
    }

    private var transitionPendingIntent: PendingIntent? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Service active — waiting for driving detection"))
        registerActivityTransitions()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "UPDATE_NOTIFICATION") {
            val text = intent.getStringExtra("notification_text")
            if (text != null) {
                updateNotification(text)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterActivityTransitions()
        _isDriving.value = false
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DriveReply Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for DriveReply driving detection service"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DriveReply")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun registerActivityTransitions() {
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        )

        val request = ActivityTransitionRequest(transitions)

        val intent = Intent(this, ActivityTransitionReceiver::class.java)
        transitionPendingIntent = PendingIntent.getBroadcast(
            this,
            TRANSITION_PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        try {
            ActivityRecognition.getClient(this)
                .requestActivityTransitionUpdates(request, transitionPendingIntent!!)
        } catch (_: SecurityException) {
            // Activity recognition permission not granted
        }
    }

    private fun unregisterActivityTransitions() {
        transitionPendingIntent?.let { pendingIntent ->
            try {
                ActivityRecognition.getClient(this)
                    .removeActivityTransitionUpdates(pendingIntent)
            } catch (_: SecurityException) {
                // Permission may have been revoked
            }
        }
    }
}
