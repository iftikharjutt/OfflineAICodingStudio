package com.offlineai.ai.prompting

data class ChatPromptContext(
    val systemPrompt: String = DEFAULT_CHAT_SYSTEM_PROMPT,
    val conversationHistory: List<Pair<String, String>> = emptyList(),
    val userRequest: String,
    val modelPath: String? = null
) {
    companion object {
        const val DEFAULT_CHAT_SYSTEM_PROMPT = """You are an expert AI software engineer pair programming with the user.
Provide clear, natural, helpful responses in clean Markdown format with syntax-highlighted code blocks when appropriate.
Be concise, accurate, and direct. Do not force JSON formatting in chat."""
    }
}

object ChatPromptBuilder {

    fun buildPrompt(context: ChatPromptContext): String {
        val family = ModelTemplateDetector.detectFamily(context.modelPath)
        return ModelTemplateDetector.formatPrompt(
            family = family,
            systemText = context.systemPrompt,
            history = context.conversationHistory,
            userText = context.userRequest
        )
    }
}
