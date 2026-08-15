package com.offlineai.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlineai.core.filesystem.FileTreeNode
import com.offlineai.core.filesystem.WorkspaceManager
import com.offlineai.core.models.ProjectModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ProjectsViewModel(
    private val workspaceManager: WorkspaceManager
) : ViewModel() {

    private val _projects = MutableStateFlow<List<ProjectModel>>(emptyList())
    val projects: StateFlow<List<ProjectModel>> = _projects.asStateFlow()

    private val _activeProject = MutableStateFlow<ProjectModel?>(null)
    val activeProject: StateFlow<ProjectModel?> = _activeProject.asStateFlow()

    private val _fileTree = MutableStateFlow<FileTreeNode?>(null)
    val fileTree: StateFlow<FileTreeNode?> = _fileTree.asStateFlow()

    private val _activeFileContent = MutableStateFlow<String?>(null)
    val activeFileContent: StateFlow<String?> = _activeFileContent.asStateFlow()

    private val _activeFilePath = MutableStateFlow<String?>(null)
    val activeFilePath: StateFlow<String?> = _activeFilePath.asStateFlow()

    init {
        viewModelScope.launch {
            workspaceManager.initializeWorkspace()
            loadProjectsFromWorkspace()
        }
    }

    fun loadProjectsFromWorkspace() {
        viewModelScope.launch {
            val projectsDir = workspaceManager.projectsDir
            val subDirs = projectsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            val list = subDirs.map { dir ->
                ProjectModel(
                    id = dir.name,
                    name = dir.name,
                    path = dir.absolutePath,
                    createdAt = dir.lastModified(),
                    updatedAt = dir.lastModified()
                )
            }
            _projects.value = list

            if (_activeProject.value == null && list.isNotEmpty()) {
                selectProject(list.first())
            }
        }
    }

    fun createNewProject(projectName: String) {
        viewModelScope.launch {
            val projDir = workspaceManager.createProjectDirectory(projectName)
            val newProj = ProjectModel(
                id = projDir.name,
                name = projDir.name,
                path = projDir.absolutePath,
                createdAt = projDir.lastModified(),
                updatedAt = projDir.lastModified()
            )
            loadProjectsFromWorkspace()
            selectProject(newProj)
        }
    }

    fun createProjectFromTemplate(templateName: String) {
        viewModelScope.launch {
            val projectName = templateName + "_" + System.currentTimeMillis()
            val projDir = workspaceManager.createProjectDirectory(projectName)
            
            if (templateName == "Snake") {
                workspaceManager.writeFileText(projDir, "index.html", com.offlineai.core.filesystem.GameTemplates.SNAKE_HTML)
                workspaceManager.writeFileText(projDir, "js/game.js", com.offlineai.core.filesystem.GameTemplates.SNAKE_JS)
            }
            
            val newProj = ProjectModel(
                id = projDir.name,
                name = projDir.name,
                path = projDir.absolutePath,
                createdAt = projDir.lastModified(),
                updatedAt = projDir.lastModified()
            )
            loadProjectsFromWorkspace()
            selectProject(newProj)
        }
    }

    fun selectProject(project: ProjectModel) {
        _activeProject.value = project
        refreshFileTree()
    }

    fun refreshFileTree() {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            val tree = workspaceManager.getFileTree(File(proj.path))
            _fileTree.value = tree
        }
    }

    fun openFile(relativePath: String) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            val content = workspaceManager.readFileText(File(proj.path), relativePath)
            _activeFilePath.value = relativePath
            _activeFileContent.value = content
        }
    }

    fun saveActiveFile(newContent: String) {
        val proj = _activeProject.value ?: return
        val path = _activeFilePath.value ?: return
        viewModelScope.launch {
            workspaceManager.writeFileText(File(proj.path), path, newContent)
            _activeFileContent.value = newContent
            refreshFileTree()
        }
    }

    fun createNewFileInActiveProject(relativePath: String, initialContent: String = "") {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            workspaceManager.writeFileText(File(proj.path), relativePath, initialContent)
            refreshFileTree()
            openFile(relativePath)
        }
    }

    fun deleteFileInActiveProject(relativePath: String) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            workspaceManager.deleteFileOrDir(File(proj.path), relativePath)
            if (_activeFilePath.value == relativePath) {
                _activeFilePath.value = null
                _activeFileContent.value = null
            }
            refreshFileTree()
        }
    }

    fun exportActiveProject(onComplete: (File?) -> Unit) {
        val proj = _activeProject.value
        if (proj == null) {
            onComplete(null)
            return
        }
        viewModelScope.launch {
            try {
                val zip = workspaceManager.exportProjectToZip(File(proj.path))
                onComplete(zip)
            } catch (e: Exception) {
                onComplete(null)
            }
        }
    }
}
