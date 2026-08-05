package com.offlineai.feature.modelsmanager

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlineai.core.filesystem.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class GgufModelInfo(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val isSelected: Boolean = false
)

class ModelsViewModel(
    private val workspaceManager: WorkspaceManager
) : ViewModel() {

    private val _availableModels = MutableStateFlow<List<GgufModelInfo>>(emptyList())
    val availableModels: StateFlow<List<GgufModelInfo>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow<GgufModelInfo?>(null)
    val selectedModel: StateFlow<GgufModelInfo?> = _selectedModel.asStateFlow()

    private val _scanMessage = MutableStateFlow("")
    val scanMessage: StateFlow<String> = _scanMessage.asStateFlow()

    init {
        loadModelsFromWorkspace()
    }

    fun loadModelsFromWorkspace() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val modelsDir: File = workspaceManager.modelsDir
                if (!modelsDir.exists()) modelsDir.mkdirs()

                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                val searchPaths = listOf(
                    modelsDir,
                    publicDownloads,
                    File("/sdcard/Download"),
                    File("/storage/emulated/0/Download"),
                    File("/sdcard/OfflineAICodingStudio/Models"),
                    File("/data/data/com.termux/files/home/OfflineAICodingStudio/Workspace/Models")
                )

                val allFoundModels = mutableListOf<File>()

                for (dir in searchPaths) {
                    try {
                        if (dir.exists() && dir.canRead()) {
                            val ggufFiles = dir.listFiles()?.filter { f -> f.isFile && (f.extension.equals("gguf", ignoreCase = true) || f.extension.equals("bin", ignoreCase = true)) } ?: emptyList()
                            for (file in ggufFiles) {
                                if (allFoundModels.none { it.name == file.name }) {
                                    allFoundModels.add(file)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip inaccessible path
                    }
                }

                val list: List<GgufModelInfo> = allFoundModels.map { f ->
                    GgufModelInfo(
                        name = f.name,
                        path = f.absolutePath,
                        sizeBytes = f.length(),
                        isSelected = (_selectedModel.value?.path == f.absolutePath)
                    )
                }

                _availableModels.value = list
                if (list.isNotEmpty() && (_selectedModel.value == null || list.none { it.isSelected })) {
                    val defaultModel = list.first().copy(isSelected = true)
                    _selectedModel.value = defaultModel
                    _availableModels.value = list.map { it.copy(isSelected = (it.path == defaultModel.path)) }
                }

                _scanMessage.value = if (list.isNotEmpty()) {
                    "Found ${list.size} model(s)! Selected: ${list.firstOrNull { it.isSelected }?.name ?: list.first().name}"
                } else {
                    "Scanned 6 storage locations. Please grant Storage Permission or place .gguf in Downloads."
                }
            }
        }
    }

    fun selectModel(model: GgufModelInfo) {
        _selectedModel.value = model.copy(isSelected = true)
        _availableModels.value = _availableModels.value.map {
            it.copy(isSelected = (it.path == model.path))
        }
    }
}
