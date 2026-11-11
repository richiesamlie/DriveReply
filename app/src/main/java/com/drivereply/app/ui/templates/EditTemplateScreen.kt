package com.drivereply.app.ui.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

private val DAY_OPTIONS = listOf(
    1 to "Mon",
    2 to "Tue",
    3 to "Wed",
    4 to "Thu",
    5 to "Fri",
    6 to "Sat",
    7 to "Sun"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTemplateScreen(
    templateId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditTemplateViewModel = viewModel()
) {
    val template by viewModel.templateState.collectAsStateWithLifecycle()
    val rule by viewModel.ruleState.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()

    var initializedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var contactFilter by rememberSaveable { mutableStateOf("") }
    var selectedDaysCsv by rememberSaveable { mutableStateOf("") }
    var timeWindowEnabled by rememberSaveable { mutableStateOf(false) }
    var startTimeText by rememberSaveable { mutableStateOf("09:00") }
    var endTimeText by rememberSaveable { mutableStateOf("17:00") }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(templateId) {
        viewModel.loadTemplate(templateId)
        if (templateId == null && initializedKey != "__new__") {
            name = ""
            body = ""
            contactFilter = ""
            selectedDaysCsv = ""
            timeWindowEnabled = false
            startTimeText = "09:00"
            endTimeText = "17:00"
            initializedKey = "__new__"
        }
    }

    LaunchedEffect(template, rule, templateId) {
        val loaded = template
        if (templateId != null && loaded != null && loaded.id == templateId && initializedKey != templateId) {
            name = loaded.name
            body = loaded.body
            contactFilter = rule?.contactName.orEmpty()
            selectedDaysCsv = rule?.daysOfWeek.orEmpty()
            val ruleStart = rule?.startTime
            val ruleEnd = rule?.endTime
            if (ruleStart != null && ruleEnd != null) {
                timeWindowEnabled = true
                startTimeText = formatMsToTime(ruleStart)
                endTimeText = formatMsToTime(ruleEnd)
            } else {
                timeWindowEnabled = false
                startTimeText = "09:00"
                endTimeText = "17:00"
            }
            initializedKey = templateId
        }
    }

    LaunchedEffect(isSaved) {
        if (isSaved) {
            viewModel.resetSavedState()
            onBack()
        }
    }

    val selectedDays = remember(selectedDaysCsv) {
        selectedDaysCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSortedSet()
    }

    val parsedStartMs = remember(timeWindowEnabled, startTimeText) {
        if (timeWindowEnabled) parseTimeToMs(startTimeText) else null
    }
    val parsedEndMs = remember(timeWindowEnabled, endTimeText) {
        if (timeWindowEnabled) parseTimeToMs(endTimeText) else null
    }
    val isTimeWindowValid = !timeWindowEnabled || (parsedStartMs != null && parsedEndMs != null)
    val canSave = name.isNotBlank() && body.isNotBlank() && isTimeWindowValid

    val originalName = template?.name ?: ""
    val originalBody = template?.body ?: ""
    val originalContact = rule?.contactName.orEmpty()
    val originalDays = rule?.daysOfWeek.orEmpty()
    val originalTimeEnabled = rule?.startTime != null && rule?.endTime != null
    val originalStartText = rule?.startTime?.let(::formatMsToTime) ?: "09:00"
    val originalEndText = rule?.endTime?.let(::formatMsToTime) ?: "17:00"
    val hasUnsavedChanges = if (templateId == null) {
        name.isNotBlank() || body.isNotBlank() || contactFilter.isNotBlank() ||
            selectedDaysCsv.isNotBlank() || timeWindowEnabled
    } else {
        name != originalName || body != originalBody || contactFilter != originalContact ||
            selectedDaysCsv != originalDays || timeWindowEnabled != originalTimeEnabled ||
            (timeWindowEnabled && (startTimeText != originalStartText || endTimeText != originalEndText))
    }

    fun handleBack() {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    fun handleSave() {
        if (!canSave) return
        viewModel.saveTemplate(
            name = name,
            body = body,
            existingId = templateId,
            contactName = contactFilter,
            daysOfWeek = selectedDays.joinToString(","),
            startTime = parsedStartMs,
            endTime = parsedEndMs
        )
    }

    BackHandler { handleBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (templateId == null) "Create Template" else "Edit Template", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = ::handleSave,
                        enabled = canSave
                    ) {
                        Icon(imageVector = Icons.Default.Done, contentDescription = "Save")
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
            Spacer(modifier = Modifier.height(8.dp))

            // Template Name
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text("Template Name") },
                placeholder = { Text("e.g. Driving, Busy, At Work") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Template Body
            OutlinedTextField(
                value = body,
                onValueChange = { if (it.length <= 1000) body = it },
                label = { Text("Message Auto-Reply") },
                placeholder = { Text("Enter your automatic response message...") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                supportingText = {
                    Text(
                        text = "${body.length} characters",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Schedule & Contact Rules Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Schedule & Contact Rules (Optional)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = contactFilter,
                        onValueChange = { if (it.length <= 120) contactFilter = it },
                        label = { Text("Specific Contact (optional)") },
                        placeholder = { Text("Leave blank to match all contacts") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Active Days (leave unselected for every day)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DAY_OPTIONS.forEach { (dayNum, dayLabel) ->
                            val isDaySelected = dayNum in selectedDays
                            FilterChip(
                                selected = isDaySelected,
                                onClick = {
                                    val updated = selectedDays.toMutableSet()
                                    if (isDaySelected) updated.remove(dayNum) else updated.add(dayNum)
                                    selectedDaysCsv = updated.sorted().joinToString(",")
                                },
                                label = { Text(dayLabel) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Active Time Window", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Restrict this rule to a time range (24-hour HH:MM)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = timeWindowEnabled,
                            onCheckedChange = { timeWindowEnabled = it }
                        )
                    }

                    if (timeWindowEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = startTimeText,
                                onValueChange = { if (it.length <= 5) startTimeText = it },
                                label = { Text("Start (HH:MM)") },
                                placeholder = { Text("09:00") },
                                isError = parsedStartMs == null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = endTimeText,
                                onValueChange = { if (it.length <= 5) endTimeText = it },
                                label = { Text("End (HH:MM)") },
                                placeholder = { Text("17:00") },
                                isError = parsedEndMs == null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (!isTimeWindowValid) {
                            Text(
                                text = "Enter valid 24-hour times in HH:MM format (00:00 to 23:59).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // WhatsApp Style Preview Card
            Text(
                text = "WhatsApp Chat Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            WhatsAppPreviewBubble(messageText = body.ifBlank { "[Auto-reply message preview]" })

            Button(
                onClick = ::handleSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save Template")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved edits. Leave without saving?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

private fun formatMsToTime(msFromMidnight: Long): String {
    val totalMinutes = (msFromMidnight / 60_000L).toInt().coerceIn(0, 23 * 60 + 59)
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60
    return String.format(Locale.US, "%02d:%02d", hour, minute)
}

private fun parseTimeToMs(value: String): Long? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return ((hour * 60L) + minute) * 60_000L
}

@Composable
fun WhatsAppPreviewBubble(messageText: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFECE5DD) // WhatsApp chat wallpaper gray
        ),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Received Message (from contact)
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = "Are you on your way?",
                            color = Color.Black,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "10:30 AM",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sent Message (DriveReply Auto-response)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .wrapContentWidth(Alignment.End)
                        .clip(RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp))
                        .background(Color(0xFFE2F9C3)) // WhatsApp light green sent bubble
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = messageText,
                            color = Color.Black,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = "10:30 AM",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            // Double checkmarks emoji or symbol
                            Text(
                                text = "✓✓",
                                color = Color(0xFF34B7F1), // WhatsApp blue ticks
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
