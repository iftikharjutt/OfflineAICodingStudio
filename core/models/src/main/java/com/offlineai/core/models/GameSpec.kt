package com.offlineai.core.models

import org.json.JSONObject

data class GameSpec(
    val title: String = "",
    val genre: String = "",
    val renderingMode: String = "HTML5 Canvas",
    val player: String = "",
    val enemies: String = "",
    val controls: String = "",
    val physics: String = "",
    val collision: String = "",
    val scoring: String = "",
    val levels: String = "",
    val difficulty: String = "",
    val sound: String = "",
    val ui: String = "",
    val mobileControls: String = "touch",
    val saveSystem: String = "",
    val assets: String = "",
    val technicalRequirements: String = ""
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("title", title)
        json.put("genre", genre)
        json.put("renderingMode", renderingMode)
        json.put("player", player)
        json.put("enemies", enemies)
        json.put("controls", controls)
        json.put("physics", physics)
        json.put("collision", collision)
        json.put("scoring", scoring)
        json.put("levels", levels)
        json.put("difficulty", difficulty)
        json.put("sound", sound)
        json.put("ui", ui)
        json.put("mobileControls", mobileControls)
        json.put("saveSystem", saveSystem)
        json.put("assets", assets)
        json.put("technicalRequirements", technicalRequirements)
        return json.toString(4)
    }

    companion object {
        fun fromJson(jsonString: String): GameSpec {
            val json = JSONObject(jsonString)
            return GameSpec(
                title = json.optString("title", ""),
                genre = json.optString("genre", ""),
                renderingMode = json.optString("renderingMode", "HTML5 Canvas"),
                player = json.optString("player", ""),
                enemies = json.optString("enemies", ""),
                controls = json.optString("controls", ""),
                physics = json.optString("physics", ""),
                collision = json.optString("collision", ""),
                scoring = json.optString("scoring", ""),
                levels = json.optString("levels", ""),
                difficulty = json.optString("difficulty", ""),
                sound = json.optString("sound", ""),
                ui = json.optString("ui", ""),
                mobileControls = json.optString("mobileControls", "touch"),
                saveSystem = json.optString("saveSystem", ""),
                assets = json.optString("assets", ""),
                technicalRequirements = json.optString("technicalRequirements", "")
            )
        }
    }
}
