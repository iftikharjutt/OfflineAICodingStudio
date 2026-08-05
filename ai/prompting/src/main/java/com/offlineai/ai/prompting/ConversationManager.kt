package com.offlineai.ai.prompting

data class ChatTurn(
    val userText: String,
    val assistantText: String
)

class ConversationManager(
    private val maxTurnsHistory: Int = 10
) {
    private val history = mutableListOf<ChatTurn>()

    fun addTurn(userText: String, assistantText: String) {
        if (userText.isNotBlank()) {
            history.add(ChatTurn(userText, assistantText))
            trimHistory()
        }
    }

    fun getHistoryPairs(): List<Pair<String, String>> {
        return history.map { Pair(it.userText, it.assistantText) }
    }

    fun clear() {
        history.clear()
    }

    private fun trimHistory() {
        while (history.size > maxTurnsHistory) {
            history.removeAt(0)
        }
    }
}
