package com.offlineai.ai.prompting

data class PromptBuilderContext(
    val projectSummary: String,
    val fileTree: List<String>,
    val activeFile: String? = null,
    val activeFileContent: String? = null,
    val recentErrors: List<String> = emptyList(),
    val userRequest: String
)

@Deprecated("Use ChatPromptBuilder or AgentPromptBuilder instead")
object StructuredPromptBuilder {

    fun buildSystemPrompt(): String = AgentPromptBuilder.buildSystemPrompt()

    fun buildUserPrompt(context: PromptBuilderContext): String {
        val agentContext = AgentPromptContext(
            projectSummary = context.projectSummary,
            fileTree = context.fileTree,
            activeFile = context.activeFile,
            activeFileContent = context.activeFileContent,
            recentErrors = context.recentErrors,
            userRequest = context.userRequest
        )
        return AgentPromptBuilder.buildUserText(agentContext)
    }

    fun buildFullChatPrompt(context: PromptBuilderContext): String {
        val agentContext = AgentPromptContext(
            projectSummary = context.projectSummary,
            fileTree = context.fileTree,
            activeFile = context.activeFile,
            activeFileContent = context.activeFileContent,
            recentErrors = context.recentErrors,
            userRequest = context.userRequest
        )
        return AgentPromptBuilder.buildPrompt(agentContext)
    }
}
