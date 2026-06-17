package com.drivereply.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivereply.app.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SettingsPane(val title: String) {
    HOME("Settings"),
    SETUP("Setup & Access"),
    PREFERENCES("Preferences"),
    AUTOMATION("Automation"),
    RELIABILITY("Reliability"),
    DEBUGGING("Debugging"),
    UPDATES_ABOUT("Updates & About")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val setupState by viewModel.setupState.collectAsStateWithLifecycle()
    val replyInGroups by viewModel.replyInGroupChats.collectAsStateWithLifecycle()
    val retentionDays by viewModel.logRetentionDays.collectAsStateWithLifecycle()
    val activeBluetoothDevices by viewModel.bluetoothDevices.collectAsStateWithLifecycle()
    val speedThreshold by viewModel.speedActivationThreshold.collectAsStateWithLifecycle()
    val debugLogText by viewModel.debugLogText.collectAsStateWithLifecycle()
    val debugLogsEnabled by viewModel.debugLogsEnabled.collectAsStateWithLifecycle()
    val isDriving by viewModel.isDriving.collectAsStateWithLifecycle()
    val updateState by viewModel.updateCheckState.collectAsStateWithLifecycle()

    var pane by remember { mutableStateOf(SettingsPane.HOME) }
    var copiedNotice by remember { mutableStateOf<String?>(null) }
    var diagnosticsSnapshot by remember {
        mutableStateOf("Tap Copy Snapshot to generate a compact status report for support.")
    }
    // When the user taps "Download & install" and we have to ask for
    // POST_NOTIFICATIONS first, this flag is set so the launcher's
    // callback can fire the actual download regardless of grant result.
    var pendingStartDownload by remember { mutableStateOf(false) }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshSetupState() }
    )
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            viewModel.refreshSetupState()
            if (pendingStartDownload) {
                pendingStartDownload = false
                // Start the download even if the user denied the
                // notification permission — the in-app progress bar is
                // the source of truth either way.
                viewModel.startDownloadAndInstall()
            }
        }
    )
    val fineLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { viewModel.refreshSetupState() }
    )
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshSetupState() }
    )
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshSetupState() }
    )

    LaunchedEffect(Unit) { viewModel.refreshSetupState() }
    LaunchedEffect(pane) {
        if (pane == SettingsPane.SETUP) {
            viewModel.refreshSetupState()
        }
    }
    copiedNotice?.let { notice ->
        LaunchedEffect(notice) {
            delay(1400)
            copiedNotice = null
        }
    }
    BackHandler(enabled = pane != SettingsPane.HOME) {
        pane = SettingsPane.HOME
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pane.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (pane == SettingsPane.HOME) onBack() else pane = SettingsPane.HOME
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            when (pane) {
                SettingsPane.HOME -> SettingsHome(
                    setupState = setupState,
                    onSelect = { pane = it }
                )
                SettingsPane.SETUP -> SettingsSetup(
                    setupState = setupState,
                    onRefresh = viewModel::refreshSetupState,
                    onOpenListenerSettings = { PermissionHelper.openNotificationListenerSettings(context) },
                    onRebindListener = { PermissionHelper.requestNotificationListenerRebind(context) },
                    onRequestActivityRecognition = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        } else {
                            viewModel.refreshSetupState()
                        }
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestFineLocation = {
                        fineLocationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onRequestBackgroundLocation = {
                        if (setupState.hasFineLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                    onRequestBluetooth = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    },
                    onOpenBatteryOptimization = {
                        PermissionHelper.openBatteryOptimizationSettings(context, context.packageName)
                    }
                )
                SettingsPane.PREFERENCES -> SettingsPreferences(
                    replyInGroups = replyInGroups,
                    retentionDays = retentionDays,
                    onSetReplyInGroups = viewModel::setReplyInGroupChats,
                    onSetRetentionDays = viewModel::setLogRetentionDays
                )
                SettingsPane.AUTOMATION -> SettingsAutomation(
                    speedThreshold = speedThreshold,
                    onSetSpeedThreshold = viewModel::setSpeedActivationThreshold,
                    activeBluetoothDevices = activeBluetoothDevices,
                    pairedDevices = viewModel.getPairedBluetoothDevices(),
                    onToggleBluetooth = viewModel::toggleBluetoothDevice
                )
                SettingsPane.RELIABILITY -> SettingsReliability(
                    batteryExempt = setupState.isBatteryOptimizationExempt,
                    onOpenBatteryOptimization = {
                        PermissionHelper.openBatteryOptimizationSettings(context, context.packageName)
                    },
                    onOpenDontKillMyApp = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
                SettingsPane.DEBUGGING -> SettingsDebugging(
                    debugLogsEnabled = debugLogsEnabled,
                    isDriving = isDriving,
                    debugLogText = debugLogText,
                    diagnosticsSnapshot = diagnosticsSnapshot,
                    onSetDebugLogs = viewModel::setDebugLogsEnabled,
                    onSetDrivingSimulation = viewModel::setManualDrivingSimulation,
                    onCopyLogs = {
                        clipboardManager.setText(AnnotatedString(debugLogText))
                        copiedNotice = "Logs copied to clipboard"
                    },
                    onClearLogs = viewModel::clearDebugLogs,
                    onCopySnapshot = {
                        coroutineScope.launch {
                            val snapshot = viewModel.buildDiagnosticsSnapshot()
                            diagnosticsSnapshot = snapshot
                            clipboardManager.setText(AnnotatedString(snapshot))
                            copiedNotice = "Diagnostics copied to clipboard"
                        }
                    }
                )
                SettingsPane.UPDATES_ABOUT -> SettingsUpdatesAbout(
                    updateState = updateState,
                    onCheckUpdates = viewModel::checkForUpdates,
                    onStartDownload = {
                        // On API 33+ the in-app update posts a system
                        // progress notification. Request POST_NOTIFICATIONS
                        // before kicking off the download so the user
                        // actually sees it. The download still proceeds
                        // even if the user denies — the in-app progress
                        // bar is the source of truth.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingStartDownload = true
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.startDownloadAndInstall()
                        }
                    },
                    onCancelDownload = viewModel::cancelDownload,
                    onOpenLink = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
            }
            copiedNotice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsHome(
    setupState: SetupState,
    onSelect: (SettingsPane) -> Unit
) {
    PaneHint("Choose a category to configure DriveReply.")
    val setupChecks = listOf(
        setupState.hasActivityRecognition,
        setupState.hasNotificationListener,
        setupState.isNotificationListenerConnected,
        setupState.hasNotificationPermission,
        setupState.isBatteryOptimizationExempt,
        setupState.hasFineLocation
    )
    val completedChecks = setupChecks.count { it }
    EntryCard(
        "Setup & Access",
        "$completedChecks/${setupChecks.size} core checks completed",
        Icons.Default.Security
    ) { onSelect(SettingsPane.SETUP) }
    EntryCard("Preferences", "Reply behavior and retention", Icons.AutoMirrored.Filled.Chat) {
        onSelect(SettingsPane.PREFERENCES)
    }
    EntryCard("Automation", "Speed and Bluetooth triggers", Icons.Default.Speed) {
        onSelect(SettingsPane.AUTOMATION)
    }
    EntryCard("Reliability", "Battery and OEM restrictions", Icons.Default.Build) {
        onSelect(SettingsPane.RELIABILITY)
    }
    EntryCard("Debugging", "Diagnostics snapshot and logs", Icons.Default.CompassCalibration) {
        onSelect(SettingsPane.DEBUGGING)
    }
    EntryCard("Updates & About", "Version and update checks", Icons.Default.Info) {
        onSelect(SettingsPane.UPDATES_ABOUT)
    }
}

@Composable
private fun SettingsSetup(
    setupState: SetupState,
    onRefresh: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onRebindListener: () -> Unit,
    onRequestActivityRecognition: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestFineLocation: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onRequestBluetooth: () -> Unit,
    onOpenBatteryOptimization: () -> Unit
) {
    PaneHint("Grant required access and verify listener health.")
    val missingRequirements = buildList {
        if (!setupState.hasActivityRecognition) add("Activity Recognition permission")
        if (!setupState.hasNotificationListener) add("Notification Interceptor permission")
        if (!setupState.isNotificationListenerConnected) add("Notification listener connection")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !setupState.hasNotificationPermission) {
            add("Notification permission")
        }
        if (!setupState.isBatteryOptimizationExempt) add("Battery optimization exemption")
        if (!setupState.hasFineLocation) add("Fine Location permission")
    }
    SetupSummaryCard(missingRequirements)
    SectionHeader("Quick Actions")

    EntryCard(
        "Notification Listener Health",
        "Permission=${setupState.hasNotificationListener}, connected=${setupState.isNotificationListenerConnected}",
        Icons.Default.Notifications
    ) { }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRebindListener, shape = RoundedCornerShape(12.dp)) { Text("Rebind") }
        TextButton(onClick = onOpenListenerSettings) { Text("Open Settings") }
        TextButton(onClick = onRefresh) { Text("Refresh") }
    }

    SectionHeader("Permissions")
    PermissionRow(
        title = "Activity Recognition",
        description = "Detects when you are likely in a vehicle.",
        granted = setupState.hasActivityRecognition,
        onGrantClick = onRequestActivityRecognition
    )
    PermissionRow(
        title = "Notification Interceptor",
        description = "Allows reading notifications and sending inline replies.",
        granted = setupState.hasNotificationListener,
        onGrantClick = onOpenListenerSettings
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionRow(
            title = "Notification Permission",
            description = "Required for foreground service notifications.",
            granted = setupState.hasNotificationPermission,
            onGrantClick = onRequestNotificationPermission
        )
    }
    PermissionRow(
        title = "Battery Optimization Exempt",
        description = "Improves reliability when app runs in background.",
        granted = setupState.isBatteryOptimizationExempt,
        onGrantClick = onOpenBatteryOptimization
    )
    PermissionRow(
        title = "Fine Location",
        description = "Enables speed-based automatic activation.",
        granted = setupState.hasFineLocation,
        onGrantClick = onRequestFineLocation
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PermissionRow(
            title = "Background Location",
            description = "Needed when speed trigger runs with app in background.",
            granted = setupState.hasBackgroundLocation,
            onGrantClick = onRequestBackgroundLocation
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PermissionRow(
            title = "Bluetooth Connect",
            description = "Lets app detect selected car Bluetooth devices.",
            granted = setupState.hasBluetoothConnect,
            onGrantClick = onRequestBluetooth
        )
    }

    val installedCount = setupState.supportedApps.count { it.isInstalled }
    SectionHeader("Supported Apps")
    EntryCard(
        "Supported Apps",
        "$installedCount/${setupState.supportedApps.size} supported apps detected",
        Icons.AutoMirrored.Filled.Chat
    ) { }
    if (installedCount == 0) {
        Text(
            "No supported messaging app detected yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    setupState.supportedApps.forEach { app ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = if (app.isInstalled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (app.isInstalled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SetupSummaryCard(missingRequirements: List<String>) {
    val ready = missingRequirements.isEmpty()
    val containerColor = if (ready) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    }
    val contentColor = if (ready) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (ready) "Setup Ready" else "Missing Requirements",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            if (ready) {
                Text(
                    text = "All core setup requirements are complete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            } else {
                missingRequirements.forEach { requirement ->
                    Text(
                        text = "- $requirement",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPreferences(
    replyInGroups: Boolean,
    retentionDays: Int,
    onSetReplyInGroups: (Boolean) -> Unit,
    onSetRetentionDays: (Int) -> Unit
) {
    PaneHint("Control reply behavior and data retention.")
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Reply in Group Chats", fontWeight = FontWeight.Bold)
                Text("If disabled, only direct chats receive auto-replies.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = replyInGroups, onCheckedChange = onSetReplyInGroups)
        }
    }
    EntryCard("History Retention", "Select how long reply logs are kept", Icons.Default.Info) { }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1 to "1 Day", 7 to "7 Days", 30 to "30 Days", -1 to "Never").forEach { (days, label) ->
            FilterChip(
                selected = retentionDays == days,
                onClick = { onSetRetentionDays(days) },
                label = { Text(label, fontSize = 12.sp) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsAutomation(
    speedThreshold: Int,
    onSetSpeedThreshold: (Int) -> Unit,
    activeBluetoothDevices: Set<String>,
    pairedDevices: List<Pair<String, String>>,
    onToggleBluetooth: (String) -> Unit
) {
    PaneHint("Configure automatic driving-mode triggers.")
    EntryCard("Speed-Based Auto-Start", "Current threshold: $speedThreshold km/h", Icons.Default.DirectionsCar) { }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "Off", 15 to "15", 30 to "30", 50 to "50").forEach { (value, label) ->
            FilterChip(
                selected = speedThreshold == value,
                onClick = { onSetSpeedThreshold(value) },
                label = { Text("$label km/h", fontSize = 12.sp) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    EntryCard("Car Bluetooth Triggers", "Select paired devices", Icons.Default.Bluetooth) { }
    if (pairedDevices.isEmpty()) {
        Text("No paired Bluetooth devices found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        pairedDevices.forEach { (name, address) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleBluetooth(address) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Checkbox(
                    checked = activeBluetoothDevices.contains(address),
                    onCheckedChange = { onToggleBluetooth(address) }
                )
            }
        }
    }
}

@Composable
private fun SettingsReliability(
    batteryExempt: Boolean,
    onOpenBatteryOptimization: () -> Unit,
    onOpenDontKillMyApp: () -> Unit
) {
    PaneHint("Mitigate background restrictions on your device.")
    ActionCard(
        "Battery Optimization",
        if (batteryExempt) "Exempted" else "Open exemption request",
        Icons.Default.BatteryAlert,
        if (batteryExempt) "Done" else "Open",
        !batteryExempt,
        onOpenBatteryOptimization
    )
    ActionCard(
        "OEM Restrictions",
        "Open dontkillmyapp.com guidance",
        Icons.Default.Link,
        "Open",
        true,
        onOpenDontKillMyApp
    )
}

@Composable
private fun SettingsDebugging(
    debugLogsEnabled: Boolean,
    isDriving: Boolean,
    debugLogText: String,
    diagnosticsSnapshot: String,
    onSetDebugLogs: (Boolean) -> Unit,
    onSetDrivingSimulation: (Boolean) -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onCopySnapshot: () -> Unit
) {
    PaneHint("Generate diagnostics and capture logs for support.")
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Enable Debug Logs", fontWeight = FontWeight.Bold)
                Text("Capture detailed events for troubleshooting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = debugLogsEnabled, onCheckedChange = onSetDebugLogs, colors = SwitchDefaults.colors())
        }
    }
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Simulate Driving", fontWeight = FontWeight.Bold)
                Text("Use manual simulation for testing without moving.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = isDriving, onCheckedChange = onSetDrivingSimulation)
        }
    }
    EntryCard("Quick Diagnostics Snapshot", "Copy compact status for support", Icons.Default.CompassCalibration) { }
    Text(
        diagnosticsSnapshot,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp, max = 160.dp)
            .verticalScroll(rememberScrollState())
    )
    Button(onClick = onCopySnapshot, shape = RoundedCornerShape(12.dp)) { Text("Copy Snapshot") }

    EntryCard("Debug Logs", "Copy or clear event logs", Icons.Default.Notifications) { }
    Text(
        debugLogText,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 220.dp)
            .verticalScroll(rememberScrollState())
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onCopyLogs, enabled = debugLogsEnabled, shape = RoundedCornerShape(12.dp)) { Text("Copy Logs") }
        OutlinedButton(onClick = onClearLogs, shape = RoundedCornerShape(12.dp)) { Text("Clear Logs") }
    }
}

@Composable
private fun SettingsUpdatesAbout(
    updateState: UpdateCheckUiState,
    onCheckUpdates: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    PaneHint("Check GitHub releases and view installed version details.")
    EntryCard("GitHub Update Check", "Installed ${updateState.installedTag}", Icons.Default.Info) { }
    Text("Latest: ${updateState.latestTag ?: "Not checked yet"}", style = MaterialTheme.typography.bodySmall)
    updateState.message?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
    }

    // Primary action: in-app download + install.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onCheckUpdates,
            enabled = !updateState.isChecking,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (updateState.isChecking) "Checking..." else "Check Updates")
        }
        if (updateState.canStartDownload) {
            Button(
                onClick = onStartDownload,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Download & install")
            }
        }
    }

    // In-flight progress UI.
    if (updateState.isDownloading) {
        val percent = updateState.downloadPercent.coerceIn(0, 100)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Downloading update…",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onCancelDownload) { Text("Cancel") }
        }
    }

    // Error message (signature mismatch, network, etc.)
    updateState.downloadError?.let { err ->
        Text(
            err,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    // Fallback: open the GitHub release page in a browser (works even when
    // the user has not enabled "install unknown apps" for our app).
    if (updateState.hasUpdate) {
        val fallback = updateState.releaseUrl
        if (!fallback.isNullOrBlank()) {
            OutlinedButton(
                onClick = { onOpenLink(fallback) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Open release page")
            }
        }
    }

    EntryCard("About", "DriveReply Auto-Assistant\nVersion ${updateState.installedTag}", Icons.Default.Info) { }
}

@Composable
private fun EntryCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PaneHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Column {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(actionLabel, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onGrantClick: () -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (granted) "Status: Granted" else "Status: Missing",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
            if (granted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            } else {
                Button(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
