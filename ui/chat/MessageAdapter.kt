package com.example.baboonchat.ui.chat

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.baboonchat.R
import com.example.baboonchat.data.model.Message
import com.example.baboonchat.data.model.MessageThread
import com.example.baboonchat.data.model.MessageType
import com.example.baboonchat.data.model.MessageChain
import com.example.baboonchat.ui.theme.ThemeManager
import io.github.kbiakov.codeview.CodeView
import io.github.kbiakov.codeview.adapters.Options
import java.util.regex.Pattern

class MessageAdapter( 
    private val viewHolderFactory: MessageViewHolderFactory,
    private var themeManager: ThemeManager
) : ListAdapter<Message, MessageViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_BOT = 1
        private const val VIEW_TYPE_SUPPORT_CONTACT = 2
        private const val VIEW_TYPE_SYSTEM = 3
        private const val VIEW_TYPE_ERROR = 4
    }

    // Add callback for representative button click
    var onRepresentativeClick: (() -> Unit)? = null

    private var chatViewModel: ChatViewModel? = null
    private val TAG = "MessageAdapter"
    private val THEME_TAG = "ThemeDebug"

    private var versionNavigationListener: MessageVersionNavigationListener? = null
    private var editMessageListener: EditMessageListener? = null

    @Deprecated("Use the constructor with both parameters")
    constructor() : this(
        MessageViewHolderFactory(),
        ThemeManager(android.app.Application())
    ) {
        throw IllegalStateException("MessageAdapter requires viewHolderFactory and themeManager")
    }

    fun setChatViewModel(viewModel: ChatViewModel) {
        this.chatViewModel = viewModel
    }

    fun setVersionNavigationListener(listener: MessageVersionNavigationListener) {
        this.versionNavigationListener = listener
    }

    fun setEditMessageListener(listener: EditMessageListener) {
        this.editMessageListener = listener
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return when {
            // Check if this is a support contact message
            message.type == MessageType.BOT && 
            message.content?.contains("Would you like to speak with a representative?") == true -> VIEW_TYPE_SUPPORT_CONTACT
            
            // System messages (like "has joined the chat")
            message.type == MessageType.ERROR && 
            (message.content?.contains("has joined the chat") == true || 
             message.content?.contains("You are #") == true ||
             message.content?.contains("Connecting to") == true) -> VIEW_TYPE_SYSTEM
            
            message.type == MessageType.USER -> VIEW_TYPE_USER
            message.type == MessageType.BOT -> VIEW_TYPE_BOT
            message.type == MessageType.ERROR -> VIEW_TYPE_ERROR
            else -> VIEW_TYPE_BOT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return when (viewType) {
            VIEW_TYPE_SUPPORT_CONTACT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_support_contact, parent, false)
                SupportContactViewHolder(view)
            }
            VIEW_TYPE_SYSTEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_system_message, parent, false)
                SystemMessageViewHolder(view)
            }
            else -> viewHolderFactory.create(parent, viewType)
        }
    }

    inner class SupportContactViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.message_text)
        private val representativeButton: Button = itemView.findViewById(R.id.btn_talk_representative)
    
        fun bind(message: Message) {
            messageTextView.text = message.content
            
            // Use BACKGROUND color type for bot message background
            val botMessageBg = themeManager.getThemeColor(ThemeManager.ThemeColorType.BACKGROUND)
            
            // Use a darker shade of primary color for bot message text
            val primaryColor = themeManager.getThemeColor(ThemeManager.ThemeColorType.BACKGROUND)
            val botMessageText = if (primaryColor != null) {
                // Make text darker than primary color
                primaryColor and 0xFF7F7F7F.toInt()
            } else {
                ContextCompat.getColor(itemView.context, R.color.dark_bot_msg_text)
            }
            
            // Set background color
            val drawable = ContextCompat.getDrawable(itemView.context, R.drawable.bot_message_background)?.mutate()
            if (drawable is GradientDrawable) {
                drawable.setColor(botMessageBg)
            }
            messageTextView.background = drawable
            messageTextView.setTextColor(botMessageText)
            
            // Show button for support contact messages
            representativeButton.visibility = View.VISIBLE
            representativeButton.setOnClickListener {
                onRepresentativeClick?.invoke()
            }
            
            // Call the timestamp setter from parent
            setTimestamp(message.timestamp)
        }
    }

    inner class SystemMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.system_message_text)
        
        fun bind(message: Message) {
            messageTextView.text = message.content
            setTimestamp(message.timestamp)
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)

        when (holder) {
            is SupportContactViewHolder -> {
                holder.bind(message)
                return
            }
            is SystemMessageViewHolder -> {
                holder.bind(message)
                return
            }
            else -> {
                // Continue with existing binding logic for other ViewHolders
                try {
                    // Use the factory to update the ViewHolder styling
                    val viewType = getItemViewType(position)
                    viewHolderFactory.updateStyle(holder, viewType)
                    
                    // Apply theme-based styling
                    when (viewType) {
                        MessageViewHolderFactory.VIEW_TYPE_USER -> themeManager.styleUserMessage(holder.itemView)
                        MessageViewHolderFactory.VIEW_TYPE_BOT -> themeManager.styleBotMessage(holder.itemView)
                        MessageViewHolderFactory.VIEW_TYPE_ERROR -> themeManager.styleErrorMessage(holder.itemView)
                    }

                    // Get the message content safely
                    val content = message.content ?: ""

                    Log.d(TAG, "Processing message ID ${message.id}, content length: ${content.length}")

                    // Check for code blocks or tables
                    val hasSpecialBlocks = content.contains("```") || containsMarkdownTable(content)

                    if (hasSpecialBlocks) {
                        // Process content with special blocks
                        processMessageWithSpecialBlocks(content, holder, message)
                    } else {
                        // Use text response for other parts
                        val formattedText = formatTextWithoutCodeBlocks(content, holder.itemView.context)

                        // Make sure regular text view has content
                        holder.getContentTextView()?.apply {
                            visibility = View.VISIBLE
                            text = formattedText
                        }

                        // Set timestamp
                        holder.setTimestamp(message.timestamp)
                    }

                    if (viewType == MessageViewHolderFactory.VIEW_TYPE_USER) {
                        val threadId = message.threadId

                        val editButton = holder.itemView.findViewById<ImageButton>(R.id.edit_message_button)
                        val nextButton = holder.itemView.findViewById<ImageButton>(R.id.next_version_button)
                        val prevButton = holder.itemView.findViewById<ImageButton>(R.id.prev_version_button)
                        val versionCounterView = holder.itemView.findViewById<TextView>(R.id.version_counter)

                        // Get the user message text color from ThemeManager
                        val userMessageTextColor = themeManager.getThemeColor(ThemeManager.ThemeColorType.USER_MESSAGE_TEXT) 
                            ?: ContextCompat.getColor(holder.itemView.context, R.color.light_user_msg_text)
                        
                        // Set the button colors to match the user message text color
                        prevButton?.setColorFilter(userMessageTextColor)
                        nextButton?.setColorFilter(userMessageTextColor)
                        editButton?.setColorFilter(userMessageTextColor)
                        versionCounterView?.setTextColor(userMessageTextColor)

                        // Set click listener to show edit dialog
                        editButton.setOnClickListener {
                            if (threadId != null) {
                                showEditMessageDialog(holder.itemView.context, message)
                            }
                        }

                        // Default state - hide navigation buttons
                        prevButton?.visibility = View.GONE
                        nextButton?.visibility = View.GONE
                        versionCounterView?.visibility = View.GONE

                        if (threadId != null) {
                            val thread = getMessageThreadInfo(threadId)
                            
                            if (thread != null && thread.versions.size > 1) {
                                // Get current version of the thread
                                val currentVersion = thread.currentVersionIndex
                                
                                // Get the lineage ID of this message
                                val messageLineageId = message.editLineageId ?: message.id
                                
                                Log.d(TAG, "Message at position $position: id=${message.id}, lineageId=$messageLineageId")
                                
                                // Find all versions that contain this message's lineage
                                val relatedVersions = mutableListOf<Int>()
                                val seenMessages = mutableSetOf<String>()
                                
                                // Check ALL versions and chains for related messages
                                for ((versionIndex, version) in thread.versions.withIndex()) {
                                    // Check all chains that originate from this version
                                    val chainsForVersion = thread.chains.values.filter { it.fromVersionIndex == versionIndex }
                                    
                                    for (chain in chainsForVersion) {
                                        for (chainMsg in chain.messages) {
                                            if (chainMsg.type == message.type && 
                                                (chainMsg.editLineageId == messageLineageId || 
                                                 chainMsg.id == messageLineageId ||
                                                 chainMsg.originalMessageId == message.id ||
                                                 message.originalMessageId == chainMsg.id)) {
                                                
                                                if (!seenMessages.contains(chainMsg.id)) {
                                                    seenMessages.add(chainMsg.id)
                                                    Log.d(TAG, "Found related message in version $versionIndex: id=${chainMsg.id}, content=${chainMsg.content?.take(20)}")
                                                    
                                                    if (!relatedVersions.contains(versionIndex)) {
                                                        relatedVersions.add(versionIndex)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Log.d(TAG, "Related version indices for message ${message.id}: $relatedVersions")
                                
                                // Only show version counter if this message has multiple versions
                                if (relatedVersions.size > 1) {
                                    // Sort indices for consistent navigation
                                    relatedVersions.sort()
                                    
                                    // Find which version we're currently viewing
                                    val currentVersionPos = relatedVersions.indexOf(currentVersion)
                                    
                                    if (currentVersionPos >= 0) {
                                        Log.d(TAG, "Setting counter to ${currentVersionPos + 1}/${relatedVersions.size}")
                                        
                                        // Show navigation controls
                                        prevButton?.visibility = View.VISIBLE
                                        nextButton?.visibility = View.VISIBLE
                                        versionCounterView?.visibility = View.VISIBLE
                                        
                                        // Set version counter text
                                        versionCounterView?.text = "${currentVersionPos + 1}/${relatedVersions.size}"
                                        
                                        // Enable/disable navigation buttons
                                        prevButton?.isEnabled = currentVersionPos > 0
                                        prevButton?.alpha = if (currentVersionPos > 0) 1.0f else 0.5f
                                        
                                        nextButton?.isEnabled = currentVersionPos < relatedVersions.size - 1
                                        nextButton?.alpha = if (currentVersionPos < relatedVersions.size - 1) 1.0f else 0.5f
                                        
                                        // Set click listeners
                                        prevButton?.setOnClickListener {
                                            if (currentVersionPos > 0) {
                                                val prevVersionIdx = relatedVersions[currentVersionPos - 1]
                                                versionNavigationListener?.navigateToSpecificVersion(threadId, prevVersionIdx)
                                            }
                                        }
                                        
                                        nextButton?.setOnClickListener {
                                            if (currentVersionPos < relatedVersions.size - 1) {
                                                val nextVersionIdx = relatedVersions[currentVersionPos + 1]
                                                versionNavigationListener?.navigateToSpecificVersion(threadId, nextVersionIdx)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Hide navigation buttons for non-user messages
                        val prevButton = holder.itemView.findViewById<ImageButton>(R.id.prev_version_button)
                        val nextButton = holder.itemView.findViewById<ImageButton>(R.id.next_version_button)
                        val versionCounterView = holder.itemView.findViewById<TextView>(R.id.version_counter)

                        prevButton?.visibility = View.GONE
                        nextButton?.visibility = View.GONE
                        versionCounterView?.visibility = View.GONE
                    }

                    // Process images
                    if (message.containsImage) {
                        val imageView = holder.itemView.findViewById<ImageView>(R.id.message_image)
                        val progressBar = holder.itemView.findViewById<ProgressBar>(R.id.image_loading_progress)

                        if (imageView != null && progressBar != null) {
                            Log.d(TAG, "Loading image for message: ${message.id}")
                            progressBar.visibility = View.VISIBLE

                            if (!message.imageUrl.isNullOrEmpty()) {
                                Log.d(TAG, "Loading from URL: ${message.imageUrl}")
                                // Loading from URL
                                Glide.with(holder.itemView.context)
                                    .load(message.imageUrl)
                                    .apply(RequestOptions()
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .error(R.drawable.ic_error_placeholder))
                                    .into(imageView)
                                    .clearOnDetach()

                                progressBar.visibility = View.GONE

                            } else if (!message.imageData.isNullOrEmpty()) {
                                try {
                                    Log.d(TAG, "Loading from base 64 data")
                                    if (message.imageData.startsWith("data:image")) {
                                        // Parse image data
                                        val base64Data = message.imageData.substring(message.imageData.indexOf(",") + 1)
                                        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)

                                        Glide.with(holder.itemView.context)
                                            .load(imageBytes)
                                            .apply(RequestOptions()
                                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                                .error(R.drawable.ic_error_placeholder))
                                            .into(imageView)
                                            .clearOnDetach()
                                    } else {
                                        // Try decoding directly
                                        val imageBytes = Base64.decode(message.imageData, Base64.DEFAULT)
                                        Glide.with(holder.itemView.context)
                                            .load(imageBytes)
                                            .apply(RequestOptions()
                                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                                .error(R.drawable.ic_error_placeholder))
                                            .into(imageView)
                                            .clearOnDetach()
                                    }
                                    progressBar.visibility = View.GONE
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error loading base64 image: ${e.message}")
                                    progressBar.visibility = View.GONE
                                }
                            } else {
                                progressBar.visibility = View.GONE
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error binding view holder: ${e.message}")
                    e.printStackTrace()

                    // Fallback to text binding
                    holder.getContentTextView()?.apply {
                        visibility = View.VISIBLE
                        text = message.content ?: ""
                    }
                    holder.setTimestamp(message.timestamp)
                }
            }
        }
    }

    private fun navigateToSpecificVersion(threadId: String, versionIndex: Int) {
        chatViewModel?.navigateToSpecificVersion(threadId, versionIndex)
    }

    private fun getMessageThreadInfo(threadId: String): MessageThread? {
        return chatViewModel?.getMessageThread(threadId)
    }

    private fun showEditMessageDialog(context: Context, message: Message) {
        // Initialize theme manager if not already done
        if (themeManager == null) {
            themeManager = ThemeManager(context)
        }

        val modalBackground = themeManager.getThemeColor(ThemeManager.ThemeColorType.MODAL_BACKGROUND) ?: Color.WHITE
        val textColor = themeManager.getThemeColor(ThemeManager.ThemeColorType.TEXT) ?: Color.BLACK
        val modalBorder = themeManager.getThemeColor(ThemeManager.ThemeColorType.MODAL_BORDER) ?: Color.GRAY
        val userMsgBg = themeManager.getThemeColor(ThemeManager.ThemeColorType.USER_MESSAGE_BACKGROUND) ?: Color.BLUE
        val userMsgText = themeManager.getThemeColor(ThemeManager.ThemeColorType.USER_MESSAGE_TEXT) ?: Color.WHITE
        
        //Create dialog view
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_message, null)

        // Get views from dialog
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val editInput = dialogView.findViewById<EditText>(R.id.edit_message_input)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        val sendButton = dialogView.findViewById<Button>(R.id.send_button)

        // Style the dialog based on theme
        dialogView.setBackgroundColor(modalBackground)
        titleTextView.setTextColor(textColor)
        
        // Create a darker background for the input field
        val editTextBackground = editInput.background as GradientDrawable
        editTextBackground.setColor(ColorUtils.setAlphaComponent(modalBorder, 50))
        editTextBackground.setStroke(1, modalBorder)
        
        // Set text color for input field
        editInput.setTextColor(textColor)
        editInput.setHintTextColor(userMsgBg)
        
        // Style buttons
        cancelButton.setTextColor(textColor)
        sendButton.setBackgroundColor(userMsgBg)
        sendButton.setTextColor(userMsgText)
        
        // Pre-fill with current message text
        editInput.setText(message.content)
        
        // Build the dialog
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Set window background to be semi-transparent
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // Setup button listeners
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        sendButton.setOnClickListener {
            val editedText = editInput.text.toString().trim()
            if (editedText.isNotEmpty() && editedText != message.content) {
                // Call the callback to handle the edited message
                editMessageListener?.onMessageEdited(message.id, editedText)
            }
            dialog.dismiss()
        }
        
        // Show the dialog
        dialog.show()
    }

    // Also add this extension function to find the activity from context
    private fun Context.findActivity(): AppCompatActivity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is AppCompatActivity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    // Function to notify adapter that theme has changed
    fun notifyThemeChanged() {
        Log.d(THEME_TAG, "Theme changed, notifying adapter...")
        notifyDataSetChanged()  // Force redraw of all items with new theme
    }
    
    // Checks if the message contains a markdown table
    private fun containsMarkdownTable(text: String): Boolean {
        val pattern = Pattern.compile("\\|(.+?)\\|[\\s\\S]*?\\|", Pattern.DOTALL)
        val matcher = pattern.matcher(text)
        return matcher.find()
    }

    interface MessageVersionNavigationListener {
        fun onPreviousVersion(threadId: String)
        fun onNextVersion(threadId: String)
        fun navigateToSpecificVersion(threadId: String, versionIndex: Int)
    }

    interface OnMessageActionListener {
        fun onEditMessage(message: Message)
        fun onCopyContent(content: String)
    }

    interface VersionNavigationListener {
        fun navigateToNextVersion(threadId: String): Boolean
        fun navigateToPreviousVersion(threadId: String): Boolean
        fun navigateToSpecificVersion(threadId: String, versionIndex: Int): Boolean
    }

    interface EditMessageListener {
        fun onMessageEdited(messageId: String, newContent: String)
    }

    // Process message with special blocks (code blocks and tables)
    private fun processMessageWithSpecialBlocks(messageText: String, holder: MessageViewHolder, message: Message) {
        try {
            // Hide the regular Content TextView since we'll be using the container
            holder.getContentTextView()?.visibility = View.GONE

            // Get the content container
            val contentContainer = holder.getContentContainer()
            if (contentContainer == null) {
                Log.e(TAG, "Content container not found in layout")
                // Fallback to regular TextView if container not found
                holder.getContentTextView()?.apply {
                    visibility = View.VISIBLE
                    text = messageText
                }
                holder.setTimestamp(message.timestamp)
                return
            }

            // Make container visible
            contentContainer.visibility = View.VISIBLE

            // Clear content container
            contentContainer.removeAllViews()

            // Find all code blocks
            val codeBlocks = findCodeBlocks(messageText)
            Log.d(TAG, "Found ${codeBlocks.size} code blocks in message")

            // Find all table blocks
            val tableBlocks = findTableBlocks(messageText)
            Log.d(TAG, "Found ${tableBlocks.size} table blocks in message")

            // Combine both types of blocks and sort by position
            val allBlocks = (codeBlocks + tableBlocks).sortedBy { it.startPosition }

            if (allBlocks.isEmpty()) {
                // If no special blocks are found, format text normally
                val formattedText = formatTextWithoutCodeBlocks(messageText, holder.itemView.context)
                val textView = createTextView(holder.itemView.context, formattedText)
                contentContainer.addView(textView)
            } else {
                var lastPosition = 0

                for (block in allBlocks) {
                    // Add text before the block
                    if (block.startPosition > lastPosition) {
                        val textBefore = messageText.substring(lastPosition, block.startPosition)
                        if (textBefore.isNotEmpty()) {
                            val formattedText = formatTextWithoutCodeBlocks(textBefore, holder.itemView.context)
                            val textView = createTextView(holder.itemView.context, formattedText)
                            contentContainer.addView(textView)
                        }
                    }

                    // Add the block based on its type
                    if (block.language == "table") {
                        // This is a table block
                        val tableView = processMarkdownTable(block.code, holder.itemView.context)
                        contentContainer.addView(tableView)
                    } else {
                        // This is a code block
                        val codeBlockView = createCodeBlockView(
                            holder.itemView.context,
                            block.language,
                            block.code
                        )
                        contentContainer.addView(codeBlockView)
                    }

                    // Update last position
                    lastPosition = block.endPosition
                }

                // Add remaining text after the last block
                if (lastPosition < messageText.length) {
                    val textAfter = messageText.substring(lastPosition)
                    if (textAfter.isNotEmpty()) {
                        val formattedText = formatTextWithoutCodeBlocks(textAfter, holder.itemView.context)
                        val textView = createTextView(holder.itemView.context, formattedText)
                        contentContainer.addView(textView)
                    }
                }
            }

            // Set the timestamp
            holder.setTimestamp(message.timestamp)

            // Hide the old code blocks container
            holder.getCodeBlocksContainer()?.visibility = View.GONE

        } catch (e: Exception) {
            Log.e(TAG, "Error processing message with special blocks: ${e.message}")
            e.printStackTrace()

            // Fallback to simple TextView
            holder.getContentTextView()?.apply {
                visibility = View.VISIBLE
                text = messageText
            }
            holder.setTimestamp(message.timestamp)
        }
    }



    // Advanced table detection to better handle specific table format
    private fun findTableBlocks(text: String): List<CodeBlock> {
        val tableBlocks = mutableListOf<CodeBlock>()

        try {
            // Pattern for standard markdown tables (header row, separator row, data rows)
            val tablePattern = Pattern.compile(
                "^\\|(.+?)\\|\\s*\\n" +  // Header row
                "\\|([-:]+\\|[-:\\s|]+)+\\s*\\n" +  // Separator row
                "(\\|.+?\\|\\s*\\n)+",  // Data rows
                Pattern.MULTILINE
            )
            val matcher = tablePattern.matcher(text)

            // First check for standard tables
            while (matcher.find()) {
                val tableText = matcher.group(0) ?: continue  
                if (!isPositionInsideCodeBlock(text, matcher.start())) {
                    tableBlocks.add(
                        CodeBlock(
                            language = "table",
                            code = tableText.trim(),
                            startPosition = matcher.start(),
                            endPosition = matcher.end()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding table blocks: ${e.message}")
            e.printStackTrace()
        }

        return tableBlocks
    }

    // Helper method to check if a position in text is inside a code block
    private fun isPositionInsideCodeBlock(text: String, position: Int): Boolean {
        val codeBlockPattern = Pattern.compile("```([a-zA-Z]*)\\s*([\\s\\S]*?)```", Pattern.DOTALL)
        val matcher = codeBlockPattern.matcher(text)

        while (matcher.find()) {
            if (position >= matcher.start() && position <= matcher.end()) {
                return true
            }
        }

        return false
    }

    private fun findCodeBlocks(text: String): List<CodeBlock> {
        val codeBlocks = mutableListOf<CodeBlock>()
    
        // Finding match for code blocks with language identifier, now handling C++
        try {
            // Updated pattern to better match language identifiers including C++
            val pattern = Pattern.compile("```([a-zA-Z+]*)\\s*([\\s\\S]*?)```", Pattern.DOTALL)
            val matcher = pattern.matcher(text)

            while (matcher.find()) {
                val language = matcher.group(1) ?: ""
                var codeContent = matcher.group(2) ?: ""

                // Handle escaped newlines in the code content
                if (codeContent.contains("\\n")) {
                    codeContent = codeContent.replace("\\n", "\n")
                }

                Log.d(TAG, "Found code block: language=$language, position=${matcher.start()}-${matcher.end()}")

                codeBlocks.add(
                    CodeBlock(
                        language = language,
                        code = codeContent.trim(),
                        startPosition = matcher.start(),
                        endPosition = matcher.end()
                    )    
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding code blocks: ${e.message}.")
            e.printStackTrace()
        }
        return codeBlocks
    }

    // Helper function for table styling - adjusts color brightness
    private fun adjustBrightness(colorHex: String, percent: Int): String {
        try {
            // Remove the # if present
            val cleanHex = if (colorHex.startsWith("#")) colorHex.substring(1) else colorHex
            
            // Parse the color
            val color = Color.parseColor("#$cleanHex")
            
            // Get RGB components
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            
            // Adjust brightness
            val adjustedR = (r * (100 + percent) / 100).coerceIn(0, 255)
            val adjustedG = (g * (100 + percent) / 100).coerceIn(0, 255)
            val adjustedB = (b * (100 + percent) / 100).coerceIn(0, 255)
            
            // Format new color
            return String.format("#%02X%02X%02X", adjustedR, adjustedG, adjustedB)
        } catch (e: Exception) {
            // In case of error, return the original color or a fallback
            return colorHex
        }
    }


    private fun processMarkdownTable(tableText: String, context: Context): View {
        return try {
            Log.d(TAG, "Processing markdown table: ${tableText.take(50)}...")
            
            // Initialize theme manager if needed
            if (themeManager == null) {
                themeManager = ThemeManager(context)
            }
            
            // Get theme colors from ThemeManager
            val tableColors = themeManager.getTableColors() ?: return TextView(context).apply {
                text = "Table rendering failed: No theme manager available"
            }
            
            // Use the retrieved colors
            val tableBgColor = tableColors.backgroundColor
            val tableHeaderBgColor = tableColors.headerBackgroundColor
            val tableBorderColor = tableColors.borderColor
            val tableTextColor = tableColors.textColor
            val tableHeaderTextColor = tableColors.headerTextColor
            val alternateRowColor = tableColors.alternateRowColor
            
            // 1. Create container for table and button
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(tableBgColor)
                setPadding(0, 8.dpToPx(context), 0, 8.dpToPx(context))
            }

            // 2. Create header bar with copy button
            val headerBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(tableHeaderBgColor)
                setPadding(8.dpToPx(context), 8.dpToPx(context), 8.dpToPx(context), 8.dpToPx(context))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Add table label
            headerBar.addView(TextView(context).apply {
                text = "TABLE"
                setTypeface(null, Typeface.BOLD)
                setTextColor(tableHeaderTextColor) 
            })

            // Add spacer to push button to the right tableTextColor
            headerBar.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })

            // Create a copy layout with icon and text (similar to code block copy button)
            val copyLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                
                // Create custom button background
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 4 * context.resources.displayMetrics.density
                    
                    // Use alternate row color for button background
                    setColor(alternateRowColor)
                    
                    // Add border with header text color for better visibility
                    setStroke(
                        (1 * context.resources.displayMetrics.density).toInt(),
                        ColorUtils.setAlphaComponent(tableHeaderTextColor, 130)
                    )
                }
                
                // Set padding for the button container
                val paddingDp = 8
                val paddingPx = (paddingDp * context.resources.displayMetrics.density).toInt()
                setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2)
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                
                setOnClickListener {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Table", tableText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Table copied", Toast.LENGTH_SHORT).show()
                    
                    // Visual feedback animation for button click
                    val originalBackground = background
                    
                    // Create highlighted background
                    val highlightBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 4 * context.resources.displayMetrics.density
                        setColor(ColorUtils.setAlphaComponent(tableHeaderTextColor, 80))
                    }
                    
                    background = highlightBg
                    
                    // Restore original background after a short delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        background = originalBackground
                    }, 150)
                }
            }

            // Add copy icon to the layout
            val copyIcon = ImageView(context).apply {
                setImageResource(R.drawable.ic_copy)
                setColorFilter(tableHeaderTextColor)  //tableHeaderTextColor
                layoutParams = LinearLayout.LayoutParams(
                    18.dpToPx(context),
                    18.dpToPx(context)
                ).apply {
                    marginEnd = 4.dpToPx(context)
                }
            }
            copyLayout.addView(copyIcon)

            // Add copy text to the layout
            val copyText = TextView(context).apply {
                text = "Copy"
                setTextColor(tableHeaderTextColor)
                textSize = 14f
            }
            copyLayout.addView(copyText)

            // Add the copy layout to the header
            headerBar.addView(copyLayout)

            // 3. Convert markdown table to HTML with theme colors
            val htmlTable = convertMarkdownTableToHtml(
                tableText,
                context,
                tableBorderColor,
                tableHeaderBgColor,
                tableHeaderTextColor,
                alternateRowColor
            )
            Log.d(TAG, "Converted HTML table:\n$htmlTable")

            // 4. Create WebView with optimized settings
            val webView = WebView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }

                settings.apply {
                    javaScriptEnabled = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }

                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = true
                setBackgroundColor(Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }

            // 5. Create HTML document with responsive styling and theme colors
            val borderColorHex = String.format("#%06X", 0xFFFFFF and tableBorderColor)
            val headerBgColorHex = String.format("#%06X", 0xFFFFFF and tableHeaderBgColor)
            val textColorHex = String.format("#%06X", 0xFFFFFF and tableHeaderTextColor)
            val bgColorHex = String.format("#%06X", 0xFFFFFF and tableBgColor)
            val alternateRowColorHex = String.format("#%06X", 0xFFFFFF and alternateRowColor)
            
            val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        background-color: transparent;
                        color: $textColorHex;
                    }
                    .table-container {
                        width: 100%;
                        overflow-x: auto;
                        -webkit-overflow-scrolling: touch;
                        margin-bottom: 8px;
                    }
                    table {
                        border-collapse: collapse;
                        width: auto;
                        margin: 0;
                        padding: 0;
                        table-layout: auto;
                    }
                    th, td {
                        border: 1px solid $borderColorHex;
                        padding: 8px;
                        text-align: left;
                        color: $textColorHex;
                        min-width: 150px;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        word-wrap: break-word;
                        white-space: normal;
                    }
                    th {
                        background-color: $headerBgColorHex;
                        position: sticky;
                        top: 0;
                    }
                    tr:nth-child(even) {
                        background-color: $bgColorHex;
                    }
                    tr:nth-child(odd):not(:first-child) {
                        background-color: $alternateRowColorHex;
                    }
                </style>
            </head>
            <body>
                <div class="table-container">
                    $htmlTable
                </div>
            </body>
            </html>
            """.trimIndent()

            // 6. Modify WebView settings for better handling of tables
            webView.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                
                // Improved web view settings
                settings.apply {
                    javaScriptEnabled = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                    setSupportZoom(true)  // Allow user to zoom if content is still hard to read
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                
                // Set transparent background
                setBackgroundColor(Color.TRANSPARENT)
                
                // Use hardware acceleration for smoother scrolling
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                
                // Set maximum height for the WebView to avoid overly tall tables
                val maxHeightPx = (200 * context.resources.displayMetrics.density).toInt()
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                minimumHeight = 0
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = true
                
                // Handle tall tables with a JavaScript interface
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("""
                            (function() {
                                var tableHeight = document.querySelector('table').offsetHeight;
                                var screenHeight = window.innerHeight;
                                return Math.min(tableHeight, screenHeight * 0.7);
                            })();
                        """.trimIndent()) { height ->
                            try {
                                val parsedHeight = height.replace("\"", "").toFloat().toInt()
                                if (parsedHeight > 0) {
                                    val maxHeight = Math.min(parsedHeight, maxHeightPx)
                                    layoutParams.height = maxHeight
                                    requestLayout()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error setting WebView height", e)
                            }
                        }
                    }
                }
            }

            // 7. Load HTML into WebView
            webView.loadDataWithBaseURL(
                null, 
                html,
                "text/html", 
                "UTF-8", 
                null
            )

            // 8. Skip the HorizontalScrollView wrapper since we're handling scrolling in the HTML
            // Instead, add the WebView directly to the container
            container.addView(headerBar)
            container.addView(webView)

            container
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering table", e)
            TextView(context).apply {
                text = "Table rendering failed. Raw content:\n\n$tableText"
            }
        }
    }

    // Helper extension function to convert dp to pixels
    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    // Convert markdown table to HTML with theme colors
    private fun convertMarkdownTableToHtml(
        markdownTable: String, 
        context: Context,
        borderColor: Int = Color.parseColor("#dee2e6"),
        headerBgColor: Int = Color.parseColor("#e9ecef"),
        headerTextColor: Int = Color.BLACK,
        alternateRowColor: Int = Color.parseColor("#f5f5f5")
    ): String {
        try {
            val lines = markdownTable.trim().split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.startsWith("|") }

            if (lines.size < 2) return "<p>Invalid table format</p>"

            // Format colors as hex
            val borderColorHex = String.format("#%06X", 0xFFFFFF and borderColor)
            val headerBgColorHex = String.format("#%06X", 0xFFFFFF and headerBgColor)
            val textColorHex = String.format("#%06X", 0xFFFFFF and headerTextColor)
            val alternateRowColorHex = String.format("#%06X", 0xFFFFFF and alternateRowColor)

            val html = StringBuilder().apply {
                append("<table style=\"border-collapse: collapse; width: 100%;\">")
                
                // Process header row
                append("<tr style=\"background-color: $headerBgColorHex;\">")
                lines[0].trim('|').split("|")
                    .map { it.trim() }
                    .forEach { cell ->
                        append("<th style=\"border: 1px solid $borderColorHex; padding: 8px; color: $textColorHex;\">")
                        append(processCellContent(cell, context))
                        append("</th>")
                    }
                append("</tr>")
                
                // Process data rows with alternating colors
                lines.drop(2).forEachIndexed { index, line ->
                    val bgColor = if (index % 2 == 0) {
                        alternateRowColorHex // Odd rows get alternate color
                    } else {
                        // Even rows stay with normal background
                        String.format("#%06X", 0xFFFFFF and borderColor)
                    }
                    
                    append("<tr style=\"background-color: $bgColor;\">")
                    line.trim('|').split("|")
                        .map { it.trim() }
                        .forEach { cell ->
                            append("<td style=\"border: 1px solid $borderColorHex; padding: 8px; color: $textColorHex;\">")
                            append(processCellContent(cell, context))
                            append("</td>")
                        }
                    append("</tr>")
                }
                
                append("</table>")
            }
            
            return html.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error converting table to HTML", e)
            return "<p>Error processing table</p>"
        }
    }
    // Process cell content for tables
    private fun processCellContent(cellText: String, context: Context): String {
        val processedText = cellText
            .replace(Regex("(?m)^(#{1,3})\\s+([^\\n]+)")) { matchResult ->
                val headerLevel = matchResult.groupValues[1].length
                val headerText = matchResult.groupValues[2]
                
                // Create different HTML based on header level
                when (headerLevel) {
                    1 -> "<h1 style=\"font-size: ${18.dpToPx(context)}px; margin: 4px 0; font-weight: bold;\">$headerText</h1>"
                    2 -> "<h2 style=\"font-size: ${16.dpToPx(context)}px; margin: 3px 0; font-weight: bold;\">$headerText</h2>"
                    3 -> "<h3 style=\"font-size: ${14.dpToPx(context)}px; margin: 2px 0; font-weight: bold;\">$headerText</h3>"
                    else -> headerText
                }
            }
            .replace(Regex("\\*\\*(.*?)\\*\\*", RegexOption.DOT_MATCHES_ALL)) { "<strong>${it.groupValues[1]}</strong>" }
            .replace(Regex("\\*(.*?)\\*", RegexOption.DOT_MATCHES_ALL)) { "<em>${it.groupValues[1]}</em>" }
            .replace(Regex("_(.*?)_", RegexOption.DOT_MATCHES_ALL)) { "<em>${it.groupValues[1]}</em>" }
            .replace(Regex("`(.*?)`", RegexOption.DOT_MATCHES_ALL)) { 
                "<code style=\"background: #f5f5f5; padding: 2px 4px; border-radius: 3px;\">${it.groupValues[1]}</code>" 
            }
            .replace(Regex("^\\* ")) { "• " }
        
        return processedText
    }

    private fun createCodeBlockView(
        context: Context,
        language: String,
        code: String
    ): View {
        try {
            // Initialize ThemeManager if needed
            if (themeManager == null) {
                themeManager = ThemeManager(context)
            }
            
            // Get code block colors from ThemeManager
            val codeColors = themeManager.getCodeBlockColors() ?: return TextView(context).apply {
                text = code
                typeface = Typeface.MONOSPACE
            }

            
            // Use the retrieved colors
            val bgColor = codeColors.backgroundColor
            val headerBgColor = codeColors.headerBackgroundColor
            val textColor = codeColors.textColor
            val headerTextColor = codeColors.headerTextColor
            val colorTheme = codeColors.colorTheme
            val buttonColor = codeColors.buttonColor

            // Inflate code block layout
            val codeBlockView = LayoutInflater.from(context)
                .inflate(R.layout.item_code_block, null, false)

            // Set layout parameters
            codeBlockView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            // Get views
            val languageLabel = codeBlockView.findViewById<TextView>(R.id.code_language_label)
            val copyLayout = codeBlockView.findViewById<LinearLayout>(R.id.copy_layout)
            val copyIcon = codeBlockView.findViewById<ImageView>(R.id.copy_icon)
            val copyText = codeBlockView.findViewById<TextView>(R.id.copy_text)
            val codeView = codeBlockView.findViewById<CodeView>(R.id.code_view)

            if (languageLabel == null || copyLayout == null || copyIcon == null || 
                copyText == null || codeView == null) {
                Log.e(TAG, "Missing views in code block layout")
                return createFallbackCodeView(context, code)
            }
            
            // Set header background color
            val headerContainer = codeBlockView.findViewById<View>(R.id.code_block_header)
            headerContainer?.setBackgroundColor(headerBgColor)
            
            // Apply colors to views
            languageLabel.setTextColor(headerTextColor)
            
            // Make sure copyText has the correct headerTextColor
            copyText.setTextColor(headerTextColor) //

            // Create dynamic button background using theme colors
            val buttonBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 4 * context.resources.displayMetrics.density
                
                // Use the button color from ThemeManager
                setColor(textColor)
                //buttonColor
                // Create a border using headerTextColor with transparency
                setStroke(
                    (1 * context.resources.displayMetrics.density).toInt(), 
                    ColorUtils.setAlphaComponent(headerTextColor, 130)
                )
            }
            copyLayout.background = buttonBg
            
            // Set icon color explicitly to headerTextColor
            copyIcon.setImageResource(R.drawable.ic_copy)
            copyIcon.setColorFilter(headerTextColor)
            //headerTextColor
            // Set language label
            val displayLanguage = when {
                language.equals("c++", ignoreCase = true) -> "C++"
                language.isNotEmpty() -> language
                else -> "plaintext"
            }
            languageLabel.text = displayLanguage

            // Set up copy functionality
            copyLayout.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Code Snippet", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                
                // Visual feedback animation when button is clicked
                val originalBackground = copyLayout.background
                
                // Create highlighted background for click effect
                val highlightBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 4 * context.resources.displayMetrics.density
                    setColor(ColorUtils.setAlphaComponent(headerTextColor, 80))
                }
                
                copyLayout.background = highlightBg
                
                // Restore original background after a short delay
                Handler(Looper.getMainLooper()).postDelayed({
                    copyLayout.background = originalBackground
                }, 150)
            }

            // Configure code view with syntax highlighting
            try {
                val actualLanguage = if (language.isEmpty()) "text" else language
                
                // Use the theme color from ThemeManager
                codeView.setOptions(
                    Options.Default.get(context)
                    .withLanguage(actualLanguage)
                    .withTheme(colorTheme)
                    .withCode(code))
                    
                // Try to access the internal text view if possible to set colors
                try {
                    val textViewField = CodeView::class.java.getDeclaredField("textView")
                    textViewField.isAccessible = true
                    val textView = textViewField.get(codeView) as? TextView
                    textView?.setTextColor(textColor)
                } catch (e: Exception) {
                    Log.e(TAG, "Error accessing CodeView internals: ${e.message}")
                }
                
                // Set background color
                codeView.setBackgroundColor(bgColor)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting code options: ${e.message}")
                codeView.setCode(code)
            }
            
            return codeBlockView
        } catch (e: Exception) {
            Log.e(TAG, "Error creating code block view: ${e.message}")
            e.printStackTrace()
            return createFallbackCodeView(context, code)
        }
    }

    // Helper method for creating fallback code view
    private fun createFallbackCodeView(context: Context, code: String): TextView {
        // Initialize ThemeManager if needed
        if (themeManager == null) {
            themeManager = ThemeManager(context)
        }
        
        // Get code block colors from ThemeManager
        val codeColors = themeManager.getCodeBlockColors()
        
        // Determine colors based on theme or use defaults
        val bgColor = codeColors?.backgroundColor ?: Color.parseColor("#f0f0f0")
        val textColor = codeColors?.textColor ?: Color.parseColor("#212121")
        
        return TextView(context).apply {
            text = code
            typeface = Typeface.MONOSPACE
            setPadding(16, 16, 16, 16)
            setBackgroundColor(bgColor)
            setTextColor(textColor)
        }
    }

    // Update createTextView to use ThemeManager for styling
    private fun createTextView(context: Context, text: SpannableString): TextView {
        val textView = TextView(context)
        textView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        textView.textSize = 16f
        textView.text = text
        
        // Apply theme-specific text color from ThemeManager
        if (themeManager == null) {
            themeManager = ThemeManager(context)
        }
        
        val currentTheme = themeManager.getCurrentTheme() ?: ThemeManager.THEME_LIGHT
        
        // Set text color based on current theme
        when (currentTheme) {
            ThemeManager.THEME_LIGHT -> textView.setTextColor(ContextCompat.getColor(context, R.color.light_text))
            ThemeManager.THEME_DARK -> textView.setTextColor(ContextCompat.getColor(context, R.color.dark_text))
            ThemeManager.THEME_BROWN -> textView.setTextColor(ContextCompat.getColor(context, R.color.brown_text))
            ThemeManager.THEME_YELLOW -> textView.setTextColor(ContextCompat.getColor(context, R.color.yellow_text))
            ThemeManager.THEME_RED -> textView.setTextColor(ContextCompat.getColor(context, R.color.red_text))
            ThemeManager.THEME_GREEN -> textView.setTextColor(ContextCompat.getColor(context, R.color.green_text))
            ThemeManager.THEME_PURPLE -> textView.setTextColor(ContextCompat.getColor(context, R.color.purple_text))
            ThemeManager.THEME_CYAN -> textView.setTextColor(ContextCompat.getColor(context, R.color.cyan_text))
        }
        
        return textView
    }


    // Format text without code blocks - with theme-aware styling
    private fun formatTextWithoutCodeBlocks(text: String, context: Context): SpannableString {
        try {
            // Process bullet points (convert * to •)
            val bulletPattern = Pattern.compile("(?m)^\\s*\\* ")
            val bulletMatcher = bulletPattern.matcher(text)
            val bulletText = bulletMatcher.replaceAll("• ")

            // Find all formatting elements and their positions
            val formattingRanges = mutableListOf<FormattingRange>()
            
            // Find headers and add newline after them
            val headerPattern = Pattern.compile("(?m)^(#{1,3})\\s+([^\\n]+)")
            val headerMatcher = headerPattern.matcher(bulletText)
            val textWithHeaders = StringBuilder(bulletText)
            
            // We need to track offset as we add newlines
            var headerOffset = 0
            
            while (headerMatcher.find()) {
                val headerMarker = headerMatcher.group(1) ?: ""
                val headerContent = headerMatcher.group(2) ?: ""
                val markerStart = headerMatcher.start() + headerOffset
                val markerEnd = headerMatcher.start(2) + headerOffset // Start of header text
                val contentEnd = headerMatcher.end() + headerOffset
                
                // Check if there's already a newline after the header
                val hasNewline = contentEnd < textWithHeaders.length && textWithHeaders[contentEnd] == '\n'
                
                if (!hasNewline) {
                    // Add a newline after the header
                    textWithHeaders.insert(contentEnd, "\n")
                    headerOffset += 1 // Increment offset for subsequent headers
                }
                
                formattingRanges.add(
                    FormattingRange(
                        markerStart, markerEnd, contentEnd,
                        FormattingType.HEADER, headerMarker.length
                    )
                )
            }
            
            // Use the updated text with newlines after headers
            val processedText = textWithHeaders.toString()
            
            // Find bold text (**text**)
            val boldPattern = Pattern.compile("\\*\\*(.*?)\\*\\*", Pattern.DOTALL)
            val boldMatcher = boldPattern.matcher(processedText)
            while (boldMatcher.find()) {
                formattingRanges.add(
                    FormattingRange(
                        boldMatcher.start(), boldMatcher.start() + 2,
                        boldMatcher.end() - 2, FormattingType.BOLD
                    )
                )
            }
            
            // Find italic text with asterisks (*text*) - but not if it's part of **bold**
            val italicAsteriskPattern = Pattern.compile("\\*(.*?)\\*", Pattern.DOTALL)
            val italicAsteriskMatcher = italicAsteriskPattern.matcher(processedText)
            while (italicAsteriskMatcher.find()) {
                // Check if this is not part of a bold marker
                val isPartOfBold = formattingRanges.any { range -> 
                    range.type == FormattingType.BOLD && 
                    (italicAsteriskMatcher.start() >= range.markerStart && 
                     italicAsteriskMatcher.end() <= range.contentEnd + 4) // +4 to include both ** markers
                }
                
                if (!isPartOfBold) {
                    formattingRanges.add(
                        FormattingRange(
                            italicAsteriskMatcher.start(), italicAsteriskMatcher.start() + 1,
                            italicAsteriskMatcher.end() - 1, FormattingType.ITALIC
                        )
                    )
                }
            }
            
            // Find italic text with underscores (_text_)
            val italicUnderscorePattern = Pattern.compile("_(.*?)_", Pattern.DOTALL)
            val italicUnderscoreMatcher = italicUnderscorePattern.matcher(processedText)
            while (italicUnderscoreMatcher.find()) {
                formattingRanges.add(
                    FormattingRange(
                        italicUnderscoreMatcher.start(), italicUnderscoreMatcher.start() + 1,
                        italicUnderscoreMatcher.end() - 1, FormattingType.ITALIC
                    )
                )
            }

            // Find inline code
            val inlineCodePattern = Pattern.compile("`(.*?)`", Pattern.DOTALL)
            val inlineCodeMatcher = inlineCodePattern.matcher(processedText)
            while (inlineCodeMatcher.find()) {
                formattingRanges.add(
                    FormattingRange(
                        inlineCodeMatcher.start(), inlineCodeMatcher.start() + 1,
                        inlineCodeMatcher.end() - 1, FormattingType.CODE
                    )
                )
            }
            
            // Build text without markers
            val builder = StringBuilder()
            val spans = mutableListOf<FormattedSpan>()
            var lastIndex = 0
            
            // Sort by start position to process in order
            val sortedRanges = formattingRanges.sortedBy { it.markerStart }
            
            // This map tracks which parts of the text have already been processed
            // to avoid duplicating content
            val processedRanges = mutableListOf<Pair<Int, Int>>()
            
            for (range in sortedRanges) {
                // Check if this range overlaps with any already processed ranges
                val overlaps = processedRanges.any { (start, end) ->
                    (range.markerStart <= end && range.contentEnd + (range.markerEnd - range.markerStart) >= start)
                }
                
                if (overlaps) {
                    // Skip this range as it would cause duplication
                    continue
                }
                
                // Add any text before this formatting element
                if (range.markerStart > lastIndex) {
                    builder.append(processedText.substring(lastIndex, range.markerStart))
                }
                
                // Get the content without the markers
                val content = processedText.substring(range.markerEnd, range.contentEnd)
                
                // Record where this content will be in our final string
                val spanStart = builder.length
                builder.append(content)
                val spanEnd = builder.length
                
                // Record the span to apply later
                spans.add(FormattedSpan(spanStart, spanEnd, range.type, range.level))
                
                // Update last index to after this formatting element (including end marker)
                lastIndex = range.contentEnd + (range.markerEnd - range.markerStart)
                
                // Mark this range as processed
                processedRanges.add(Pair(range.markerStart, lastIndex))
            }
            
            // Add any remaining text
            if (lastIndex < processedText.length) {
                builder.append(processedText.substring(lastIndex))
            }
            
            // Create the SpannableString
            val result = SpannableString(builder.toString())
            
            // Apply all the spans with theme-aware colors
            val currentTheme = themeManager.getCurrentTheme() ?: ThemeManager.THEME_LIGHT
            Log.d(THEME_TAG, "Formatting text with theme: $currentTheme")
            
            for (span in spans) {
                when (span.type) {
                    FormattingType.HEADER -> {
                        // Apply header styling based on level
                        when (span.level) {
                            1 -> { // # - Largest header
                                result.setSpan(
                                    RelativeSizeSpan(1.5f),
                                    span.start, span.end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                result.setSpan(
                                    StyleSpan(Typeface.BOLD),
                                    span.start, span.end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                
                                // Apply theme-specific color
                                when (currentTheme) {
                                    ThemeManager.THEME_DARK -> {
                                        result.setSpan(
                                            ForegroundColorSpan(Color.WHITE),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_BROWN -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.brown_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_YELLOW -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.yellow_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_RED -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.red_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_GREEN -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.green_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_PURPLE -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.purple_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_CYAN -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.cyan_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                }
                            }
                            2 -> { // ## - Medium header
                                result.setSpan(
                                    RelativeSizeSpan(1.25f),
                                    span.start, span.end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                result.setSpan(
                                    StyleSpan(Typeface.BOLD),
                                    span.start, span.end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                
                                // Apply theme-specific color
                                when (currentTheme) {
                                    ThemeManager.THEME_DARK -> {
                                        result.setSpan(
                                            ForegroundColorSpan(Color.WHITE),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_BROWN -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.brown_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_YELLOW -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.yellow_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_RED -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.red_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_GREEN -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.green_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_PURPLE -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.purple_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_CYAN -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.cyan_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                }
                            }
                            3 -> { // ### - Small header
                                result.setSpan(
                                    RelativeSizeSpan(1.1f),
                                    span.start, span.end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                result.setSpan(
                                    StyleSpan(Typeface.BOLD),
                                    span.start, span.end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                
                                // Apply theme-specific color
                                when (currentTheme) {
                                    ThemeManager.THEME_DARK -> {
                                        result.setSpan(
                                            ForegroundColorSpan(Color.WHITE),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_BROWN -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.brown_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_YELLOW -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.yellow_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_RED -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.red_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_GREEN -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.green_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_PURPLE -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.purple_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                    ThemeManager.THEME_CYAN -> {
                                        result.setSpan(
                                            ForegroundColorSpan(ContextCompat.getColor(context, R.color.cyan_text)),
                                            span.start, span.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                }
                            }
                        }
                    }
                    FormattingType.BOLD -> {
                        result.setSpan(
                            StyleSpan(Typeface.BOLD),
                            span.start, span.end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    FormattingType.ITALIC -> {
                        result.setSpan(
                            StyleSpan(Typeface.ITALIC),
                            span.start, span.end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    FormattingType.CODE -> {
                        // Get theme-specific colors for inline code
                        val bgColor: Int
                        val fgColor: Int
                        
                        when (currentTheme) {
                            ThemeManager.THEME_DARK -> {
                                bgColor = Color.parseColor("#2D2D2D")
                                fgColor = Color.parseColor("#CC7832")
                            }
                            ThemeManager.THEME_BROWN -> {
                                bgColor = ContextCompat.getColor(context, R.color.brown_dark)
                                fgColor = ContextCompat.getColor(context, R.color.brown_light)
                            }
                            ThemeManager.THEME_YELLOW -> {
                                bgColor = ContextCompat.getColor(context, R.color.yellow_dark)
                                fgColor = ContextCompat.getColor(context, R.color.yellow_light)
                            }
                            ThemeManager.THEME_RED -> {
                                bgColor = ContextCompat.getColor(context, R.color.red_dark)
                                fgColor = ContextCompat.getColor(context, R.color.red_light)
                            }
                            ThemeManager.THEME_GREEN -> {
                                bgColor = ContextCompat.getColor(context, R.color.green_dark)
                                fgColor = ContextCompat.getColor(context, R.color.green_light)
                            }
                            ThemeManager.THEME_PURPLE -> {
                                bgColor = ContextCompat.getColor(context, R.color.purple_dark)
                                fgColor = ContextCompat.getColor(context, R.color.purple_light)
                            }
                            ThemeManager.THEME_CYAN -> {
                                bgColor = ContextCompat.getColor(context, R.color.cyan_dark)
                                fgColor = ContextCompat.getColor(context, R.color.cyan_light)
                            }
                            else -> {
                                // Default light theme
                                bgColor = ContextCompat.getColor(context, R.color.inline_code_bg)
                                    ?: Color.parseColor("#f5f5f5")
                                fgColor = ContextCompat.getColor(context, R.color.inline_code_fg)
                                    ?: Color.parseColor("#e83e8c")
                            }
                        }
                                
                        // Apply formatting
                        result.setSpan(
                            BackgroundColorSpan(bgColor),
                            span.start, span.end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        result.setSpan(
                            ForegroundColorSpan(fgColor),
                            span.start, span.end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        result.setSpan(
                            TypefaceSpan("monospace"),
                            span.start, span.end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting text: ${e.message}")
            e.printStackTrace()
            return SpannableString(text)
        }
    }

    // Helper data classes for formatting
    private enum class FormattingType {
        HEADER, BOLD, ITALIC, CODE
    }

    private data class FormattingRange(
        val markerStart: Int,  // Start of the opening marker
        val markerEnd: Int,    // End of the opening marker (start of content)
        val contentEnd: Int,   // End of the content (start of closing marker)
        val type: FormattingType,
        val level: Int = 0     // Used for headers (1, 2, 3)
    )

    private data class FormattedSpan(
        val start: Int,
        val end: Int,
        val type: FormattingType,
        val level: Int = 0
    )

    // Data class to store block info (for both code and tables)
    private data class CodeBlock(
        val language: String,
        val code: String,
        val startPosition: Int,
        val endPosition: Int
    )

    // Helper method to darken a color for the header
    private fun darkenColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] *= 0.8f // Reduce brightness to 80%
        return Color.HSVToColor(hsv)
    }

    
    private class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}
 

