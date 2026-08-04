package com.offlineai.feature.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class TerminalViewModel : ViewModel() {

    private val _terminalOutput = MutableStateFlow<List<String>>(
        listOf("Offline AI Terminal initialized.", "Type a command and press Execute (e.g. ls, pwd, date, node -v)...")
    )
    val terminalOutput: StateFlow<List<String>> = _terminalOutput.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    fun executeCommand(command: String, workingDir: File?) {
        if (command.isBlank()) return

        val cmdLine = command.trim()
        val dir = workingDir ?: File("/data/data/com.termux/files/home")

        _terminalOutput.value = _terminalOutput.value + "$ ${dir.name} > $cmdLine"
        _isExecuting.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("/system/bin/sh", "-c", cmdLine)
                    .directory(dir)
                    .redirectErrorStream(true)
                    .start()

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                val outputLines = mutableListOf<String>()

                while (reader.readLine().also { line = it } != null) {
                    line?.let { outputLines.add(it) }
                }

                process.waitFor()
                _terminalOutput.value = _terminalOutput.value + outputLines
            } catch (e: Exception) {
                _terminalOutput.value = _terminalOutput.value + "Error executing command: ${e.message}"
            } finally {
                _isExecuting.value = false
            }
        }
    }

    fun clearTerminal() {
        _terminalOutput.value = emptyList()
    }
}
