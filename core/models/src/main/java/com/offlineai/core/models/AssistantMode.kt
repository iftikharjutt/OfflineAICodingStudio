package com.offlineai.core.models

enum class AssistantMode {
    CHAT,  // Natural conversation (Markdown, Code blocks, Multi-turn memory, No JSON)
    AGENT  // Autonomous Coding (JSON Patch generation & execution)
}
