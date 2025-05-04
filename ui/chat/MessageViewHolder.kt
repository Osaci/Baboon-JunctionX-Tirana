package com.example.baboonchat.ui.chat

import android.text.SpannableString
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.baboonchat.R
import com.example.baboonchat.data.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val contentTextView: TextView? = itemView.findViewById(R.id.message_content)
    private val contentContainer: LinearLayout? = itemView.findViewById(R.id.message_content_container)
    private val codeBlocksContainer: LinearLayout? = itemView.findViewById(R.id.code_blocks_container)
    private val timestampTextView: TextView? = itemView.findViewById(R.id.message_timestamp)
    private val avatarImageView: ImageView? = itemView.findViewById(R.id.avatar_image)
    
    /**
     * Binds the message data to the view
     * Make this open so it can be overridden
     */
    open fun bind(message: Message, formattedText: SpannableString? = null) {
        // Set the formatted text to the content TextView if provided
        if (formattedText != null) {
            contentTextView?.text = formattedText
        } else {
            contentTextView?.text = message.content
        }
        
        // Set timestamp
        setTimestamp(message.timestamp)
    }
    
    /**
     * Sets the message timestamp
     */
    fun setTimestamp(date: Date) {
        timestampTextView?.let {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            it.text = dateFormat.format(date)
        }
    }
    
    /**
     * Get the message content TextView
     */
    fun getContentTextView(): TextView? {
        return contentTextView
    }
    
    /**
     * Get the content container
     */
    fun getContentContainer(): LinearLayout? {
        return contentContainer
    }
    
    /**
     * Get the code blocks container
     */
    fun getCodeBlocksContainer(): LinearLayout? {
        return codeBlocksContainer
    }
    
    /**
     * Clears all content containers
     */
    fun clearContent() {
        contentContainer?.removeAllViews()
        codeBlocksContainer?.removeAllViews()
    }
}