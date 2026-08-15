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
    val isSelectedA: Boolean = false,
    val isSelectedB: Boolean = false
)

class ModelsViewModel(
    private val workspaceManager: WorkspaceManager
) : ViewModel() {

    private val _availableModels = MutableStateFlow<List<GgufModelInfo>>(emptyList())
    val availableModels: StateFlow<List<GgufModelInfo>> = _availableModels.asStateFlow()

    private val _selectedModelA = MutableStateFlow<GgufModelInfo?>(null)
    val selectedModelA: StateFlow<GgufModelInfo?> = _selectedModelA.asStateFlow()

    private val _selectedModelB = MutableStateFlow<GgufModelInfo?>(null)
    val selectedModelB: StateFlow<GgufModelInfo?> = _selectedModelB.asStateFlow()

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
                        isSelectedA = (_selectedModelA.value?.path == f.absolutePath),
                        isSelectedB = (_selectedModelB.value?.path == f.absolutePath)
                    )
                }

                _availableModels.value = list
                if (list.isNotEmpty() && (_selectedModelA.value == null || list.none { it.isSelectedA })) {
                    val defaultModel = list.first().copy(isSelectedA = true)
                    _selectedModelA.value = defaultModel
                    _availableModels.value = list.map { it.copy(isSelectedA = (it.path == defaultModel.path)) }
                }

                _scanMessage.value = if (list.isNotEmpty()) {
                    "Found ${list.size} model(s)! Selected A: ${list.firstOrNull { it.isSelectedA }?.name ?: list.first().name}"
                } else {
                    "Scanned 6 storage locations. Please grant Storage Permission or place .gguf in Downloads."
                }
            }
        }
    }

    fun selectModelA(model: GgufModelInfo) {
        _selectedModelA.value = model.copy(isSelectedA = true)
        updateList()
    }

    fun selectModelB(model: GgufModelInfo) {
        _selectedModelB.value = model.copy(isSelectedB = true)
        updateList()
    }

    private fun updateList() {
        val aPath = _selectedModelA.value?.path
        val bPath = _selectedModelB.value?.path
        _availableModels.value = _availableModels.value.map {
            it.copy(
                isSelectedA = (it.path == aPath),
                isSelectedB = (it.path == bPath)
            )
        }
    }
}
