package com.offlineai.feature.modelsmanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun ModelsScreen(
    viewModel: ModelsViewModel,
    modifier: Modifier = Modifier
) {
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModelA by viewModel.selectedModelA.collectAsState()
    val selectedModelB by viewModel.selectedModelB.collectAsState()
    val scanMessage by viewModel.scanMessage.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Local GGUF Models", style = MaterialTheme.typography.titleMedium)
                }

                Button(onClick = { viewModel.loadModelsFromWorkspace() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan Storage")
                    Spacer(Modifier.width(4.dp))
                    Text("Scan Models")
                }
            }
        }

        if (scanMessage.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = scanMessage,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        HorizontalDivider()

        if (availableModels.isEmpty()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No GGUF models detected", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Scanned model locations:\n" +
                        "1. /sdcard/Download/\n" +
                        "2. Internal App Storage\n\n" +
                        "Place your .gguf model in Downloads and tap 'Scan Models'.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Text("Grant Storage Access")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableModels) { model ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (model.isSelectedA || model.isSelectedB) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Size: ${model.sizeBytes / (1024 * 1024)} MB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Path: ${model.path}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Row {
                                        Button(
                                            onClick = { viewModel.selectModelA(model) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (model.isSelectedA) MaterialTheme.colorScheme.primary else Color.Gray
                                            ),
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Text("Set A")
                                        }
                                        Button(
                                            onClick = { viewModel.selectModelB(model) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (model.isSelectedB) MaterialTheme.colorScheme.secondary else Color.Gray
                                            )
                                        ) {
                                            Text("Set B")
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}
