package com.drivereply.app

import android.app.Application
import com.drivereply.app.data.AppDatabase
import com.drivereply.app.data.PreferencesManager
import com.drivereply.app.util.DebugEventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DriveReplyApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            preferencesManager.debugLogsEnabled.collectLatest { enabled ->
                DebugEventLogger.setEnabled(enabled)
            }
        }
    }
}
