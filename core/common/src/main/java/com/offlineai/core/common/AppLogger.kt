package com.offlineai.core.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileLock = Any()

    fun initialize(logsDirectory: File) {
        if (!logsDirectory.exists()) {
            logsDirectory.mkdirs()
        }
        logFile = File(logsDirectory, "app.log")
        log("INFO", "AppLogger initialized at ${logFile?.absolutePath}")
    }

    fun log(level: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] [$level] $message" +
            (throwable?.let { "\n${it.stackTraceToString()}" } ?: "")
        println(logEntry)
        synchronized(fileLock) {
            logFile?.let { file ->
                try {
                    file.appendText("$logEntry\n")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun getRecentLogs(maxLines: Int = 100): List<String> =
        withContext(Dispatchers.IO) {
            val file = logFile ?: return@withContext emptyList()
            if (!file.exists()) return@withContext emptyList()
            synchronized(fileLock) {
                val lines = file.readLines()
                lines.takeLast(maxLines)
            }
        }
}
