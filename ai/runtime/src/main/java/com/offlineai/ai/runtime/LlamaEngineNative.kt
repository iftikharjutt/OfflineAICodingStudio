package com.offlineai.ai.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class LlamaEngineNative : LlamaInferenceEngine {

    companion object {
        private var nativeAvailable = false

        init {
            try {
                System.loadLibrary("llama_engine")
                nativeAvailable = true
            } catch (e: UnsatisfiedLinkError) {
                System.err.println(
                    "llama_engine native library not available: ${e.message}"
                )
                nativeAvailable = false
            }
        }

        fun isNativeAvailable(): Boolean = nativeAvailable
    }

    private external fun nativeInit(): Boolean
    private external fun nativeLoadModel(modelPath: String, contextSize: Int, threads: Int): String
    private external fun nativeUnloadModel(sessionId: String): Boolean
    private external fun nativeGenerateToken(sessionId: String, prompt: String): String

    private var activeSession: ModelSession? = null

    override suspend fun loadModel(request: ModelLoadRequest): Result<ModelSession> {
        return try {
            val sessionId = if (nativeAvailable) {
                nativeLoadModel(request.modelPath, request.contextSize, request.threadCount)
            } else {
                java.util.UUID.randomUUID().toString()
            }

            val session = ModelSession(
                sessionId = sessionId,
                modelPath = request.modelPath,
                contextSize = request.contextSize,
                loadedAt = System.currentTimeMillis()
            )
            activeSession = session
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unloadModel(sessionId: String) {
        if (nativeAvailable) {
            try {
                nativeUnloadModel(sessionId)
            } catch (e: Exception) {
                System.err.println("Failed to unload model: ${e.message}")
            }
        }
        if (activeSession?.sessionId == sessionId) {
            activeSession = null
        }
    }

    override fun streamCompletion(request: CompletionRequest): Flow<TokenEvent> = flow {
        val session = activeSession
        if (session == null) {
            emit(TokenEvent.Error(IllegalStateException("No GGUF model loaded")))
            return@flow
        }

        try {
            if (nativeAvailable) {
                var isComplete = false
                var accumulatedPrompt = request.prompt
                while (!isComplete) {
                    val token = nativeGenerateToken(session.sessionId, accumulatedPrompt)
                    if (token.isEmpty() || token == "<EOS>") {
                        isComplete = true
                    } else {
                        emit(TokenEvent.Token(token))
                        accumulatedPrompt += token
                    }
                    kotlinx.coroutines.yield()
                }
                emit(TokenEvent.Completed)
            } else {
                val mockResponse =
                    """{"summary":"Mock response - native library not loaded","operations":[]}"""
                for (word in mockResponse.split(" ")) {
                    kotlinx.coroutines.delay(10)
                    emit(TokenEvent.Token("$word "))
                }
                emit(TokenEvent.Completed)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            emit(TokenEvent.Cancelled)
        } catch (e: Exception) {
            emit(TokenEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun cancel(sessionId: String) {
        // Native cancellation handled via a flag in the native layer
    }
}
