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
import kotlinx.coroutines.Job
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

    private var generationJob: Job? = null

    fun toggleDualMode(enabled: Boolean) { _isDualModeEnabled.value = enabled }
    fun setMode(mode: AssistantMode) { _activeMode.value = mode }
    fun clearHistory() {
        conversationManager.clear()
        _messages.value = emptyList()
        Log.i("ChatViewModel", "Conversation history cleared.")
    }
    fun toggleMemory(enabled: Boolean) { _isMemoryEnabled.value = enabled }

    fun stopGeneration() {
        Log.i("ChatViewModel", "stopGeneration() requested")
        generationJob?.cancel()
        generationJob = null
        viewModelScope.launch {
            try { dualManager.stopModelA() } catch (e: Exception) { Log.w("ChatViewModel", "stopModelA: ${e.message}") }
            try { dualManager.stopModelB() } catch (e: Exception) { Log.w("ChatViewModel", "stopModelB: ${e.message}") }
            _isGenerating.value = false
        }
    }

    fun sendMessage(userText: String, activeProjectDir: File?, modelPath: String? = null, systemPrompt: String? = null) {
        require(userText.isNotBlank()) { "User request text cannot be blank" }
        if (_isGenerating.value) stopGeneration()
        val effectiveModelPath = modelPath ?: activeSessionModelPath
        val currentMode = _activeMode.value
        val userMsg = ChatMessage(sender = "user", text = userText, mode = currentMode)
        val assistantMsgId = java.util.UUID.randomUUID().toString()
        val assistantPlaceholder = ChatMessage(id = assistantMsgId, sender = "assistant", text = "", mode = currentMode)
        _messages.value = _messages.value + userMsg + assistantPlaceholder
        _isGenerating.value = true

        generationJob = viewModelScope.launch {
            val responseBuilder = StringBuilder()
            var tokenCount = 0
            var generationFailed = false
            var stoppedByUserOrLoop = false
            try {
                if (currentMode == AssistantMode.GAME_STUDIO) {
                    val projectName = "OfflineGame_${System.currentTimeMillis()}"
                    gameOrchestrator.runGameGenerationPipeline(userText, projectName).collect { status ->
                        responseBuilder.append(status).append("\n")
                        updateAssistantMessageText(assistantMsgId, responseBuilder.toString(), null)
                        if (isRepetitiveNonsense(responseBuilder.toString())) {
                            stoppedByUserOrLoop = true
                            updateAssistantMessageText(assistantMsgId, responseBuilder.toString() + "\n\n⏹ Auto-stopped: model started repeating the same lines.", null)
                            stopGeneration()
                            return@collect
                        }
                    }
                    _isGenerating.value = false
                    return@launch
                }

                val history = if (_isMemoryEnabled.value) conversationManager.getHistoryPairs() else emptyList()
                val fullPrompt = if (currentMode == AssistantMode.CHAT) {
                    ChatPromptBuilder.buildPrompt(ChatPromptContext(
                        systemPrompt = systemPrompt ?: ChatPromptContext.DEFAULT_CHAT_SYSTEM_PROMPT,
                        conversationHistory = history, userRequest = userText, modelPath = effectiveModelPath
                    ))
                } else {
                    val fileTree = if (activeProjectDir != null) workspaceManager.getFileTree(activeProjectDir).children.map { it.path } else emptyList()
                    val activeContent = if (activeProjectDir != null) {
                        try { workspaceManager.readFileText(activeProjectDir, "index.html") } catch (e: Exception) { null }
                    } else null
                    AgentPromptBuilder.buildPrompt(AgentPromptContext(
                        projectSummary = activeProjectDir?.name ?: "Web Project",
                        fileTree = fileTree, activeFile = "index.html", activeFileContent = activeContent,
                        conversationHistory = history, userRequest = userText, modelPath = effectiveModelPath
                    ))
                }

                val family = ModelTemplateDetector.detectFamily(effectiveModelPath)
                val stopTokens = ModelTemplateDetector.getStopTokens(family)
                Log.i("ChatViewModel", "Starting inference: mode=$currentMode, family=$family, path=$effectiveModelPath")

                if (_isDualModeEnabled.value && dualManager.sessionA != null && dualManager.sessionB != null) {
                    var generationFailedA = false
                    try {
                        dualManager.streamModelA(fullPrompt, maxTokens = 2048, stopTokens = stopTokens).collect { event ->
                            when (event) {
                                is TokenEvent.Token -> {
                                    responseBuilder.append(event.text)
                                    updateAssistantMessageText(assistantMsgId, "Model A Drafting...\n\n${responseBuilder}", null)
                                    if (isRepetitiveNonsense(responseBuilder.toString())) {
                                        stoppedByUserOrLoop = true; generationFailedA = true
                                        updateAssistantMessageText(assistantMsgId, responseBuilder.toString() + "\n\n⏹ Auto-stopped: model started repeating lines of code.", null)
                                        stopGeneration()
                                    }
                                }
                                is TokenEvent.Error -> generationFailedA = true
                                is TokenEvent.Completed -> {}
                                is TokenEvent.Cancelled -> { generationFailedA = true; stoppedByUserOrLoop = true }
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) { stoppedByUserOrLoop = true; generationFailedA = true }
                    catch (e: Exception) { generationFailedA = true }

                    if (generationFailedA || responseBuilder.isEmpty()) generationFailed = true
                    else if (!stoppedByUserOrLoop) {
                        val reviewPrompt = "You are an expert AI code reviewer. Review, fix bugs, and improve. Output final polished version only.\n\nOriginal:\n${responseBuilder}\n\nPolished:\n"
                        val responseBuilderB = StringBuilder()
                        var generationFailedB = false
                        try {
                            dualManager.streamModelB(reviewPrompt, maxTokens = 2048, stopTokens = stopTokens).collect { event ->
                                when (event) {
                                    is TokenEvent.Token -> {
                                        responseBuilderB.append(event.text)
                                        updateAssistantMessageText(assistantMsgId, "Model B Refining...\n\n${responseBuilderB}", null)
                                        if (isRepetitiveNonsense(responseBuilderB.toString())) {
                                            stoppedByUserOrLoop = true; generationFailedB = true
                                            updateAssistantMessageText(assistantMsgId, responseBuilderB.toString() + "\n\n⏹ Auto-stopped: model started repeating lines of code.", null)
                                            stopGeneration()
                                        }
                                    }
                                    is TokenEvent.Error -> generationFailedB = true
                                    is TokenEvent.Completed -> {}
                                    is TokenEvent.Cancelled -> { generationFailedB = true; stoppedByUserOrLoop = true }
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) { stoppedByUserOrLoop = true; generationFailedB = true }
                        catch (e: Exception) { generationFailedB = true }
                        if (!generationFailedB && !stoppedByUserOrLoop) {
                            responseBuilder.clear(); responseBuilder.append(responseBuilderB.toString()); tokenCount = responseBuilder.length
                        } else generationFailed = true
                    }
                } else {
                    val flow = when {
                        dualManager.sessionA != null -> dualManager.streamModelA(fullPrompt, maxTokens = 2048, stopTokens = stopTokens)
                        dualManager.sessionB != null -> dualManager.streamModelB(fullPrompt, maxTokens = 2048, stopTokens = stopTokens)
                        else -> throw IllegalStateException("No models loaded in DualModelManager")
                    }
                    flow.collect { event ->
                        when (event) {
                            is TokenEvent.Token -> {
                                tokenCount++
                                responseBuilder.append(event.text)
                                updateAssistantMessageText(assistantMsgId, responseBuilder.toString(), null)
                                if (isRepetitiveNonsense(responseBuilder.toString())) {
                                    stoppedByUserOrLoop = true; generationFailed = true
                                    updateAssistantMessageText(assistantMsgId, responseBuilder.toString() + "\n\n⏹ Auto-stopped: model started repeating the same lines. Rephrase or try a different model.", null)
                                    stopGeneration()
                                }
                            }
                            is TokenEvent.Error -> {
                                Log.e("ChatViewModel", "Inference error: ${event.throwable.message}", event.throwable)
                                updateAssistantMessageText(assistantMsgId, "Inference Error: ${event.throwable.message}", null)
                                generationFailed = true
                            }
                            is TokenEvent.Completed -> {}
                            is TokenEvent.Cancelled -> {
                                stoppedByUserOrLoop = true; generationFailed = true
                                val partial = responseBuilder.toString().ifBlank { "(no tokens yet)" }
                                updateAssistantMessageText(assistantMsgId, "$partial\n\n⏹ Generation stopped.", null)
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                stoppedByUserOrLoop = true
                val partial = responseBuilder.toString()
                updateAssistantMessageText(assistantMsgId, if (partial.isNotBlank()) "$partial\n\n⏹ Generation stopped by user." else "⏹ Generation stopped by user.", null)
                _isGenerating.value = false
                return@launch
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Uncaught exception in sendMessage: ${e.message}", e)
                updateAssistantMessageText(assistantMsgId, "Inference Error: ${e.message}", null)
                _isGenerating.value = false
                return@launch
            }

            if (stoppedByUserOrLoop) { _isGenerating.value = false; return@launch }

            if (!generationFailed) {
                val rawResponse = responseBuilder.toString()
                if (tokenCount == 0 && rawResponse.isBlank()) {
                    updateAssistantMessageText(assistantMsgId, "Inference Failed: Zero tokens generated by GGUF model. Please check the logs.", null)
                    _isGenerating.value = false; return@launch
                } else if (rawResponse.isBlank()) {
                    updateAssistantMessageText(assistantMsgId, "Inference completed, but the model generated an empty response. Try adjusting your prompt.", null)
                    _isGenerating.value = false; return@launch
                } else if (isRepetitiveNonsense(rawResponse)) {
                    updateAssistantMessageText(assistantMsgId, "The AI encountered a generation loop and produced repetitive text. Try modifying your prompt.", null)
                    _isGenerating.value = false; return@launch
                }
                val historyPairs = conversationManager.getHistoryPairs()
                if (historyPairs.isNotEmpty() && historyPairs.last().second.trim() == rawResponse.trim()) {
                    updateAssistantMessageText(assistantMsgId, "The AI generated the exact same response as before. Please rephrase your prompt.", null)
                    _isGenerating.value = false; return@launch
                }
                if (currentMode == AssistantMode.AGENT) {
                    val parseResult = FileOperationParser.parseJsonResponse(rawResponse)
                    val parsedPatch = parseResult.getOrNull()
                    val hasOps = parsedPatch != null && parsedPatch.operations.isNotEmpty()
                    val summaryText = if (parsedPatch != null && parsedPatch.summary.isNotBlank()) parsedPatch.summary else rawResponse
                    updateAssistantMessagePatch(assistantMsgId, summaryText, if (hasOps) parsedPatch else null)
                }
                conversationManager.addTurn(userText, rawResponse)
                _isGenerating.value = false
            } else {
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
                    list[index] = msg.copy(isApplied = true, text = msg.text + "\n\nApplied Changes:\n- " + logs.joinToString("\n- "))
                    _messages.value = list
                }
            }
        }
    }

    fun createProjectFromChatCode(messageId: String, onProjectCreated: () -> Unit = {}) {
        viewModelScope.launch {
            val list = _messages.value.toMutableList()
            val index = list.indexOfFirst { it.id == messageId }
            if (index == -1) return@launch
            val msg = list[index]
            val rawText = msg.text
            val projectName = "AIGeneratedProject_${System.currentTimeMillis()}"
            val filesWritten = mutableListOf<String>()
            try {
                val fenceRegex = Regex("""```([a-zA-Z0-9_+.-]*)\n([\s\S]*?)```""")
                val fences = fenceRegex.findAll(rawText).map { match ->
                    match.groupValues[1].lowercase().trim() to match.groupValues[2].trim()
                }.toList()

                val isWeb = fences.any { (lang, body) ->
                    lang in listOf("html", "htm", "css", "js", "javascript") ||
                        body.contains("<!DOCTYPE html>", ignoreCase = true) ||
                        body.contains("<html", ignoreCase = true) ||
                        body.contains("<body", ignoreCase = true)
                } || rawText.contains("<!DOCTYPE html>", ignoreCase = true) || rawText.contains("<html", ignoreCase = true)

                if (isWeb) {
                    val projDir = workspaceManager.createProjectDirectory(projectName)
                    var htmlContent: String? = null
                    var cssContent: String? = null
                    var jsContent: String? = null
                    for ((lang, body) in fences) {
                        when {
                            lang in listOf("html", "htm") || body.contains("<!DOCTYPE html>", ignoreCase = true) || body.contains("<html", ignoreCase = true) ->
                                if (htmlContent == null) htmlContent = body
                            lang == "css" -> if (cssContent == null) cssContent = body
                            lang in listOf("js", "javascript") -> if (jsContent == null) jsContent = body
                        }
                    }
                    if (htmlContent == null) {
                        val single = fences.firstOrNull()?.second ?: rawText
                        if (single.contains("<html", ignoreCase = true) || single.contains("<!DOCTYPE", ignoreCase = true) || single.contains("<body", ignoreCase = true))
                            htmlContent = single
                    }
                    if (htmlContent != null) {
                        if (cssContent == null) {
                            val styleMatch = Regex("""<style[^>]*>([\s\S]*?)</style>""", RegexOption.IGNORE_CASE).find(htmlContent!!)
                            if (styleMatch != null) {
                                cssContent = styleMatch.groupValues[1].trim()
                                htmlContent = htmlContent!!.replace(styleMatch.value, "")
                            }
                        }
                        if (jsContent == null) {
                            val scriptMatch = Regex("""<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE).find(htmlContent!!)
                            if (scriptMatch != null && scriptMatch.groupValues[1].isNotBlank()) {
                                jsContent = scriptMatch.groupValues[1].trim()
                                htmlContent = htmlContent!!.replace(scriptMatch.value, "")
                            }
                        }
                        if (!htmlContent!!.contains("css/style.css", ignoreCase = true) && !htmlContent!!.contains("href=\"style.css\"", ignoreCase = true)) {
                            htmlContent = if (htmlContent!!.contains("</head>", ignoreCase = true))
                                htmlContent!!.replace(Regex("</head>", RegexOption.IGNORE_CASE), "    <link rel=\"stylesheet\" href=\"css/style.css\">\n</head>")
                            else htmlContent + "\n<link rel=\"stylesheet\" href=\"css/style.css\">\n"
                        }
                        if (!htmlContent!!.contains("js/main.js", ignoreCase = true) && !htmlContent!!.contains("src=\"script.js\"", ignoreCase = true) && !htmlContent!!.contains("src=\"main.js\"", ignoreCase = true)) {
                            htmlContent = if (htmlContent!!.contains("</body>", ignoreCase = true))
                                htmlContent!!.replace(Regex("</body>", RegexOption.IGNORE_CASE), "    <script src=\"js/main.js\"></script>\n</body>")
                            else htmlContent + "\n<script src=\"js/main.js\"></script>\n"
                        }
                        workspaceManager.writeFileText(projDir, "index.html", htmlContent!!.trim())
                        filesWritten.add("index.html")
                    }
                    if (cssContent != null) {
                        workspaceManager.writeFileText(projDir, "css/style.css", cssContent!!.trim())
                        filesWritten.add("css/style.css")
                    }
                    if (jsContent != null) {
                        workspaceManager.writeFileText(projDir, "js/main.js", jsContent!!.trim())
                        filesWritten.add("js/main.js")
                    }
                    if (filesWritten.isEmpty()) {
                        workspaceManager.writeFileText(projDir, "index.html", rawText)
                        filesWritten.add("index.html")
                    }
                    val summary = filesWritten.joinToString(", ")
                    list[index] = msg.copy(text = msg.text + "\n\n✅ Project created: $projectName\nFiles written: $summary\n(Full scaffold includes css/, js/, assets/ — open Projects tab to edit.)")
                } else {
                    val codeSnippet = fences.firstOrNull()?.second ?: rawText
                    val baseDir = File(workspaceManager.projectsDir, projectName)
                    if (!baseDir.exists()) baseDir.mkdirs()
                    File(baseDir, "MainActivity.kt").writeText(codeSnippet)
                    filesWritten.add("MainActivity.kt")
                    list[index] = msg.copy(text = msg.text + "\n\n✅ Transferred to Projects Tab: $projectName (MainActivity.kt)")
                }
                _messages.value = list
                onProjectCreated()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "createProjectFromChatCode failed: ${e.message}", e)
                list[index] = msg.copy(text = msg.text + "\n\n❌ Failed to create project: ${e.message}")
                _messages.value = list
            }
        }
    }

    private fun isRepetitiveNonsense(text: String): Boolean {
        if (text.length < 120) return false
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 50) {
            val last50 = words.takeLast(50)
            if (last50.distinct().size < 10) return true
        }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 6) {
            var streak = 1
            for (i in 1 until lines.size) {
                if (lines[i] == lines[i - 1] && lines[i].length > 2) {
                    streak++
                    if (streak >= 6) return true
                } else streak = 1
            }
        }
        if (text.length > 200) {
            val tail = text.takeLast(800)
            val chunks = tail.chunked(40)
            if (chunks.size >= 8) {
                val mostCommon = chunks.groupingBy { it }.eachCount().maxByOrNull { it.value }
                if (mostCommon != null && mostCommon.value >= 6) return true
            }
        }
        return false
    }
}
