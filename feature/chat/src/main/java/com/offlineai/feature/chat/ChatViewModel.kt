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
import com.offlineai.ai.runtime.DualModelManager
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
    val isApplied: Boolean = false,
    val textB: String? = null
)

class ChatViewModel(
    private val workspaceManager: WorkspaceManager,
    private val dualManager: DualModelManager
) : ViewModel() {

    private val executor = AgenticPatchExecutor(workspaceManager)
    private val conversationManager = ConversationManager(maxTurnsHistory = 10)
    private val gameOrchestrator = com.offlineai.ai.agent.GameOrchestrator(workspaceManager, dualManager)

    private val _activeMode = MutableStateFlow(AssistantMode.CHAT)
    val activeMode: StateFlow<AssistantMode> = _activeMode.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isMemoryEnabled = MutableStateFlow(true)
    val isMemoryEnabled: StateFlow<Boolean> = _isMemoryEnabled.asStateFlow()


    var activeSessionModelPath: String? = null
    
    private val _isDualModeEnabled = MutableStateFlow(false)
    val isDualModeEnabled: StateFlow<Boolean> = _isDualModeEnabled.asStateFlow()

    fun toggleDualMode(enabled: Boolean) {
        _isDualModeEnabled.value = enabled
    }

    fun setMode(mode: AssistantMode) {
        _activeMode.value = mode
    }

    fun clearHistory() {
        conversationManager.clear()
        _messages.value = emptyList()
        Log.i("ChatViewModel", "Conversation history cleared.")
    }

    fun toggleMemory(enabled: Boolean) {
        _isMemoryEnabled.value = enabled
    }


    fun sendMessage(userText: String, activeProjectDir: File?, modelPath: String? = null, systemPrompt: String? = null) {
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
                if (currentMode == AssistantMode.GAME_STUDIO) {
                    val projectName = "OfflineGame_${System.currentTimeMillis()}"
                    gameOrchestrator.runGameGenerationPipeline(userText, projectName).collect { status ->
                        responseBuilder.append(status).append("\n")
                        updateAssistantMessageText(assistantMsgId, responseBuilder.toString(), null)
                    }
                    _isGenerating.value = false
                    return@launch
                }

                val history = if (_isMemoryEnabled.value) conversationManager.getHistoryPairs() else emptyList()

                val fullPrompt = if (currentMode == AssistantMode.CHAT) {
                    val chatContext = ChatPromptContext(
                        systemPrompt = systemPrompt ?: ChatPromptContext.DEFAULT_CHAT_SYSTEM_PROMPT,
                        conversationHistory = history,
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
                        conversationHistory = history,
                        userRequest = userText,
                        modelPath = effectiveModelPath
                    )
                    // We can optionally use systemPrompt if AgentPromptBuilder supported it, but let's just stick to the context for Chat for now, 
                    // or override it in AgentPromptContext if needed. For now, Agent uses a strict JSON instruction.
                    AgentPromptBuilder.buildPrompt(agentContext)
                }

                val family = ModelTemplateDetector.detectFamily(effectiveModelPath)
                val stopTokens = ModelTemplateDetector.getStopTokens(family)

                Log.i("ChatViewModel", "Starting inference: mode=$currentMode, family=$family, effectiveModelPath=$effectiveModelPath, promptLen=${fullPrompt.length}")

                val startTime = System.currentTimeMillis()

                val reqStartTime = System.currentTimeMillis()
                
                if (_isDualModeEnabled.value && dualManager.sessionA != null && dualManager.sessionB != null) {
                    // Collaborative Review Mode
                    var generationFailedA = false
                    
                    try {
                        dualManager.streamModelA(fullPrompt, maxTokens = 2048, stopTokens = stopTokens).collect { event ->
                            when (event) {
                                is TokenEvent.Token -> {
                                    responseBuilder.append(event.text)
                                    updateAssistantMessageText(assistantMsgId, "Model A Drafting...\n\n${responseBuilder.toString()}", null)
                                }
                                is TokenEvent.Error -> generationFailedA = true
                                is TokenEvent.Completed -> {}
                                is TokenEvent.Cancelled -> generationFailedA = true
                            }
                        }
                    } catch(e: Exception) { generationFailedA = true }
                    
                    if (generationFailedA || responseBuilder.isEmpty()) {
                        generationFailed = true
                    } else {
                        // Model B Reviews
                        val reviewPrompt = "You are an expert AI code reviewer. Please review, fix any bugs, and improve the following response. Output the final polished version directly.\n\nOriginal Response:\n${responseBuilder.toString()}\n\nPolished Version:\n"
                        val responseBuilderB = StringBuilder()
                        var generationFailedB = false
                        
                        try {
                            dualManager.streamModelB(reviewPrompt, maxTokens = 2048, stopTokens = stopTokens).collect { event ->
                                when (event) {
                                    is TokenEvent.Token -> {
                                        responseBuilderB.append(event.text)
                                        updateAssistantMessageText(assistantMsgId, "Model B Refining...\n\n${responseBuilderB.toString()}", null)
                                    }
                                    is TokenEvent.Error -> generationFailedB = true
                                    is TokenEvent.Completed -> {}
                                    is TokenEvent.Cancelled -> generationFailedB = true
                                }
                            }
                        } catch(e: Exception) { generationFailedB = true }
                        
                        if (!generationFailedB) {
                            responseBuilder.clear()
                            responseBuilder.append(responseBuilderB.toString())
                            tokenCount = responseBuilder.length
                        } else {
                            generationFailed = true
                        }
                    }
                } else {
                    // Single Mode
                    val flow = if (dualManager.sessionA != null) {
                        dualManager.streamModelA(fullPrompt, maxTokens = 2048, stopTokens = stopTokens)
                    } else if (dualManager.sessionB != null) {
                        dualManager.streamModelB(fullPrompt, maxTokens = 2048, stopTokens = stopTokens)
                    } else {
                        throw IllegalStateException("No models loaded in DualModelManager")
                    }
                    
                    flow.collect { event ->
                        when (event) {
                            is TokenEvent.Token -> {
                                tokenCount++
                                responseBuilder.append(event.text)
                                updateAssistantMessageText(assistantMsgId, responseBuilder.toString(), null)
                            }
                            is TokenEvent.Error -> {
                                Log.e("ChatViewModel", "Inference error: ${event.throwable.message}", event.throwable)
                                updateAssistantMessageText(assistantMsgId, "Inference Error: ${event.throwable.message}", null)
                                generationFailed = true
                            }
                            is TokenEvent.Completed -> {}
                            is TokenEvent.Cancelled -> generationFailed = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Uncaught exception in sendMessage: ${e.message}", e)
                updateAssistantMessageText(assistantMsgId, "Inference Error: ${e.message}", null)
                _isGenerating.value = false
                return@launch
            }

            if (!generationFailed) {
                val rawResponse = responseBuilder.toString()

                if (tokenCount == 0) {
                    Log.e("ChatViewModel", "Inference failed: Zero tokens generated")
                    updateAssistantMessageText(assistantMsgId, "Inference Failed: Zero tokens generated by GGUF model. Please check the logs.", null)
                    _isGenerating.value = false
                    return@launch
                } else if (rawResponse.isBlank()) {
                    Log.w("ChatViewModel", "Inference completed but response was entirely whitespace/blank.")
                    updateAssistantMessageText(assistantMsgId, "Inference completed, but the model generated an empty or whitespace response. Try adjusting your prompt.", null)
                    _isGenerating.value = false
                    return@launch
                } else if (isRepetitiveNonsense(rawResponse)) {
                    Log.w("ChatViewModel", "Inference produced repetitive nonsense.")
                    updateAssistantMessageText(assistantMsgId, "The AI encountered a generation loop and produced repetitive text. Please try modifying your prompt or restarting the chat.", null)
                    _isGenerating.value = false
                    return@launch
                }
                
                val historyPairs = conversationManager.getHistoryPairs()
                if (historyPairs.isNotEmpty() && historyPairs.last().second.trim() == rawResponse.trim()) {
                    Log.w("ChatViewModel", "Inference produced the exact same response as last time.")
                    updateAssistantMessageText(assistantMsgId, "The AI generated the exact same response as before. Please try rephrasing your prompt to break the loop.", null)
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

    private fun updateAssistantMessageText(messageId: String, newText: String, newTextB: String?) {
        val list = _messages.value.toMutableList()
        val index = list.indexOfFirst { it.id == messageId }
        if (index != -1) {
            list[index] = list[index].copy(text = newText, textB = newTextB)
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

    fun createProjectFromChatCode(messageId: String, onProjectCreated: () -> Unit = {}) {
        viewModelScope.launch {
            val list = _messages.value.toMutableList()
            val index = list.indexOfFirst { it.id == messageId }
            if (index != -1) {
                val msg = list[index]
                val rawText = msg.text
                
                val regex = Regex("```(?:[a-zA-Z]*)\\n([\\s\\S]*?)```")
                val match = regex.find(rawText)
                val codeSnippet = match?.groupValues?.get(1) ?: rawText

                val isWeb = codeSnippet.contains("<!DOCTYPE html>") || codeSnippet.contains("<html>") || codeSnippet.contains("<body>")
                val projectName = "AIGeneratedProject_${System.currentTimeMillis()}"
                
                val baseDir = File(workspaceManager.projectsDir, projectName)
                if (!baseDir.exists()) {
                    baseDir.mkdirs()
                }

                if (isWeb) {
                    File(baseDir, "index.html").writeText(codeSnippet)
                } else {
                    File(baseDir, "MainActivity.kt").writeText(codeSnippet)
                }
                
                list[index] = msg.copy(
                    text = msg.text + "\n\n✅ Transferred to Projects Tab: $projectName"
                )
                _messages.value = list
                onProjectCreated()
            }
        }
    }
    
    private fun isRepetitiveNonsense(text: String): Boolean {
        if (text.length < 100) return false
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 50) {
            val last50 = words.takeLast(50)
            val uniqueInLast50 = last50.distinct().size
            if (uniqueInLast50 < 10) return true
        }
        return false
    }
}
