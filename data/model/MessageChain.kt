package com.example.baboonchat.data.model

import java.util.Date

/**
 * Represents a message chain that contains a sequence of messages
 * created after a specific version of a user message
 */
data class MessageChain(
    val chainId: String,
    val fromVersionIndex: Int,
    val messages: MutableList<Message> = mutableListOf(),
    val timestamp: Date = Date()
)

/**
 * Extension to MessageThread to support chains
 */
// Add these properties and methods to your MessageThread class
/*
    // Map of chainId to MessageChain
    val chains: MutableMap<String, MessageChain> = mutableMapOf()
    
    // Current active chain that's being displayed
    var activeChainId: String? = null
    
    /**
     * Creates a new chain from the current version
     */
    fun createNewChain(sourceMsgIds: List<String>? = null): String {
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
*/