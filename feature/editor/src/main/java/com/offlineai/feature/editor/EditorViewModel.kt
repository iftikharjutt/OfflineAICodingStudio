package com.offlineai.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlineai.core.filesystem.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DefaultFileHandle(
    override val path: String,
    override val name: String
) : FileHandle

class EditorViewModel(
    private val workspaceManager: WorkspaceManager
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
        editorBuffer.redo()
    }
}
