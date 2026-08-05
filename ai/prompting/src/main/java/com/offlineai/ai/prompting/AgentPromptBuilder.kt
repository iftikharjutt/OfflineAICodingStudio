package com.offlineai.ai.prompting

data class AgentPromptContext(
    val projectSummary: String,
    val fileTree: List<String>,
    val activeFile: String? = null,
    val activeFileContent: String? = null,
    val recentErrors: List<String> = emptyList(),
    val conversationHistory: List<Pair<String, String>> = emptyList(),
    val userRequest: String,
    val modelPath: String? = null
)

object AgentPromptBuilder {

    fun buildSystemPrompt(): String {
        return """You are an autonomous AI software engineer for web projects (HTML, CSS, JavaScript).
Your goal is to inspect the project context and generate precise file operations to accomplish the user request.

You MUST output ONLY a single valid JSON object. No markdown backticks (no ```json), no conversation text outside JSON.

JSON SCHEMA:
{
  "summary": "Brief explanation of the changes made",
  "operations": [
    { "type": "create_file", "path": "<relative path>", "content": "<full file content>" },
    { "type": "replace_file", "path": "<relative path>", "content": "<full new content>" },
    { "type": "replace_block", "path": "<relative path>", "find": "<exact find text>", "replace": "<replacement text>" },
    { "type": "delete_file", "path": "<relative path>" },
    { "type": "create_directory", "path": "<relative path>" }
  ]
}

Rules:
- Generate complete, working code for requested files.
- Put changes in the "operations" array and a clear description in "summary"."""
    }

    fun buildUserText(context: AgentPromptContext): String {
        val filesStr = if (context.fileTree.isNotEmpty()) context.fileTree.joinToString("\n- ") else "No files yet"
        val errorsStr = if (context.recentErrors.isNotEmpty()) {
            "\nRECENT ERRORS:\n" + context.recentErrors.joinToString("\n- ")
        } else ""
        val activeFileStr = if (context.activeFile != null && context.activeFileContent != null) {
            "\nACTIVE FILE (${context.activeFile}):\n${context.activeFileContent.take(3000)}\n"
        } else ""

        return """PROJECT: ${context.projectSummary}
FILES:
- $filesStr
$activeFileStr$errorsStr
USER REQUEST: ${context.userRequest}"""
    }

    fun buildPrompt(context: AgentPromptContext): String {
        val family = ModelTemplateDetector.detectFamily(context.modelPath)
        return ModelTemplateDetector.formatPrompt(
            family = family,
            systemText = buildSystemPrompt(),
            history = context.conversationHistory,
            userText = buildUserText(context)
        )
    }
}
