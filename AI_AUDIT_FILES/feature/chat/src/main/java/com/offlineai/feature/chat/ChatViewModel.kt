package com.offlineai.feature.chat

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

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "assistant",
                text = "Hello! I am your offline AI Coding Assistant. Switch between Chat Mode for natural conversation & guidance, or Agent Mode for autonomous code editing.",
                mode = AssistantMode.CHAT
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    fun setMode(mode: AssistantMode) {
        _activeMode.value = mode
    }

    fun clearHistory() {
        conversationManager.clear()
        _messages.value = listOf(
            ChatMessage(
                sender = "assistant",
                text = "Conversation history cleared. Ready for a new session!",
                mode = _activeMode.value
            )
        )
    }

    fun sendMessage(userText: String, activeProjectDir: File?, modelPath: String? = null) {
        if (userText.isBlank()) return

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
            var generationFailed = false

            try {
                val fullPrompt = if (currentMode == AssistantMode.CHAT) {
                    val chatContext = ChatPromptContext(
                        conversationHistory = conversationManager.getHistoryPairs(),
                        userRequest = userText,
                        modelPath = modelPath
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
                        modelPath = modelPath
                    )
                    AgentPromptBuilder.buildPrompt(agentContext)
                }

                val family = ModelTemplateDetector.detectFamily(modelPath)
                val stopTokens = ModelTemplateDetector.getStopTokens(family)

                inferenceEngine.streamCompletion(
                    CompletionRequest(
                        sessionId = "chat-session",
                        prompt = fullPrompt,
                        maxTokens = 2048,
                        temperature = if (currentMode == AssistantMode.CHAT) 0.7f else 0.2f,
                        stopSequences = stopTokens,
                        modelPath = modelPath
                    )
                ).collect { event ->
                    when (event) {
                        is TokenEvent.Token -> {
                            responseBuilder.append(event.text)
                            updateAssistantMessageText(assistantMsgId, responseBuilder.toString())
                        }
                        is TokenEvent.Error -> {
                            val errorText = "Error: ${event.throwable.message}"
                            updateAssistantMessageText(assistantMsgId, errorText)
                            _isGenerating.value = false
                            generationFailed = true
                            return@collect
                        }
                        is TokenEvent.Completed -> {}
                        is TokenEvent.Cancelled -> {
                            _isGenerating.value = false
                            generationFailed = true
                            return@collect
                        }
                    }
                }
            } catch (e: Exception) {
                updateAssistantMessageText(assistantMsgId, "Generation Error: ${e.message}")
                _isGenerating.value = false
                return@launch
            }

            if (!generationFailed) {
                val rawResponse = responseBuilder.toString()

                if (currentMode == AssistantMode.AGENT) {
                    val parseResult = FileOperationParser.parseJsonResponse(rawResponse)
                    val parsedPatch = parseResult.getOrNull()
                    val hasOps = parsedPatch != null && parsedPatch.operations.isNotEmpty()

                    val summaryText = if (parsedPatch != null && parsedPatch.summary.isNotBlank()) {
                        parsedPatch.summary
                    } else {
                        "Agent finished patch generation."
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
