package com.offlineai.ai.prompting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTemplateDetectorTest {

    @Test
    fun testDetectFamily() {
        assertEquals(ModelFamily.QWEN, ModelTemplateDetector.detectFamily("/models/qwen2.5-coder-7b.gguf"))
        assertEquals(ModelFamily.LLAMA, ModelTemplateDetector.detectFamily("/models/llama-3.2-3b.gguf"))
        assertEquals(ModelFamily.GEMMA, ModelTemplateDetector.detectFamily("/models/gemma-2-9b.gguf"))
        assertEquals(ModelFamily.DEEPSEEK, ModelTemplateDetector.detectFamily("/models/deepseek-coder-6.7b.gguf"))
        assertEquals(ModelFamily.MISTRAL, ModelTemplateDetector.detectFamily("/models/mistral-7b-v0.3.gguf"))
        assertEquals(ModelFamily.PHI, ModelTemplateDetector.detectFamily("/models/phi-3-mini.gguf"))
        assertEquals(ModelFamily.GENERIC, ModelTemplateDetector.detectFamily("/models/custom-model.gguf"))
    }

    @Test
    fun testQwenPromptFormatting() {
        val prompt = ModelTemplateDetector.formatPrompt(
            family = ModelFamily.QWEN,
            systemText = "You are a helpful assistant.",
            history = listOf("Hello" to "Hi there!"),
            userText = "Write a function"
        )
        assertTrue(prompt.contains("<|im_start|>system\nYou are a helpful assistant.\n<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>user\nHello\n<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>assistant\nHi there!\n<|im_end|>"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun testLlamaPromptFormatting() {
        val prompt = ModelTemplateDetector.formatPrompt(
            family = ModelFamily.LLAMA,
            systemText = "System prompt",
            history = emptyList(),
            userText = "User prompt"
        )
        assertTrue(prompt.contains("<|start_header_id|>system<|end_header_id|>"))
        assertTrue(prompt.contains("<|start_header_id|>user<|end_header_id|>"))
        assertTrue(prompt.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"))
    }
}
