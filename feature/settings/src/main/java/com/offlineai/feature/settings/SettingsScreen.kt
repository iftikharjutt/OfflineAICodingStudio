package com.offlineai.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("App & Inference Settings", style = MaterialTheme.typography.titleLarge)

        HorizontalDivider()

        // Inference Config
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("LLM Inference Configuration", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Text("Context Size: ${settings.contextSize} tokens")
                Slider(
                    value = settings.contextSize.toFloat(),
                    onValueChange = { viewModel.updateContextSize(it.toInt()) },
                    valueRange = 2048f..16384f,
                    steps = 6
                )

                Spacer(Modifier.height(8.dp))

                Text("CPU Threads: ${settings.threadCount}")
                Slider(
                    value = settings.threadCount.toFloat(),
                    onValueChange = { viewModel.updateThreadCount(it.toInt()) },
                    valueRange = 1f..8f,
                    steps = 7
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use GPU (Vulkan)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Offload layers to the phone GPU for faster Qwen 7B replies. " +
                                "Requires a Vulkan-built llama_engine.so. Falls back to CPU if GPU is unavailable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.useGpu,
                        onCheckedChange = { viewModel.toggleUseGpu(it) }
                    )
                }

                if (settings.useGpu) {
                    Spacer(Modifier.height(8.dp))
                    Text("GPU Layers: ${settings.gpuLayers} (99 = all layers)")
                    Slider(
                        value = settings.gpuLayers.toFloat(),
                        onValueChange = { viewModel.updateGpuLayers(it.toInt()) },
                        valueRange = 1f..99f,
                        steps = 0
                    )
                    Text(
                        "Tip: if the app crashes on load, lower layers (e.g. 20–40) or turn GPU off.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Custom AI Persona (System Prompt)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = settings.systemPrompt,
                    onValueChange = { viewModel.updateSystemPrompt(it) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text("System Prompt") }
                )
            }
        }

        // Model Roles Config
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AI Model Roles", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Text("Architect & Coder: Model A", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "By default, the primary model handles planning and code generation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Model B for Review & Debug")
                        Text(
                            "Offload code review and console error fixing to the secondary model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.useModelBForReviewAndDebug,
                        onCheckedChange = { viewModel.toggleUseModelB(it) }
                    )
                }
            }
        }

        // Editor & UI Config
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Editor & Workspace Options", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto Save Before Preview")
                    Switch(
                        checked = settings.autoSaveOnPreview,
                        onCheckedChange = { viewModel.toggleAutoSave(it) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Mode Theme")
                    Switch(
                        checked = settings.isDarkMode,
                        onCheckedChange = { viewModel.toggleDarkMode(it) }
                    )
                }
            }
        }
    }
}
