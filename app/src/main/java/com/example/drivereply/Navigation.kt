package com.example.drivereply

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.drivereply.ui.activity.ActivityScreen
import com.example.drivereply.ui.main.MainScreen
import com.example.drivereply.ui.onboarding.OnboardingScreen
import com.example.drivereply.ui.settings.SettingsScreen
import com.example.drivereply.ui.templates.EditTemplateScreen
import com.example.drivereply.ui.templates.TemplatesScreen
import kotlinx.coroutines.flow.first

enum class Tab {
    Home, Templates, Activity
}

@Composable
fun MainNavigation(openUpdates: Boolean = false) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as DriveReplyApplication }
    var hasCompletedOnboarding by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(app) {
        hasCompletedOnboarding = app.preferencesManager.hasCompletedOnboarding.first()
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

    // When the user taps the in-app update progress notification, deep-link
    // straight into Settings. We still respect the onboarding gate so a
    // first-launch user sees onboarding first.
    val startDestination: NavKey = when {
        !onboardingState -> Onboarding
        openUpdates -> Settings
        else -> Main
    }

    key(startDestination) {
        val backStack = rememberNavBackStack(startDestination)

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
                entry<Onboarding> {
                    OnboardingScreen(
                        onCompleted = { hasCompletedOnboarding = true },
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
                        onItemClick = onNavigate,
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
