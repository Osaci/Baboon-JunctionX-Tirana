package com.example.baboonchat.ui.chat

import android.util.Log
import com.example.baboonchat.data.model.MessageThread

class MessageThreadLogger {
    companion object {
        private const val TAG = "ThreadChainLogger"
        
        fun logThreadState(
            messageThreads: Map<String, MessageThread>,
            currentMessages: List<com.example.baboonchat.data.model.Message>
        ) {
            Log.d(TAG, "======== THREAD STATE LOG ========")
            Log.d(TAG, "Total threads: ${messageThreads.size}")
            Log.d(TAG, "Currently visible messages: ${currentMessages.size}")
            
            messageThreads.forEach { (threadId, thread) ->
                Log.d(TAG, "Thread: $threadId (${thread.versions.size} versions, current: ${thread.currentVersionIndex})")
                
                // Log versions
                thread.versions.forEachIndexed { index, version ->
                    val isActive = if (index == thread.currentVersionIndex) "ACTIVE" else "inactive"
                    Log.d(TAG, "  Version $index [$isActive]:")
                    
                    // Check if this version's message is visible
                    val userMsgId = version.userMessage.id
                    val isUserMsgVisible = currentMessages.any { it.id == userMsgId }
                    Log.d(TAG, "    User msg [${if (isUserMsgVisible) "VISIBLE" else "hidden"}]: ${version.userMessage.content?.take(30) ?: "null"}...")
                    
                    // Log bot response if it exists
                    version.botResponse?.let { botResponse ->
                        val isBotMsgVisible = currentMessages.any { it.id == botResponse.id }
                        Log.d(TAG, "    Bot msg [${if (isBotMsgVisible) "VISIBLE" else "hidden"}]: ${botResponse.content?.take(30) ?: "null"}...")
                    }
                }
                
                // Log chains
                Log.d(TAG, "  Chains (${thread.chains.size}):")
                thread.chains.forEach { (chainId, chain) ->
                    val isActive = if (chainId == thread.activeChainId) "ACTIVE" else "inactive"
                    Log.d(TAG, "    Chain: $chainId [${isActive}] (from version: ${chain.fromVersionIndex})")
                    Log.d(TAG, "      Messages (${chain.messages.size}):")
                    chain.messages.forEachIndexed { index, msg ->
                        val isVisible = currentMessages.any { it.id == msg.id }
                        Log.d(TAG, "        ${index+1}. ${msg.type} [${if (isVisible) "VISIBLE" else "hidden"}]: ${msg.content?.take(200) ?: "null"}...")
                    }
                }
            }
            
            Log.d(TAG, "================================")
        }
    }
}