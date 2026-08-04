package com.offlineai.core.filesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class WorkspaceManager(private val baseDir: File) {

    val workspaceRoot: File = File(baseDir, "Workspace")
    val projectsDir: File = File(workspaceRoot, "Projects")
    val modelsDir: File = File(workspaceRoot, "Models")
    val templatesDir: File = File(workspaceRoot, "Templates")
    val downloadsDir: File = File(workspaceRoot, "Downloads")
    val logsDir: File = File(workspaceRoot, "Logs")
    val cacheDir: File = File(workspaceRoot, "Cache")

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    suspend fun initializeWorkspace() = withContext(Dispatchers.IO) {
        val dirs = listOf(workspaceRoot, projectsDir, modelsDir, templatesDir, downloadsDir, logsDir, cacheDir)
        for (dir in dirs) {
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
        _isInitialized.value = true
    }

    suspend fun createProjectDirectory(projectName: String): File = withContext(Dispatchers.IO) {
        val sanitized = projectName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val projDir = File(projectsDir, sanitized)
        if (!projDir.exists()) {
            projDir.mkdirs()
        }
        // Initialize default web starter files
        File(projDir, "index.html").writeText("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$projectName</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <h1>Welcome to $projectName</h1>
    <p>Created with Offline AI Coding Studio.</p>
    <script src="script.js"></script>
</body>
</html>
        """.trimIndent())

        File(projDir, "style.css").writeText("""
body {
    font-family: system-ui, sans-serif;
    background-color: #121212;
    color: #e0e0e0;
    padding: 2rem;
}
h1 {
    color: #4caf50;
}
        """.trimIndent())

        File(projDir, "script.js").writeText("""
console.log("$projectName loaded!");
        """.trimIndent())

        File(projDir, "README.md").writeText("# $projectName\n\nGenerated using Offline AI Coding Studio.")

        projDir
    }

    suspend fun getFileTree(projectDir: File): FileTreeNode = withContext(Dispatchers.IO) {
        buildTreeNode(projectDir, projectDir)
    }

    private fun buildTreeNode(file: File, rootDir: File): FileTreeNode {
        val relativePath = file.relativeTo(rootDir).path.ifEmpty { "." }
        return if (file.isDirectory) {
            val children = (file.listFiles() ?: emptyArray())
                .sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                .map { buildTreeNode(it, rootDir) }
            FileTreeNode(
                name = file.name,
                path = relativePath,
                isDirectory = true,
                children = children
            )
        } else {
            FileTreeNode(
                name = file.name,
                path = relativePath,
                isDirectory = false,
                sizeBytes = file.length(),
                extension = file.extension
            )
        }
    }

    suspend fun readFileText(projectDir: File, relativePath: String): String = withContext(Dispatchers.IO) {
        val target = File(projectDir, relativePath)
        require(FileSecurityUtils.isPathSafe(projectDir, target)) { "Access denied: Path outside project boundary" }
        target.readText()
    }

    suspend fun writeFileText(projectDir: File, relativePath: String, content: String) = withContext(Dispatchers.IO) {
        val target = File(projectDir, relativePath)
        require(FileSecurityUtils.isPathSafe(projectDir, target)) { "Access denied: Path outside project boundary" }
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    suspend fun deleteFileOrDir(projectDir: File, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(projectDir, relativePath)
        require(FileSecurityUtils.isPathSafe(projectDir, target)) { "Access denied: Path outside project boundary" }
        if (target.isDirectory) {
            target.deleteRecursively()
        } else {
            target.delete()
        }
    }
}
