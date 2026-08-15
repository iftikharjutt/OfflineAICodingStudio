package com.offlineai.core.filesystem

object GameTemplates {
    val SNAKE_HTML = """
<!DOCTYPE html>
<html>
<head>
    <title>Snake Template</title>
    <style>
        body { background: #222; color: #fff; text-align: center; font-family: sans-serif; }
        canvas { background: #000; display: block; margin: 0 auto; border: 2px solid #444; }
    </style>
</head>
<body>
    <h1>Snake Game</h1>
    <canvas id="gameCanvas" width="400" height="400"></canvas>
    <script src="js/game.js"></script>
</body>
</html>
    """.trimIndent()

    val SNAKE_JS = """
const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');
let snake = [{x: 200, y: 200}];
let dir = {x: 0, y: -20};
let food = {x: 100, y: 100};
let score = 0;

document.addEventListener('keydown', e => {
    if (e.key === 'ArrowUp' && dir.y === 0) dir = {x: 0, y: -20};
    if (e.key === 'ArrowDown' && dir.y === 0) dir = {x: 0, y: 20};
    if (e.key === 'ArrowLeft' && dir.x === 0) dir = {x: -20, y: 0};
    if (e.key === 'ArrowRight' && dir.x === 0) dir = {x: 20, y: 0};
});

function loop() {
    let head = {x: snake[0].x + dir.x, y: snake[0].y + dir.y};
    snake.unshift(head);
    if (head.x === food.x && head.y === food.y) {
        score++;
        food = {x: Math.floor(Math.random()*20)*20, y: Math.floor(Math.random()*20)*20};
    } else {
        snake.pop();
    }
    
    ctx.fillStyle = 'black';
    ctx.fillRect(0, 0, 400, 400);
    
    ctx.fillStyle = 'red';
    ctx.fillRect(food.x, food.y, 20, 20);
    
    ctx.fillStyle = 'lime';
    snake.forEach(p => ctx.fillRect(p.x, p.y, 18, 18));
    
    setTimeout(loop, 100);
}
loop();
    """.trimIndent()
}
