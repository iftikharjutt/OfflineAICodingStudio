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

    private external fun nativeInit(): Boolean
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
        if (!file.exists() || !file.isFile) {
            val err = IllegalArgumentException("GGUF model file does not exist at path: ${request.modelPath}")
            Log.e(TAG, "loadModel failed: ${err.message}")
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
                return Result.failure(err)
            }

            val session = ModelSession(
                sessionId = sessionId,
                modelPath = request.modelPath,
                contextSize = request.contextSize,
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
                Log.e(TAG, "Failed to unload model session $sessionId:${e.message}", e)
            }
        }
        if (activeSession?.sessionId == sessionId) {
            activeSession = null
        }
    }

    override fun streamCompletion(request: CompletionRequest): Flow<TokenEvent> = flow {
        val session = activeSession
        if (session == null) {
            val err = ModelNotLoadedException("No GGUF model loaded. Please select and load a model first.")
            Log.e(TAG, "streamCompletion failed: ${err.message}")
            emit(TokenEvent.Error(err))
            return@flow
        }

        if (!nativeAvailable) {
            val err = NativeInferenceException("Native LLM engine unavailable. llama_engine.so library missing.")
            Log.e(TAG, "streamCompletion failed: ${err.message}")
            emit(TokenEvent.Error(err))
            return@flow
        }

        require(request.prompt.isNotBlank()) { "Completion prompt cannot be blank" }

        val stopTokens = request.stopSequences.filter { it.isNotBlank() }
        val startTime = System.currentTimeMillis()
        var generatedCount = 0

        try {
            var isComplete = false
            var isFirst = true
            val accumulated = StringBuilder()

            while (!isComplete) {
                // Fix: Only pass prompt on first token to avoid JNI string overhead
                val promptArg = if (isFirst) request.prompt else ""
                val isFirstCall = isFirst
                val token = nativeGenerateToken(session.sessionId, promptArg, isFirst)
                isFirst = false

                if (token.isEmpty()) {
                    Log.e(TAG, "nativeGenerateToken returned empty string (isFirstCall=$isFirstCall). Tokenization or eval failed.")
                    emit(TokenEvent.Error(NativeInferenceException("nativeGenerateToken returned empty string – tokenization or eval failed. Check native logcat for details.")))
                    return@flow
                } else if (token == "<EOS>") {
                    if (isFirstCall) {
                        Log.w(TAG, "Model generated <EOS> immediately on first token.")
                    }
                    isComplete = true
                } else {
                    generatedCount++
                    accumulated.append(token)
                    val currentStr = accumulated.toString()

                    val matchesStop = stopTokens.any { stopSequence ->
                        stopSequence.isNotBlank() && (
                            token == stopSequence || 
                            token.contains(stopSequence) || 
                            currentStr.endsWith(stopSequence)
                        )
                    }

                    if (matchesStop) {
                        Log.i(TAG, "Stop sequence matched on token count $generatedCount: $token")
                        isComplete = true
                    } else {
                        emit(TokenEvent.Token(token))
                    }
                }
                kotlinx.coroutines.yield()
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "streamCompletion finished: generatedTokens=$generatedCount, elapsed=${elapsed}ms")

            if (generatedCount == 0) {
                val errMsg = "Inference Failed: Zero tokens generated by GGUF model. " +
                        "Possible causes: (1) llama_tokenize returned 0 tokens, " +
                        "(2) immediate EOS token generated, or " +
                        "(3) stop sequence matched on first token. " +
                        "Check logcat for 'Tokenized prompt into X tokens'."
                Log.e(TAG, errMsg)
                emit(TokenEvent.Error(IllegalStateException(errMsg)))
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
        Log.i(TAG, "Cancellation requested for session: $sessionId")
    }
}
