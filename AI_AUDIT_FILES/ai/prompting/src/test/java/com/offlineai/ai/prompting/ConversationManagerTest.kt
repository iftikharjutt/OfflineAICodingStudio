package com.offlineai.ai.prompting

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationManagerTest {

    @Test
    fun testConversationHistorySlidingWindow() {
        val manager = ConversationManager(maxTurnsHistory = 2)
        manager.addTurn("Hello", "Hi!")
        manager.addTurn("What is 2+2?", "4")
        assertEquals(2, manager.getHistoryPairs().size)

        // Adding 3rd turn should trim the 1st turn
        manager.addTurn("Write code", "Here is code")
        val history = manager.getHistoryPairs()
        assertEquals(2, history.size)
        assertEquals("What is 2+2?", history[0].first)
        assertEquals("Write code", history[1].first)
    }
}
