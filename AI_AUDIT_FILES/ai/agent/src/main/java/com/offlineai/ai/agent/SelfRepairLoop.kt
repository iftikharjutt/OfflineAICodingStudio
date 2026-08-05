package com.offlineai.ai.agent

import com.offlineai.ai.prompting.AgentPromptBuilder
import com.offlineai.ai.prompting.AgentPromptContext
import com.offlineai.ai.prompting.FileOperationParser
import com.offlineai.ai.prompting.ModelTemplateDetector
import com.offlineai.ai.runtime.CompletionRequest
import com.offlineai.ai.runtime.LlamaInferenceEngine
import com.offlineai.ai.runtime.TokenEvent
import com.offlineai.core.filesystem.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class RepairAttempt(
    val attemptNumber: Int,
    val errorMessage: String,
    val patchSummary: String,
    val isSuccess: Boolean
)

class SelfRepairLoop(
    private val workspaceManager: WorkspaceManager,
    private val inferenceEngine: LlamaInferenceEngine,
    private val maxAttempts: Int = 3
) {
    private val executor = AgenticPatchExecutor(workspaceManager)

    private val _repairHistory = MutableStateFlow<List<RepairAttempt>>(emptyList())
    val repairHistory: StateFlow<List<RepairAttempt>> = _repairHistory.asStateFlow()

    private val _isRepairing = MutableStateFlow(false)
    val isRepairing: StateFlow<Boolean> = _isRepairing.asStateFlow()

    suspend fun attemptAutoRepair(
        projectDir: File,
        errorMessage: String,
        activeFilePath: String? = null,
        modelPath: String? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (_repairHistory.value.size >= maxAttempts) {
            return@withContext Result.failure(
                IllegalStateException("Maximum auto-repair attempts ($maxAttempts) reached")
            )
        }

        _isRepairing.value = true
        val attemptNum = _repairHistory.value.size + 1

        try {
            val fileTree = workspaceManager.getFileTree(projectDir).children.map { it.path }
            val activeContent = activeFilePath?.let {
                workspaceManager.readFileText(projectDir, it)
            }

            val agentContext = AgentPromptContext(
                projectSummary = projectDir.name,
                fileTree = fileTree,
                activeFile = activeFilePath,
                activeFileContent = activeContent,
                recentErrors = listOf(errorMessage),
                userRequest = "Fix the following runtime error: $errorMessage",
                modelPath = modelPath
            )

            val fullPrompt = AgentPromptBuilder.buildPrompt(agentContext)
            val family = ModelTemplateDetector.detectFamily(modelPath)
            val stopTokens = ModelTemplateDetector.getStopTokens(family)

            val responseBuilder = StringBuilder()

            inferenceEngine.streamCompletion(
                CompletionRequest(
                    sessionId = "repair-session-$attemptNum",
                    prompt = fullPrompt,
                    maxTokens = 2048,
                    temperature = 0.2f,
                    stopSequences = stopTokens,
                    modelPath = modelPath
                )
            ).collect { event ->
                when (event) {
                    is TokenEvent.Token -> responseBuilder.append(event.text)
                    is TokenEvent.Error -> throw event.throwable
                    is TokenEvent.Completed -> {}
                    is TokenEvent.Cancelled -> {}
                }
            }

            val parseResult = FileOperationParser.parseJsonResponse(responseBuilder.toString())
            val parsedPatch = parseResult.getOrThrow()

            val appliedLogs = executor.executeOperations(projectDir, parsedPatch.operations)

            val attempt = RepairAttempt(
                attemptNumber = attemptNum,
                errorMessage = errorMessage,
                patchSummary = parsedPatch.summary,
                isSuccess = true
            )
            _repairHistory.value = _repairHistory.value + attempt
            Result.success(appliedLogs)

        } catch (e: Exception) {
            val attempt = RepairAttempt(
                attemptNumber = attemptNum,
                errorMessage = errorMessage,
                patchSummary = "Repair failed: ${e.message}",
                isSuccess = false
            )
            _repairHistory.value = _repairHistory.value + attempt
            Result.failure(e)
        } finally {
            _isRepairing.value = false
        }
    }
}
