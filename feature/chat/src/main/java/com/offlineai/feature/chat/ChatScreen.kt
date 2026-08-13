package com.offlineai.feature.chat

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
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val activeMode by viewModel.activeMode.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val diagnosticReport by DiagnosticsManager.currentReport.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = activeMode == AssistantMode.CHAT,
                        onClick = { viewModel.setMode(AssistantMode.CHAT) },
                        label = { Text("Chat") },
                        leadingIcon = { Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(18.dp)) }
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = activeMode == AssistantMode.AGENT,
                        onClick = { viewModel.setMode(AssistantMode.AGENT) },
                        label = { Text("Agent") },
                        leadingIcon = { Icon(Icons.Default.Build, null, Modifier.size(18.dp)) }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showDiagnosticsDialog = true }) {
                        Icon(
                            if (diagnosticReport.lastErrorMessage != null) Icons.Default.Warning else Icons.Default.Info,
                            contentDescription = "Diagnostics",
                            tint = if (diagnosticReport.lastErrorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (selectedModelPath == null) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "No GGUF model loaded. Open Models to select one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageBubble(msg) {
                    if (activeProjectDir != null) viewModel.applyPatchToProject(msg.id, activeProjectDir)
                }
            }
        }

        if (isGenerating) LinearProgressIndicator(Modifier.fillMaxWidth())

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(if (activeMode == AssistantMode.CHAT) "Ask the offline AI…" else "Ask Agent to build or refactor…") },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText, activeProjectDir, selectedModelPath)
                            inputText = ""
                        }
                    },
                    enabled = !isGenerating && inputText.isNotBlank(),
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send prompt")
                }
            }
        }
    }

    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            icon = { Icon(Icons.Default.BugReport, null) },
            title = { Text("Diagnostics") },
            text = {
                Text(
                    DiagnosticsManager.generateFormattedSummary(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(DiagnosticsManager.generateFormattedSummary()))
                }) { Text("Copy report") }
            },
            dismissButton = { TextButton(onClick = { showDiagnosticsDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage, onApplyPatch: () -> Unit) {
    val isUser = message.sender == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 360.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isUser) "You" else "Offline AI",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                    )
                    if (!isUser) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(if (message.mode == AssistantMode.CHAT) "CHAT" else "AGENT") },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(message.text.ifBlank { "…" }, style = MaterialTheme.typography.bodyMedium)

                val patch: ParsedAiResponse? = message.parsedPatch
                if (patch != null && message.mode == AssistantMode.AGENT) {
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Generated operations (${patch.operations.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            patch.operations.forEach { op ->
                                Text("• ${getOpDescription(op)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (message.isApplied) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.width(5.dp))
                                    Text("Applied to project", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                }
                            } else {
                                Button(onClick = onApplyPatch, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Build, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Apply changes")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getOpDescription(op: FileOperation): String = when (op) {
    is FileOperation.CreateFile -> "Create: ${op.path}"
    is FileOperation.ReplaceFile -> "Replace: ${op.path}"
    is FileOperation.ReplaceBlock -> "Update: ${op.path}"
    is FileOperation.DeleteFile -> "Delete: ${op.path}"
    is FileOperation.CreateDirectory -> "Create directory: ${op.path}"
}
