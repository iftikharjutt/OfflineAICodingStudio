package com.offlineai.ai.runtime

import kotlinx.coroutines.flow.Flow

class NativeInferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ModelNotLoadedException(message: String = "No GGUF model loaded in session.") : Exception(message)

data class ModelLoadRequest(
    val modelPath: String,
    val contextSize: Int = 4096,
    val threadCount: Int = 4,
    val useMmap: Boolean = true
)

data class ModelSession(
    val sessionId: String,
    val modelPath: String,
    val contextSize: Int,
    val loadedAt: Long
)

data class CompletionRequest(
    val sessionId: String,
    val prompt: String,
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val repeatPenalty: Float = 1.1f,
    val stopSequences: List<String> = emptyList(),
    val modelPath: String? = null
)

sealed interface TokenEvent {
    data class Token(val text: String) : TokenEvent
    data class Error(val throwable: Throwable) : TokenEvent
    data object Completed : TokenEvent
    data object Cancelled : TokenEvent
}

interface LlamaInferenceEngine {
    suspend fun loadModel(request: ModelLoadRequest): Result<ModelSession>
    suspend fun unloadModel(sessionId: String)
    fun streamCompletion(request: CompletionRequest): Flow<TokenEvent>
    suspend fun cancel(sessionId: String)
}
