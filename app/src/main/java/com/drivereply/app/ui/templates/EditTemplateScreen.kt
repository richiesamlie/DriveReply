package com.drivereply.app.ui.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
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
import com.drivereply.app.data.TemplateRule
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTemplateScreen(
    templateId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditTemplateViewModel = viewModel()
) {
    val template by viewModel.templateState.collectAsStateWithLifecycle()
    val rules by viewModel.rulesState.collectAsStateWithLifecycle()
    val initialRules by viewModel.initialRules.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()

    var initializedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<TemplateRule?>(null) }
    var showRuleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(templateId) {
        viewModel.loadTemplate(templateId)
        if (templateId == null && initializedKey != "__new__") {
            name = ""
            body = ""
            initializedKey = "__new__"
        }
    }

    LaunchedEffect(template, templateId) {
        val loaded = template
        if (templateId != null && loaded != null && loaded.id == templateId && initializedKey != templateId) {
            name = loaded.name
            body = loaded.body
            initializedKey = templateId
        }
    }

    LaunchedEffect(isSaved) {
        if (isSaved) {
            viewModel.resetSavedState()
            onBack()
        }
    }

    val canSave = name.isNotBlank() && body.isNotBlank()

    val originalName = template?.name ?: ""
    val originalBody = template?.body ?: ""
    val hasUnsavedChanges = if (templateId == null) {
        name.isNotBlank() || body.isNotBlank() || rules.isNotEmpty()
    } else {
        name != originalName || body != originalBody || rules != initialRules
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
            existingId = templateId
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Routing & Schedule Rules",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Optional: pick this template for specific contacts or time windows.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                editingRule = null
                                showRuleDialog = true
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Rule")
                        }
                    }

                    if (rules.isEmpty()) {
                        Text(
                            text = "No rules configured. This template applies when chosen as the active default.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rules.forEach { rule ->
                                RuleItemRow(
                                    rule = rule,
                                    onEdit = {
                                        editingRule = rule
                                        showRuleDialog = true
                                    },
                                    onDelete = { viewModel.deleteRuleDraft(rule.id) }
                                )
                            }
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

    if (showRuleDialog) {
        TemplateRuleEditorDialog(
            initialRule = editingRule,
            onDismiss = { showRuleDialog = false },
            onApply = { contactName, selectedDays, startMs, endMs ->
                viewModel.saveRuleDraft(
                    ruleId = editingRule?.id,
                    existingId = templateId,
                    contactName = contactName,
                    selectedDays = selectedDays,
                    startTimeMs = startMs,
                    endTimeMs = endMs
                )
                showRuleDialog = false
            }
        )
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

@Composable
private fun RuleItemRow(
    rule: TemplateRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val contactLabel = rule.contactName?.takeIf { it.isNotBlank() } ?: "All contacts"
    val daysLabel = formatRuleDays(rule.daysOfWeek)
    val timeLabel = if (rule.startTime != null && rule.endTime != null) {
        "${formatMsToTime(rule.startTime)} - ${formatMsToTime(rule.endTime)}"
    } else {
        "All day"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = contactLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$daysLabel • $timeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Rule")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Rule",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TemplateRuleEditorDialog(
    initialRule: TemplateRule?,
    onDismiss: () -> Unit,
    onApply: (contactName: String?, selectedDays: Set<Int>, startMs: Long?, endMs: Long?) -> Unit
) {
    var contactFilter by remember(initialRule) { mutableStateOf(initialRule?.contactName.orEmpty()) }
    var selectedDays by remember(initialRule) {
        mutableStateOf(
            initialRule?.daysOfWeek
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it in 1..7 }
                ?.toSet()
                ?: emptySet()
        )
    }
    var timeWindowEnabled by remember(initialRule) {
        mutableStateOf(initialRule?.startTime != null && initialRule.endTime != null)
    }
    var startMs by remember(initialRule) { mutableLongStateOf(initialRule?.startTime ?: 9 * 3_600_000L) }
    var endMs by remember(initialRule) { mutableLongStateOf(initialRule?.endTime ?: 17 * 3_600_000L) }
    var pickingStart by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule == null) "Add Rule" else "Edit Rule", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = contactFilter,
                    onValueChange = { if (it.length <= 120) contactFilter = it },
                    label = { Text("Specific Contact (Optional)") },
                    placeholder = { Text("Leave blank for all contacts") },
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DAY_OPTIONS.forEach { (dayNum, dayLabel) ->
                        val isDaySelected = dayNum in selectedDays
                        FilterChip(
                            selected = isDaySelected,
                            onClick = {
                                selectedDays = if (isDaySelected) selectedDays - dayNum else selectedDays + dayNum
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
                    Text("Active Time Window", fontWeight = FontWeight.SemiBold)
                    Switch(checked = timeWindowEnabled, onCheckedChange = { timeWindowEnabled = it })
                }

                if (timeWindowEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pickingStart = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("From ${formatMsToTime(startMs)}")
                        }
                        OutlinedButton(
                            onClick = { pickingStart = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("To ${formatMsToTime(endMs)}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        contactFilter.trim().takeIf { it.isNotEmpty() },
                        selectedDays,
                        if (timeWindowEnabled) startMs else null,
                        if (timeWindowEnabled) endMs else null
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    pickingStart?.let { isStart ->
        val currentMs = if (isStart) startMs else endMs
        val totalMinutes = (currentMs / 60_000L).toInt().coerceIn(0, 23 * 60 + 59)
        val pickerState = rememberTimePickerState(
            initialHour = totalMinutes / 60,
            initialMinute = totalMinutes % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickingStart = null },
            title = { Text(if (isStart) "Start Time" else "End Time") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ms = ((pickerState.hour * 60L) + pickerState.minute) * 60_000L
                        if (isStart) startMs = ms else endMs = ms
                        pickingStart = null
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatRuleDays(daysValue: String?): String {
    if (daysValue.isNullOrBlank()) return "Every day"
    val dayMap = DAY_OPTIONS.toMap()
    val labels = daysValue.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .mapNotNull { dayMap[it] }
    return if (labels.isEmpty()) "Every day" else labels.joinToString(", ")
}

private fun formatMsToTime(msFromMidnight: Long): String {
    val totalMinutes = (msFromMidnight / 60_000L).toInt().coerceIn(0, 23 * 60 + 59)
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60
    return String.format(Locale.US, "%02d:%02d", hour, minute)
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
