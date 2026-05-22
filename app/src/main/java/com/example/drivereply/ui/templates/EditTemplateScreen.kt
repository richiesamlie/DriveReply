package com.example.drivereply.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTemplateScreen(
    templateId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditTemplateViewModel = viewModel()
) {
    val template by viewModel.templateState.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    LaunchedEffect(templateId) {
        viewModel.loadTemplate(templateId)
    }

    LaunchedEffect(template) {
        template?.let {
            name = it.name
            body = it.body
        }
    }

    LaunchedEffect(isSaved) {
        if (isSaved) {
            viewModel.resetSavedState()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (templateId == null) "Create Template" else "Edit Template", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveTemplate(name, body, templateId) },
                        enabled = name.isNotBlank() && body.isNotBlank()
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
                onValueChange = { name = it },
                label = { Text("Template Name") },
                placeholder = { Text("e.g. Driving, Busy, At Work") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Template Body
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
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

            // WhatsApp Style Preview Card
            Text(
                text = "WhatsApp Chat Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            WhatsAppPreviewBubble(messageText = body.ifBlank { "[Auto-reply message preview]" })

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
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
