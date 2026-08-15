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

    enum class ModelLoadState {
        Idle,
        Loading,
        Loaded,
        Failed
    }

    private val executor = AgenticPatchExecutor(workspaceManager)
    private val conversationManager = ConversationManager(maxTurnsHistory = 10)

    private val _activeMode = MutableStateFlow(AssistantMode.CHAT)
    val activeMode: StateFlow<AssistantMode> = _activeMode.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _modelLoadState = MutableStateFlow(ModelLoadState.Idle)
    val modelLoadState: StateFlow<ModelLoadState> = _modelLoadState.asStateFlow()

    private val _modelLoadError = MutableStateFlow<String?>(null)
    val modelLoadError: StateFlow<String?> = _modelLoadError.asStateFlow()

    var activeSessionModelPath: String? = null
        private set

    fun setModelLoadState(path: String?, state: ModelLoadState, error: String? = null) {
        activeSessionModelPath = if (state == ModelLoadState.Loaded) path else null
        _modelLoadState.value = state
        _modelLoadError.value = error
    }

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
        if (_isGenerating.value) {
            Log.w("ChatViewModel", "Ignoring sendMessage while another generation is active.")
            return
        }
        if (_modelLoadState.value != ModelLoadState.Loaded) {
            Log.w("ChatViewModel", "Ignoring sendMessage because no model session is ready: ${_modelLoadState.value}")
            return
        }

        val effectiveModelPath = activeSessionModelPath ?: modelPath
        if (effectiveModelPath == null) {
            Log.w("ChatViewModel", "Ignoring sendMessage because no loaded model path is available.")
            return
        }

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
                    ChatPromptBuilder.buildPrompt(
                        ChatPromptContext(
                            conversationHistory = conversationManager.getHistoryPairs(),
                            userRequest = userText,
                            modelPath = effectiveModelPath
                        )
                    )
                } else {
                    val fileTree = activeProjectDir?.let {
                        workspaceManager.getFileTree(it).children.map { child -> child.path }
                    } ?: emptyList()
                    val activeContent = activeProjectDir?.let {
                        runCatching { workspaceManager.readFileText(it, "index.html") }.getOrNull()
                    }

                    AgentPromptBuilder.buildPrompt(
                        AgentPromptContext(
                            projectSummary = activeProjectDir?.name ?: "Web Project",
                            fileTree = fileTree,
                            activeFile = "index.html",
                            activeFileContent = activeContent,
                            conversationHistory = conversationManager.getHistoryPairs(),
                            userRequest = userText,
                            modelPath = effectiveModelPath
                        )
                    )
                }

                val family = ModelTemplateDetector.detectFamily(effectiveModelPath)
                val stopTokens = ModelTemplateDetector.getStopTokens(family)
                Log.i(
                    "ChatViewModel",
                    "Starting inference: mode=$currentMode, family=$family, model=$effectiveModelPath, promptLen=${fullPrompt.length}"
                )

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
                    if (generationFailed) return@collect

                    when (event) {
                        is TokenEvent.Token -> {
                            tokenCount++
                            responseBuilder.append(event.text)
                            updateAssistantMessageText(assistantMsgId, responseBuilder.toString())
                        }
                        is TokenEvent.Error -> {
                            generationFailed = true
                            Log.e("ChatViewModel", "Inference error: ${event.throwable.message}", event.throwable)
                            updateAssistantMessageText(
                                assistantMsgId,
                                "Inference Error: ${event.throwable.message ?: "Unknown inference error"}"
                            )
                            _isGenerating.value = false
                        }
                        is TokenEvent.Completed -> {
                            Log.i(
                                "ChatViewModel",
                                "Inference completed: tokens=$tokenCount, elapsed=${System.currentTimeMillis() - startTime}ms"
                            )
                        }
                        is TokenEvent.Cancelled -> {
                            generationFailed = true
                            Log.w("ChatViewModel", "Inference cancelled")
                            _isGenerating.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                generationFailed = true
                Log.e("ChatViewModel", "Uncaught exception in sendMessage: ${e.message}", e)
                updateAssistantMessageText(assistantMsgId, "Inference Error: ${e.message ?: "Unknown error"}")
                _isGenerating.value = false
            }

            if (generationFailed) return@launch

            val rawResponse = responseBuilder.toString()
            if (tokenCount == 0) {
                Log.e("ChatViewModel", "Inference completed with zero generated tokens")
                updateAssistantMessageText(
                    assistantMsgId,
                    "Inference Failed: zero tokens were generated. Check the selected GGUF model and native logcat output."
                )
                _isGenerating.value = false
                return@launch
            }

            if (rawResponse.isBlank()) {
                updateAssistantMessageText(
                    assistantMsgId,
                    "Inference completed, but the model generated an empty response."
                )
                _isGenerating.value = false
                return@launch
            }

            if (currentMode == AssistantMode.AGENT) {
                val parseResult = FileOperationParser.parseJsonResponse(rawResponse)
                val parsedPatch = parseResult.getOrNull()
                val hasOps = parsedPatch?.operations?.isNotEmpty() == true
                val summaryText = if (parsedPatch?.summary?.isNotBlank() == true) parsedPatch.summary else rawResponse
                updateAssistantMessagePatch(assistantMsgId, summaryText, if (hasOps) parsedPatch else null)
            }

            conversationManager.addTurn(userText, rawResponse)
            _isGenerating.value = false
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
            list[index] = list[index].copy(text = summaryText, parsedPatch = patch)
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
