package com.drivereply.app.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivereply.app.DriveReplyApplication
import com.drivereply.app.data.ReplyLogEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AnalyticsUiState(
    val totalReplies: Int = 0,
    val uniqueContacts: Int = 0,
    val weeklyData: List<Pair<String, Int>> = emptyList(),
    val appDistribution: Map<String, Int> = emptyMap()
)

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val replyLogDao = app.database.replyLogDao()

    val logs: StateFlow<List<ReplyLogEntry>> = replyLogDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val uiState: StateFlow<AnalyticsUiState> = logs.map { logList ->
        val now = LocalDate.now()
        val days = (0..6).map { now.minusDays(it.toLong()) }.reversed()
        val formatter = DateTimeFormatter.ofPattern("E")
        val weekly = days.map { date ->
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val count = logList.count { it.timestamp in startOfDay until endOfDay }
            date.format(formatter) to count
        }
        val distribution = logList.groupBy { it.packageName }
            .mapValues { it.value.size }
        val uniqueContacts = logList.asSequence().map { it.contactName }.distinct().count()

        AnalyticsUiState(
            totalReplies = logList.size,
            uniqueContacts = uniqueContacts,
            weeklyData = weekly,
            appDistribution = distribution
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalyticsUiState()
    )
}
