package com.offlineai.ai.prompting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptBuilderTest {

    @Test
    fun testChatPromptDoesNotForceJson() {
        val context = ChatPromptContext(
            userRequest = "Write a python calculator function",
            modelPath = "qwen2.5-coder.gguf"
        )
        val prompt = ChatPromptBuilder.buildPrompt(context)

        assertFalse("Chat prompt should NOT contain JSON operations instructions", prompt.contains("\"operations\""))
        assertFalse("Chat prompt should NOT contain JSON schema instructions", prompt.contains("\"create_file\""))
        assertTrue("Chat prompt should contain user request", prompt.contains("Write a python calculator function"))
    }
}
