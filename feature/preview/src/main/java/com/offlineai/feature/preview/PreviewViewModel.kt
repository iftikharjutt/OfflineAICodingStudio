package com.offlineai.feature.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlineai.core.filesystem.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class PreviewViewModel(
    private val workspaceManager: WorkspaceManager
) : ViewModel() {

    private var serverManager: LocalPreviewServerManager? = null

    private val _currentUrl = MutableStateFlow("http://127.0.0.1:8080/index.html")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<String>>(emptyList())
    val consoleLogs: StateFlow<List<String>> = _consoleLogs.asStateFlow()

    fun startServerForProject(projectDir: File) {
        if (serverManager == null) {
            serverManager = LocalPreviewServerManager(projectDir, 8080)
            viewModelScope.launch {
                _isServerRunning.value = true
                serverManager?.startServer()
            }
        } else {
            serverManager?.updateProjectDirectory(projectDir)
        }
    }

    fun updateUrl(newUrl: String) {
        _currentUrl.value = newUrl
    }

    fun addConsoleLog(log: String) {
        _consoleLogs.value = _consoleLogs.value + log
    }

    fun reloadPreview() {
        val current = _currentUrl.value
        _currentUrl.value = ""
        _currentUrl.value = current
    }

    override fun onCleared() {
        super.onCleared()
        serverManager?.stopServer()
    }
}
