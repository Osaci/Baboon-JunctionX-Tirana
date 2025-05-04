package com.example.baboonchat.data.local

import android.content.Context

/**
 * Factory class to create ChatHistoryManager instances.
 * This decouples the ViewModel from direct Context dependency.
 */
class ChatHistoryManagerFactory(private val applicationContext: Context) {
    
    /**
     * Creates a new ChatHistoryManager instance with the application context
     */
    fun create(): ChatHistoryManager {
        return ChatHistoryManager(applicationContext)
    }
    
    companion object {
        @Volatile
        private var INSTANCE: ChatHistoryManager? = null
        
        /**
         * Get a singleton instance of ChatHistoryManager
         */
        fun getInstance(context: Context): ChatHistoryManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ChatHistoryManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}