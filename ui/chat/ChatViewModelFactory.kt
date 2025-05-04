package com.example.baboonchat.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.baboonchat.data.local.ChatHistoryManagerFactory
import com.example.baboonchat.data.remote.MessageRepository

/**
 * Factory to create ChatViewModel with proper dependencies
 * This allows us to keep the ViewModel as a standard ViewModel while
 * still providing it with context-dependent components
 */
class ChatViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            // Create MessageRepository
            val repository = MessageRepository()
            
            // Create ChatHistoryManager via factory
            val historyManager = ChatHistoryManagerFactory.getInstance(context)
            
            // Create ViewModel with dependencies
            return ChatViewModel(repository, historyManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}