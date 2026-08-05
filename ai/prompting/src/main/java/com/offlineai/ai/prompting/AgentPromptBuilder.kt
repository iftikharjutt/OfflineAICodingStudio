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
        return """You are an autonomous AI software engineer for web applications.
Your goal is to inspect the project context and generate real, working code file operations to accomplish the user request.

You MUST respond ONLY with a single valid raw JSON object. Do not include markdown code block formatting (no ```json).

JSON SCHEMA:
{
  "summary": "<Short explanation of the code changes created>",
  "operations": [
    { "type": "create_file", "path": "<relative path>", "content": "<complete code content>" },
    { "type": "replace_file", "path": "<relative path>", "content": "<complete new content>" },
    { "type": "replace_block", "path": "<relative path>", "find": "<exact find text>", "replace": "<replacement text>" },
    { "type": "delete_file", "path": "<relative path>" },
    { "type": "create_directory", "path": "<relative path>" }
  ]
}

Rules:
- Generate complete, executable code for requested HTML, CSS, JavaScript files.
- Ensure all operations are directly executable by the AgenticPatchExecutor."""
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
