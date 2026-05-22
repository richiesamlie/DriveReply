package com.example.drivereply

import android.app.Application
import com.example.drivereply.data.AppDatabase
import com.example.drivereply.data.PreferencesManager

class DriveReplyApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
}
