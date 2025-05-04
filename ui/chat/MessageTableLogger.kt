package com.example.baboonchat.ui.chat

import android.util.Log
import com.example.baboonchat.data.model.Message
import com.example.baboonchat.data.model.MessageThread
import com.example.baboonchat.data.model.MessageType

/**
 * Logger class that displays message chains in a tabular format
 */
class MessageTableLogger {
    companion object {
        private const val TAG = "MessageTableLogger"
        
        fun logMessageChainsAsTable(thread: MessageThread) {
            val chains = thread.chains.values.sortedBy { it.fromVersionIndex }
            if (chains.isEmpty()) return
            
            // Find max chain length
            val maxChainLength = chains.maxOfOrNull { it.messages.size } ?: 0
            
            // Create header with chain IDs
            val header = buildString {
                append("Position | ")
                chains.forEachIndexed { index, chain ->
                    val versionNumber = chain.fromVersionIndex
                    append("Ver $versionNumber | ")
                }
            }
            
            // Log header
            Log.d(TAG, "=".repeat(header.length))
            Log.d(TAG, "MESSAGE CHAINS TABLE (Thread: $thread.id)")
            Log.d(TAG, "Current Version: ${thread.currentVersionIndex}")
            Log.d(TAG, "Active Chain: ${thread.activeChainId}")
            Log.d(TAG, "=".repeat(header.length))
            Log.d(TAG, header)
            Log.d(TAG, "-".repeat(header.length))
            
            // Log each row (message position)
            for (position in 0 until maxChainLength) {
                val row = buildString {
                    append(String.format("%-8d | ", position))
                    
                    // For each chain, get the message at this position
                    chains.forEach { chain ->
                        val message = if (position < chain.messages.size) chain.messages[position] else null
                        val lineageInfo = "(lineage: ${message?.editLineageId?.take(6) ?: "none"}, originalMsg: ${message?.originalMessageId?.take(6) ?: "none"})"
                        val messageText = message?.let { 
                            val prefix = when (it.type) {
                                MessageType.USER -> "U: "
                                MessageType.BOT -> "B: "
                                else -> "?: "
                            }
                            val lineageInfo = "(lineageId: ${it.editLineageId?.take(6) ?: "none"})"
                            prefix + (it.content?.take(10) ?: "null") + "..." + lineageInfo
                        } ?: "          "
                        
                        append(String.format("%-30s | ", messageText))
                    }
                }
                Log.d(TAG, row)
            }
            
            Log.d(TAG, "=".repeat(header.length))
            
            // Log edit lineage information
            Log.d(TAG, "EDIT LINEAGE TRACKING:")
            val trackedMessages = mutableMapOf<String, MutableList<Message>>()
            
            // Group messages by edit lineage
            chains.forEach { chain ->
                chain.messages.forEach { message ->
                    if (message.type == MessageType.USER) {
                        val lineageId = message.editLineageId ?: message.id
                        if (!trackedMessages.containsKey(lineageId)) {
                            trackedMessages[lineageId] = mutableListOf()
                        }
                        trackedMessages[lineageId]?.add(message)
                    }
                }
            }
            
            // Log each lineage group
            trackedMessages.entries.forEachIndexed { idx, (lineageId, messages) ->
                Log.d(TAG, "Lineage #${idx+1} (ID: $lineageId):")
                messages.forEach { message ->
                    val chainId = message.chainId
                    val chain = thread.chains[chainId]
                    val version = chain?.fromVersionIndex ?: -1
                    Log.d(TAG, "  Ver $version: ${message.content?.take(20)}... (id: ${message.id}, originalId: ${message.originalMessageId ?: "none"})")
                }
                Log.d(TAG, "-".repeat(20))
            }
        }
    }
}