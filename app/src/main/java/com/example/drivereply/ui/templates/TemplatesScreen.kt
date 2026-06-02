package com.example.drivereply.ui.templates

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drivereply.data.MessageTemplate
import com.example.drivereply.data.TemplateRule

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplatesScreen(
    onAddEditTemplate: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplatesViewModel = viewModel()
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val templateRules by viewModel.templateRules.collectAsStateWithLifecycle()
    var templateToDelete by remember { mutableStateOf<MessageTemplate?>(null) }
    val rulesByTemplate = remember(templateRules) { templateRules.groupBy { it.templateId } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-Reply Templates", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddEditTemplate(null) },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Template")
            }
        },
        modifier = modifier
    ) { paddingValues ->
        if (templates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "💬",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Templates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Create custom messages to automatically send to WhatsApp senders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { onAddEditTemplate(null) }) {
                        Text("Create My First Template")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(templates, key = { it.id }) { template ->
                    val isSelected = template.isActive
                    val summaries = buildRuleSummary(rulesByTemplate[template.id].orEmpty())

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        onClick = { viewModel.setActiveTemplate(template.id) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setActiveTemplate(template.id) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = template.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Active",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onAddEditTemplate(template.id) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit"
                                        )
                                    }
                                    IconButton(
                                        onClick = { templateToDelete = template }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = template.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (summaries.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    summaries.forEach { summary ->
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(summary, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (templateToDelete != null) {
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text("Delete Template") },
            text = { Text("Are you sure you want to delete the template \"${templateToDelete?.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        templateToDelete?.let { viewModel.deleteTemplate(it) }
                        templateToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun buildRuleSummary(rules: List<TemplateRule>): List<String> {
    if (rules.isEmpty()) return listOf("Any time", "All contacts")

    val summaries = mutableListOf<String>()

    val dayRule = rules.firstOrNull { !it.daysOfWeek.isNullOrBlank() }?.daysOfWeek
    if (!dayRule.isNullOrBlank()) {
        summaries.add(formatDays(dayRule))
    }

    val timeRule = rules.firstOrNull { it.startTime != null && it.endTime != null }
    if (timeRule != null) {
        summaries.add("${formatTime(timeRule.startTime!!)}-${formatTime(timeRule.endTime!!)}")
    }

    val contacts = rules.mapNotNull { it.contactName?.takeIf { name -> name.isNotBlank() } }.distinct()
    summaries.add(if (contacts.isEmpty()) "All contacts" else "${contacts.size} contact(s)")

    return summaries
}

private fun formatDays(daysValue: String): String {
    val dayMap = mapOf(
        1 to "Mon",
        2 to "Tue",
        3 to "Wed",
        4 to "Thu",
        5 to "Fri",
        6 to "Sat",
        7 to "Sun"
    )
    val labels = daysValue.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .mapNotNull { dayMap[it] }

    return if (labels.isEmpty()) "Any day" else labels.joinToString(", ")
}

private fun formatTime(msFromMidnight: Long): String {
    val totalMinutes = (msFromMidnight / 60_000L).toInt()
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60
    return String.format("%02d:%02d", hour, minute)
}
