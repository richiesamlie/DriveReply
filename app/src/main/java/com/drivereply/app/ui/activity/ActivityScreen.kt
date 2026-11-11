package com.drivereply.app.ui.activity

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivereply.app.ui.analytics.AnalyticsPanel
import com.drivereply.app.ui.log.ReplyLogPanel
import com.drivereply.app.ui.log.ReplyLogViewModel

private enum class ActivityTab(val label: String) {
    Log("Log"),
    Stats("Stats")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ActivityScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    replyLogViewModel: ReplyLogViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableStateOf(ActivityTab.Log) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val logs by replyLogViewModel.logs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (selectedTab == ActivityTab.Log && logs.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Reply History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                ActivityTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            when (selectedTab) {
                ActivityTab.Log -> ReplyLogPanel(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = replyLogViewModel
                )
                ActivityTab.Stats -> AnalyticsPanel(modifier = Modifier.fillMaxSize())
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Reply History") },
            text = { Text("Are you sure you want to permanently clear all automatic reply logs? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        replyLogViewModel.clearLogs()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
