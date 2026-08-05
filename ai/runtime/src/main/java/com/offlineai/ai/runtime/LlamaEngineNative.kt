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
                DiagnosticsManager.recordError(e)
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
            val err = NativeInferenceException("Native llama_engine library (libllama_engine.so) is not loaded on this device.")
            Log.e(TAG, "loadModel failed: ${err.message}")
            DiagnosticsManager.recordError(err)
            return Result.failure(err)
        }

        val file = File(request.modelPath)
        if (!file.exists() || !file.isFile) {
            val err = IllegalArgumentException("GGUF model file does not exist at path: ${request.modelPath}")
            Log.e(TAG, "loadModel failed: ${err.message}")
            DiagnosticsManager.recordError(err)
            return Result.failure(err)
        }

        return try {
            Log.i(TAG, "Loading GGUF model: path=${request.modelPath}, ctxSize=${request.contextSize}, threads=${request.threadCount}")
            val startTime = System.currentTimeMillis()
            val sessionId = nativeLoadModel(request.modelPath, request.contextSize, request.threadCount)
            val loadDuration = System.currentTimeMillis() - startTime

            if (sessionId.isBlank()) {
                val err = NativeInferenceException("nativeLoadModel returned empty session ID for model: ${request.modelPath}")
                Log.e(TAG, "loadModel failed: ${err.message}")
                DiagnosticsManager.recordError(err)
                return Result.failure(err)
            }

            val session = ModelSession(
                sessionId = sessionId,
                modelPath = request.modelPath,
                contextSize = request.contextSize,
                loadedAt = System.currentTimeMillis()
            )
            activeSession = session

            DiagnosticsManager.updateModelStatus(
                sessionId = sessionId,
                modelPath = request.modelPath,
                contextSize = request.contextSize,
                threadCount = request.threadCount
            )

            Log.i(TAG, "Model loaded successfully: sessionID=$sessionId, loadTime=${loadDuration}ms")
            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "loadModel exception: ${e.message}", e)
            DiagnosticsManager.recordError(e)
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
                DiagnosticsManager.recordError(e)
            }
        }
        if (activeSession?.sessionId == sessionId) {
            activeSession = null
            DiagnosticsManager.updateModelStatus(null, null, 0, 0)
        }
    }

    override fun streamCompletion(request: CompletionRequest): Flow<TokenEvent> = flow {
        val session = activeSession
        if (session == null) {
            val err = ModelNotLoadedException("No GGUF model loaded. Please select and load a model in Models Manager tab first.")
            Log.e(TAG, "streamCompletion failed: ${err.message}")
            DiagnosticsManager.recordError(err)
            emit(TokenEvent.Error(err))
            return@flow
        }

        if (!nativeAvailable) {
            val err = NativeInferenceException("Native LLM engine unavailable. llama_engine.so library missing.")
            Log.e(TAG, "streamCompletion failed: ${err.message}")
            DiagnosticsManager.recordError(err)
            emit(TokenEvent.Error(err))
            return@flow
        }

        require(request.prompt.isNotBlank()) { "Completion prompt cannot be blank" }

        val stopTokens = request.stopSequences
        val startTime = System.currentTimeMillis()
        var generatedCount = 0
        var firstTokenText: String? = null

        Log.i(TAG, "Starting streamCompletion: session=${session.sessionId}, modelPath=${session.modelPath}, promptLength=${request.prompt.length}, maxTokens=${request.maxTokens}, temp=${request.temperature}")

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
                    if (firstTokenText == null) {
                        firstTokenText = token
                        Log.i(TAG, "First generated token: '$token'")
                    }
                    generatedCount++
                    accumulated.append(token)
                    val currentStr = accumulated.toString()

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

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "streamCompletion finished: generatedTokens=$generatedCount, elapsed=${elapsed}ms, avgSpeed=${if (elapsed > 0) (generatedCount * 1000L / elapsed) else 0} t/s")

            if (generatedCount == 0) {
                val err = IllegalStateException("Inference failed: Zero tokens generated by GGUF model.")
                Log.w(TAG, err.message ?: "Zero tokens generated")
                DiagnosticsManager.recordError(err)
                emit(TokenEvent.Error(err))
            } else {
                emit(TokenEvent.Completed)
            }

        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "streamCompletion cancelled by caller.")
            emit(TokenEvent.Cancelled)
        } catch (e: Exception) {
            Log.e(TAG, "streamCompletion error: ${e.message}", e)
            DiagnosticsManager.recordError(e)
            emit(TokenEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun cancel(sessionId: String) {
        Log.i(TAG, "Cancellation requested for session: $sessionId")
    }
}
