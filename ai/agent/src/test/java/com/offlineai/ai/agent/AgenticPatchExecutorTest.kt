package com.offlineai.ai.agent

import com.offlineai.core.filesystem.WorkspaceManager
import com.offlineai.core.models.FileOperation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgenticPatchExecutorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCreateFileOperation() = runBlocking {
        val projectDir = tempFolder.newFolder("test-project")
        val workspaceManager = WorkspaceManager(tempFolder.root)
        val executor = AgenticPatchExecutor(workspaceManager)

        val ops = listOf(
            FileOperation.CreateFile("test.txt", "Hello World")
        )

        val results = executor.executeOperations(projectDir, ops)
        assertEquals(1, results.size)
        assertTrue(results[0].contains("Created file"))
        assertTrue(java.io.File(projectDir, "test.txt").exists())
    }

    @Test
    fun testDeleteFileOperation() = runBlocking {
        val projectDir = tempFolder.newFolder("test-project")
        val fileToDelete = java.io.File(projectDir, "delete-me.txt")
        fileToDelete.writeText("temporary")

        val workspaceManager = WorkspaceManager(tempFolder.root)
        val executor = AgenticPatchExecutor(workspaceManager)

        val ops = listOf(FileOperation.DeleteFile("delete-me.txt"))
        val results = executor.executeOperations(projectDir, ops)

        assertEquals(1, results.size)
        assertFalse(fileToDelete.exists())
    }

    @Test
    fun testReplaceBlockOperation() = runBlocking {
        val projectDir = tempFolder.newFolder("test-project")
        val cssFile = java.io.File(projectDir, "style.css")
        cssFile.writeText("body { color: red; }")

        val workspaceManager = WorkspaceManager(tempFolder.root)
        val executor = AgenticPatchExecutor(workspaceManager)

        val ops = listOf(
            FileOperation.ReplaceBlock("style.css", "color: red", "color: blue")
        )

        val results = executor.executeOperations(projectDir, ops)
        assertEquals(1, results.size)
        assertTrue(cssFile.readText().contains("color: blue"))
        assertFalse(cssFile.readText().contains("color: red"))
    }

    @Test
    fun testCreateDirectoryOperation() = runBlocking {
        val projectDir = tempFolder.newFolder("test-project")
        val workspaceManager = WorkspaceManager(tempFolder.root)
        val executor = AgenticPatchExecutor(workspaceManager)

        val ops = listOf(FileOperation.CreateDirectory("assets/images"))
        val results = executor.executeOperations(projectDir, ops)

        assertEquals(1, results.size)
        assertTrue(java.io.File(projectDir, "assets/images").isDirectory)
    }
}
