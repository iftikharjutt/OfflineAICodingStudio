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
    private external fun nativeGenerateToken(sessionId: String, prompt: String, isFirstToken: Boolean): String

    private var activeSession: ModelSession? = null

    override suspend fun loadModel(request: ModelLoadRequest): Result<ModelSession> {
        if (!nativeAvailable) {
            return Result.failure(
                NativeInferenceException("Native llama_engine library is not loaded on this device.")
            )
        }

        return try {
            val sessionId = nativeLoadModel(request.modelPath, request.contextSize, request.threadCount)
            if (sessionId.isBlank()) {
                return Result.failure(
                    NativeInferenceException("Failed to load GGUF model from path: ${request.modelPath}")
                )
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
            emit(TokenEvent.Error(ModelNotLoadedException("No GGUF model loaded. Please select and load a model first.")))
            return@flow
        }

        if (!nativeAvailable) {
            emit(TokenEvent.Error(NativeInferenceException("Native LLM engine unavailable. llama_engine.so library missing.")))
            return@flow
        }

        val stopTokens = request.stopSequences

        try {
            var isComplete = false
            var isFirst = true
            val accumulated = StringBuilder()

            while (!isComplete) {
                val token = nativeGenerateToken(session.sessionId, request.prompt, isFirst)
                isFirst = false

                if (token.isEmpty() || token == "<EOS>") {
                    isComplete = true
                } else {
                    accumulated.append(token)
                    val currentStr = accumulated.toString()

                    // Check if token matches any stop sequence
                    val matchesStop = stopTokens.any { stopSequence ->
                        stopSequence.isNotBlank() && (token.contains(stopSequence) || currentStr.endsWith(stopSequence))
                    }

                    if (matchesStop) {
                        isComplete = true
                    } else {
                        emit(TokenEvent.Token(token))
                    }
                }
                kotlinx.coroutines.yield()
            }
            emit(TokenEvent.Completed)

        } catch (e: kotlinx.coroutines.CancellationException) {
            emit(TokenEvent.Cancelled)
        } catch (e: Exception) {
            emit(TokenEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun cancel(sessionId: String) {
        // Native cancellation flag
    }
}
