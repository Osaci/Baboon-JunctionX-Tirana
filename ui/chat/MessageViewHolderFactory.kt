// MessageViewHolderFactory.kt
package com.example.baboonchat.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.baboonchat.R

/**
 * Factory for creating and styling message view holders
 */
class MessageViewHolderFactory {
    
    companion object {
        const val VIEW_TYPE_USER = 0
        const val VIEW_TYPE_BOT = 1
        const val VIEW_TYPE_BOT_WITH_IMAGE = 2
        const val VIEW_TYPE_ERROR = 3
    }
    
    /**
     * Creates a MessageViewHolder based on the view type
     */
    fun create(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = layoutInflater.inflate(R.layout.item_message_user, parent, false)
                MessageViewHolder(view)
            }
            VIEW_TYPE_BOT_WITH_IMAGE -> {
                val view = layoutInflater.inflate(R.layout.item_message_bot_with_image, parent, false)
                MessageViewHolder(view)
            }
            VIEW_TYPE_ERROR -> {
                val view = layoutInflater.inflate(R.layout.item_message_error, parent, false)
                MessageViewHolder(view)
            }
            else -> { // VIEW_TYPE_BOT
                val view = layoutInflater.inflate(R.layout.item_message_bot, parent, false)
                MessageViewHolder(view)
            }
        }
    }
    
    /**
     * Updates the style of a view holder based on the view type
     */
    fun updateStyle(holder: MessageViewHolder, viewType: Int) {
        // Apply theme-specific styling if needed
        when (viewType) {
            VIEW_TYPE_USER -> {
                // Apply user message styling
            }
            VIEW_TYPE_BOT, VIEW_TYPE_BOT_WITH_IMAGE -> {
                // Apply bot message styling
            }
            VIEW_TYPE_ERROR -> {
                // Apply error message styling
            }
        }
    }
}