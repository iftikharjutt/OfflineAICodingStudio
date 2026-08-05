package com.offlineai.ai.prompting

enum class ModelFamily {
    QWEN,
    LLAMA,
    GEMMA,
    DEEPSEEK,
    MISTRAL,
    PHI,
    GENERIC
}

object ModelTemplateDetector {

    fun detectFamily(modelPath: String?): ModelFamily {
        if (modelPath == null) return ModelFamily.GENERIC
        val lower = modelPath.lowercase()
        return when {
            lower.contains("qwen") -> ModelFamily.QWEN
            lower.contains("llama") -> ModelFamily.LLAMA
            lower.contains("gemma") -> ModelFamily.GEMMA
            lower.contains("deepseek") -> ModelFamily.DEEPSEEK
            lower.contains("mistral") || lower.contains("mixtral") -> ModelFamily.MISTRAL
            lower.contains("phi") -> ModelFamily.PHI
            else -> ModelFamily.GENERIC
        }
    }

    fun getStopTokens(family: ModelFamily): List<String> {
        return when (family) {
            ModelFamily.QWEN -> listOf("<|im_end|>", "<|im_start|>", "<|endoftext|>")
            ModelFamily.LLAMA -> listOf("<|eot_id|>", "<|start_header_id|>", "<|eom_id|>")
            ModelFamily.GEMMA -> listOf("<end_of_turn>", "<start_of_turn>")
            ModelFamily.DEEPSEEK -> listOf("<|end_of_sentence|>", "<|EOT|>", "<|im_end|>")
            ModelFamily.MISTRAL -> listOf("</s>", "[INST]")
            ModelFamily.PHI -> listOf("<|end|>", "<|user|>", "<|assistant|>")
            ModelFamily.GENERIC -> listOf("User:", "System:", "</s>")
        }
    }

    fun formatPrompt(
        family: ModelFamily,
        systemText: String,
        history: List<Pair<String, String>>,
        userText: String
    ): String {
        return when (family) {
            ModelFamily.QWEN -> buildQwenPrompt(systemText, history, userText)
            ModelFamily.LLAMA -> buildLlamaPrompt(systemText, history, userText)
            ModelFamily.GEMMA -> buildGemmaPrompt(systemText, history, userText)
            ModelFamily.DEEPSEEK -> buildDeepSeekPrompt(systemText, history, userText)
            ModelFamily.MISTRAL -> buildMistralPrompt(systemText, history, userText)
            ModelFamily.PHI -> buildPhiPrompt(systemText, history, userText)
            ModelFamily.GENERIC -> buildGenericPrompt(systemText, history, userText)
        }
    }

    private fun buildQwenPrompt(systemText: String, history: List<Pair<String, String>>, userText: String): String {
        val sb = StringBuilder()
        if (systemText.isNotBlank()) {
            sb.append("<|im_start|>system\n").append(systemText).append("\n<|im_end|>\n")
        }
        for ((u, a) in history) {
            sb.append("<|im_start|>user\n").append(u).append("\n<|im_end|>\n")
            if (a.isNotBlank()) {
                sb.append("<|im_start|>assistant\n").append(a).append("\n<|im_end|>\n")
            }
        }
        sb.append("<|im_start|>user\n").append(userText).append("\n<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun buildLlamaPrompt(systemText: String, history: List<Pair<String, String>>, userText: String): String {
        val sb = StringBuilder()
        if (systemText.isNotBlank()) {
            sb.append("<|start_header_id|>system<|end_header_id|>\n\n").append(systemText).append("<|eot_id|>")
        }
        for ((u, a) in history) {
            sb.append("<|start_header_id|>user<|end_header_id|>\n\n").append(u).append("<|eot_id|>")
            if (a.isNotBlank()) {
                sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n").append(a).append("<|eot_id|>")
            }
        }
        sb.append("<|start_header_id|>user<|end_header_id|>\n\n").append(userText).append("<|eot_id|>")
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    private fun buildGemmaPrompt(systemText: String, history: List<Pair<String, String>>, userText: String): String {
        val sb = StringBuilder()
        var firstTurn = true
        for ((u, a) in history) {
            sb.append("<start_of_turn>user\n")
            if (firstTurn && systemText.isNotBlank()) {
                sb.append("System Instructions:\n").append(systemText).append("\n\n")
                firstTurn = false
            }
            sb.append(u).append("<end_of_turn>\n")
            if (a.isNotBlank()) {
                sb.append("<start_of_turn>model\n").append(a).append("<end_of_turn>\n")
            }
        }
        sb.append("<start_of_turn>user\n")
        if (firstTurn && systemText.isNotBlank()) {
            sb.append("System Instructions:\n").append(systemText).append("\n\n")
        }
        sb.append(userText).append("<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun buildDeepSeekPrompt(systemText: String, history: List<Pair<String, String>>, userText: String): String {
        val sb = StringBuilder("<|begin_of_sentence|>")
        if (systemText.isNotBlank()) {
            sb.append(systemText).append("\n\n")
        }
        for ((u, a) in history) {
            sb.append("<|User|>").append(u).append("\n")
            if (a.isNotBlank()) {
                sb.append("<|Assistant|>").append(a).append("<|end_of_sentence|>\n")
            }
        }
        sb.append("<|User|>").append(userText).append("\n")
        sb.append("<|Assistant|>")
        return sb.toString()
    }

    private fun buildMistralPrompt(systemText: String, history: List<Pair<String, String>>, userText: String): String {
        val sb = StringBuilder()
        var first = true
        for ((u, a) in history) {
            sb.append("[INST] ")
            if (first && systemText.isNotBlank()) {
                sb.append(systemText).append("\n\n")
                first = false
            }
            sb.append(u).append(" [/INST] ")
            if (a.isNotBlank()) {
                sb.append(a).append("</s>")
            }
        }
        sb.append("[INST] ")
        if (first && systemText.isNotBlank()) {
            sb.append(systemText).append("\n\n")
        }
        sb.append(userText).append(" [/INST]")
        return sb.toString()
    }

    private fun buildPhiPrompt(systemText: String, history: List<Pair<String, String>>, userText: String): String {
        val sb = StringBuilder()
        if (systemText.isNotBlank()) {
            sb.append("<|system|>\n").append(systemText).append("<|end|>\n")
        }
        for ((u, a) in history) {
            sb.append("<|user|>\n").append(u).append("<|end|>\n")
            if (a.isNotBlank()) {
                sb.append("<|assistant|>\n").append(a).append("<|end|>\n")
            }
        }
        sb.append("<|user|>\n").append(userText).append("<|end|>\n")
        sb.append("<|assistant|>\n")
        return sb.toString()
    }

    private fun buildGenericPrompt(systemText: String, history: List<Pair<String, String>>, userText: String): String {
        val sb = StringBuilder()
        if (systemText.isNotBlank()) {
            sb.append("System: ").append(systemText).append("\n\n")
        }
        for ((u, a) in history) {
            sb.append("User: ").append(u).append("\n")
            if (a.isNotBlank()) {
                sb.append("Assistant: ").append(a).append("\n\n")
            }
        }
        sb.append("User: ").append(userText).append("\n")
        sb.append("Assistant: ")
        return sb.toString()
    }
}
