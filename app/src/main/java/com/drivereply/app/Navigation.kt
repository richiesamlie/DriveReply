package com.drivereply.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.drivereply.app.ui.activity.ActivityScreen
import com.drivereply.app.ui.main.MainScreen
import com.drivereply.app.ui.onboarding.OnboardingScreen
import com.drivereply.app.ui.settings.SettingsPane
import com.drivereply.app.ui.settings.SettingsScreen
import com.drivereply.app.ui.templates.EditTemplateScreen
import com.drivereply.app.ui.templates.TemplatesScreen
import kotlinx.coroutines.flow.first

enum class Tab {
    Home, Templates, Activity
}

@Composable
fun MainNavigation(
    openUpdates: Boolean = false,
    onOpenUpdatesConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as DriveReplyApplication }
    var hasCompletedOnboarding by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var openSettingsToUpdates by rememberSaveable { mutableStateOf(openUpdates) }

    LaunchedEffect(app) {
        if (hasCompletedOnboarding == null) {
            hasCompletedOnboarding = app.preferencesManager.hasCompletedOnboarding.first()
        }
    }

    val onboardingState = hasCompletedOnboarding
    if (onboardingState == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val rootKey: NavKey = if (!onboardingState) Onboarding else Main

    key(rootKey) {
        val backStack = rememberNavBackStack(rootKey)

        LaunchedEffect(openUpdates, onboardingState) {
            if (openUpdates) {
                openSettingsToUpdates = true
                if (onboardingState && backStack.lastOrNull() != Settings) {
                    backStack.add(Settings)
                }
                onOpenUpdatesConsumed()
            }
        }

        NavDisplay(
            backStack = backStack,
            onBack = {
                if (backStack.lastOrNull() == Settings) {
                    openSettingsToUpdates = false
                }
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            },
            entryProvider = entryProvider {
                entry<Main> {
                    MainShell(
                        onNavigate = { key -> backStack.add(key) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                entry<Onboarding> {
                    OnboardingScreen(
                        onCompleted = { hasCompletedOnboarding = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                entry<Settings> {
                    SettingsScreen(
                        initialPane = if (openSettingsToUpdates) SettingsPane.UPDATES_ABOUT else SettingsPane.HOME,
                        onBack = {
                            openSettingsToUpdates = false
                            if (backStack.size > 1) {
                                backStack.removeLastOrNull()
                            } else {
                                backStack[0] = Main
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                entry<EditTemplate> { key ->
                    EditTemplateScreen(
                        templateId = key.templateId,
                        onBack = {
                            if (backStack.size > 1) {
                                backStack.removeLastOrNull()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        )
    }
}

@Composable
fun MainShell(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by rememberSaveable { mutableStateOf(Tab.Home) }

    val handleNavigation: (Any) -> Unit = { key ->
        when (key) {
            Templates -> currentTab = Tab.Templates
            Activity -> currentTab = Tab.Activity
            Main -> currentTab = Tab.Home
            is NavKey -> onNavigate(key)
        }
    }

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
                    icon = { Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = "Templates") },
                    label = { Text("Templates") }
                )
                NavigationBarItem(
                    selected = currentTab == Tab.Activity,
                    onClick = { currentTab = Tab.Activity },
                    icon = { Icon(imageVector = Icons.Default.History, contentDescription = "Activity") },
                    label = { Text("Activity") }
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentTab) {
                Tab.Home -> {
                    MainScreen(
                        onItemClick = handleNavigation,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Tab.Templates -> {
                    TemplatesScreen(
                        onAddEditTemplate = { id -> onNavigate(EditTemplate(id)) },
                        onOpenSettings = { onNavigate(Settings) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Tab.Activity -> {
                    ActivityScreen(
                        onOpenSettings = { onNavigate(Settings) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
