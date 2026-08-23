package com.offlineai.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlineai.ai.prompting.ParsedAiResponse
import com.offlineai.ai.runtime.DiagnosticsManager
import com.offlineai.core.models.AssistantMode
import com.offlineai.core.models.FileOperation
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    activeProjectDir: File?,
    selectedModelPath: String? = null,
    systemPrompt: String? = null,
    onProjectCreated: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val activeMode by viewModel.activeMode.collectAsState()
    val isMemoryEnabled by viewModel.isMemoryEnabled.collectAsState()
    val isDualModeEnabled by viewModel.isDualModeEnabled.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val diagnosticReport by DiagnosticsManager.currentReport.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Mode:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = activeMode == AssistantMode.CHAT,
                        onClick = { viewModel.setMode(AssistantMode.CHAT) },
                        label = { Text("Chat Mode") },
                        leadingIcon = { Icon(Icons.Default.ChatBubble, contentDescription = null, tint = Color(0xFF2196F3)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF2196F3).copy(alpha = 0.2f), selectedLabelColor = Color(0xFF2196F3))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = activeMode == AssistantMode.AGENT,
                        onClick = { viewModel.setMode(AssistantMode.AGENT) },
                        label = { Text("Agent Mode") },
                        leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFFFF9800)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFF9800).copy(alpha = 0.2f), selectedLabelColor = Color(0xFFFF9800))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = activeMode == AssistantMode.GAME_STUDIO,
                        onClick = { viewModel.setMode(AssistantMode.GAME_STUDIO) },
                        label = { Text("Game Studio") },
                        leadingIcon = { Icon(Icons.Default.VideogameAsset, contentDescription = null, tint = Color(0xFF9C27B0)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF9C27B0).copy(alpha = 0.2f), selectedLabelColor = Color(0xFF9C27B0))
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Dual Mode", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 4.dp))
                    Switch(checked = isDualModeEnabled, onCheckedChange = { viewModel.toggleDualMode(it) }, modifier = Modifier.scale(0.8f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Memory", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 4.dp))
                    Switch(checked = isMemoryEnabled, onCheckedChange = { viewModel.toggleMemory(it) }, modifier = Modifier.scale(0.8f))
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showDiagnosticsDialog = true }) {
                        Icon(imageVector = if (diagnosticReport.lastErrorMessage != null) Icons.Default.Warning else Icons.Default.Info, contentDescription = "Diagnostics Report", tint = if (diagnosticReport.lastErrorMessage != null) Color.Red else MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (selectedModelPath == null) {
            Surface(color = Color(0xFFFFF3CD), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF856404), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "No GGUF Model loaded. Open 'Models' tab to select/load a model.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF856404))
                }
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                ChatMessageBubble(
                    message = msg,
                    onApplyPatch = { if (activeProjectDir != null) viewModel.applyPatchToProject(msg.id, activeProjectDir) },
                    onCreateProject = { viewModel.createProjectFromChatCode(msg.id) { onProjectCreated() } }
                )
            }
        }

        if (isGenerating) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Generating… (auto-stops if the model repeats lines)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { viewModel.stopGeneration() }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop generation", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }
        }

        HorizontalDivider()

        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            if (activeMode == AssistantMode.CHAT) "Ask AI a question or request guidance..."
                            else if (activeMode == AssistantMode.GAME_STUDIO) "Create a game (e.g., 'Create a retro snake game')..."
                            else "Ask Agent to build, edit, or refactor code..."
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    maxLines = 3,
                    enabled = !isGenerating
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isGenerating) {
                    FilledIconButton(
                        onClick = { viewModel.stopGeneration() },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop generation")
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText, activeProjectDir, selectedModelPath, systemPrompt)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Prompt",
                            tint = if (inputText.isNotBlank()) {
                                if (activeMode == AssistantMode.CHAT) Color(0xFF2196F3) else Color(0xFFFF9800)
                            } else Color.Gray
                        )
                    }
                }
            }
        }
    }

    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("System Diagnostics & Error Report")
                }
            },
            text = {
                val reportText = DiagnosticsManager.generateFormattedSummary()
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(text = reportText, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val reportText = DiagnosticsManager.generateFormattedSummary()
                    clipboardManager.setText(AnnotatedString(reportText))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Full Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiagnosticsDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onApplyPatch: () -> Unit,
    onCreateProject: () -> Unit
) {
    val isUser = message.sender == "user"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isUser) "You" else "AI Assistant",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isUser) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (message.mode) {
                                AssistantMode.CHAT -> Color(0xFF2196F3)
                                AssistantMode.AGENT -> Color(0xFFFF9800)
                                AssistantMode.GAME_STUDIO -> Color(0xFF9C27B0)
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(text = message.mode.name, style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (message.textB != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Model A", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(text = message.text.ifBlank { "..." }, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp, letterSpacing = 0.25.sp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Model B", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(text = message.textB.ifBlank { "..." }, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp, letterSpacing = 0.25.sp))
                        }
                    }
                } else {
                    Text(text = message.text.ifBlank { "..." }, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp, letterSpacing = 0.25.sp), modifier = Modifier.padding(bottom = 4.dp))
                }
                if (!isUser && message.text.contains("```") && !message.text.contains("✅")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onCreateProject, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Project from Code")
                    }
                }
                val patch: ParsedAiResponse? = message.parsedPatch
                if (patch != null && message.mode == AssistantMode.AGENT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = "Generated Operations (${patch.operations.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            patch.operations.forEach { op ->
                                Text(text = "• ${getOpDescription(op)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (message.isApplied) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Applied to Project", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                }
                            } else {
                                Button(onClick = onApplyPatch, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) {
                                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Apply Changes to Project")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getOpDescription(op: FileOperation): String {
    return when (op) {
        is FileOperation.CreateFile -> "Create File: ${op.path}"
        is FileOperation.ReplaceFile -> "Replace File: ${op.path}"
        is FileOperation.ReplaceBlock -> "Update Block: ${op.path}"
        is FileOperation.DeleteFile -> "Delete File: ${op.path}"
        is FileOperation.CreateDirectory -> "Create Directory: ${op.path}"
    }
}
