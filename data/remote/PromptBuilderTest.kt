package com.example.baboonchat.data.remote

import com.example.baboonchat.data.model.Message
import com.example.baboonchat.data.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.UUID

class PromptBuilderTest {

    @Test
    fun testBuildPromptWithHistory() {
        // Create sample conversation history
        val conversationHistory = listOf(
            Message(
                id = "1",
                content = "Hello, how are you?",
                type = MessageType.USER,
                timestamp = Date(1619788800000), // May 1, 2021
                threadId = "thread-1",
                chainId = "chain-1"
            ),
            Message(
                id = "2",
                content = "I'm doing well, thank you for asking. How can I help you today?",
                type = MessageType.BOT,
                timestamp = Date(1619788860000), // May 1, 2021, 1 minute later
                threadId = "thread-1",
                chainId = "chain-1"
            )
        )

        val currentMessage = "Tell me about machine learning."

        // Build prompt with history
        val prompt = PromptBuilder.buildPromptWithHistory(currentMessage, conversationHistory)

        // Verify the prompt structure
        assertTrue(prompt.contains("=== CONVERSATION HISTORY START ==="))
        assertTrue(prompt.contains("USER: Hello, how are you?"))
        assertTrue(prompt.contains("ASSISTANT: I'm doing well, thank you for asking. How can I help you today?"))
        assertTrue(prompt.contains("=== CONVERSATION HISTORY END ==="))
        assertTrue(prompt.contains("=== CURRENT USER MESSAGE ==="))
        assertTrue(prompt.contains("Tell me about machine learning."))
    }

    @Test
    fun testGetRelevantMessagesForPrompt() {
        val chainId1 = "chain-1"
        val chainId2 = "chain-2"
        val threadId = "thread-1"

        // Create sample messages from different chains
        val allMessages = listOf(
            Message(
                id = "1",
                content = "Message in chain 1",
                type = MessageType.USER,
                timestamp = Date(1619788800000),
                threadId = threadId,
                chainId = chainId1
            ),
            Message(
                id = "2",
                content = "Response in chain 1",
                type = MessageType.BOT,
                timestamp = Date(1619788860000),
                threadId = threadId,
                chainId = chainId1
            ),
            Message(
                id = "3",
                content = "Message in chain 2",
                type = MessageType.USER,
                timestamp = Date(1619788920000),
                threadId = threadId,
                chainId = chainId2
            ),
            Message(
                id = "4",
                content = "Response in chain 2",
                type = MessageType.BOT,
                timestamp = Date(1619788980000),
                threadId = threadId,
                chainId = chainId2
            )
        )

        // Test filtering for chain 1
        val chain1Messages = PromptBuilder.getRelevantMessagesForPrompt(allMessages, chainId1)
        assertEquals(2, chain1Messages.size)
        assertEquals("Message in chain 1", chain1Messages[0].content)
        assertEquals("Response in chain 1", chain1Messages[1].content)

        // Test filtering for chain 2
        val chain2Messages = PromptBuilder.getRelevantMessagesForPrompt(allMessages, chainId2)
        assertEquals(2, chain2Messages.size)
        assertEquals("Message in chain 2", chain2Messages[0].content)
        assertEquals("Response in chain 2", chain2Messages[1].content)

        // Test with null chain ID
        val nullChainMessages = PromptBuilder.getRelevantMessagesForPrompt(allMessages, null)
        assertTrue(nullChainMessages.isEmpty())
    }
}