package com.example.baboonchat.data.remote

import android.util.Log
import com.example.baboonchat.data.model.Message
import com.example.baboonchat.data.model.MessageType

/**
 * Utility class for building prompts that incorporate message history
 * for the Gemini generation model.
 */
class PromptBuilder {
    companion object {
        private const val TAG = "PromptBuilder"
        
        /**
         * Builds a prompt that includes the conversation history from the active message chain
         * 
         * @param userMessage The current user message
         * @param conversationHistory List of previous messages in the active chain
         * @return A formatted prompt string that includes history and the current message
         */
        fun buildPromptWithHistory(userMessage: String, conversationHistory: List<Message>): String {
            val builder = StringBuilder()
            
            // Add a header to clearly mark the conversation history section
            builder.append("=== CONVERSATION HISTORY START ===\n\n")
            
            // Add previous messages to provide context
            if (conversationHistory.isNotEmpty()) {
                for (message in conversationHistory) {
                    val prefix = when (message.type) {
                        MessageType.USER -> "USER: "
                        MessageType.BOT -> "ASSISTANT: "
                        else -> "SYSTEM: "
                    }
                    
                    // Only add previous messages, not the current one we're about to send
                    if (message.content != userMessage) {
                        builder.append("$prefix${message.content}\n\n")
                    }
                }
            }
            
            builder.append("=== CONVERSATION HISTORY END ===\n\n")
            
            // Add a clear marker for the current user message
            builder.append("=== CURRENT USER MESSAGE ===\n")
            builder.append(userMessage)
            
            val finalPrompt = builder.toString()
            Log.d(TAG, "Built prompt with history: ${finalPrompt.take(200)}...")
            
            return finalPrompt
        }
        
        /**
         * Returns only the relevant messages from the conversation history
         * for the active chain, excluding any messages that aren't visible
         * in the current context.
         *
         * @param allMessages All messages from the conversation
         * @param currentChainId The ID of the currently active chain
         * @return List of messages that are part of the active chain
         */
        fun getRelevantMessagesForPrompt(allMessages: List<Message>, currentChainId: String?): List<Message> {
            // If no chain ID is provided, return an empty list
            if (currentChainId == null) {
                return emptyList()
            }
            
            // Filter messages that belong to the current chain and order them chronologically
            return allMessages
                .filter { it.chainId == currentChainId }
                .sortedBy { it.timestamp }
        }
    }
}