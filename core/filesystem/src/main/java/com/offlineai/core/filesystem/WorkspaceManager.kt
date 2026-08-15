package com.offlineai.core.filesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        
        // Game Studio Architecture Directories
        File(projDir, "css").mkdirs()
        File(projDir, "js").mkdirs()
        File(projDir, "assets/images").mkdirs()
        File(projDir, "assets/audio").mkdirs()
        File(projDir, "assets/fonts").mkdirs()
        val gsDir = File(projDir, ".gamestudio")
        gsDir.mkdirs()

        // Initialize default web starter files
        File(projDir, "index.html").writeText("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$projectName</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div id="game-container">
        <canvas id="gameCanvas" width="800" height="600"></canvas>
    </div>
    <script src="js/main.js"></script>
    <script src="js/game.js"></script>
    <script src="js/player.js"></script>
    <script src="js/enemies.js"></script>
    <script src="js/collision.js"></script>
    <script src="js/controls.js"></script>
    <script src="js/audio.js"></script>
</body>
</html>
        """.trimIndent())

        File(projDir, "css/style.css").writeText("""
body {
    margin: 0;
    padding: 0;
    background-color: #121212;
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    color: #fff;
    font-family: sans-serif;
}
#game-container {
    box-shadow: 0 0 20px rgba(0,0,0,0.5);
}
canvas {
    background-color: #000;
    display: block;
}
        """.trimIndent())

        // Empty scaffolding JS files
        listOf("main.js", "game.js", "player.js", "enemies.js", "collision.js", "controls.js", "audio.js").forEach { jsFile ->
            File(projDir, "js/$jsFile").writeText("// $jsFile\n")
        }
        
        // Add basic main logic
        File(projDir, "js/main.js").writeText("""
// main.js - Entry Point
window.onload = () => {
    console.log("$projectName initialized.");
    // Game initialization logic here
};
        """.trimIndent())

        File(projDir, "README.md").writeText("# $projectName\n\nGenerated using Offline Game Studio.")

        // GameStudio internal memory files
        File(gsDir, "game-spec.json").writeText("{}")
        File(gsDir, "architecture.json").writeText("{}")
        File(gsDir, "tasks.json").writeText("[]")
        File(gsDir, "errors.json").writeText("[]")
        File(gsDir, "history.json").writeText("[]")

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

    suspend fun exportProjectToZip(projectDir: File): File = withContext(Dispatchers.IO) {
        val zipFile = File(downloadsDir, "${projectDir.name}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            projectDir.walkTopDown().forEach { file ->
                val zipFileName = file.absolutePath.removePrefix(projectDir.absolutePath).removePrefix("/")
                if (zipFileName.isNotEmpty()) {
                    val entry = ZipEntry(zipFileName + (if (file.isDirectory) "/" else ""))
                    zos.putNextEntry(entry)
                    if (file.isFile) {
                        file.inputStream().use { it.copyTo(zos) }
                    }
                }
            }
        }
        zipFile
    }
}
