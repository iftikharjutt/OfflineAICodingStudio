package com.offlineai.feature.projects

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
import androidx.compose.ui.unit.dp
import com.offlineai.core.filesystem.FileTreeNode
import com.offlineai.core.models.ProjectModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.projects.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val fileTree by viewModel.fileTree.collectAsState()
    val activeFilePath by viewModel.activeFilePath.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showProjectDropdown by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Projects Header Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Workspace Projects",
                style = MaterialTheme.typography.titleLarge
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New Project")
                    Spacer(Modifier.width(4.dp))
                    Text("New Project")
                }
                OutlinedButton(onClick = { viewModel.createProjectFromTemplate("Snake") }) {
                    Icon(Icons.Default.Games, contentDescription = "Template")
                    Spacer(Modifier.width(4.dp))
                    Text("Snake Template")
                }

                if (activeProject != null) {
                    OutlinedButton(onClick = { showCreateFileDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New File")
                        Spacer(Modifier.width(4.dp))
                        Text("New File")
                    }
                    
                    OutlinedButton(onClick = { 
                        viewModel.exportActiveProject { file -> 
                            // Result is saved to Downloads directory
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Export Zip")
                        Spacer(Modifier.width(4.dp))
                        Text("Export")
                    }
                }
            }
        }

        HorizontalDivider()

        // Projects Selector
        if (projects.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active: ",
                    style = MaterialTheme.typography.labelLarge
                )
                Box {
                    AssistChip(
                        onClick = { showProjectDropdown = true },
                        label = { Text(activeProject?.name ?: "Select Project") },
                        leadingIcon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(
                        expanded = showProjectDropdown,
                        onDismissRequest = { showProjectDropdown = false }
                    ) {
                        projects.forEach { project ->
                            DropdownMenuItem(
                                text = { Text(project.name) },
                                onClick = {
                                    viewModel.selectProject(project)
                                    showProjectDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No projects in workspace yet.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showCreateDialog = true }) {
                        Text("Create Your First Project")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.createProjectFromTemplate("Snake") }) {
                        Text("Or start from a Snake Template")
                    }
                }
            }
        }

        // File Tree View
        Text(
            text = "File Explorer",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )

        fileTree?.let { rootNode ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
            ) {
                items(rootNode.children) { node ->
                    FileTreeNodeItem(
                        node = node,
                        activeFilePath = activeFilePath,
                        onFileClick = { path -> viewModel.openFile(path) },
                        onDeleteClick = { path -> viewModel.deleteFileInActiveProject(path) }
                    )
                }
            }
        }
    }

    // Create Project Dialog
    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Web Project") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Project Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.createNewProject(nameInput)
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create File Dialog
    if (showCreateFileDialog) {
        var fileNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create New File") },
            text = {
                OutlinedTextField(
                    value = fileNameInput,
                    onValueChange = { fileNameInput = it },
                    label = { Text("File Path (e.g. app.js or css/main.css)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileNameInput.isNotBlank()) {
                            viewModel.createNewFileInActiveProject(fileNameInput)
                            showCreateFileDialog = false
                        }
                    }
                ) {
                    Text("Create File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FileTreeNodeItem(
    node: FileTreeNode,
    activeFilePath: String?,
    onFileClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    depth: Int = 0
) {
    var expanded by remember { mutableStateOf(true) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDirectory) {
                        expanded = !expanded
                    } else {
                        onFileClick(node.path)
                    }
                }
                .padding(vertical = 6.dp, horizontal = (depth * 16).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        node.isDirectory && expanded -> Icons.Default.FolderOpen
                        node.isDirectory -> Icons.Default.Folder
                        node.extension in listOf("html", "htm") -> Icons.Default.Html
                        node.extension in listOf("css", "scss") -> Icons.Default.Style
                        node.extension in listOf("js", "ts") -> Icons.Default.Javascript
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = if (node.path == activeFilePath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (node.path == activeFilePath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = { onDeleteClick(node.path) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (node.isDirectory && expanded) {
            node.children.forEach { child ->
                FileTreeNodeItem(
                    node = child,
                    activeFilePath = activeFilePath,
                    onFileClick = onFileClick,
                    onDeleteClick = onDeleteClick,
                    depth = depth + 1
                )
            }
        }
    }
}
