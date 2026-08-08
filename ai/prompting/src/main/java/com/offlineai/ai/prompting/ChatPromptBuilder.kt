package com.offlineai.ai.prompting

data class ChatPromptContext(
    val systemPrompt: String = DEFAULT_CHAT_SYSTEM_PROMPT,
    val conversationHistory: List<Pair<String, String>> = emptyList(),
    val userRequest: String,
    val modelPath: String? = null
) {
    companion object {
        const val DEFAULT_CHAT_SYSTEM_PROMPT = """You are an expert AI software engineer and a helpful assistant.
If the user greets you, respond politely.
When asked to generate code, you MUST output complete, fully functional, self-contained, working code files.
Never truncate code, never skip sections, and never use comments like '// TODO' or '...'.
Write all HTML, CSS, and JavaScript from start to finish without omitting any lines."""
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
