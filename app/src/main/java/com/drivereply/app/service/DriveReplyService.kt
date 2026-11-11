package com.drivereply.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.drivereply.app.DriveReplyApplication
import com.drivereply.app.MainActivity
import com.drivereply.app.receiver.ActivityTransitionReceiver
import com.drivereply.app.util.DebugEventLogger
import com.drivereply.app.util.PermissionHelper
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class DriveReplyService : Service() {

    enum class DrivingStateSource {
        AUTOMATIC,
        ACTIVITY_RECOGNITION,
        BLUETOOTH,
        GPS_SPEED,
        MANUAL_SIMULATION
    }

    companion object {
        private const val TAG = "DriveReplyService"
        private const val CHANNEL_ID = "drive_reply_service"
        private const val NOTIFICATION_ID = 1001
        private const val TRANSITION_PENDING_INTENT_REQUEST_CODE = 100
        private const val LISTENER_HEALTH_CHECK_INTERVAL_MS = 15_000L
        private const val LISTENER_REBIND_COOLDOWN_MS = 30_000L
        internal const val EXIT_DEBOUNCE_MS = 2 * 60 * 1000L // 2 minutes
        internal const val CONVERSATION_COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes

        private val _isDriving = MutableStateFlow(false)
        val isDriving: StateFlow<Boolean> = _isDriving.asStateFlow()
        private val _manualSimulationOverride = MutableStateFlow(false)
        private val _activeSources: MutableSet<DrivingStateSource> = ConcurrentHashMap.newKeySet()
        private val activityExitHandler = Handler(Looper.getMainLooper())
        private var activityExitRunnable: Runnable? = null

        @Volatile
        private var _lastStartRequestedAtMs: Long = 0L
        val lastStartRequestedAtMs: Long get() = _lastStartRequestedAtMs

        /**
         * Notification body shown on the persistent foreground-service
         * notification. Receivers and the service itself can update this
         * through [setNotificationText]; the service collects the flow and
         * republishes to the OS without anyone calling startService().
         */
        private val _notificationText = MutableStateFlow(
            "Service active — waiting for driving detection"
        )
        val notificationText: StateFlow<String> = _notificationText.asStateFlow()

        fun setNotificationText(text: String) {
            if (_notificationText.value != text) {
                _notificationText.value = text
                DebugEventLogger.log(TAG, "Notification text set: $text")
            }
        }

        private val _repliedContacts: MutableSet<String> = ConcurrentHashMap.newKeySet()
        private val _lastReplyTimestampByConversation = ConcurrentHashMap<String, Long>()

        fun hasRepliedToConversation(
            dedupeKey: String,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            if (dedupeKey in _repliedContacts) return true
            val lastReplyAt = _lastReplyTimestampByConversation[dedupeKey] ?: return false
            if ((nowMs - lastReplyAt) < CONVERSATION_COOLDOWN_MS) {
                return true
            }
            _lastReplyTimestampByConversation.remove(dedupeKey, lastReplyAt)
            return false
        }

        fun markConversationReplied(
            dedupeKey: String,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            if (hasRepliedToConversation(dedupeKey, nowMs)) return false
            val added = _repliedContacts.add(dedupeKey)
            if (added) {
                _lastReplyTimestampByConversation[dedupeKey] = nowMs
            }
            return added
        }

        fun unmarkConversationReplied(dedupeKey: String) {
            _repliedContacts.remove(dedupeKey)
            _lastReplyTimestampByConversation.remove(dedupeKey)
        }

        fun clearRepliedContacts() {
            _repliedContacts.clear()
            val now = System.currentTimeMillis()
            _lastReplyTimestampByConversation.entries.removeIf { (_, timestamp) ->
                (now - timestamp) >= CONVERSATION_COOLDOWN_MS
            }
        }

        @Synchronized
        fun setDrivingState(
            driving: Boolean,
            source: DrivingStateSource = DrivingStateSource.AUTOMATIC
        ) {
            if (source != DrivingStateSource.MANUAL_SIMULATION && _manualSimulationOverride.value && !driving) {
                _activeSources.remove(source)
                DebugEventLogger.log(
                    TAG,
                    "Ignored automatic stop from $source while manual simulation override is active"
                )
                return
            }

            if (source == DrivingStateSource.MANUAL_SIMULATION) {
                _manualSimulationOverride.value = driving
                if (driving) {
                    _activeSources.add(DrivingStateSource.MANUAL_SIMULATION)
                    _repliedContacts.clear()
                    _lastReplyTimestampByConversation.clear()
                } else {
                    _activeSources.remove(DrivingStateSource.MANUAL_SIMULATION)
                    _activeSources.remove(DrivingStateSource.AUTOMATIC)
                }
            } else if (source == DrivingStateSource.AUTOMATIC) {
                if (driving) {
                    _activeSources.add(DrivingStateSource.AUTOMATIC)
                } else {
                    _activeSources.remove(DrivingStateSource.AUTOMATIC)
                    _activeSources.remove(DrivingStateSource.ACTIVITY_RECOGNITION)
                    _activeSources.remove(DrivingStateSource.BLUETOOTH)
                    _activeSources.remove(DrivingStateSource.GPS_SPEED)
                }
            } else {
                if (driving) {
                    _activeSources.add(source)
                } else {
                    _activeSources.remove(source)
                }
            }

            val wasDriving = _isDriving.value
            val nextDriving = _activeSources.isNotEmpty()
            if (!wasDriving && nextDriving) {
                clearRepliedContacts()
            }

            DebugEventLogger.log(
                TAG,
                "Driving state -> $nextDriving (source=$source, activeSources=$_activeSources, manualOverride=${_manualSimulationOverride.value})"
            )
            _isDriving.value = nextDriving
        }

        fun onActivityTransitionEnter() {
            cancelActivityExitDebounce()
            setDrivingState(true, DrivingStateSource.ACTIVITY_RECOGNITION)
            DebugEventLogger.log(TAG, "Detected IN_VEHICLE ENTER, driving mode enabled")
            setNotificationText("Driving detected — auto-reply active 🚗")
        }

        fun onActivityTransitionExit() {
            cancelActivityExitDebounce()
            val runnable = Runnable {
                setDrivingState(false, DrivingStateSource.ACTIVITY_RECOGNITION)
                DebugEventLogger.log(TAG, "Detected IN_VEHICLE EXIT debounce elapsed")
                if (!_isDriving.value) {
                    setNotificationText("Driving stopped — auto-reply paused")
                }
                activityExitRunnable = null
            }
            activityExitRunnable = runnable
            activityExitHandler.postDelayed(runnable, EXIT_DEBOUNCE_MS)
            DebugEventLogger.log(TAG, "Detected IN_VEHICLE EXIT, debounce started (${EXIT_DEBOUNCE_MS}ms)")
        }

        fun cancelActivityExitDebounce() {
            activityExitRunnable?.let { activityExitHandler.removeCallbacks(it) }
            activityExitRunnable = null
        }

        internal fun resetStateForTest() {
            cancelActivityExitDebounce()
            _activeSources.clear()
            _manualSimulationOverride.value = false
            _isDriving.value = false
            _repliedContacts.clear()
            _lastReplyTimestampByConversation.clear()
        }

        fun start(context: Context) {
            _lastStartRequestedAtMs = System.currentTimeMillis()
            val intent = Intent(context, DriveReplyService::class.java)
            try {
                context.startForegroundService(intent)
                DebugEventLogger.log(TAG, "Foreground service start requested")
            } catch (e: Exception) {
                DebugEventLogger.log(TAG, "Foreground service start blocked or failed", e)
            }
        }

        fun stop(context: Context) {
            cancelActivityExitDebounce()
            val intent = Intent(context, DriveReplyService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                DebugEventLogger.log(TAG, "Foreground service stop failed", e)
            }
            _activeSources.clear()
            _isDriving.value = false
            _manualSimulationOverride.value = false
            DebugEventLogger.log(TAG, "Foreground service stop requested")
        }
    }

    private var transitionPendingIntent: PendingIntent? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private val speedHandler = Handler(Looper.getMainLooper())
    private var speedExitRunnable: Runnable? = null
    private var currentThreshold = 0
    private var isLocationTrackingActive = false
    private val listenerHealthHandler = Handler(Looper.getMainLooper())
    private var listenerHealthRunnable: Runnable? = null
    private var lastListenerRebindAttemptMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        DebugEventLogger.log(TAG, "Service created")
        createNotificationChannel()
        promoteToForeground(includeLocation = false)
        registerActivityTransitions()
        startListenerHealthWatchdog()

        // Bridge the static notification-text state flow into the OS notification.
        // This lets receivers update the notification body without calling
        // startService() from a background context (which Android 12+ restricts).
        serviceScope.launch {
            notificationText.collect { text ->
                updateNotification(text)
            }
        }

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
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelActivityExitDebounce()
        stopListenerHealthWatchdog()
        unregisterActivityTransitions()
        stopLocationSpeedTracking()
        serviceScope.cancel()
        _activeSources.clear()
        _isDriving.value = false
        _manualSimulationOverride.value = false
        DebugEventLogger.log(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun promoteToForeground(includeLocation: Boolean) {
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val baseType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (includeLocation && PermissionHelper.hasFineLocationPermission(this)) {
                baseType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                baseType
            }
        } else if (includeLocation && PermissionHelper.hasFineLocationPermission(this)) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(_notificationText.value),
            fgsType
        )
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
        if (!PermissionHelper.hasFineLocationPermission(this)) {
            DebugEventLogger.log(TAG, "Skipping speed tracking: fine location permission not granted")
            stopLocationSpeedTracking()
            return
        }

        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        }

        stopLocationSpeedTracking()
        promoteToForeground(includeLocation = true)

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

                        setDrivingState(true, DrivingStateSource.GPS_SPEED)
                        setNotificationText(String.format(Locale.US, "Driving detected via Speed: %.1f km/h 🚗", speedKmH))
                    } else {
                        if (DrivingStateSource.GPS_SPEED in _activeSources && speedExitRunnable == null) {
                            DebugEventLogger.log(
                                TAG,
                                String.format(Locale.US, "Speed below threshold (%.1f km/h), scheduling stop debounce", speedKmH)
                            )
                            speedExitRunnable = Runnable {
                                setDrivingState(false, DrivingStateSource.GPS_SPEED)
                                if (!_isDriving.value) {
                                    setNotificationText("Service active — waiting for driving detection")
                                }
                                speedExitRunnable = null
                            }
                            speedHandler.postDelayed(speedExitRunnable!!, EXIT_DEBOUNCE_MS)
                        }
                    }
                }
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
            isLocationTrackingActive = true
        } catch (_: SecurityException) {
            // Location permission not granted
            isLocationTrackingActive = false
        }
    }

    private fun stopLocationSpeedTracking() {
        locationCallback?.let { callback ->
            fusedLocationClient?.removeLocationUpdates(callback)
        }
        locationCallback = null
        speedExitRunnable?.let { speedHandler.removeCallbacks(it) }
        speedExitRunnable = null
        setDrivingState(false, DrivingStateSource.GPS_SPEED)
        if (isLocationTrackingActive) {
            isLocationTrackingActive = false
            promoteToForeground(includeLocation = false)
        }
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
        listenerHealthHandler.postDelayed(listenerHealthRunnable!!, LISTENER_HEALTH_CHECK_INTERVAL_MS)
    }

    private fun stopListenerHealthWatchdog() {
        listenerHealthRunnable?.let { listenerHealthHandler.removeCallbacks(it) }
        listenerHealthRunnable = null
    }
}
