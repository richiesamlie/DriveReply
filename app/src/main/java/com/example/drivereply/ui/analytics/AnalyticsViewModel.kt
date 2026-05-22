package com.example.drivereply.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.data.ReplyLogEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val replyLogDao = app.database.replyLogDao()

    val logs: StateFlow<List<ReplyLogEntry>> = replyLogDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val last7DaysReplies: StateFlow<List<Pair<String, Int>>> = logs.map { logList ->
        val now = LocalDate.now()
        val days = (0..6).map { now.minusDays(it.toLong()) }.reversed()
        val formatter = DateTimeFormatter.ofPattern("E")
        days.map { date ->
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val count = logList.count { it.timestamp in startOfDay until endOfDay }
            date.format(formatter) to count
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val appDistribution: StateFlow<Map<String, Int>> = logs.map { logList ->
        logList.groupBy { it.packageName }
            .mapValues { it.value.size }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap()
    )
}
