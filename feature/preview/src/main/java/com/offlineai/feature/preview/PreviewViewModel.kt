package com.offlineai.feature.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlineai.core.filesystem.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

import com.offlineai.ai.runtime.DualModelManager

class PreviewViewModel(
    private val workspaceManager: WorkspaceManager,
    private val dualModelManager: DualModelManager
) : ViewModel() {

    private var serverManager: LocalPreviewServerManager? = null

    private val _currentUrl = MutableStateFlow("http://127.0.0.1/index.html")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _serverStatusText = MutableStateFlow("Game Server: STOPPED")
    val serverStatusText: StateFlow<String> = _serverStatusText.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<String>>(emptyList())
    val consoleLogs: StateFlow<List<String>> = _consoleLogs.asStateFlow()

    private var autoFixAttempts = 0
    private var activeProjectDir: File? = null

    fun startServerForProject(projectDir: File) {
        activeProjectDir = projectDir
        if (serverManager == null) {
            serverManager = LocalPreviewServerManager(projectDir)
            viewModelScope.launch {
                _isServerRunning.value = true
                serverManager?.startServer()
            }
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                val port = serverManager?.activePort ?: 8080
                _currentUrl.value = "http://127.0.0.1:$port/index.html"
                _serverStatusText.value = "Game Server: RUNNING\nAddress: http://127.0.0.1:$port"
            }
        } else {
            serverManager?.updateProjectDirectory(projectDir)
            val port = serverManager?.activePort ?: 8080
            _currentUrl.value = "http://127.0.0.1:$port/index.html"
            _serverStatusText.value = "Game Server: RUNNING\nAddress: http://127.0.0.1:$port"
        }
    }

    fun updateUrl(newUrl: String) {
        _currentUrl.value = newUrl
    }

    fun addConsoleLog(log: String) {
        _consoleLogs.value = _consoleLogs.value + log
        
        if (log.contains("[ERROR]") && autoFixAttempts < 5) {
            autoFixAttempts++
            _consoleLogs.value = _consoleLogs.value + "[AUTO-FIX] Attempting to fix error (Attempt $autoFixAttempts/5) via Model B..."
            triggerAutoFix(log)
        }
    }

    private fun triggerAutoFix(errorLog: String) {
        val projectDir = activeProjectDir ?: return
        viewModelScope.launch {
            try {
                var jsCode = ""
                val jsFile = File(projectDir, "js/game.js")
                if (jsFile.exists()) {
                    jsCode = jsFile.readText()
                } else {
                    val htmlFile = File(projectDir, "index.html")
                    if (htmlFile.exists()) jsCode = htmlFile.readText()
                }

                val prompt = """
                    You are an expert AI Debugger. The user's HTML5 Canvas game encountered the following error:
                    $errorLog
                    
                    Here is the current code:
                    $jsCode
                    
                    Identify the exact bug and output ONLY the corrected code in a ```javascript block (or ```html block if it was an html file). Do not provide explanations.
                """.trimIndent()

                val builder = StringBuilder()
                var failed = false
                val sessionFlow = dualModelManager.sessionB?.let { dualModelManager.streamModelB(prompt, 1024, emptyList()) } 
                    ?: dualModelManager.sessionA?.let { dualModelManager.streamModelA(prompt, 1024, emptyList()) }

                if (sessionFlow != null) {
                    sessionFlow.collect { event ->
                        if (event is com.offlineai.ai.runtime.TokenEvent.Token) builder.append(event.text)
                    }
                } else failed = true

                if (!failed) {
                    val rawText = builder.toString()
                    val match = Regex("```(?:javascript|html|js)\\n([\\s\\S]*?)```").find(rawText)
                    val newCode = match?.groupValues?.get(1) ?: rawText.replace("```javascript", "").replace("```", "").trim()
                    
                    if (newCode.isNotEmpty()) {
                        if (jsFile.exists()) jsFile.writeText(newCode)
                        else File(projectDir, "index.html").writeText(newCode)
                        
                        _consoleLogs.value = _consoleLogs.value + "[AUTO-FIX] Patch applied. Reloading preview."
                        reloadPreview()
                    }
                }
            } catch(e: Exception) {
                _consoleLogs.value = _consoleLogs.value + "[AUTO-FIX FAILED] ${e.message}"
            }
        }
    }

    fun reloadPreview() {
        val current = _currentUrl.value
        _currentUrl.value = ""
        _currentUrl.value = current
    }

    override fun onCleared() {
        super.onCleared()
        serverManager?.stopServer()
        _serverStatusText.value = "Game Server: STOPPED"
    }
}
