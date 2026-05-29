package com.example.drivereply.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import com.example.drivereply.Templates
import com.example.drivereply.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Trigger permission check on resume/start
    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DriveReply",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = { onItemClick(Settings) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Status Card
            StatusCard(
                isServiceEnabled = uiState.isServiceEnabled,
                isDriving = uiState.isDriving,
                onCardClick = { viewModel.toggleDrivingState() }
            )

            // Master Toggle Switch
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.isServiceEnabled) "Monitoring Active" else "Service Stopped",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.isServiceEnabled) "Checking transitions in background" else "Toggle to start driving detection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isServiceEnabled,
                        onCheckedChange = { viewModel.toggleService(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    )
                }
            }

            // Quick Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    title = "Replies Sent Today",
                    value = uiState.repliesToday.toString(),
                    icon = Icons.Default.Chat,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Active Auto-Reply",
                    value = uiState.activeTemplate?.name ?: "None",
                    icon = Icons.Default.Description,
                    modifier = Modifier.weight(1f)
                )
            }

            ReadinessCard(
                isReady = uiState.isAutoReplyReady,
                blockers = uiState.readinessBlockers,
                showEnableService = !uiState.isServiceEnabled,
                showStartDriving = uiState.isServiceEnabled && !uiState.isDriving,
                showGrantListenerAccess = !uiState.hasNotificationListener,
                showRebindListener = uiState.hasNotificationListener && !uiState.isNotificationListenerConnected,
                showOpenTemplates = uiState.activeTemplate == null,
                onEnableService = { viewModel.toggleService(true) },
                onStartDriving = { viewModel.toggleDrivingState() },
                onGrantListenerAccess = { viewModel.openNotificationListenerSettings() },
                onRebindListener = { viewModel.rebindNotificationListener() },
                onOpenTemplates = { onItemClick(Templates) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ReadinessCard(
    isReady: Boolean,
    blockers: List<String>,
    showEnableService: Boolean,
    showStartDriving: Boolean,
    showGrantListenerAccess: Boolean,
    showRebindListener: Boolean,
    showOpenTemplates: Boolean,
    onEnableService: () -> Unit,
    onStartDriving: () -> Unit,
    onGrantListenerAccess: () -> Unit,
    onRebindListener: () -> Unit,
    onOpenTemplates: () -> Unit
) {
    val containerColor = if (isReady) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
    }
    val contentColor = if (isReady) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = contentColor
                )
                Text(
                    text = if (isReady) "Auto-Reply Ready" else "Auto-Reply Not Ready",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            if (isReady) {
                Text(
                    text = "All required conditions are satisfied.",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            } else {
                blockers.forEach { blocker ->
                    Text(
                        text = "- $blocker",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showEnableService) {
                        OutlinedButton(onClick = onEnableService, shape = RoundedCornerShape(12.dp)) {
                            Text("Enable Service", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (showStartDriving) {
                        OutlinedButton(onClick = onStartDriving, shape = RoundedCornerShape(12.dp)) {
                            Text("Start Driving", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (showGrantListenerAccess) {
                        OutlinedButton(onClick = onGrantListenerAccess, shape = RoundedCornerShape(12.dp)) {
                            Text("Grant Listener", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (showRebindListener) {
                        OutlinedButton(onClick = onRebindListener, shape = RoundedCornerShape(12.dp)) {
                            Text("Rebind Listener", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (showOpenTemplates) {
                        OutlinedButton(onClick = onOpenTemplates, shape = RoundedCornerShape(12.dp)) {
                            Text("Open Templates", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    isServiceEnabled: Boolean,
    isDriving: Boolean,
    onCardClick: () -> Unit
) {
    // Pulse animation setup
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val backgroundBrush = when {
        !isServiceEnabled -> {
            Brush.linearGradient(
                colors = listOf(Color(0xFF616161), Color(0xFF424242))
            )
        }
        isDriving -> {
            Brush.linearGradient(
                colors = listOf(Color(0xFF00B0FF), Color(0xFF00E676))
            )
        }
        else -> {
            Brush.linearGradient(
                colors = listOf(Color(0xFFFF9100), Color(0xFFFFD600))
            )
        }
    }

    val statusIcon = when {
        !isServiceEnabled -> Icons.Default.PlayDisabled
        isDriving -> Icons.Default.DirectionsCar
        else -> Icons.Default.CompassCalibration
    }

    val statusText = when {
        !isServiceEnabled -> "Service Offline"
        isDriving -> "Driving Detected"
        else -> "Monitoring Transitions"
    }

    val subtitleText = when {
        !isServiceEnabled -> "Turn on the master toggle to start"
        isDriving -> "Auto-replying to messages... 🚗\n(Tap to simulate stop)"
        else -> "Waiting for vehicle activity\n(Tap to simulate driving)"
    }

    Card(
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .scale(if (isServiceEnabled) pulseScale else 1f)
            .clickable(enabled = isServiceEnabled) { onCardClick() },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = "Status Icon",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusText,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitleText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        ),
        modifier = modifier.height(130.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

