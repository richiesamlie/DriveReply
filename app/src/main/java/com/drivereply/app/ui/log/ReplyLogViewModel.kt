package com.drivereply.app.ui.log

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivereply.app.DriveReplyApplication
import com.drivereply.app.data.ReplyLogEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReplyLogViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val replyLogDao = app.database.replyLogDao()
    private val preferencesManager = app.preferencesManager

    val logs: StateFlow<List<ReplyLogEntry>> = replyLogDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val logRetentionDays: StateFlow<Int> = preferencesManager.logRetentionDays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 7
        )

    init {
        viewModelScope.launch {
            val days = preferencesManager.logRetentionDays.first()
            if (days > 0) {
                val threshold = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
                replyLogDao.deleteOlderThan(threshold)
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            replyLogDao.deleteAll()
        }
    }
}
