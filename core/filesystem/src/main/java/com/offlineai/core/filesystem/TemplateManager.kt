package com.offlineai.core.filesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class WebTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: String
)

object TemplateManager {

    val defaultTemplates = listOf(
        WebTemplate("landing-page", "Landing Page", "Modern responsive landing page starter with CSS grid.", "Web"),
        WebTemplate("todo-app", "Todo List App", "Interactive Vanilla JavaScript task manager app.", "Web App"),
        WebTemplate("canvas-game", "2D Canvas Game", "HTML5 Canvas game template with animation loop.", "Game"),
        WebTemplate("markdown-notes", "Markdown Notes", "Local storage markdown editor with live preview.", "Tool")
    )

    suspend fun createProjectFromTemplate(
        projectsDir: File,
        projectName: String,
        templateId: String
    ): File = withContext(Dispatchers.IO) {
        val projDir = File(projectsDir, projectName)
        projDir.mkdirs()

        when (templateId) {
            "todo-app" -> {
                File(projDir, "index.html").writeText("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>$projectName - Todo App</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="container">
        <h1>Task Manager</h1>
        <div class="input-row">
            <input type="text" id="taskInput" placeholder="Enter new task...">
            <button id="addBtn">Add Task</button>
        </div>
        <ul id="taskList"></ul>
    </div>
    <script src="script.js"></script>
</body>
</html>
                """.trimIndent())

                File(projDir, "style.css").writeText("""
body { font-family: system-ui, sans-serif; background: #181818; color: #fff; display: flex; justify-content: center; padding-top: 50px; }
.container { background: #242424; padding: 20px; borderRadius: 8px; width: 350px; }
.input-row { display: flex; gap: 8px; margin-bottom: 15px; }
input { flex: 1; padding: 8px; border-radius: 4px; border: 1px solid #444; background: #333; color: white; }
button { background: #00bcd4; color: white; border: none; padding: 8px 12px; border-radius: 4px; cursor: pointer; }
ul { list-style: none; padding: 0; }
li { background: #333; padding: 8px; margin-top: 5px; border-radius: 4px; display: flex; justify-content: space-between; }
                """.trimIndent())

                File(projDir, "script.js").writeText("""
document.getElementById('addBtn').addEventListener('click', () => {
    const input = document.getElementById('taskInput');
    if (!input.value.trim()) return;
    const li = document.createElement('li');
    li.textContent = input.value;
    document.getElementById('taskList').appendChild(li);
    input.value = '';
});
                """.trimIndent())
            }
            "canvas-game" -> {
                File(projDir, "index.html").writeText("""
<!DOCTYPE html>
<html>
<head><title>$projectName Game</title></head>
<body style="background:#000; display:flex; justify-content:center; align-items:center; height:100vh; margin:0;">
    <canvas id="gameCanvas" width="400" height="400" style="border:2px solid #00ff66;"></canvas>
    <script src="game.js"></script>
</body>
</html>
                """.trimIndent())

                File(projDir, "game.js").writeText("""
const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');
let x = 200, y = 200, dx = 2, dy = -2;

function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.beginPath();
    ctx.arc(x, y, 15, 0, Math.PI * 2);
    ctx.fillStyle = "#00ff66";
    ctx.fill();
    ctx.closePath();

    if(x + dx > canvas.width || x + dx < 0) dx = -dx;
    if(y + dy > canvas.height || y + dy < 0) dy = -dy;

    x += dx; y += dy;
    requestAnimationFrame(draw);
}
draw();
                """.trimIndent())
            }
            else -> {
                // Default Landing Page
                File(projDir, "index.html").writeText("<!DOCTYPE html>\n<html>\n<head><title>$projectName</title></head>\n<body>\n<h1>$projectName</h1>\n</body>\n</html>")
            }
        }
        projDir
    }
}
