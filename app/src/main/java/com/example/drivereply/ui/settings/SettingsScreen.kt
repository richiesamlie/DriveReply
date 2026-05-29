package com.example.drivereply.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drivereply.util.PermissionHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val replyInGroups by viewModel.replyInGroupChats.collectAsStateWithLifecycle()
    val retentionDays by viewModel.logRetentionDays.collectAsStateWithLifecycle()
    val activeBluetoothDevices by viewModel.bluetoothDevices.collectAsStateWithLifecycle()
    val speedThreshold by viewModel.speedActivationThreshold.collectAsStateWithLifecycle()
    val debugLogText by viewModel.debugLogText.collectAsStateWithLifecycle()
    val debugLogsEnabled by viewModel.debugLogsEnabled.collectAsStateWithLifecycle()
    val updateState by viewModel.updateCheckState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var copiedNotice by remember { mutableStateOf<String?>(null) }
    var diagnosticsSnapshot by remember {
        mutableStateOf("Tap Copy Snapshot to generate a compact status report for support.")
    }

    var isBatteryExempt by remember {
        mutableStateOf(PermissionHelper.isBatteryOptimizationExempt(context))
    }

    var hasBluetoothPermission by remember {
        mutableStateOf(PermissionHelper.hasBluetoothConnectPermission(context))
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasBluetoothPermission = isGranted
        }
    )

    LaunchedEffect(Unit) {
        hasBluetoothPermission = PermissionHelper.hasBluetoothConnectPermission(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Section: Preferences
            SettingsSectionHeader(title = "Preferences")

            // Toggle for Group Chat
            SettingsToggleCard(
                title = "Reply in Group Chats",
                description = "If disabled, WhatsApp group chat messages will be ignored, replying only to individual senders.",
                icon = Icons.Default.Group,
                checked = replyInGroups,
                onCheckedChange = { viewModel.setReplyInGroupChats(it) }
            )

            // Log Retention Picker
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "History Retention Duration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Specify how long auto-reply history logs should be kept before being automatically cleared.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val options = listOf(
                            1 to "1 Day",
                            7 to "7 Days",
                            30 to "30 Days",
                            -1 to "Never"
                        )
                        options.forEach { (days, label) ->
                            val isSelected = retentionDays == days
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setLogRetentionDays(days) },
                                label = { Text(label, fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Section: Automation Triggers
            SettingsSectionHeader(title = "Automation Triggers")

            // Speed-Based Auto-Start Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Speed-Based Auto-Start",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Automatically activate driving mode when your speed exceeds the selected threshold (requires GPS location permission).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val options = listOf(
                            0 to "Disabled",
                            15 to "15 km/h",
                            30 to "30 km/h",
                            50 to "50 km/h"
                        )
                        options.forEach { (kmh, label) ->
                            val isSelected = speedThreshold == kmh
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setSpeedActivationThreshold(kmh) },
                                label = { Text(label, fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Car Bluetooth Triggers Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Car Bluetooth Triggers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select paired Bluetooth devices (like your car's media system) that should automatically toggle the driving mode when connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!hasBluetoothPermission) {
                        // Request permission button
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Bluetooth Permission", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val pairedDevices = remember(hasBluetoothPermission) {
                            viewModel.getPairedBluetoothDevices()
                        }
                        
                        if (pairedDevices.isEmpty()) {
                            Text(
                                text = "No paired Bluetooth devices found on your system.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pairedDevices.forEach { (name, address) ->
                                    val isChecked = activeBluetoothDevices.contains(address)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.toggleBluetoothDevice(address) }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { viewModel.toggleBluetoothDevice(address) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section: Reliability & Background Tasks
            SettingsSectionHeader(title = "App Reliability")

            // Battery settings link
            SettingsActionCard(
                title = "Battery Optimization Settings",
                description = if (isBatteryExempt) {
                    "Exempted. The app is running with unrestricted battery access."
                } else {
                    "Tap to request battery optimization exemption to avoid background service interruption."
                },
                icon = Icons.Default.BatteryAlert,
                actionLabel = if (isBatteryExempt) "Done" else "Optimize",
                enabled = !isBatteryExempt,
                onClick = {
                    PermissionHelper.openBatteryOptimizationSettings(context, context.packageName)
                    // Refresh optimization status
                    isBatteryExempt = PermissionHelper.isBatteryOptimizationExempt(context)
                }
            )

            // DontKillMyApp link
            SettingsActionCard(
                title = "OEM Restriction Instructions",
                description = "Some manufacturers (Samsung, Xiaomi, OnePlus, etc.) heavily restrict background services. Learn how to configure your phone's auto-start rules.",
                icon = Icons.Default.Link,
                actionLabel = "Learn More",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            )

            // Section: About
            SettingsSectionHeader(title = "Debugging")

            SettingsToggleCard(
                title = "Enable Debug Logs",
                description = "Capture detailed internal events for troubleshooting. Disable to reduce noise.",
                icon = Icons.Default.Info,
                checked = debugLogsEnabled,
                onCheckedChange = { viewModel.setDebugLogsEnabled(it) }
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Quick Diagnostics Snapshot",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Copies key runtime states (listener, permissions, service, supported apps) in one compact block.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = diagnosticsSnapshot,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val snapshot = viewModel.buildDiagnosticsSnapshot()
                                    diagnosticsSnapshot = snapshot
                                    clipboardManager.setText(AnnotatedString(snapshot))
                                    copiedNotice = "Diagnostics copied to clipboard"
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Copy Snapshot", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Debug Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (debugLogsEnabled) {
                            "Reproduce the issue, then tap Copy Logs and paste them into chat."
                        } else {
                            "Debug log capture is currently disabled."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = debugLogText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = debugLogsEnabled,
                            onClick = {
                                clipboardManager.setText(AnnotatedString(debugLogText))
                                copiedNotice = "Logs copied to clipboard"
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Copy Logs", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearDebugLogs() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Clear Logs", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            copiedNotice?.let { notice ->
                LaunchedEffect(notice) {
                    kotlinx.coroutines.delay(1400)
                    copiedNotice = null
                }
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // Section: About
            SettingsSectionHeader(title = "Updates")

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "GitHub Update Check",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Installed: ${updateState.installedTag}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Latest: ${updateState.latestTag ?: "Not checked yet"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!updateState.message.isNullOrBlank()) {
                        Text(
                            text = updateState.message!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updateState.hasUpdate) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.checkForUpdates() },
                            enabled = !updateState.isChecking,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (updateState.isChecking) "Checking..." else "Check Updates")
                        }

                        if (updateState.hasUpdate) {
                            val targetUrl = updateState.downloadUrl ?: updateState.releaseUrl
                            if (!targetUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        if (!updateState.downloadUrl.isNullOrBlank()) {
                                            "Download APK"
                                        } else {
                                            "Open Release"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section: About
            SettingsSectionHeader(title = "About")

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "DriveReply Auto-Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Version ${updateState.installedTag}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!updateState.latestTag.isNullOrBlank()) {
                            Text(
                                text = "Latest GitHub Release ${updateState.latestTag}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Developed for seamless safety and communication while driving.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.5.sp
    )
}

@Composable
fun SettingsToggleCard(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
fun SettingsActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    actionLabel: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
