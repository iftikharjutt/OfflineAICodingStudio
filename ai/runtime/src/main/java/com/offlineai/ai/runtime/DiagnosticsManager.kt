package com.offlineai.ai.runtime

import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

data class DiagnosticReport(
    val timestamp: Long = System.currentTimeMillis(),
    val nativeLibraryLoaded: Boolean,
    val activeSessionId: String?,
    val modelPath: String?,
    val modelFileExists: Boolean,
    val modelFileSizeMb: Double,
    val contextSize: Int,
    val threadCount: Int,
    val availableRamMb: Long,
    val maxHeapRamMb: Long,
    val lastErrorTimestamp: Long? = null,
    val lastErrorType: String? = null,
    val lastErrorMessage: String? = null,
    val lastErrorStackTrace: String? = null
)

object DiagnosticsManager {

    private const val TAG = "DiagnosticsManager"

    private val _currentReport = MutableStateFlow(createInitialReport())
    val currentReport: StateFlow<DiagnosticReport> = _currentReport.asStateFlow()

    private fun createInitialReport(): DiagnosticReport {
        val runtime = Runtime.getRuntime()
        val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)
        val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)

        return DiagnosticReport(
            nativeLibraryLoaded = LlamaEngineNative.isNativeAvailable(),
            activeSessionId = null,
            modelPath = null,
            modelFileExists = false,
            modelFileSizeMb = 0.0,
            contextSize = 0,
            threadCount = 0,
            availableRamMb = freeMemoryMb,
            maxHeapRamMb = maxMemoryMb
        )
    }

    fun updateModelStatus(
        sessionId: String?,
        modelPath: String?,
        contextSize: Int,
        threadCount: Int
    ) {
        val file = modelPath?.let { File(it) }
        val exists = file?.exists() == true
        val sizeMb = if (exists && file != null) file.length() / (1024.0 * 1024.0) else 0.0

        val runtime = Runtime.getRuntime()
        val freeMemoryMb = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024 * 1024)

        _currentReport.value = _currentReport.value.copy(
            timestamp = System.currentTimeMillis(),
            nativeLibraryLoaded = LlamaEngineNative.isNativeAvailable(),
            activeSessionId = sessionId,
            modelPath = modelPath,
            modelFileExists = exists,
            modelFileSizeMb = sizeMb,
            contextSize = contextSize,
            threadCount = threadCount,
            availableRamMb = freeMemoryMb
        )

        Log.i(TAG, "Diagnostics Updated: ModelPath=$modelPath, Exists=$exists, SizeMB=$sizeMb, SessionId=$sessionId")
    }

    fun recordError(throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTraceStr = sw.toString()

        Log.e(TAG, "Diagnostic Error Recorded: ${throwable.message}", throwable)

        _currentReport.value = _currentReport.value.copy(
            timestamp = System.currentTimeMillis(),
            lastErrorTimestamp = System.currentTimeMillis(),
            lastErrorType = throwable.javaClass.simpleName,
            lastErrorMessage = throwable.message ?: throwable.toString(),
            lastErrorStackTrace = stackTraceStr
        )
    }

    fun generateFormattedSummary(): String {
        val r = _currentReport.value
        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("OFFLINE AI STUDIO — SYSTEM DIAGNOSTIC REPORT")
        sb.appendLine("==================================================")
        sb.appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Native JNI (libllama_engine.so): ${if (r.nativeLibraryLoaded) "ACTIVE ✅" else "FAILED ❌"}")
        sb.appendLine("Active Session ID: ${r.activeSessionId ?: "None (Model Not Loaded)"}")
        sb.appendLine("Model Path: ${r.modelPath ?: "None"}")
        sb.appendLine("Model File Exists: ${if (r.modelFileExists) "YES ✅" else "NO ❌"}")
        sb.appendLine("Model File Size: ${String.format("%.2f", r.modelFileSizeMb)} MB")
        sb.appendLine("Context Size: ${r.contextSize} tokens")
        sb.appendLine("Thread Count: ${r.threadCount} threads")
        sb.appendLine("Available RAM: ${r.availableRamMb} MB / Max Heap: ${r.maxHeapRamMb} MB")
        sb.appendLine("--------------------------------------------------")

        if (r.lastErrorMessage != null) {
            sb.appendLine("LAST ERROR DETECTED:")
            sb.appendLine("Time: ${java.util.Date(r.lastErrorTimestamp ?: 0)}")
            sb.appendLine("Type: ${r.lastErrorType}")
            sb.appendLine("Message: ${r.lastErrorMessage}")
            sb.appendLine("Stack Trace:\n${r.lastErrorStackTrace}")
        } else {
            sb.appendLine("No inference errors recorded in this session.")
        }
        sb.appendLine("==================================================")
        return sb.toString()
    }
}
