package com.example.baboonchat.data.model

import android.util.Log
import java.util.Date

/**
 * Extension methods for MessageThread to support chat history functionality
 */

/**
 * Gets a summary of this thread's state
 * Useful for debugging and UI display
 */
fun MessageThread.getSummary(): Map<String, Any> {
    val summary = mutableMapOf<String, Any>()
    
    summary["id"] = id
    summary["versionCount"] = versions.size
    summary["currentVersion"] = currentVersionIndex
    summary["chainCount"] = chains.size
    
    val totalMessages = chains.values.sumOf { it.messages.size }
    summary["messageCount"] = totalMessages
    
    val userMessages = chains.values.sumOf { chain ->
        chain.messages.count { it.type == MessageType.USER }
    }
    summary["userMessageCount"] = userMessages
    
    val botMessages = chains.values.sumOf { chain ->
        chain.messages.count { it.type == MessageType.BOT }
    }
    summary["botMessageCount"] = botMessages
    
    // Get thread timespan
    val allTimestamps = chains.values.flatMap { chain ->
        chain.messages.map { it.timestamp.time }
    }
    if (allTimestamps.isNotEmpty()) {
        summary["firstMessageTime"] = Date(allTimestamps.minOrNull() ?: 0)
        summary["lastMessageTime"] = Date(allTimestamps.maxOrNull() ?: 0)
    }
    
    // Active chain info
    activeChainId?.let { chainId ->
        val chain = chains[chainId]
        if (chain != null) {
            summary["activeChainId"] = chainId
            summary["activeChainMessages"] = chain.messages.size
        }
    }
    
    return summary
}

/**
 * Calculate the memory usage of this thread
 * This is an estimate based on typical string sizes
 */
fun MessageThread.estimateMemoryUsage(): Long {
    var totalBytes = 0L
    
    // Count bytes in all messages
    chains.values.forEach { chain ->
        chain.messages.forEach { message ->
            // Message content
            val contentSize = message.content?.length ?: 0
            totalBytes += contentSize * 2L // Unicode chars ~2 bytes
            
            // Image data (if any)
            if (message.containsImage) {
                val imageDataSize = message.imageData?.length ?: 0
                totalBytes += (imageDataSize * 0.75).toLong() // Base64 efficiency factor
            }
            
            // Fixed overhead per message (IDs, timestamps, etc)
            totalBytes += 256L // Rough estimate
        }
    }
    
    // Structure overhead
    totalBytes += chains.size * 128L // Chain overhead
    totalBytes += versions.size * 64L // Version overhead
    
    return totalBytes
}

/**
 * Clean up the thread by removing orphaned or unused chains
 * Can be used to reduce memory usage
 */
fun MessageThread.cleanupUnusedChains(): Int {
    val chainsToRemove = mutableListOf<String>()
    
    // Find chains that are not referenced by any version
    chains.forEach { (chainId, chain) ->
        val versionReferencesChain = versions.any { version ->
            val userMessage = version.userMessage
            val botResponse = version.botResponse
            
            userMessage.chainId == chainId ||
            botResponse?.chainId == chainId
        }
        
        if (!versionReferencesChain && chainId != activeChainId) {
            chainsToRemove.add(chainId)
        }
    }
    
    // Remove orphaned chains
    chainsToRemove.forEach { chainId ->
        chains.remove(chainId)
    }
    
    return chainsToRemove.size
}