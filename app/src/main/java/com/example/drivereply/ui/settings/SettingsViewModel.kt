package com.example.drivereply.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivereply.DriveReplyApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val preferencesManager = app.preferencesManager

    val replyInGroupChats: StateFlow<Boolean> = preferencesManager.replyInGroupChats
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val logRetentionDays: StateFlow<Int> = preferencesManager.logRetentionDays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 7
        )

    fun setReplyInGroupChats(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setReplyInGroupChats(enabled)
        }
    }

    fun setLogRetentionDays(days: Int) {
        viewModelScope.launch {
            preferencesManager.setLogRetentionDays(days)
        }
    }
}
