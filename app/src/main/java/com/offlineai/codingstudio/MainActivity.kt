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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Storage
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

    // Keep the native session alive for the lifetime of the selected model/settings.
    // The previous implementation used awaitCancellation() inside this effect, which
    // immediately entered the cleanup path when Compose restarted the effect for a
    // settings/model update and could clear the session while Chat was being used.
    LaunchedEffect(selectedModel?.path, currentSettings.contextSize, currentSettings.threadCount) {
        val model = selectedModel
        if (model == null) {
            chatViewModel.setModelLoadState(null, null)
            return@LaunchedEffect
        }

        chatViewModel.setModelLoadState(model.path, ChatViewModel.ModelLoadState.Loading)
        val result = inferenceEngine.loadModel(
            ModelLoadRequest(
                modelPath = model.path,
                contextSize = currentSettings.contextSize,
                threadCount = currentSettings.threadCount
            )
        )

        result.onSuccess { session ->
            chatViewModel.setModelLoadState(session.modelPath, ChatViewModel.ModelLoadState.Loaded)
            Log.i("OfflineAICodingStudio", "Inference session ready: ${session.sessionId}")
        }.onFailure { error ->
            chatViewModel.setModelLoadState(model.path, ChatViewModel.ModelLoadState.Failed(error.message ?: "Unable to load GGUF model"))
            Log.e("OfflineAICodingStudio", "Unable to load selected GGUF model", error)
        }
    }

    LaunchedEffect(activeProject, activeFilePath) {
        val proj = activeProject
        val path = activeFilePath
        if (proj != null && path != null) editorViewModel.loadFile(File(proj.path), path)
    }

    LaunchedEffect(activeProject) {
        activeProject?.let { previewViewModel.startServerForProject(File(it.path)) }
    }

    val destinations = listOf(
        NavigationDestination.Chat to Icons.Default.Chat,
        NavigationDestination.Projects to Icons.Default.Folder,
        NavigationDestination.Editor to Icons.Default.Code,
        NavigationDestination.Preview to Icons.Default.PlayArrow,
        NavigationDestination.Terminal to Icons.Default.Build,
        NavigationDestination.Models to Icons.Default.Storage,
        NavigationDestination.Settings to SettingsIcon,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedDestination.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                destinations.forEach { (destination, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = destination.title) },
                        label = null,
                        selected = selectedDestination == destination,
                        onClick = { selectedDestination = destination },
                        alwaysShowLabel = false
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp, vertical = 8.dp)
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
