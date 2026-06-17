package com.drivereply.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivereply.app.util.PermissionHelper

private enum class DetectionMode(val label: String, val description: String) {
    ACTIVITY("Activity Recognition", "Auto-detect with motion/activity sensors."),
    BLUETOOTH("Bluetooth", "Use selected car Bluetooth devices."),
    MANUAL("Manual", "Manually toggle driving mode from settings.")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var step by remember { mutableIntStateOf(0) }
    var templateName by remember { mutableStateOf("Driving Reply") }
    var templateBody by remember {
        mutableStateOf("I'm currently driving and will reply as soon as it's safe.")
    }
    var detectionMode by remember { mutableStateOf(DetectionMode.ACTIVITY) }

    var hasActivityRecognition by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasNotificationListener by remember { mutableStateOf(false) }

    fun refreshPermissionStatus() {
        hasActivityRecognition = PermissionHelper.hasActivityRecognitionPermission(context)
        hasNotificationPermission = PermissionHelper.hasNotificationPermission(context)
        hasNotificationListener = PermissionHelper.hasNotificationListenerPermission(context)
    }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { refreshPermissionStatus() }
    )
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { refreshPermissionStatus() }
    )

    LaunchedEffect(Unit) { refreshPermissionStatus() }
    LaunchedEffect(step) {
        if (step == 1) {
            refreshPermissionStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome to DriveReply", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (step + 1) / 4f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Step ${step + 1} of 4",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (step) {
                0 -> {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🚗", style = MaterialTheme.typography.displaySmall)
                            Text("Stay safe while staying connected.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "DriveReply automatically replies to incoming messages when you're driving.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                1 -> {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                            PermissionItem(
                                title = "Notification Listener",
                                granted = hasNotificationListener,
                                actionLabel = "Open Settings",
                                onAction = { PermissionHelper.openNotificationListenerSettings(context) }
                            )
                            PermissionItem(
                                title = "Activity Recognition",
                                granted = hasActivityRecognition,
                                actionLabel = "Grant",
                                onAction = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                    } else {
                                        refreshPermissionStatus()
                                    }
                                }
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                PermissionItem(
                                    title = "Notifications",
                                    granted = hasNotificationPermission,
                                    actionLabel = "Grant",
                                    onAction = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create your auto-reply", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = templateName,
                                onValueChange = { templateName = it },
                                label = { Text("Template Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = templateBody,
                                onValueChange = { templateBody = it },
                                label = { Text("Message") },
                                minLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${templateBody.length} characters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Choose detection mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            DetectionMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = detectionMode == mode,
                                    onClick = { detectionMode = mode },
                                    label = { Text(mode.label) },
                                    leadingIcon = {
                                        if (detectionMode == mode) {
                                            Text("✓")
                                        }
                                    }
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (step > 0) {
                    Button(
                        onClick = { step -= 1 },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isCompleting
                    ) {
                        Text("Back")
                    }
                }

                val canContinue = when (step) {
                    2 -> templateName.isNotBlank() && templateBody.isNotBlank()
                    else -> true
                }

                Button(
                    onClick = {
                        if (step < 3) {
                            step += 1
                        } else {
                            viewModel.completeOnboarding(
                                templateName = templateName,
                                templateBody = templateBody,
                                onComplete = onCompleted
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canContinue && !uiState.isCompleting
                ) {
                    Text(if (step == 3) "Finish Setup" else "Next")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (granted) "Granted" else "Required",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            )
        }
        if (!granted) {
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
