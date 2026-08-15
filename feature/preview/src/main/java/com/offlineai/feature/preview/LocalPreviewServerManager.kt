package com.offlineai.feature.preview

import com.offlineai.core.filesystem.FileSecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LocalPreviewServerManager(
    private var projectDir: File
) {
    private var serverSocket: ServerSocket? = null
    var activePort: Int = -1
        private set

    private var isRunning = false
    private val threadPool: ExecutorService = Executors.newFixedThreadPool(4)

    fun updateProjectDirectory(newProjectDir: File) {
        projectDir = newProjectDir
    }

    suspend fun startServer() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        try {
            serverSocket = ServerSocket(0)
            activePort = serverSocket?.localPort ?: -1
            isRunning = true
            while (isRunning && serverSocket?.isClosed == false) {
                val clientSocket = serverSocket?.accept() ?: break
                handleClient(clientSocket)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
            threadPool.shutdownNow()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleClient(socket: Socket) {
        threadPool.submit {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val out = PrintWriter(socket.getOutputStream())
                val rawOut = socket.getOutputStream()

                val requestLine = reader.readLine() ?: return@submit
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@submit

                var requestedPath = parts[1].substringBefore("?")
                if (requestedPath == "/" || requestedPath.isEmpty()) {
                    requestedPath = "/index.html"
                }

                val targetFile = File(projectDir, requestedPath.removePrefix("/"))

                if (FileSecurityUtils.isPathSafe(projectDir, targetFile)
                    && targetFile.exists()
                    && !targetFile.isDirectory
                ) {
                    val bytes = targetFile.readBytes()
                    val contentType = when (targetFile.extension.lowercase()) {
                        "html", "htm" -> "text/html; charset=utf-8"
                        "css" -> "text/css"
                        "js" -> "application/javascript"
                        "json" -> "application/json"
                        "png" -> "image/png"
                        "jpg", "jpeg" -> "image/jpeg"
                        "svg" -> "image/svg+xml"
                        else -> "text/plain"
                    }
                    out.println("HTTP/1.1 200 OK")
                    out.println("Content-Type: $contentType")
                    out.println("Content-Length: ${bytes.size}")
                    out.println("Access-Control-Allow-Origin: *")
                    out.println("Connection: close")
                    out.println()
                    out.flush()
                    rawOut.write(bytes)
                    rawOut.flush()
                } else {
                    val notFoundText =
                        "<h1>404 Not Found</h1><p>File $requestedPath not found.</p>"
                    val bytes = notFoundText.toByteArray()
                    out.println("HTTP/1.1 404 Not Found")
                    out.println("Content-Type: text/html; charset=utf-8")
                    out.println("Content-Length: ${bytes.size}")
                    out.println("Connection: close")
                    out.println()
                    out.flush()
                    rawOut.write(bytes)
                    rawOut.flush()
                }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
