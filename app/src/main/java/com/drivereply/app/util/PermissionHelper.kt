package com.drivereply.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.drivereply.app.service.WhatsAppNotificationListener

object PermissionHelper {

    fun hasActivityRecognitionPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationListenerPermission(context: Context): Boolean {
        return try {
            if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
                return true
            }
            val componentName = ComponentName(context, WhatsAppNotificationListener::class.java)
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            enabledListeners
                .split(':')
                .mapNotNull { ComponentName.unflattenFromString(it.trim()) }
                .any { it == componentName }
        } catch (_: Exception) {
            false
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required below API 33
        }
    }

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required below API 31
        }
    }

    fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        // minSdk is 29 (Android 10 / Q), so ACCESS_BACKGROUND_LOCATION is always applicable.
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isBatteryOptimizationExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openNotificationListenerSettings(context: Context) {
        val primary = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(primary)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: ActivityNotFoundException) {
                // No settings activity available on this device/profile.
            }
        }
    }

    fun requestNotificationListenerRebind(context: Context, forceComponentReset: Boolean = false) {
        val componentName = ComponentName(context, WhatsAppNotificationListener::class.java)
        try {
            if (forceComponentReset) {
                val packageManager = context.packageManager
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            // minSdk is 29 (>= API 24 / N), so requestRebind is always available.
            NotificationListenerService.requestRebind(componentName)
        } catch (e: Exception) {
            DebugEventLogger.w("PermissionHelper", "Failed to request notification listener rebind", e)
        }
    }

    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(context: Context, packageName: String) {
        val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:$packageName".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(directIntent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (_: ActivityNotFoundException) {
                // Ignore if battery optimization settings screen is unavailable.
            }
        }
    }
}
