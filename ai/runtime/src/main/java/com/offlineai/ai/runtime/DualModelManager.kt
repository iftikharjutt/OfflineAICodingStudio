package com.offlineai.ai.runtime

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlin.math.max

class DualModelManager(
    private val context: Context,
    private val inferenceEngine: LlamaEngineNative
) {
    private val TAG = "DualModelManager"

    var sessionA: ModelSession? = null
        private set
    var sessionB: ModelSession? = null
        private set

    // Memory Threshold in Bytes (e.g. 1.5 GB minimum available required for a second model)
    private val MIN_RAM_FOR_DUAL_BYTES = 1536L * 1024L * 1024L

    fun getAvailableRAM(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)

        var nativeRam: Long = -1L
        if (LlamaEngineNative.isNativeAvailable()) {
            try {
                nativeRam = inferenceEngine.nativeGetAvailableRAM()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to call nativeGetAvailableRAM: ${e.message}")
            }
        }

        return if (mi.availMem > 0) mi.availMem else nativeRam
    }

    private fun calculateThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return max(1, cores / 2) // split evenly for dual mode
    }

    suspend fun loadModelA(
        modelPath: String,
        contextSize: Int = 2048,
        gpuLayers: Int = 0
    ): Result<ModelSession> {
        val threads = calculateThreads()
        val request = ModelLoadRequest(
            modelPath = modelPath,
            contextSize = contextSize,
            threadCount = threads,
            gpuLayers = gpuLayers
        )
        Log.i(TAG, "loadModelA: gpuLayers=$gpuLayers contextSize=$contextSize")
        val result = inferenceEngine.loadModel(request)
        if (result.isSuccess) {
            sessionA = result.getOrNull()
        }
        return result
    }

    suspend fun loadModelB(
        modelPath: String,
        contextSize: Int = 2048,
        gpuLayers: Int = 0
    ): Result<ModelSession> {
        val availRam = getAvailableRAM()
        if (availRam < MIN_RAM_FOR_DUAL_BYTES) {
            val err = IllegalStateException(
                "Insufficient RAM for Dual Mode. Available: ${availRam / 1024 / 1024}MB. " +
                    "Required: ${MIN_RAM_FOR_DUAL_BYTES / 1024 / 1024}MB."
            )
            Log.e(TAG, err.message!!)
            return Result.failure(err)
        }

        val threads = calculateThreads()
        val request = ModelLoadRequest(
            modelPath = modelPath,
            contextSize = contextSize,
            threadCount = threads,
            gpuLayers = gpuLayers
        )
        Log.i(TAG, "loadModelB: gpuLayers=$gpuLayers contextSize=$contextSize")
        val result = inferenceEngine.loadModel(request)
        if (result.isSuccess) {
            sessionB = result.getOrNull()
        }
        return result
    }

    suspend fun unloadModelA() {
        sessionA?.let {
            inferenceEngine.unloadModel(it.sessionId)
            sessionA = null
        }
    }

    suspend fun unloadModelB() {
        sessionB?.let {
            inferenceEngine.unloadModel(it.sessionId)
            sessionB = null
        }
    }

    fun streamModelA(prompt: String, maxTokens: Int = 2048, stopTokens: List<String> = emptyList()): Flow<TokenEvent> {
        val session = sessionA ?: throw IllegalStateException("Model A is not loaded")
        val req = CompletionRequest(session.sessionId, prompt, maxTokens, stopSequences = stopTokens)
        return inferenceEngine.streamCompletion(req)
    }

    fun streamModelB(prompt: String, maxTokens: Int = 2048, stopTokens: List<String> = emptyList()): Flow<TokenEvent> {
        val session = sessionB ?: throw IllegalStateException("Model B is not loaded")
        val req = CompletionRequest(session.sessionId, prompt, maxTokens, stopSequences = stopTokens)
        return inferenceEngine.streamCompletion(req)
    }

    suspend fun stopModelA() {
        sessionA?.let { inferenceEngine.cancel(it.sessionId) }
    }

    suspend fun stopModelB() {
        sessionB?.let { inferenceEngine.cancel(it.sessionId) }
    }
}
