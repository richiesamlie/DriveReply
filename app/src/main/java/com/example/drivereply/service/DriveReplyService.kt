package com.example.drivereply.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.MainActivity
import com.example.drivereply.receiver.ActivityTransitionReceiver
import com.example.drivereply.util.DebugEventLogger
import com.example.drivereply.util.PermissionHelper
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class DriveReplyService : Service() {

    enum class DrivingStateSource {
        AUTOMATIC,
        MANUAL_SIMULATION
    }

    companion object {
        private const val TAG = "DriveReplyService"
        private const val CHANNEL_ID = "drive_reply_service"
        private const val NOTIFICATION_ID = 1001
        private const val TRANSITION_PENDING_INTENT_REQUEST_CODE = 100
        private const val LISTENER_HEALTH_CHECK_INTERVAL_MS = 15_000L
        private const val LISTENER_REBIND_COOLDOWN_MS = 30_000L

        private val _isDriving = MutableStateFlow(false)
        val isDriving: StateFlow<Boolean> = _isDriving.asStateFlow()
        private val _manualSimulationOverride = MutableStateFlow(false)
        @Volatile
        private var _lastStartRequestedAtMs: Long = 0L
        val lastStartRequestedAtMs: Long get() = _lastStartRequestedAtMs

        private val _repliedContacts = mutableSetOf<String>()
        val repliedContacts: MutableSet<String> get() = _repliedContacts

        fun clearRepliedContacts() {
            _repliedContacts.clear()
        }

        fun setDrivingState(
            driving: Boolean,
            source: DrivingStateSource = DrivingStateSource.AUTOMATIC
        ) {
            if (source == DrivingStateSource.AUTOMATIC && _manualSimulationOverride.value && !driving) {
                DebugEventLogger.log(
                    TAG,
                    "Ignored automatic stop while manual simulation override is active"
                )
                return
            }

            if (source == DrivingStateSource.MANUAL_SIMULATION) {
                _manualSimulationOverride.value = driving
                if (driving) {
                    clearRepliedContacts()
                }
            }

            DebugEventLogger.log(
                TAG,
                "Driving state -> $driving (source=$source, manualOverride=${_manualSimulationOverride.value})"
            )
            _isDriving.value = driving
        }

        fun start(context: Context) {
            _lastStartRequestedAtMs = System.currentTimeMillis()
            val intent = Intent(context, DriveReplyService::class.java)
            context.startForegroundService(intent)
            DebugEventLogger.log(TAG, "Foreground service start requested")
        }

        fun stop(context: Context) {
            val intent = Intent(context, DriveReplyService::class.java)
            context.stopService(intent)
            _isDriving.value = false
            _manualSimulationOverride.value = false
            DebugEventLogger.log(TAG, "Foreground service stop requested")
        }
    }

    private var transitionPendingIntent: PendingIntent? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    private var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private val speedHandler = Handler(Looper.getMainLooper())
    private var speedExitRunnable: Runnable? = null
    private var currentThreshold = 0
    private val listenerHealthHandler = Handler(Looper.getMainLooper())
    private var listenerHealthRunnable: Runnable? = null
    private var lastListenerRebindAttemptMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        DebugEventLogger.log(TAG, "Service created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Service active — waiting for driving detection"))
        registerActivityTransitions()
        startListenerHealthWatchdog()
        
        // Listen to speed activation threshold settings from DataStore
        val app = application as DriveReplyApplication
        app.preferencesManager.speedActivationThreshold
            .onEach { threshold ->
                DebugEventLogger.log(TAG, "Speed activation threshold updated to $threshold km/h")
                if (threshold > 0) {
                    startLocationSpeedTracking(threshold)
                } else {
                    stopLocationSpeedTracking()
                }
            }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "UPDATE_NOTIFICATION") {
            val text = intent.getStringExtra("notification_text")
            if (text != null) {
                updateNotification(text)
                DebugEventLogger.log(TAG, "Notification updated: $text")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopListenerHealthWatchdog()
        unregisterActivityTransitions()
        stopLocationSpeedTracking()
        serviceScope.cancel()
        _isDriving.value = false
        _manualSimulationOverride.value = false
        DebugEventLogger.log(TAG, "Service destroyed")
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

    private fun startLocationSpeedTracking(threshold: Int) {
        currentThreshold = threshold
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        }
        
        stopLocationSpeedTracking()
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L) // 10 seconds
            .setMinUpdateIntervalMillis(5000L)
            .build()
            
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                if (location.hasSpeed()) {
                    val speedKmH = location.speed * 3.6
                    if (speedKmH >= currentThreshold) {
                        speedExitRunnable?.let { speedHandler.removeCallbacks(it) }
                        speedExitRunnable = null
                        
                        if (!_isDriving.value) {
                            setDrivingState(true)
                            clearRepliedContacts()
                        }
                        updateNotification(String.format("Driving detected via Speed: %.1f km/h 🚗", speedKmH))
                    } else {
                        if (_isDriving.value && speedExitRunnable == null) {
                            DebugEventLogger.log(
                                TAG,
                                "Speed below threshold (${String.format("%.1f", speedKmH)} km/h), scheduling stop debounce"
                            )
                            speedExitRunnable = Runnable {
                                setDrivingState(false)
                                updateNotification("Service active — waiting for driving detection")
                                speedExitRunnable = null
                            }
                            speedHandler.postDelayed(speedExitRunnable!!, 2 * 60 * 1000L) // 2 minutes debounce
                        }
                    }
                }
            }
        }
        
        try {
            fusedLocationClient?.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Location permission not granted
        }
    }

    private fun stopLocationSpeedTracking() {
        locationCallback?.let { callback ->
            fusedLocationClient?.removeLocationUpdates(callback)
        }
        locationCallback = null
        speedExitRunnable?.let { speedHandler.removeCallbacks(it) }
        speedExitRunnable = null
    }

    private fun startListenerHealthWatchdog() {
        stopListenerHealthWatchdog()
        listenerHealthRunnable = object : Runnable {
            override fun run() {
                val permissionGranted = PermissionHelper.hasNotificationListenerPermission(this@DriveReplyService)
                val listenerConnected = WhatsAppNotificationListener.isListenerConnected.value

                if (permissionGranted && !listenerConnected) {
                    val now = System.currentTimeMillis()
                    if ((now - lastListenerRebindAttemptMs) >= LISTENER_REBIND_COOLDOWN_MS) {
                        lastListenerRebindAttemptMs = now
                        PermissionHelper.requestNotificationListenerRebind(this@DriveReplyService)
                        DebugEventLogger.log(
                            TAG,
                            "Listener health check requested rebind (permissionGranted=true, connected=false)"
                        )
                    }
                }

                listenerHealthHandler.postDelayed(this, LISTENER_HEALTH_CHECK_INTERVAL_MS)
            }
        }
        listenerHealthHandler.post(listenerHealthRunnable!!)
    }

    private fun stopListenerHealthWatchdog() {
        listenerHealthRunnable?.let { listenerHealthHandler.removeCallbacks(it) }
        listenerHealthRunnable = null
    }
}
