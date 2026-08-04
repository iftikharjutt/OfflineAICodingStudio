package com.offlineai.feature.editor

import com.offlineai.core.filesystem.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class EditorBufferImpl(
    private val workspaceManager: WorkspaceManager
) : EditorBuffer {

    private val _text = MutableStateFlow("")
    override val text: StateFlow<String> = _text.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    override val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private var currentProjectDir: File? = null
    private var currentFileHandle: FileHandle? = null
    private var originalContent: String = ""

    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    override fun setProjectContext(projectDir: File) {
        currentProjectDir = projectDir
    }

    override suspend fun load(file: FileHandle) {
        val projDir = currentProjectDir ?: return
        currentFileHandle = file
        val content = workspaceManager.readFileText(projDir, file.path)
        originalContent = content
        _text.value = content
        _isDirty.value = false
        undoStack.clear()
        redoStack.clear()
    }

    override suspend fun save() {
        val projDir = currentProjectDir ?: return
        val handle = currentFileHandle ?: return
        val contentToSave = _text.value
        workspaceManager.writeFileText(projDir, handle.path, contentToSave)
        originalContent = contentToSave
        _isDirty.value = false
    }

    override fun updateText(newText: String) {
        if (_text.value != newText) {
            undoStack.add(_text.value)
            _text.value = newText
            _isDirty.value = (_text.value != originalContent)
            redoStack.clear()
        }
    }

    override fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_text.value)
            _text.value = previous
            _isDirty.value = (_text.value != originalContent)
        }
    }

    override fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_text.value)
            _text.value = next
            _isDirty.value = (_text.value != originalContent)
        }
    }
}
