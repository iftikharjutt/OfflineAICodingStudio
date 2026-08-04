package com.offlineai.core.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DiagnosticReportExporter {

    suspend fun generateDiagnosticReport(logsDir: File): String = withContext(Dispatchers.IO) {
        val runtime = Runtime.getRuntime()
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)
        val freeMemMb = runtime.freeMemory() / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        val recentLogs = AppLogger.getRecentLogs(50).joinToString("\n")

        """
================================================================================
OFFLINE AI CODING STUDIO DIAGNOSTIC REPORT
Timestamp: ${java.util.Date()}
================================================================================

SYSTEM ENVIRONMENT:
- OS Architecture: ${System.getProperty("os.arch")}
- OS Name: ${System.getProperty("os.name")}
- Java Version: ${System.getProperty("java.version")}
- Available CPU Processors: ${runtime.availableProcessors()}

MEMORY USAGE:
- Max Heap Memory: $maxMemMb MB
- Total Allocated Memory: $totalMemMb MB
- Free Memory: $freeMemMb MB
- Used Memory: ${totalMemMb - freeMemMb} MB

RECENT LOGS (Last 50 lines):
$recentLogs
================================================================================
        """.trimIndent()
    }
}
