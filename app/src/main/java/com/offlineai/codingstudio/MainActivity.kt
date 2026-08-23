package com.offlineai.codingstudio

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlineai.ai.runtime.DualModelManager
import com.offlineai.ai.runtime.LlamaEngineNative
import com.offlineai.ai.runtime.LlamaInferenceEngine
import com.offlineai.core.datastore.settingsDataStore
import com.offlineai.core.filesystem.WorkspaceManager
import com.offlineai.core.navigation.NavigationDestination
import com.offlineai.core.ui.OfflineAITheme
import com.offlineai.core.ui.PrimaryCyan
import com.offlineai.feature.chat.ChatScreen
import com.offlineai.feature.chat.ChatViewModel
import com.offlineai.feature.editor.EditorScreen
import com.offlineai.feature.editor.EditorViewModel
import com.offlineai.feature.modelsmanager.ModelsScreen
import com.offlineai.feature.modelsmanager.ModelsViewModel
import com.offlineai.feature.preview.PreviewScreen
import com.offlineai.feature.preview.PreviewViewModel
import com.offlineai.feature.projects.ProjectsScreen
import com.offlineai.feature.projects.ProjectsViewModel
import com.offlineai.feature.settings.SettingsScreen
import com.offlineai.feature.settings.SettingsViewModel
import com.offlineai.feature.terminal.TerminalScreen
import com.offlineai.feature.terminal.TerminalViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request standard runtime permissions gracefully if needed on Android 10 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            try {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            } catch (e: Exception) {
                // Ignore permission request failures
            }
        }

        val workspaceManager = WorkspaceManager(applicationContext.filesDir)
        val inferenceEngine = LlamaEngineNative()
        val dualModelManager = DualModelManager(applicationContext, inferenceEngine)
        val projectsViewModel = ProjectsViewModel(workspaceManager)
        val editorViewModel = EditorViewModel(workspaceManager, inferenceEngine)
        val previewViewModel = PreviewViewModel(workspaceManager, dualModelManager)
        val chatViewModel = ChatViewModel(workspaceManager, dualModelManager)
        val terminalViewModel = TerminalViewModel()
        val modelsViewModel = ModelsViewModel(workspaceManager)
        val settingsViewModel = SettingsViewModel(applicationContext.settingsDataStore)

        setContent {
            OfflineAITheme {
                AppShell(
                    projectsViewModel = projectsViewModel,
                    editorViewModel = editorViewModel,
                    previewViewModel = previewViewModel,
                    chatViewModel = chatViewModel,
                    terminalViewModel = terminalViewModel,
                    modelsViewModel = modelsViewModel,
                    settingsViewModel = settingsViewModel,
                    inferenceEngine = inferenceEngine,
                    dualModelManager = dualModelManager
                )
            }
        }
    }
}

private data class NavItem(
    val destination: NavigationDestination,
    val icon: ImageVector,
    val shortLabel: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    projectsViewModel: ProjectsViewModel,
    editorViewModel: EditorViewModel,
    previewViewModel: PreviewViewModel,
    chatViewModel: ChatViewModel,
    terminalViewModel: TerminalViewModel,
    modelsViewModel: ModelsViewModel,
    settingsViewModel: SettingsViewModel,
    inferenceEngine: LlamaInferenceEngine,
    dualModelManager: DualModelManager
) {
    var selectedDestination by remember { mutableStateOf<NavigationDestination>(NavigationDestination.Chat) }
    var isPreviewFullscreen by remember { mutableStateOf(false) }
    val activeProject by projectsViewModel.activeProject.collectAsState()
    val activeFilePath by projectsViewModel.activeFilePath.collectAsState()

    val selectedModelA by modelsViewModel.selectedModelA.collectAsState()
    val selectedModelB by modelsViewModel.selectedModelB.collectAsState()
    val currentSettings by settingsViewModel.settings.collectAsState()

    LaunchedEffect(selectedModelA, currentSettings) {
        selectedModelA?.let { model ->
            val layers = if (currentSettings.useGpu) currentSettings.gpuLayers else 0
            dualModelManager.loadModelA(
                modelPath = model.path,
                contextSize = currentSettings.contextSize,
                gpuLayers = layers
            )
        }
    }

    LaunchedEffect(selectedModelB, currentSettings) {
        selectedModelB?.let { model ->
            val layers = if (currentSettings.useGpu) currentSettings.gpuLayers else 0
            dualModelManager.loadModelB(
                modelPath = model.path,
                contextSize = currentSettings.contextSize,
                gpuLayers = layers
            )
        }
    }

    LaunchedEffect(activeProject, activeFilePath) {
        val proj = activeProject
        val path = activeFilePath
        if (proj != null && path != null) {
            editorViewModel.loadFile(File(proj.path), path)
        }
    }

    LaunchedEffect(activeProject) {
        val proj = activeProject
        if (proj != null) {
            previewViewModel.startServerForProject(File(proj.path))
        }
    }

    val destinations = listOf(
        NavItem(NavigationDestination.Chat, Icons.Default.Chat, "Chat"),
        NavItem(NavigationDestination.Projects, Icons.Default.Folder, "Files"),
        NavItem(NavigationDestination.Editor, Icons.Default.Code, "Code"),
        NavItem(NavigationDestination.Preview, Icons.Default.PlayArrow, "Play"),
        NavItem(NavigationDestination.Terminal, Icons.Default.Terminal, "Term"),
        NavItem(NavigationDestination.Models, Icons.Default.Memory, "LLM"),
        NavItem(NavigationDestination.Settings, Icons.Default.SettingsIcon, "Set"),
    )

    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colorScheme.background,
        contentColor = colorScheme.onBackground,
        topBar = {
            if (!isPreviewFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "OFFLINE STUDIO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.2.sp,
                                    color = colorScheme.onSurfaceVariant,
                                ),
                            )
                            Text(
                                text = selectedDestination.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    },
                    actions = {
                        Surface(
                            color = colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Text(
                                text = "OS",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryCyan,
                                ),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.surface,
                        titleContentColor = colorScheme.onSurface,
                        actionIconContentColor = colorScheme.onSurfaceVariant,
                    ),
                )
            }
        },
        bottomBar = {
            if (!isPreviewFullscreen) {
                NavigationBar(
                    containerColor = colorScheme.surface,
                    contentColor = colorScheme.onSurfaceVariant,
                    tonalElevation = 0.dp,
                ) {
                    destinations.forEach { item ->
                        val selected = selectedDestination == item.destination
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    item.icon,
                                    contentDescription = item.destination.title,
                                )
                            },
                            label = {
                                Text(
                                    text = item.shortLabel,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            selected = selected,
                            onClick = { selectedDestination = item.destination },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryCyan,
                                selectedTextColor = PrimaryCyan,
                                indicatorColor = colorScheme.surfaceVariant,
                                unselectedIconColor = colorScheme.onSurfaceVariant,
                                unselectedTextColor = colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(paddingValues)
                .padding(if (isPreviewFullscreen) 0.dp else 8.dp)
        ) {
            when (selectedDestination) {
                NavigationDestination.Chat -> ChatScreen(
                    viewModel = chatViewModel,
                    activeProjectDir = activeProject?.let { File(it.path) },
                    selectedModelPath = selectedModelA?.path,
                    systemPrompt = currentSettings.systemPrompt,
                    onProjectCreated = { projectsViewModel.loadProjectsFromWorkspace() }
                )
                NavigationDestination.Projects -> ProjectsScreen(viewModel = projectsViewModel)
                NavigationDestination.Editor -> EditorScreen(
                    viewModel = editorViewModel,
                    selectedModelPath = selectedModelA?.path
                )
                NavigationDestination.Preview -> PreviewScreen(
                    viewModel = previewViewModel,
                    isFullscreen = isPreviewFullscreen,
                    onToggleFullscreen = { isPreviewFullscreen = !isPreviewFullscreen }
                )
                NavigationDestination.Terminal -> TerminalScreen(
                    viewModel = terminalViewModel,
                    workingDir = activeProject?.let { File(it.path) }
                )
                NavigationDestination.Models -> ModelsScreen(viewModel = modelsViewModel)
                NavigationDestination.Settings -> SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
