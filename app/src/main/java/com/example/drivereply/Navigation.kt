package com.example.drivereply

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.drivereply.ui.log.ReplyLogScreen
import com.example.drivereply.ui.main.MainScreen
import com.example.drivereply.ui.settings.SettingsScreen
import com.example.drivereply.ui.templates.EditTemplateScreen
import com.example.drivereply.ui.templates.TemplatesScreen

enum class Tab {
    Home, Templates, Log
}

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                MainShell(
                    onNavigate = { key -> backStack.add(key) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Settings> {
                SettingsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<EditTemplate> { key ->
                EditTemplateScreen(
                    templateId = key.templateId,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

@Composable
fun MainShell(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(Tab.Home) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == Tab.Home,
                    onClick = { currentTab = Tab.Home },
                    icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentTab == Tab.Templates,
                    onClick = { currentTab = Tab.Templates },
                    icon = { Icon(imageVector = Icons.Default.Chat, contentDescription = "Templates") },
                    label = { Text("Templates") }
                )
                NavigationBarItem(
                    selected = currentTab == Tab.Log,
                    onClick = { currentTab = Tab.Log },
                    icon = { Icon(imageVector = Icons.Default.History, contentDescription = "Logs") },
                    label = { Text("Log") }
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentTab) {
                Tab.Home -> {
                    MainScreen(
                        onItemClick = onNavigate,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Tab.Templates -> {
                    TemplatesScreen(
                        onAddEditTemplate = { id -> onNavigate(EditTemplate(id)) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Tab.Log -> {
                    ReplyLogScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
