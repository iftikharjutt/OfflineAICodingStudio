package com.offlineai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlineai.ai.agent.AgenticPatchExecutor
import com.offlineai.ai.prompting.FileOperationParser
import com.offlineai.ai.prompting.ParsedAiResponse
import com.offlineai.ai.prompting.PromptBuilderContext
import com.offlineai.ai.prompting.StructuredPromptBuilder
import com.offlineai.ai.runtime.CompletionRequest
import com.offlineai.ai.runtime.LlamaInferenceEngine
import com.offlineai.ai.runtime.TokenEvent
import com.offlineai.core.filesystem.WorkspaceManager
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
    val parsedPatch: ParsedAiResponse? = null,
    val isApplied: Boolean = false
)

class ChatViewModel(
    private val workspaceManager: WorkspaceManager,
    private val inferenceEngine: LlamaInferenceEngine
) : ViewModel() {

    private val executor = AgenticPatchExecutor(workspaceManager)

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "assistant",
                text = "Hello! I am your offline AI Coding Assistant. Ask me to generate a new website feature, fix bugs, edit code, or create files."
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    fun sendMessage(userText: String, activeProjectDir: File?) {
        if (userText.isBlank()) return
        val userMsg = ChatMessage(sender = "user", text = userText)
        _messages.value = _messages.value + userMsg
        _isGenerating.value = true

        viewModelScope.launch {
            val fileTree = if (activeProjectDir != null) {
                workspaceManager.getFileTree(activeProjectDir).children.map { it.path }
            } else emptyList()

            val activeContent = if (activeProjectDir != null) {
                try {
                    workspaceManager.readFileText(activeProjectDir, "index.html")
                } catch (e: Exception) { null }
            } else null

            val context = PromptBuilderContext(
                projectSummary = activeProjectDir?.name ?: "Web Project",
                fileTree = fileTree,
                activeFile = "index.html",
                activeFileContent = activeContent,
                userRequest = userText
            )

            val systemPrompt = StructuredPromptBuilder.buildSystemPrompt()
            val userPrompt = StructuredPromptBuilder.buildUserPrompt(context)
            val fullPrompt = "$systemPrompt\n\n$userPrompt"

            val responseBuilder = StringBuilder()
            var generationFailed = false

            try {
                inferenceEngine.streamCompletion(
                    CompletionRequest(
                        sessionId = "chat-session",
                        prompt = fullPrompt,
                        maxTokens = 2048,
                        temperature = 0.3f
                    )
                ).collect { event ->
                    when (event) {
                        is TokenEvent.Token -> responseBuilder.append(event.text)
                        is TokenEvent.Error -> {
                            val fallbackMsg = ChatMessage(
                                sender = "assistant",
                                text = "Inference error: ${event.throwable.message}"
                            )
                            _messages.value = _messages.value + fallbackMsg
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
                val errorMsg = ChatMessage(
                    sender = "assistant",
                    text = "Error during generation: ${e.message}"
                )
                _messages.value = _messages.value + errorMsg
                _isGenerating.value = false
                return@launch
            }

            if (!generationFailed) {
                val parseResult = FileOperationParser.parseJsonResponse(responseBuilder.toString())
                val parsedPatch = parseResult.getOrNull()
                val responseMsg = ChatMessage(
                    sender = "assistant",
                    text = parsedPatch?.summary ?: responseBuilder.toString().take(500),
                    parsedPatch = parsedPatch
                )
                _messages.value = _messages.value + responseMsg
                _isGenerating.value = false
            }
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
                        text = msg.text + "\nApplied Changes:\n" + logs.joinToString("\n- ")
                    )
                    _messages.value = list
                }
            }
        }
    }
}
