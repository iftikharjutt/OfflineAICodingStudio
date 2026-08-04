package com.offlineai.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
