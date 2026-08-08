package com.offlineai.feature.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlineai.ai.agent.AgenticPatchExecutor
import com.offlineai.ai.prompting.AgentPromptBuilder
import com.offlineai.ai.prompting.AgentPromptContext
import com.offlineai.ai.prompting.ChatPromptBuilder
import com.offlineai.ai.prompting.ChatPromptContext
import com.offlineai.ai.prompting.ConversationManager
import com.offlineai.ai.prompting.FileOperationParser
import com.offlineai.ai.prompting.ModelTemplateDetector
import com.offlineai.ai.prompting.ParsedAiResponse
import com.offlineai.ai.runtime.CompletionRequest
import com.offlineai.ai.runtime.LlamaInferenceEngine
import com.offlineai.ai.runtime.TokenEvent
import com.offlineai.core.filesystem.WorkspaceManager
import com.offlineai.core.models.AssistantMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: AssistantMode = AssistantMode.CHAT,
    val parsedPatch: ParsedAiResponse? = null,
    val isApplied: Boolean = false
)

class ChatViewModel(
    private val workspaceManager: WorkspaceManager,
    private val inferenceEngine: LlamaInferenceEngine
) : ViewModel() {

    private val executor = AgenticPatchExecutor(workspaceManager)
    private val conversationManager = ConversationManager(maxTurnsHistory = 10)

    private val _activeMode = MutableStateFlow(AssistantMode.CHAT)
    val activeMode: StateFlow<AssistantMode> = _activeMode.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    var activeSessionModelPath: String? = null

    fun setMode(mode: AssistantMode) {
        _activeMode.value = mode
    }

    fun clearHistory() {
        conversationManager.clear()
        _messages.value = emptyList()
        Log.i("ChatViewModel", "Conversation history cleared.")
    }

    fun sendMessage(userText: String, activeProjectDir: File?, modelPath: String? = null) {
        require(userText.isNotBlank()) { "User request text cannot be blank" }

        // Resolve modelPath to ensure fallback to activeSessionModelPath if parameter is null
        val effectiveModelPath = modelPath ?: activeSessionModelPath

        val currentMode = _activeMode.value
        val userMsg = ChatMessage(sender = "user", text = userText, mode = currentMode)

        val assistantMsgId = java.util.UUID.randomUUID().toString()
        val assistantPlaceholder = ChatMessage(
            id = assistantMsgId,
            sender = "assistant",
            text = "",
            mode = currentMode
        )

        _messages.value = _messages.value + userMsg + assistantPlaceholder
        _isGenerating.value = true

        viewModelScope.launch {
            val responseBuilder = StringBuilder()
            var tokenCount = 0
            var generationFailed = false

            try {
                val fullPrompt = if (currentMode == AssistantMode.CHAT) {
                    val chatContext = ChatPromptContext(
                        conversationHistory = conversationManager.getHistoryPairs(),
                        userRequest = userText,
                        modelPath = effectiveModelPath
                    )
                    ChatPromptBuilder.buildPrompt(chatContext)
                } else {
                    val fileTree = if (activeProjectDir != null) {
                        workspaceManager.getFileTree(activeProjectDir).children.map { it.path }
                    } else emptyList()

                    val activeContent = if (activeProjectDir != null) {
                        try {
                            workspaceManager.readFileText(activeProjectDir, "index.html")
                        } catch (e: Exception) { null }
                    } else null

                    val agentContext = AgentPromptContext(
                        projectSummary = activeProjectDir?.name ?: "Web Project",
                        fileTree = fileTree,
                        activeFile = "index.html",
                        activeFileContent = activeContent,
                        conversationHistory = conversationManager.getHistoryPairs(),
                        userRequest = userText,
                        modelPath = effectiveModelPath
                    )
                    AgentPromptBuilder.buildPrompt(agentContext)
                }

                val family = ModelTemplateDetector.detectFamily(effectiveModelPath)
                val stopTokens = ModelTemplateDetector.getStopTokens(family)

                Log.i("ChatViewModel", "Starting inference: mode=$currentMode, family=$family, effectiveModelPath=$effectiveModelPath, promptLen=${fullPrompt.length}")

                val startTime = System.currentTimeMillis()

                inferenceEngine.streamCompletion(
                    CompletionRequest(
                        sessionId = "chat-session",
                        prompt = fullPrompt,
                        maxTokens = 2048,
                        temperature = if (currentMode == AssistantMode.CHAT) 0.7f else 0.2f,
                        stopSequences = stopTokens,
                        modelPath = effectiveModelPath
                    )
                ).collect { event ->
                    when (event) {
                        is TokenEvent.Token -> {
                            tokenCount++
                            responseBuilder.append(event.text)
                            updateAssistantMessageText(assistantMsgId, responseBuilder.toString())
                        }
                        is TokenEvent.Error -> {
                            Log.e("ChatViewModel", "Inference error: ${event.throwable.message}", event.throwable)
                            val errorText = "Inference Error: ${event.throwable.message}"
                            updateAssistantMessageText(assistantMsgId, errorText)
                            _isGenerating.value = false
                            generationFailed = true
                            return@collect
                        }
                        is TokenEvent.Completed -> {
                            val elapsed = System.currentTimeMillis() - startTime
                            Log.i("ChatViewModel", "Inference completed: tokens=$tokenCount, elapsed=${elapsed}ms")
                        }
                        is TokenEvent.Cancelled -> {
                            Log.w("ChatViewModel", "Inference cancelled")
                            _isGenerating.value = false
                            generationFailed = true
                            return@collect
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Uncaught exception in sendMessage: ${e.message}", e)
                updateAssistantMessageText(assistantMsgId, "Inference Error: ${e.message}")
                _isGenerating.value = false
                return@launch
            }

            if (!generationFailed) {
                val rawResponse = responseBuilder.toString()

                if (tokenCount == 0) {
                    Log.e("ChatViewModel", "Inference failed: Zero tokens generated")
                    updateAssistantMessageText(assistantMsgId, "Inference Failed: Zero tokens generated by GGUF model. Please check the logs.")
                    _isGenerating.value = false
                    return@launch
                } else if (rawResponse.isBlank()) {
                    Log.w("ChatViewModel", "Inference completed but response was entirely whitespace/blank.")
                    updateAssistantMessageText(assistantMsgId, "Inference completed, but the model generated an empty or whitespace response. Try adjusting your prompt.")
                    _isGenerating.value = false
                    return@launch
                }

                if (currentMode == AssistantMode.AGENT) {
                    val parseResult = FileOperationParser.parseJsonResponse(rawResponse)
                    val parsedPatch = parseResult.getOrNull()
                    val hasOps = parsedPatch != null && parsedPatch.operations.isNotEmpty()

                    val summaryText = if (parsedPatch != null && parsedPatch.summary.isNotBlank()) {
                        parsedPatch.summary
                    } else {
                        rawResponse
                    }

                    updateAssistantMessagePatch(assistantMsgId, summaryText, if (hasOps) parsedPatch else null)
                }

                conversationManager.addTurn(userText, rawResponse)
                _isGenerating.value = false
            }
        }
    }

    private fun updateAssistantMessageText(messageId: String, newText: String) {
        val list = _messages.value.toMutableList()
        val index = list.indexOfFirst { it.id == messageId }
        if (index != -1) {
            list[index] = list[index].copy(text = newText)
            _messages.value = list
        }
    }

    private fun updateAssistantMessagePatch(messageId: String, summaryText: String, patch: ParsedAiResponse?) {
        val list = _messages.value.toMutableList()
        val index = list.indexOfFirst { it.id == messageId }
        if (index != -1) {
            list[index] = list[index].copy(
                text = summaryText,
                parsedPatch = patch
            )
            _messages.value = list
        }
    }

    fun applyPatchToProject(messageId: String, projectDir: File) {
        viewModelScope.launch {
            val list = _messages.value.toMutableList()
            val index = list.indexOfFirst { it.id == messageId }
            if (index != -1) {
                val msg = list[index]
                val patch = msg.parsedPatch
                if (patch != null && !msg.isApplied) {
                    val logs = executor.executeOperations(projectDir, patch.operations)
                    list[index] = msg.copy(
                        isApplied = true,
                        text = msg.text + "\n\nApplied Changes:\n- " + logs.joinToString("\n- ")
                    )
                    _messages.value = list
                }
            }
        }
    }
}
