package com.offlineai.ai.prompting

import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptBuilderTest {

    @Test
    fun testAgentPromptGeneratesJsonSchema() {
        val context = AgentPromptContext(
            projectSummary = "My Web App",
            fileTree = listOf("index.html", "style.css"),
            userRequest = "Add dark mode toggle",
            modelPath = "qwen2.5-coder.gguf"
        )
        val prompt = AgentPromptBuilder.buildPrompt(context)

        assertTrue("Agent prompt must include JSON schema instructions", prompt.contains("\"operations\""))
        assertTrue("Agent prompt must include create_file instruction", prompt.contains("create_file"))
        assertTrue("Agent prompt must contain user request", prompt.contains("Add dark mode toggle"))
    }
}
