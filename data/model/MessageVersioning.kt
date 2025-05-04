package com.example.baboonchat.data.model

import android.util.Log
import java.util.Date
import java.util.UUID

/**
 * Represents a single version of a message with its response
 */
data class MessageVersion(
    val userMessage: Message,
    var botResponse: Message? = null,
    val timestamp: Date = Date()
)

/**
 * Represents a message thread with multiple versions, chains and history
 */
data class MessageThread(
    val id: String, // Make sure this parameter is added
    val TAG: String = "MessageThread",
    
    // List of all versions of this thread
    val versions: MutableList<MessageVersion> = mutableListOf(),
    
    // Current version index
    var currentVersionIndex: Int = 0,
    
    // Map of chainId to MessageChain
    val chains: MutableMap<String, MessageChain> = mutableMapOf(),
    
    // Current active chain that's being displayed
    var activeChainId: String? = null
) {

    /**
     * Adds a new version to the thread
     */
    fun addVersion(userMessage: Message, botResponse: Message? = null): Int {
        val newVersion = MessageVersion(userMessage, botResponse)
        versions.add(newVersion)
        currentVersionIndex = versions.size - 1
        
        // For the first version, create an initial chain
        if (versions.size == 1) {
            createNewChain()
        }
        
        return currentVersionIndex
    }
    
    fun updateCurrentVersion(userMessage: Message? = null, botResponse: Message? = null, updateChain: Boolean = true) {
        if (versions.isEmpty() || currentVersionIndex < 0 || currentVersionIndex >= versions.size) {
            return
        }
        val currentVersion = versions[currentVersionIndex]
        
        userMessage?.let {
            versions[currentVersionIndex] = currentVersion.copy(userMessage = it)
        }
        
        botResponse?.let {
            versions[currentVersionIndex] = versions[currentVersionIndex].copy(botResponse = it)
            
            if (updateChain) {
                // Also update in active chain if it exists
                activeChainId?.let { chainId ->
                    val chain = chains[chainId]
                    chain?.let { activeChain ->
                        if (activeChain.messages.size >= 2) {
                            // Update bot response in the chain (typically the second message)
                            val updatedMessages = activeChain.messages.toMutableList()


                            for (i in 1 until updatedMessages.size) {
                                if (updatedMessages[i].type == MessageType.BOT) {
                                    updatedMessages[i] = botResponse.copy(chainId = chainId)
                                    break
                                }
                            }
                            activeChain.messages.clear()
                            activeChain.messages.addAll(updatedMessages)
                        } else {
                            // Add bot response to chain
                            activeChain.messages.add(botResponse.copy(chainId = chainId))
                        }
                    }
                }
            }
        }
    }
    
    fun getCurrentVersion(): MessageVersion? {
        return if (versions.isNotEmpty() && currentVersionIndex >= 0 && currentVersionIndex < versions.size) {
            versions[currentVersionIndex]
        } else {
            null
        }
    }
    
    fun hasNextVersion(): Boolean {
        return currentVersionIndex < versions.size - 1
    }
    
    fun hasPreviousVersion(): Boolean {
        return currentVersionIndex > 0
    }
    
    fun moveToNextVersion(): MessageVersion? {
        if (hasNextVersion()) {
            currentVersionIndex++
            val nextVersion = getCurrentVersion()
            
            // When moving to next version, check if this version has chains
            val chainsForVersion = getChainsForVersion(currentVersionIndex)
            if (chainsForVersion.isNotEmpty()) {
                // Use the most recent chain
                activeChainId = chainsForVersion.maxByOrNull { it.timestamp }?.chainId
            } else {
                // Create a new chain for this version if none exists
                createNewChain()
            }
            
            return nextVersion
        }
        return null
    }
    
    fun moveToPreviousVersion(): MessageVersion? {
        if (hasPreviousVersion()) {
            currentVersionIndex--
            val prevVersion = getCurrentVersion()
            
            // When moving to previous version, check if this version has chains
            val chainsForVersion = getChainsForVersion(currentVersionIndex)
            if (chainsForVersion.isNotEmpty()) {
                // Use the most recent chain
                activeChainId = chainsForVersion.maxByOrNull { it.timestamp }?.chainId
            } else {
                // Create a new chain for this version if none exists
                createNewChain()
            }
            
            return prevVersion
        }
        return null
    }

    fun logVersions(tag: String) {
        versions.forEachIndexed { index, version ->
            val currentMarker = if (index == currentVersionIndex) " (CURRENT)" else ""
            val userContent = version.userMessage.content?.take(20) ?: "null"
            val botContent = version.botResponse?.content?.take(20) ?: "null"
            Log.d(tag, "Version $index$currentMarker - User: $userContent..., Bot: $botContent...")
        }
    }

    fun canNavigateBack(): Boolean {
        return currentVersionIndex > 0
    }
    
    fun canNavigateForward(): Boolean {
        return currentVersionIndex < versions.size - 1
    }
    
    fun getCurrentVersionNumber(): Int {
        return currentVersionIndex + 1
    }
    
    fun getTotalVersions(): Int {
        return versions.size
    }
    
    /**
     * Creates a new chain from the current version
     */
    fun createNewChain(): String {
        val chainId = UUID.randomUUID().toString()
        val currentVersion = getCurrentVersion() ?: return chainId
        
        // Create chain starting from current version
        val chain = MessageChain(
            chainId = chainId,
            fromVersionIndex = currentVersionIndex
        )
        
        // Add the user message from the current version
        chain.messages.add(currentVersion.userMessage.copy(chainId = chainId))
        
        // Add the bot response if available
        currentVersion.botResponse?.let { 
            chain.messages.add(it.copy(chainId = chainId)) 
        }
        
        // Store the chain
        chains[chainId] = chain
        
        // Set as active chain
        activeChainId = chainId
        
        return chainId
    }
    
    /**
     * Finds all messages in all chains that belong to the same lineage
     */
    fun findRelatedMessages(lineageId: String): List<Message> {
        return chains.values.flatMap { chain ->
            chain.messages.filter { msg ->
                msg.editLineageId == lineageId || 
                msg.id == lineageId || 
                msg.originalMessageId == lineageId
            }
        }
    }  

    /**
     * Adds a message to the currently active chain
     */
    fun addMessageToActiveChain(message: Message): Boolean {
        val chainId = activeChainId ?: return false
        val chain = chains[chainId] ?: return false
        
        chain.messages.add(message.copy(chainId = chainId))
        return true
    }
    
    /**
     * Sets the active chain and returns its messages
     */
    fun activateChain(chainId: String): List<Message>? {
        if (!chains.containsKey(chainId)) return null
        
        activeChainId = chainId
        return chains[chainId]?.messages
    }
    
    /**
     * Gets all chains that started from a specific version
     */
    fun getChainsForVersion(versionIndex: Int): List<MessageChain> {
        return chains.values.filter { it.fromVersionIndex == versionIndex }
    }
    
    /**
     * Gets messages in chronological order from the active chain
     * This is useful for building prompts with history
     */
    fun getActiveChainMessages(): List<Message> {
        val chainId = activeChainId ?: return emptyList()
        val chain = chains[chainId] ?: return emptyList()
        
        // Return messages sorted by timestamp to ensure proper order
        return chain.messages.sortedBy { it.timestamp }
    }
    
    /**
     * Logs the current chain structure for debugging
     */
    fun logChainStructure() {
        Log.d(TAG, "Thread $id has ${chains.size} chains:")
        chains.forEach { (chainId, chain) ->
            val isActive = if (chainId == activeChainId) " (ACTIVE)" else ""
            Log.d(TAG, "Chain $chainId$isActive - From version ${chain.fromVersionIndex}, " +
                      "has ${chain.messages.size} messages")
            
            // Log first few messages in this chain
            chain.messages.take(3).forEachIndexed { index, msg ->
                val type = msg.type.name
                val content = msg.content?.take(20) ?: "null"
                Log.d(TAG, "  Message $index: [$type] $content...")
            }
            
            if (chain.messages.size > 3) {
                Log.d(TAG, "  ...and ${chain.messages.size - 3} more messages")
            }
        }
    }
}