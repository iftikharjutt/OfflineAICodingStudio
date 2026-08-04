package com.offlineai.ai.agent

import com.offlineai.core.filesystem.WorkspaceManager
import com.offlineai.core.models.FileOperation
import java.io.File

class AgenticPatchExecutor(
    private val workspaceManager: WorkspaceManager
) {
    suspend fun executeOperations(projectDir: File, operations: List<FileOperation>): List<String> {
        val appliedSummary = mutableListOf<String>()

        for (op in operations) {
            when (op) {
                is FileOperation.CreateFile -> {
                    workspaceManager.writeFileText(projectDir, op.path, op.content)
                    appliedSummary.add("Created file: ${op.path}")
                }
                is FileOperation.ReplaceFile -> {
                    workspaceManager.writeFileText(projectDir, op.path, op.content)
                    appliedSummary.add("Replaced file: ${op.path}")
                }
                is FileOperation.ReplaceBlock -> {
                    val existing = workspaceManager.readFileText(projectDir, op.path)
                    val updated = existing.replace(op.find, op.replace)
                    workspaceManager.writeFileText(projectDir, op.path, updated)
                    appliedSummary.add("Updated block in: ${op.path}")
                }
                is FileOperation.DeleteFile -> {
                    workspaceManager.deleteFileOrDir(projectDir, op.path)
                    appliedSummary.add("Deleted file: ${op.path}")
                }
                is FileOperation.CreateDirectory -> {
                    val dir = File(projectDir, op.path)
                    dir.mkdirs()
                    appliedSummary.add("Created directory: ${op.path}")
                }
            }
        }

        return appliedSummary
    }
}
