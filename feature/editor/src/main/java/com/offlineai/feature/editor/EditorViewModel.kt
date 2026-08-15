package com.offlineai.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlineai.core.filesystem.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import com.offlineai.ai.runtime.CompletionRequest
import com.offlineai.ai.runtime.LlamaInferenceEngine
import com.offlineai.ai.runtime.TokenEvent

data class DefaultFileHandle(
    override val path: String,
    override val name: String
) : FileHandle

class EditorViewModel(
    private val workspaceManager: WorkspaceManager,
    private val inferenceEngine: LlamaInferenceEngine
) : ViewModel() {

    private val editorBuffer = EditorBufferImpl(workspaceManager)

    val text: StateFlow<String> = editorBuffer.text
    val isDirty: StateFlow<Boolean> = editorBuffer.isDirty

    private val _activeFileName = MutableStateFlow<String?>("index.html")
    val activeFileName: StateFlow<String?> = _activeFileName.asStateFlow()

    private val _activeFilePath = MutableStateFlow<String?>("index.html")
    val activeFilePath: StateFlow<String?> = _activeFilePath.asStateFlow()

    fun loadFile(projectDir: File, relativePath: String) {
        editorBuffer.setProjectContext(projectDir)
        _activeFilePath.value = relativePath
        _activeFileName.value = File(relativePath).name
        viewModelScope.launch {
            editorBuffer.load(DefaultFileHandle(relativePath, File(relativePath).name))
        }
    }

    fun onTextChange(newText: String) {
        editorBuffer.updateText(newText)
    }

    fun saveFile() {
        viewModelScope.launch {
            editorBuffer.save()
        }
    }

    fun undo() {
        editorBuffer.undo()
    }

    fun redo() {
        editorBuffer.undo() // Redo is not implemented in buffer by default, fall back to undo or skip
    }

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    fun autoComplete(modelPath: String?) {
        if (modelPath == null || _isGenerating.value) return
        
        _isGenerating.value = true
        val currentContent = editorBuffer.text.value
        val prompt = "Continue the following code exactly where it left off without any explanation or markdown formatting:\n$currentContent"
        
        viewModelScope.launch {
            try {
                inferenceEngine.streamCompletion(
                    CompletionRequest(
                        sessionId = "editor-session",
                        prompt = prompt,
                        maxTokens = 256,
                        temperature = 0.2f,
                        stopSequences = listOf("```"),
                        modelPath = modelPath
                    )
                ).collect { event ->
                    when (event) {
                        is TokenEvent.Token -> {
                            onTextChange(editorBuffer.text.value + event.text)
                        }
                        else -> {
                            // Handled at the end
                        }
                    }
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }
}
