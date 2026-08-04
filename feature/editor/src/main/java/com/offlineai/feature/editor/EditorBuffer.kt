package com.offlineai.feature.editor

import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface FileHandle {
    val path: String
    val name: String
}

interface EditorBuffer {
    val text: StateFlow<String>
    val isDirty: StateFlow<Boolean>
    fun setProjectContext(projectDir: File)
    suspend fun load(file: FileHandle)
    suspend fun save()
    fun updateText(newText: String)
    fun undo()
    fun redo()
}
