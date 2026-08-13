package com.offlineai.ai.runtime

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class LlamaEngineNative : LlamaInferenceEngine {

    companion object {
        private const val TAG = "LlamaEngineNative"
        private var nativeAvailable = false

        init {
            try {
                System.loadLibrary("llama_engine")
                nativeAvailable = true
                Log.i(TAG, "Native library 'llama_engine.so' loaded successfully.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "llama_engine native library not available: ${e.message}", e)
                nativeAvailable = false
            }
        }

        fun isNativeAvailable(): Boolean = nativeAvailable
    }

    private external fun nativeLoadModel(modelPath: String, contextSize: Int, threads: Int): String
    private external fun nativeUnloadModel(sessionId: String): Boolean
    private external fun nativeGenerateToken(sessionId: String, prompt: String, isFirstToken: Boolean): String

    private var activeSession: ModelSession? = null

    override suspend fun loadModel(request: ModelLoadRequest): Result<ModelSession> {
        if (!nativeAvailable) {
            val err = NativeInferenceException("Native llama_engine library is not loaded on this device.")
            Log.e(TAG, "loadModel failed: ${err.message}")
            return Result.failure(err)
        }

        val file = File(request.modelPath)
        if (!file.exists() || !file.isFile || file.length() == 0L) {
            val err = IllegalArgumentException("GGUF model file is missing or empty: ${request.modelPath}")
            Log.e(TAG, "loadModel failed: ${err.message}")
            return Result.failure(err)
        }

        return try {
            // Do not leave the previous native model alive when switching models/settings.
            activeSession?.let { previous ->
                runCatching { nativeUnloadModel(previous.sessionId) }
                    .onFailure { Log.w(TAG, "Failed to unload previous session ${previous.sessionId}", it) }
                activeSession = null
            }

            val contextSize = request.contextSize.coerceAtLeast(512)
            val threads = request.threadCount.coerceAtLeast(1)
            Log.i(TAG, "Loading GGUF model: path=${request.modelPath}, ctxSize=$contextSize, threads=$threads")
            val startTime = System.currentTimeMillis()
            val sessionId = nativeLoadModel(request.modelPath, contextSize, threads)
            val loadDuration = System.currentTimeMillis() - startTime

            if (sessionId.isBlank()) {
                val err = NativeInferenceException("Native model loading failed for: ${request.modelPath}")
                Log.e(TAG, "loadModel failed: ${err.message}")
                return Result.failure(err)
            }

            val session = ModelSession(
                sessionId = sessionId,
                modelPath = request.modelPath,
                contextSize = contextSize,
                loadedAt = System.currentTimeMillis()
            )
            activeSession = session
            Log.i(TAG, "Model loaded successfully: sessionID=$sessionId, loadTime=${loadDuration}ms")
            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "loadModel exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun unloadModel(sessionId: String) {
        if (nativeAvailable) {
            try {
                Log.i(TAG, "Unloading model session: $sessionId")
                nativeUnloadModel(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unload model session $sessionId: ${e.message}", e)
            }
        }
        if (activeSession?.sessionId == sessionId) activeSession = null
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

        require(request.prompt.isNotBlank()) { "Completion prompt cannot be blank" }
        if (request.modelPath != null && File(request.modelPath).canonicalPath != File(session.modelPath).canonicalPath) {
            emit(TokenEvent.Error(ModelNotLoadedException("Requested model is not the currently loaded model. Please wait for model loading to finish.")))
            return@flow
        }

        val stopTokens = request.stopSequences.filter(String::isNotBlank)
        val maxTokens = request.maxTokens.coerceAtLeast(1)
        val startTime = System.currentTimeMillis()
        var generatedCount = 0

        try {
            var isFirst = true
            val accumulated = StringBuilder()

            while (generatedCount < maxTokens) {
                val promptArg = if (isFirst) request.prompt else ""
                val token = nativeGenerateToken(session.sessionId, promptArg, isFirst)
                isFirst = false

                when {
                    token.isEmpty() -> {
                        val err = NativeInferenceException("Native generation returned an empty token. Check native logcat for tokenization/decode errors.")
                        Log.e(TAG, err.message ?: "Empty native token")
                        emit(TokenEvent.Error(err))
                        return@flow
                    }
                    token == "<EOS>" -> break
                    else -> {
                        generatedCount++
                        accumulated.append(token)
                        val current = accumulated.toString()
                        val matchedStop = stopTokens.any { stop -> token.contains(stop) || current.endsWith(stop) }
                        if (matchedStop) break
                        emit(TokenEvent.Token(token))
                    }
                }
                kotlinx.coroutines.yield()
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "streamCompletion finished: generatedTokens=$generatedCount, elapsed=${elapsed}ms")

            if (generatedCount == 0) {
                emit(TokenEvent.Error(IllegalStateException("Inference generated zero tokens. The model may be incompatible, exhausted, or the prompt may have consumed the context window.")))
            } else {
                emit(TokenEvent.Completed)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "streamCompletion cancelled by caller.")
            emit(TokenEvent.Cancelled)
        } catch (e: Exception) {
            Log.e(TAG, "streamCompletion error: ${e.message}", e)
            emit(TokenEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun cancel(sessionId: String) {
        // Native generation is one token per JNI call, so coroutine cancellation stops the loop promptly.
        Log.i(TAG, "Cancellation requested for session: $sessionId")
    }
}
