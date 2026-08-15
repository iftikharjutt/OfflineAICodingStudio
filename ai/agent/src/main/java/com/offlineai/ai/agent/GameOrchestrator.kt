package com.offlineai.ai.agent

import com.offlineai.ai.runtime.DualModelManager
import com.offlineai.core.filesystem.WorkspaceManager
import com.offlineai.core.models.GameSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

import com.offlineai.ai.runtime.TokenEvent
import org.json.JSONObject

class GameOrchestrator(
    private val workspaceManager: WorkspaceManager,
    private val dualModelManager: DualModelManager
) {
    suspend fun runGameGenerationPipeline(userRequest: String, projectName: String): Flow<String> = flow {
        emit("Starting GAME ANALYSIS...")
        
        val projectDir = workspaceManager.createProjectDirectory(projectName)
        emit("Scaffolded Project Architecture in ${projectDir.name}/")

        emit("Architect is drafting Game Specification...")
        val specPrompt = """
            You are an expert HTML5 Game Architect. The user wants to build a game based on:
            "$userRequest"
            
            Respond ONLY with a valid JSON GameSpec.
            Example: {"title": "$projectName", "genre": "arcade", "renderingMode": "HTML5 Canvas", "player": "square", "enemies": "circles", "controls": "arrow keys"}
        """.trimIndent()
        
        val specBuilder = StringBuilder()
        var failed = false
        try {
            val sessionFlow = dualModelManager.sessionA?.let { dualModelManager.streamModelA(specPrompt, 1024, listOf("}")) }
            if (sessionFlow != null) {
                sessionFlow.collect { event ->
                    if (event is TokenEvent.Token) specBuilder.append(event.text)
                }
            } else { failed = true }
        } catch(e: Exception) { failed = true }

        if (!failed) {
            val specJsonStr = specBuilder.toString().trim() + "}"
            try {
                val spec = GameSpec.fromJson(if (specJsonStr.contains("{")) "{" + specJsonStr.substringAfter("{") else specJsonStr)
                workspaceManager.writeFileText(projectDir, ".gamestudio/game-spec.json", spec.toJson())
                emit("Game Specification generated and saved.")
            } catch(e: Exception) {
                emit("Failed to parse spec, using defaults.")
            }
        }

        emit("Coder model generating JavaScript logic...")
        val codePrompt = """
            You are an expert Game Developer. Write the complete logic for the game:
            "$userRequest"
            The HTML canvas id is 'gameCanvas'.
            Provide ONLY raw Javascript code. No markdown.
        """.trimIndent()
        
        val codeBuilder = StringBuilder()
        try {
            val sessionFlow = dualModelManager.sessionA?.let { dualModelManager.streamModelA(codePrompt, 2048, emptyList()) }
            sessionFlow?.collect { event ->
                if (event is TokenEvent.Token) codeBuilder.append(event.text)
            }
        } catch(e: Exception) {}

        val jsCode = codeBuilder.toString().replace("```javascript", "").replace("```", "").trim()
        if (jsCode.isNotEmpty()) {
            workspaceManager.writeFileText(projectDir, "js/game.js", jsCode)
            emit("Applying generated logic to js/game.js...")
        } else {
            emit("Failed to generate code.")
        }

        emit("Game Ready! Switch to Preview tab to test.")
    }
}
