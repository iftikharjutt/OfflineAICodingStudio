package com.offlineai.ai.prompting

data class PromptBuilderContext(
    val projectSummary: String,
    val fileTree: List<String>,
    val activeFile: String? = null,
    val activeFileContent: String? = null,
    val recentErrors: List<String> = emptyList(),
    val userRequest: String
)

object StructuredPromptBuilder {

    fun buildSystemPrompt(): String {
        return """
You are an expert offline AI coding assistant.
Your task is to generate, edit, modify, or repair web projects (HTML, CSS, JavaScript).

CRITICAL INSTRUCTIONS:
1. You MUST respond ONLY with a valid JSON object matching the schema below.
2. Do NOT include markdown code block backticks (like ```json) or text before/after JSON.
3. Every operation must be precise.

JSON SCHEMA:
{
  "summary": "Brief explanation of changes",
  "operations": [
    {
      "type": "create_file",
      "path": "index.html",
      "content": "<!doctype html>..."
    },
    {
      "type": "replace_block",
      "path": "style.css",
      "find": "body { }",
      "replace": "body { margin: 0; background: #111; }"
    },
    {
      "type": "replace_file",
      "path": "script.js",
      "content": "console.log('Hello');"
    },
    {
      "type": "delete_file",
      "path": "unused.txt"
    },
    {
      "type": "create_directory",
      "path": "assets"
    }
  ]
}
        """.trimIndent()
    }

    fun buildUserPrompt(context: PromptBuilderContext): String {
        val filesStr = context.fileTree.joinToString("\n- ")
        val errorsStr = if (context.recentErrors.isNotEmpty()) {
            "RECENT ERRORS:\n" + context.recentErrors.joinToString("\n- ")
        } else ""

        val activeFileStr = if (context.activeFile != null && context.activeFileContent != null) {
            "ACTIVE FILE (${context.activeFile}):\n${context.activeFileContent}\n"
        } else ""

        return """
PROJECT SUMMARY: ${context.projectSummary}
FILES:
- $filesStr

$activeFileStr
$errorsStr

USER REQUEST:
${context.userRequest}
        """.trimIndent()
    }
}
