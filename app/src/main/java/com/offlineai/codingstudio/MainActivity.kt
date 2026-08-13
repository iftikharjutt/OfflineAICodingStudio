package com.offlineai.codingstudio

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.offlineai.ai.runtime.LlamaEngineNative
import com.offlineai.ai.runtime.LlamaInferenceEngine
import com.offlineai.ai.runtime.ModelLoadRequest
import com.offlineai.core.datastore.settingsDataStore
import com.offlineai.core.filesystem.WorkspaceManager
import com.offlineai.core.navigation.NavigationDestination
import com.offlineai.core.ui.OfflineAITheme
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

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }

        val workspaceManager = WorkspaceManager(applicationContext.filesDir)
        val inferenceEngine = LlamaEngineNative()
        val projectsViewModel = ProjectsViewModel(workspaceManager)
        val editorViewModel = EditorViewModel(workspaceManager)
        val previewViewModel = PreviewViewModel(workspaceManager)
        val chatViewModel = ChatViewModel(workspaceManager, inferenceEngine)
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
                    inferenceEngine = inferenceEngine
                )
            }
        }
    }
}

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
    inferenceEngine: LlamaInferenceEngine
) {
    var selectedDestination by remember { mutableStateOf<NavigationDestination>(NavigationDestination.Chat) }
    val activeProject by projectsViewModel.activeProject.collectAsState()
    val activeFilePath by projectsViewModel.activeFilePath.collectAsState()
    val selectedModel by modelsViewModel.selectedModel.collectAsState()
    val currentSettings by settingsViewModel.settings.collectAsState()

    // Keep exactly one native model session alive. When the model/settings change,
    // the previous session is unloaded before the new one is loaded.
    LaunchedEffect(selectedModel?.path, currentSettings.contextSize, currentSettings.threadCount) {
        val model = selectedModel
        if (model == null) {
            chatViewModel.activeSessionModelPath = null
            return@LaunchedEffect
        }

        val result = inferenceEngine.loadModel(
            ModelLoadRequest(
                modelPath = model.path,
                contextSize = currentSettings.contextSize,
                threadCount = currentSettings.threadCount
            )
        )

        result.onSuccess { session ->
            chatViewModel.activeSessionModelPath = session.modelPath
            Log.i("OfflineAICodingStudio", "Inference session ready: ${session.sessionId}")
        }.onFailure { error ->
            chatViewModel.activeSessionModelPath = null
            Log.e("OfflineAICodingStudio", "Unable to load selected GGUF model", error)
        }

        try {
            awaitCancellation()
        } finally {
            result.getOrNull()?.let { session ->
                inferenceEngine.unloadModel(session.sessionId)
            }
            if (chatViewModel.activeSessionModelPath == model.path) {
                chatViewModel.activeSessionModelPath = null
            }
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
        activeProject?.let { previewViewModel.startServerForProject(File(it.path)) }
    }

    val destinations = listOf(
        NavigationDestination.Chat to Icons.Default.Code,
        NavigationDestination.Projects to Icons.Default.Folder,
        NavigationDestination.Editor to Icons.Default.Code,
        NavigationDestination.Preview to Icons.Default.PlayArrow,
        NavigationDestination.Terminal to Icons.Default.Build,
        NavigationDestination.Models to Icons.Default.Build,
        NavigationDestination.Settings to Icons.Default.SettingsIcon,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedDestination.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        bottomBar = {
            NavigationBar {
                destinations.forEach { (destination, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = destination.title) },
                        label = { Text(destination.title) },
                        selected = selectedDestination == destination,
                        onClick = { selectedDestination = destination }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (selectedDestination) {
                NavigationDestination.Chat -> ChatScreen(
                    viewModel = chatViewModel,
                    activeProjectDir = activeProject?.let { File(it.path) },
                    selectedModelPath = selectedModel?.path
                )
                NavigationDestination.Projects -> ProjectsScreen(viewModel = projectsViewModel)
                NavigationDestination.Editor -> EditorScreen(viewModel = editorViewModel)
                NavigationDestination.Preview -> PreviewScreen(viewModel = previewViewModel)
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
