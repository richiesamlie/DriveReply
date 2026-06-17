package com.drivereply.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivereply.app.data.ReplyLogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = viewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Stats", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        AnalyticsPanel(
            modifier = Modifier.padding(paddingValues),
            viewModel = viewModel
        )
    }
}

@Composable
fun AnalyticsPanel(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = viewModel()
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val weeklyData by viewModel.last7DaysReplies.collectAsStateWithLifecycle()
    val appDist by viewModel.appDistribution.collectAsStateWithLifecycle()

    AnalyticsContent(
        logs = logs,
        weeklyData = weeklyData,
        appDist = appDist,
        modifier = modifier
    )
}

@Composable
fun AnalyticsContent(
    logs: List<ReplyLogEntry>,
    weeklyData: List<Pair<String, Int>>,
    appDist: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val totalReplies = logs.size
    val uniqueContacts = logs.map { it.contactName }.distinct().size

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    title = "Total Replies",
                    value = "$totalReplies",
                    icon = "💬",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Unique Contacts",
                    value = "$uniqueContacts",
                    icon = "👤",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Last 7 Days",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Auto-replies sent over the last week",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (weeklyData.isNotEmpty()) {
                        SplineChart(data = weeklyData)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No weekly records available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Platform Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Auto-reply volume by messaging application",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (totalReplies > 0) {
                        DonutChart(distribution = appDist, totalCount = totalReplies)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No messaging logs available yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = icon, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SplineChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    val maxVal = remember(data) { (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(5) }
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val paddingLeft = 40f
                val paddingRight = 20f
                val paddingTop = 20f
                val paddingBottom = 40f

                val chartWidth = width - paddingLeft - paddingRight
                val chartHeight = height - paddingTop - paddingBottom

                // Draw Gridlines & Y-Axis Labels
                val gridLines = 3
                for (i in 0..gridLines) {
                    val ratio = i.toFloat() / gridLines
                    val y = paddingTop + ratio * chartHeight
                    drawLine(
                        color = gridColor.copy(alpha = 0.4f),
                        start = Offset(paddingLeft, y),
                        end = Offset(width - paddingRight, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Prepare Spline coordinates
                val points = data.mapIndexed { idx, pair ->
                    val x = paddingLeft + (idx.toFloat() / 6f) * chartWidth
                    val y = paddingTop + chartHeight - (pair.second.toFloat() / maxVal) * chartHeight
                    Offset(x, y)
                }

                // Draw Spline Line and Gradient Area
                if (points.isNotEmpty()) {
                    val path = Path()
                    val fillPath = Path()

                    path.moveTo(points.first().x, points.first().y)
                    fillPath.moveTo(points.first().x, paddingTop + chartHeight)
                    fillPath.lineTo(points.first().x, points.first().y)

                    for (i in 1 until points.size) {
                        val current = points[i]
                        val previous = points[i - 1]
                        val control1 = Offset((current.x + previous.x) / 2f, previous.y)
                        val control2 = Offset((current.x + previous.x) / 2f, current.y)

                        path.cubicTo(control1.x, control1.y, control2.x, control2.y, current.x, current.y)
                        fillPath.cubicTo(control1.x, control1.y, control2.x, control2.y, current.x, current.y)
                    }

                    fillPath.lineTo(points.last().x, paddingTop + chartHeight)
                    fillPath.close()

                    // Draw Gradient Fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.4f),
                                primaryColor.copy(alpha = 0.0f)
                            ),
                            startY = paddingTop,
                            endY = paddingTop + chartHeight
                        )
                    )

                    // Draw Spline Stroke
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Data Dots
                    points.forEach { pt ->
                        drawCircle(
                            color = primaryColor,
                            radius = 4.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }

        // Draw X-Axis labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { pair ->
                Text(
                    text = pair.first,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DonutChart(
    distribution: Map<String, Int>,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    // Curated Harmonious Palette for App Distribution
    val colors = listOf(
        Color(0xFF25D366), // WhatsApp (Vibrant green)
        Color(0xFF0088CC), // Telegram (Sky Blue)
        Color(0xFF3A76F0), // Signal (Royal Blue)
        Color(0xFFA200FF), // Messenger (Vibrant Purple)
        Color(0xFF9E9E9E)  // Other (Warm Gray)
    )

    val appNames = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "org.thoughtcrime.securesms" to "Signal",
        "com.facebook.orca" to "Messenger"
    )

    val sliceList = remember(distribution) {
        var runningAngle = 0f
        distribution.entries.sortedByDescending { it.value }.mapIndexed { index, entry ->
            val angle = (entry.value.toFloat() / totalCount) * 360f
            val color = colors[index % colors.size]
            val segment = DonutSegment(
                packageName = entry.key,
                appName = appNames[entry.key] ?: "Other App",
                count = entry.value,
                startAngle = runningAngle,
                sweepAngle = angle,
                color = color
            )
            runningAngle += angle
            segment
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Donut Canvas
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val strokeWidth = 14.dp.toPx()
                sliceList.forEach { slice ->
                    drawArc(
                        color = slice.color,
                        startAngle = slice.startAngle - 90f,
                        sweepAngle = slice.sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$totalCount",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Legends
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sliceList.forEach { slice ->
                val percentage = ((slice.count.toFloat() / totalCount) * 100).toInt()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(slice.color, RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = "${slice.appName} ($percentage%)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

data class DonutSegment(
    val packageName: String,
    val appName: String,
    val count: Int,
    val startAngle: Float,
    val sweepAngle: Float,
    val color: Color
)
