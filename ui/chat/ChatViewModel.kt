package com.example.baboonchat.ui.chat

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.baboonchat.data.local.ChatHistoryManager
import com.example.baboonchat.data.model.Message
import com.example.baboonchat.data.model.MessageThread
import com.example.baboonchat.data.model.MessageType
import com.example.baboonchat.data.model.MessageVersion
import com.example.baboonchat.data.remote.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import android.util.Log
import com.example.baboonchat.network.WebSocketManager
import java.io.File
import org.json.JSONObject

/**
 * ViewModel that handles chat functionality and manages message threads
 * with local storage support.
 */
class ChatViewModel(
    private val repository: MessageRepository = MessageRepository(),
    private val historyManager: ChatHistoryManager? = null
) : ViewModel() {

    private val TAG = "ChatViewModel"

    private var webSocketManager: WebSocketManager? = null
    private val _isConnectedToRepresentative = MutableLiveData<Boolean>(false)
    val isConnectedToRepresentative: LiveData<Boolean> = _isConnectedToRepresentative

    private val _representativeName = MutableLiveData<String>()
    val representativeName: LiveData<String> = _representativeName

    // Add support info data class
    data class SupportInfo(
        val phone: String,
        val email: String
    )

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    // Loading state for UI
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Store message threads for version history
    private val messageThreads = mutableMapOf<String, MessageThread>()

    // Track the active thread for navigation operations
    private var activeThreadId: String? = null

    // Flag to prevent auto-saving during initial loading
    private var isInitializing = true

    init {
        // Load saved history when ViewModel is created
        if (historyManager != null) {
            _isLoading.value = true
            loadSavedHistory()
        } else {
            isInitializing = false
            Log.d(TAG, "No history manager provided, history persistence disabled")
        }
    }

    fun connectToRepresentative() {
        viewModelScope.launch {
            try {
                // Get chat history
                val chatHistory = _messages.value?.map { message ->
                    mapOf(
                        "role" to if (message.type == MessageType.USER) "user" else "assistant",
                        "content" to (message.content ?: "")
                    )
                } ?: emptyList()

                // Request handover
                val response = repository.initiateHandover(chatHistory)

                if (response.sessionToken != null) {
                    // Connect to WebSocket
                    webSocketManager = WebSocketManager(object : WebSocketManager.WebSocketListener {
                        override fun onConnected() {
                            _isConnectedToRepresentative.postValue(true)
                        }

                        override fun onDisconnected() {
                            _isConnectedToRepresentative.postValue(false)
                        }

                        override fun onError(error: Throwable) {
                            Log.e(TAG, "WebSocket error", error)
                        }

                        override fun onQueued(position: Int) {
                            // Add system message
                            addSystemMessage("You are #$position in the queue. Please wait...")
                        }

                        override fun onRepresentativeMessage(message: String) {
                            // Add representative message to chat
                            addRepresentativeMessage(message)
                        }

                        override fun onChatAssigned(repName: String) {
                            _representativeName.postValue(repName)
                            addSystemMessage("$repName has joined the chat")
                        }

                        override fun onChatEnded() {
                            addSystemMessage("Chat ended. Thank you for contacting support.")
                            _isConnectedToRepresentative.postValue(false)
                        }
                    })

                    webSocketManager?.connect(response.websocketUrl, response.sessionToken)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to representative", e)
            }
        }
    }

    /**
     * Loads chat history from local storage
     */
    private fun loadSavedHistory() {
        historyManager?.let { manager ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Loading chat history from local storage...")
                    val savedThreads = manager.loadHistory()

                    withContext(Dispatchers.Main) {
                        if (savedThreads.isNotEmpty()) {
                            messageThreads.clear()
                            messageThreads.putAll(savedThreads)

                            // Set active thread to the last one used
                            val lastThread = savedThreads.values.maxByOrNull { 
                                it.versions.maxOfOrNull { version -> version.timestamp.time } ?: 0L 
                            }

                            lastThread?.let { thread ->
                                activeThreadId = thread.id

                                // Set messages to the active chain
                                val chainId = thread.activeChainId
                                if (chainId != null) {
                                    val chainMessages = thread.chains[chainId]?.messages ?: emptyList()

                                    _messages.value = chainMessages.toList()
                                    Log.d(TAG, "Set UI to display thread ${thread.id}, chain $chainId with ${chainMessages.size} messages")
                                }
                            }

                            Log.d(TAG, "Chat history loaded successfully with ${savedThreads.size} threads")
                        } else {
                            Log.d(TAG, "No saved chat history found or empty history")
                        }

                        _isLoading.value = false
                        isInitializing = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading saved history", e)
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        isInitializing = false
                    }
                }
            }
        } ?: run {
            _isLoading.value = false
            isInitializing = false
        }
    }

    /**
     * Saves current chat history to local storage
     */
    private fun saveHistory() {
        if (isInitializing || historyManager == null) return
        if (messageThreads.isEmpty()) {
            Log.d(TAG, "No message threads to save")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Saving chat history with ${messageThreads.size} threads...")
                historyManager.saveHistory(messageThreads)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving history", e)
            }
        }
    }

    /**
     * Creates an automatic backup of the current chat history
     */
    fun createBackup() {
        historyManager?.let { manager ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Save latest changes first
                    manager.saveHistory(messageThreads)
                    // Then create a backup
                    manager.createBackup("manual")
                    Log.d(TAG, "Manual backup created successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating backup", e)
                }
            }
        } ?: Log.d(TAG, "Cannot create backup: no history manager")
    }

    /**
     * Clears all chat history
     */
    fun clearHistory() {
        if (historyManager == null) {
            Log.d(TAG, "No history manager available, nothing to clear")
            return
        }

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Clearing all chat history...")
                historyManager.clearHistory()

                withContext(Dispatchers.Main) {
                    messageThreads.clear()
                    _messages.value = emptyList()
                    activeThreadId = null

                    Log.d(TAG, "Chat history cleared successfully")
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing history", e)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Reloads message history from local storage
     * Useful after importing history
     */
    fun reloadHistory() {
        if (historyManager == null) {
            Log.d(TAG, "No history manager available, cannot reload")
            return
        }

        _isLoading.value = true
        isInitializing = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Reloading chat history from storage...")
                val savedThreads = historyManager.loadHistory()

                withContext(Dispatchers.Main) {
                    // Clear current state
                    messageThreads.clear()
                    _messages.value = emptyList()
                    activeThreadId = null

                    // Add loaded threads
                    messageThreads.putAll(savedThreads)

                    // Set active thread to the last one used
                    val lastThread = savedThreads.values.maxByOrNull { 
                        it.versions.maxOfOrNull { version -> version.timestamp.time } ?: 0L 
                    }

                    lastThread?.let { thread ->
                        activeThreadId = thread.id

                        // Set messages to the active chain
                        val chainId = thread.activeChainId
                        if (chainId != null) {
                            val chainMessages = thread.chains[chainId]?.messages ?: emptyList()
                            _messages.value = chainMessages.toList()
                            Log.d(TAG, "UI updated with ${chainMessages.size} messages from thread ${thread.id}")
                        }
                    }

                    Log.d(TAG, "Chat history reloaded with ${savedThreads.size} threads")
                    isInitializing = false
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading saved history", e)
                withContext(Dispatchers.Main) {
                    isInitializing = false
                    _isLoading.value = false
                }
            }
        }
    }

    fun sendMessage(content: String) {
        val threadId = activeThreadId ?: UUID.randomUUID().toString()

        Log.d(TAG, "Sending message in thread: $threadId")
        if (activeThreadId == null) {
            Log.d(TAG, "Creating new thread with ID: $threadId")
        }

        // Create user message
        val userMessage = Message(
            id = System.currentTimeMillis().toString(),
            content = content,
            type = MessageType.USER,
            timestamp = Date(),
            threadId = threadId,
            versionNumber = 0
        )

        // Create thread to track versions
        val messageThread = messageThreads[threadId] ?: MessageThread(threadId).also {
            messageThreads[threadId] = it
            Log.d(TAG, "Created new MessageThread with ID: $threadId")
        }
        val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()

        // Handle thread/chain logic (same for both AI and representative chat)
        if (activeThreadId != null) {
            // Add to the active chain
            val chainId = messageThread.activeChainId
            if (chainId != null) {
                val chainMessage = userMessage.copy(chainId = chainId)
                messageThread.addMessageToActiveChain(chainMessage)
                // Add message to the visible messages list
                currentMessages.add(chainMessage)
                _messages.value = currentMessages.toList()
                Log.d(TAG, "Added message to existing chain: $chainId")
            } else { 
                // Create a new chain if needed
                val newChainId = messageThread.createNewChain()
                val chainMessage = userMessage.copy(chainId = newChainId)

                messageThread.addMessageToActiveChain(chainMessage)
                // Add user message to the active/visible messages list
                currentMessages.add(chainMessage)
                _messages.value = currentMessages.toList()
                Log.d(TAG, "Created new chain $newChainId for message in thread $threadId")
            }
        } else {
            // This is a new thread
            messageThread.addVersion(userMessage)
            activeThreadId = threadId

            // Add user message to the visible messages list
            currentMessages.add(userMessage)
            _messages.value = currentMessages.toList()
            Log.d(TAG, "Started new thread $threadId with first message")
        }

        // Save history after updating with user message
        saveHistory()

        // Check if connected to representative or AI
        if (_isConnectedToRepresentative.value == true) {
            // Send via WebSocket to representative
            Log.d(TAG, "Sending message to representative via WebSocket")
            webSocketManager?.sendMessage(content)

            // No need to wait for response or add to messages again
            // The response will come through WebSocket listener
        } else {
            // Normal AI chat flow - send to API
            viewModelScope.launch {
                try {
                    Log.d(TAG, "Sending message to API: $content")

                    // Get active chain messages for history context
                    val activeChainId = messageThread.activeChainId
                    val conversationHistory = if (activeChainId != null) {
                        // Get messages from active chain sorted by timestamp
                        messageThread.getActiveChainMessages()
                    } else {
                        // If no active chain, just use the current user message
                        listOf(userMessage)
                    }

                    Log.d(TAG, "Including ${conversationHistory.size} previous messages as context")

                    // Send the message with history context
                    val response = repository.sendMessage(
                        message = content,
                        conversationHistory = conversationHistory,
                        activeChainId = activeChainId
                    )

                    Log.d(TAG, "Received response from API, length: ${response.length}")

                    // Create bot response message
                    val botResponse = createBotResponseMessage(response, threadId)

                    // Get current active thread
                    val thread = messageThreads[threadId]
                    if (thread != null) {
                        if (thread.activeChainId != null) {
                            // Add to active chain
                            val chainBotResponse = botResponse.copy(chainId = thread.activeChainId)
                            thread.addMessageToActiveChain(chainBotResponse)

                            // Add bot response to visible messages
                            val updatedMessages = _messages.value?.toMutableList() ?: mutableListOf()
                            updatedMessages.add(chainBotResponse)
                            _messages.value = updatedMessages

                            Log.d(TAG, "Added bot response to chain ${thread.activeChainId}")

                        } else {
                            // Update the current version with the bot response
                            thread.updateCurrentVersion(botResponse = botResponse, updateChain = false)

                            // Add bot response to visible messages
                            val updatedMessages = _messages.value?.toMutableList() ?: mutableListOf()
                            updatedMessages.add(botResponse)
                            _messages.value = updatedMessages

                            Log.d(TAG, "Updated version ${thread.currentVersionIndex} with bot response")
                        }

                        // Log the updated chain structure for debugging
                        thread.logChainStructure()

                        // Save history after receiving bot response
                        saveHistory()

                        // Create automatic backup every 10 messages
                        val totalMessages = thread.chains.values.sumOf { it.messages.size }
                        if (totalMessages % 10 == 0 && historyManager != null) {
                            historyManager.createBackup()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending message", e)

                    // Handle error
                    val errorMessage = Message(
                        id = System.currentTimeMillis().toString(),
                        content = "Error: ${e.message}",
                        type = MessageType.ERROR,
                        timestamp = Date(),
                        threadId = threadId
                    )

                    val thread = messageThreads[threadId]
                    if (thread != null) {
                        if (thread.activeChainId != null) {
                            // Add to active chain
                            val chainErrorMsg = errorMessage.copy(chainId = thread.activeChainId)
                            thread.addMessageToActiveChain(chainErrorMsg)

                            // Add error to visible messages
                            val updatedMessages = _messages.value?.toMutableList() ?: mutableListOf()
                            updatedMessages.add(chainErrorMsg)
                            _messages.value = updatedMessages

                            Log.e(TAG, "Added error message to chain ${thread.activeChainId}")
                        } else {
                            // Update the current version with the error response
                            thread.updateCurrentVersion(botResponse = errorMessage)

                            // Add error to visible messages
                            val updatedMessages = _messages.value?.toMutableList() ?: mutableListOf()
                            updatedMessages.add(errorMessage)
                            _messages.value = updatedMessages

                            Log.e(TAG, "Updated version ${thread.currentVersionIndex} with error message")
                        }

                        // Save history even after error
                        saveHistory()
                    }
                }
            }
        }
    }

    // Add this function to handle representative messages
    fun addRepresentativeMessage(content: String) {
        val threadId = activeThreadId ?: return
        val messageThread = messageThreads[threadId] ?: return

        // Create representative message
        val repMessage = Message(
            id = System.currentTimeMillis().toString(),
            content = content,
            type = MessageType.BOT, // Or create a new type like MessageType.REPRESENTATIVE
            timestamp = Date(),
            threadId = threadId
        )

        // Add to active chain
        val chainId = messageThread.activeChainId
        if (chainId != null) {
            val chainMessage = repMessage.copy(chainId = chainId)
            messageThread.addMessageToActiveChain(chainMessage)

            // Add to visible messages
            val updatedMessages = _messages.value?.toMutableList() ?: mutableListOf()
            updatedMessages.add(chainMessage)
            _messages.value = updatedMessages

            Log.d(TAG, "Added representative message to chain $chainId")
        }

        // Save history
        saveHistory()
    }

    // Add this function to handle system messages
    fun addSystemMessage(content: String) {
        val threadId = activeThreadId ?: return
        val messageThread = messageThreads[threadId] ?: return

        // Create system message
        val systemMessage = Message(
            id = System.currentTimeMillis().toString(),
            content = content,
            type = MessageType.ERROR, // Or create MessageType.SYSTEM
            timestamp = Date(),
            threadId = threadId
        )

        // Add to active chain
        val chainId = messageThread.activeChainId
        if (chainId != null) {
            val chainMessage = systemMessage.copy(chainId = chainId)
            messageThread.addMessageToActiveChain(chainMessage)

            // Add to visible messages
            val updatedMessages = _messages.value?.toMutableList() ?: mutableListOf()
            updatedMessages.add(chainMessage)
            _messages.value = updatedMessages

            Log.d(TAG, "Added system message to chain $chainId")
        }

        // Save history
        saveHistory()
    }

    // Sealed class for different response types
    sealed class BotResponseType {
        data class Text(val message: String) : BotResponseType()
        data class Image(val message: String, val url: String?, val base64: String?) : BotResponseType()
        data class SupportContact(
            val message: String, 
            val supportInfo: SupportInfo, 
            val showRepresentativeButton: Boolean
        ) : BotResponseType()
    }

    // LiveData for support contacts
    private val _supportContact = MutableLiveData<BotResponseType.SupportContact?>()
    val supportContact: LiveData<BotResponseType.SupportContact?> = _supportContact

    private fun createBotResponseMessage(responseText: String, threadId: String): Message {
        Log.d(TAG, "Raw response text: $responseText")

        // First, try to parse as JSON
        try {
            val jsonResponse = JSONObject(responseText)
            if (jsonResponse.has("response")) {
                val responseData = jsonResponse.getJSONObject("response")
                val responseType = responseData.optString("type", "text")

                Log.d(TAG, "Parsed response type: $responseType")

                when (responseType) {
                    "support_contact" -> {
                        val text = responseData.getString("text")
                        val supportInfoJson = responseData.getJSONObject("support_info")
                        val showButton = responseData.optBoolean("show_representative_button", false)

                        val supportInfo = SupportInfo(
                            phone = supportInfoJson.getString("phone"),
                            email = supportInfoJson.getString("email")
                        )

                        Log.d(TAG, "Creating support contact message with text: $text")

                        // Notify UI about support contact
                        _supportContact.value = BotResponseType.SupportContact(text, supportInfo, showButton)

                        // Return message for chat history
                        return Message(
                            id = System.currentTimeMillis().toString(),
                            content = text,
                            type = MessageType.BOT,
                            timestamp = Date(),
                            threadId = threadId
                        )
                    }
                    "text" -> {
                        val text = responseData.getString("text")
                        Log.d(TAG, "Creating text message with content: $text")

                        return Message(
                            id = System.currentTimeMillis().toString(),
                            content = text,
                            type = MessageType.BOT,
                            timestamp = Date(),
                            threadId = threadId
                        )
                    }
                    "image" -> {
                        val text = responseData.getString("text")
                        val imageUrl = responseData.optString("url", null)
                        val imageBase64 = responseData.optString("base64", null)

                        Log.d(TAG, "Creating image message with text: $text, has URL: ${imageUrl != null}, has base64: ${imageBase64 != null}")

                        return Message(
                            id = System.currentTimeMillis().toString(),
                            content = text,
                            type = MessageType.BOT,
                            timestamp = Date(),
                            containsImage = true,
                            imageUrl = imageUrl,
                            imageData = imageBase64,
                            threadId = threadId
                        )
                    }
                    else -> {
                        Log.w(TAG, "Unknown response type: $responseType")
                    }
                }
            }

        } catch (e: Exception) {
            Log.d(TAG, "Response is not JSON or parsing failed, treating as legacy format")
        }

        // If JSON parsing failed or response is not JSON, fall back to legacy parsing
        // Check for image url prefix
        if (responseText.contains("!IMAGEURL!")) {
            Log.d(TAG, "Response contains image URL")
            val parts = responseText.split("!IMAGEURL!")
            val contentText = parts[0].trim()
            val imageUrl = parts[1].trim()

            return Message(
                id = System.currentTimeMillis().toString(),
                content = contentText,
                type = MessageType.BOT,
                timestamp = Date(),
                containsImage = true,
                imageUrl = imageUrl,
                threadId = threadId
            )
        }
        // Check for image data prefix
        else if (responseText.contains("!IMAGEDATA!")) {
            Log.d(TAG, "Response contains image data")
            val parts = responseText.split("!IMAGEDATA!")
            val contentText = parts[0].trim()
            val imageData = parts[1].trim()

            return Message(
                id = System.currentTimeMillis().toString(),
                content = contentText,
                type = MessageType.BOT,
                timestamp = Date(),
                containsImage = true,
                imageData = imageData,
                threadId = threadId
            )
        }
        // Regular text response (legacy format)
        else {
            return Message(
                id = System.currentTimeMillis().toString(),
                content = responseText,
                type = MessageType.BOT,
                timestamp = Date(),
                threadId = threadId
            )
        }
    }

    fun logAllThreads() {
        Log.d(TAG, "====== THREAD SUMMARY ======")
        messageThreads.forEach { (threadId, thread) ->
            val isActive = if (threadId == activeThreadId) " (ACTIVE)" else ""
            Log.d(TAG, "Thread $threadId$isActive")
            Log.d(TAG, "├─ Versions: ${thread.versions.size}, Current: ${thread.currentVersionIndex}")
            Log.d(TAG, "├─ Chains: ${thread.chains.size}, Active: ${thread.activeChainId}")

            thread.logVersions(TAG)

            thread.chains.forEach { (chainId, chain) ->
                val isActiveChain = if (chainId == thread.activeChainId) " (ACTIVE)" else ""
                Log.d(TAG, "├─ Chain $chainId$isActiveChain")
                Log.d(TAG, "│  ├─ From version: ${chain.fromVersionIndex}")
                Log.d(TAG, "│  ├─ Messages: ${chain.messages.size}")
                Log.d(TAG, "│  └─ Created: ${chain.timestamp}")
            }
        }
        Log.d(TAG, "============================")
    }

    fun editMessage(messageId: String, newContent: String) {
        Log.d(TAG, "Starting edit for message $messageId")
        val currentMessages = _messages.value?.toMutableList() ?: return

        // Find the message to edit
        val messageIndex = currentMessages.indexOfFirst { it.id == messageId }
        if (messageIndex == -1) {
            Log.e(TAG, "Could not find message with ID: $messageId")
            return
        }

        val messageToEdit = currentMessages[messageIndex]
        val threadId = messageToEdit.threadId ?: return

        // Determine the lineade ID (use existing one or create a new from original message Id)
        var editLineageId = messageToEdit.editLineageId

        Log.d(TAG, "Editing message in thread $threadId, lineage ID: $editLineageId")

        if (editLineageId == null) {
            val thread = messageThreads[threadId]

            if (thread != null) {
                for (chain in thread.chains.values) {
                    for (msg in chain.messages) {
                        if (msg.id == messageId || msg.originalMessageId == messageId) {
                            // Found a match - use its lineage if available, or its ID as lineage
                            editLineageId = msg.editLineageId ?: msg.id
                            break
                        }
                    }
                    if (editLineageId != null) break
                }
            }
            // If no lineage found, use messages own Id
            editLineageId = editLineageId ?: messageId
        }
        Log.d(TAG, "Using lineage ID: $editLineageId")

        // Track active thread for navigation
        activeThreadId = threadId

        // Get the thread or create one if it doesn't exist
        val thread = messageThreads[threadId] ?: run {
            Log.d(TAG, "Creating new thread for $threadId")
            val newThread = MessageThread(threadId)
            messageThreads[threadId] = newThread
            newThread
        }

        Log.d(TAG, "Current thread state: versions=${thread.versions.size}, currentIndex=${thread.currentVersionIndex}")

        // Create a new message version
        val editedUserMessage = Message(
            id = System.currentTimeMillis().toString(),
            content = newContent,
            type = MessageType.USER,
            timestamp = Date(),
            threadId = threadId,
            hasVersionHistory = true,
            versionNumber = thread.versions.size,
            originalMessageId = messageId,
            editLineageId = editLineageId ?: messageId
        )

        // Add the new version to the thread
        val newVersionIndex = thread.addVersion(editedUserMessage)
        Log.d(TAG, "Added version at index $newVersionIndex. Total versions: ${thread.versions.size}")

        markAllThreadVersionsWithHistory(threadId)

        // Create new chain for edited version
        val newChainId = thread.createNewChain()
        Log.d(TAG, "Created new chain $newChainId for edited message")

        // Find previous chain to copy messages from
        val previousChainId = thread.chains.keys
            .filter { it != newChainId }
            .maxByOrNull { thread.chains[it]?.timestamp ?: Date(0) }

        val messagesToInclude = mutableListOf<Message>()
        val previousChain = previousChainId?.let { thread.chains[it] }

        if (previousChain != null) {
            Log.d(TAG, "Previous chain found: $previousChainId with ${previousChain.messages.size} messages")

            // Find the index of the message being edited in the previous chain
            val originalMessageIndex = previousChain.messages.indexOfFirst { 
                it.id == messageId || (it.type == MessageType.USER && it.content == messageToEdit.content)
            }

            if (originalMessageIndex >= 0) {
                Log.d(TAG, "Found original message at position $originalMessageIndex in previous chain")

                // Clear the data structures we'll use to track messages
                val orderedMessages = mutableMapOf<Int, Message>()
                thread.chains[newChainId]?.messages?.clear()

                // Copy messages BEFORE the edited message with their original positions
                for (i in 0 until originalMessageIndex) {
                    val originalMsg = previousChain.messages[i]
                    val msg = originalMsg.copy(
                        chainId = newChainId,
                        editLineageId = originalMsg.editLineageId ?: originalMsg.id, // Keep lineage
                        originalMessageId = originalMsg.originalMessageId ?: originalMsg.id // Keep original message Id
                    )
                    orderedMessages[i] = msg
                    thread.addMessageToActiveChain(msg)
                    Log.d(TAG, "Copied previous message to new chain: ${msg.content?.take(20)}...")
                }

                // Add the edited message at the ORIGINAL position (replacing the old one)
                val chainMessage = editedUserMessage.copy(chainId = newChainId)
                orderedMessages[originalMessageIndex] = chainMessage
                thread.addMessageToActiveChain(chainMessage)
                Log.d(TAG, "Added edited message at position $originalMessageIndex")

                // Add messages to the visible list in correct order
                messagesToInclude.addAll(orderedMessages.toSortedMap().values)
                Log.d(TAG, "Final message list has ${messagesToInclude.size} messages")
            } else {
                // If we can't find the original message, just add the edited message
                val chainMessage = editedUserMessage.copy(chainId = newChainId)
                messagesToInclude.add(chainMessage)
                thread.addMessageToActiveChain(chainMessage)
                Log.d(TAG, "Could not find original message in previous chain, adding only edited message")
            }
        } else {
            // No previous chain, just add the edited message
            Log.d(TAG, "No previous chain found")
            val chainMessage = editedUserMessage.copy(chainId = newChainId)
            messagesToInclude.add(chainMessage)
            thread.addMessageToActiveChain(chainMessage)
        }

        Log.d(TAG, "== EDIT LINEAGE SUMMARY ==")
        thread.chains.forEach { (chainId, chain) ->
            chain.messages.forEach { msg ->
                if (msg.type == MessageType.USER && (msg.editLineageId != null || msg.originalMessageId != null)) {
                    Log.d(TAG, "Chain ${chainId.take(8)}: Message ${msg.id.take(8)}, content=${msg.content?.take(20)}, " +
                            "originalId=${msg.originalMessageId?.take(8) ?: "none"}, " +
                            "lineageId=${msg.editLineageId?.take(8) ?: "none"}")
                }
            }
        }              
        // Update the UI with preserved messages plus edited message
        _messages.value = messagesToInclude

        // Save history after edit
        saveHistory()

        // Fetch a new response for the edited message
        viewModelScope.launch {
            try {
                Log.d(TAG, "Sending edited message to API: $newContent")

                // Get the conversation history for this new chain
                val conversationHistory = thread.getActiveChainMessages()
                Log.d(TAG, "Including ${conversationHistory.size} messages as context for edited message")

                // Send message with conversation history context
                val response = repository.sendMessage(
                    message = newContent,
                    conversationHistory = conversationHistory,
                    activeChainId = newChainId
                )

                Log.d(TAG, "Received response for edited message, length: ${response.length}")

                // Create the new bot response
                val newBotResponse = createBotResponseMessage(response, threadId).copy(
                    versionNumber = newVersionIndex,
                    hasVersionHistory = true,
                    chainId = newChainId
                )

                // Update the thread with the new bot response
                thread.updateCurrentVersion(botResponse = newBotResponse, updateChain = false)
                thread.addMessageToActiveChain(newBotResponse)

                // Add the new bot response to visible messages
                val updatedMessages = _messages.value?.toMutableList() ?: mutableListOf()
                updatedMessages.add(newBotResponse)
                _messages.value = updatedMessages

                Log.d(TAG, "Added bot response to edited message chain")

                // Log chain table and structure
                MessageTableLogger.logMessageChainsAsTable(thread)
                thread.logChainStructure()

                // Save history after receiving bot response
                saveHistory()

            } catch (e: Exception) {
                Log.e(TAG, "Error sending edited message", e)

                // Handle error
                val errorMessage = Message(
                    id = System.currentTimeMillis().toString(),
                    content = "Error: ${e.message}",
                    type = MessageType.ERROR,
                    timestamp = Date(),
                    threadId = threadId,
                    versionNumber = newVersionIndex,
                    hasVersionHistory = true,
                    chainId = newChainId
                )

                // Update the thread with the error response
                thread.updateCurrentVersion(botResponse = errorMessage, updateChain = false)
                thread.addMessageToActiveChain(errorMessage)

                // Add error to visible messages
                val updatedMessages = _messages.value?.toMutableList() ?: mutableListOf()
                updatedMessages.add(errorMessage)
                _messages.value = updatedMessages
                MessageTableLogger.logMessageChainsAsTable(thread)

                // Save history even after error
                saveHistory()
            }
        }
    }

    /**
     * Navigate to specific version of a message thread
     */
    fun navigateToSpecificVersion(threadId: String, targetVersionIndex: Int): Boolean {
        Log.d(TAG, "Navigating to version $targetVersionIndex in thread $threadId")
        val thread = messageThreads[threadId] ?: return false

        // Validate target index
        if (targetVersionIndex < 0 || targetVersionIndex >= thread.versions.size) {
            Log.e(TAG, "Invalid version index: $targetVersionIndex (valid range: 0-${thread.versions.size - 1})")
            return false
        }

        // Set the current version index directly
        thread.currentVersionIndex = targetVersionIndex

        // Find or create a chain for this version
        val chainsForVersion = thread.getChainsForVersion(targetVersionIndex)
        val chainId = if (chainsForVersion.isNotEmpty()) {
            chainsForVersion.maxByOrNull { it.timestamp }?.chainId
        } else {
            thread.createNewChain()
        }

        // Switch to this chain
        if (chainId != null) {
            // Get messages from this chain
            val chainMessages = thread.chains[chainId]?.messages ?: emptyList()
            // Replace current messages from the messages in this chain
            _messages.value = ArrayList(chainMessages)

            Log.d(TAG, "Switched to chain $chainId with ${chainMessages.size} messages")

            // Log chain table and structure
            MessageTableLogger.logMessageChainsAsTable(thread)
            thread.logChainStructure()

            // Save history after navigation
            saveHistory()

            return true
        }
        return false
    }

    /**
     * Navigate to the previous version of a message thread
     */
    fun navigateToPreviousVersion(threadId: String): Boolean {
        Log.d(TAG, "Navigating to previous version in thread $threadId")
        val thread = messageThreads[threadId] ?: run {
            Log.e(TAG, "Thread not found: $threadId")
            return false
        }

        if (!thread.hasPreviousVersion()) {
            Log.d(TAG, "No previous version available - already at version ${thread.currentVersionIndex}")
            return false
        }

        val prevIndex = thread.currentVersionIndex - 1
        Log.d(TAG, "Moving from version ${thread.currentVersionIndex} to $prevIndex")

        val previousVersion = thread.moveToPreviousVersion() ?: return false

        // Get the chain related to this thread
        val chainId = thread.activeChainId ?: return false

        val result = handleChainNavigation(chainId)

        // Save history after navigation if successful
        if (result) {
            saveHistory()
        }

        return result
    }

    /**
     * Navigate to the next version of a message thread
     */
    fun navigateToNextVersion(threadId: String): Boolean {
        Log.d(TAG, "Navigating to next version in thread $threadId")
        val thread = messageThreads[threadId] ?: run {
            Log.e(TAG, "Thread not found: $threadId")
            return false
        }

        if (!thread.hasNextVersion()) {
            Log.d(TAG, "No next version available - already at latest version ${thread.currentVersionIndex}")
            return false
        }

        val nextIndex = thread.currentVersionIndex + 1
        Log.d(TAG, "Moving from version ${thread.currentVersionIndex} to $nextIndex")

        val nextVersion = thread.moveToNextVersion() ?: return false
        // Get the chain related to this version
        val chainId = thread.activeChainId ?: return false

        val result = handleChainNavigation(chainId)

        // Save history after navigation if successful
        if (result) {
            saveHistory()
        }

        return result
    }

    private fun markAllThreadVersionsWithHistory(threadId: String) {
        val thread = messageThreads[threadId] ?: return

        // Only mark with history if there's more than one version
        if (thread.versions.size <= 1) return

        Log.d(TAG, "Marking all ${thread.versions.size} versions in thread $threadId with hasVersionHistory")

        // Update all versions with hasVersionHistory flag
        for (i in thread.versions.indices) {
            val version = thread.versions[i]

            // Update user message
            val updatedUserMessage = version.userMessage.copy(hasVersionHistory = true)

            // Update bot response if it exists
            val updatedBotResponse = version.botResponse?.copy(hasVersionHistory = true)

            // Replace the version
            thread.versions[i] = MessageVersion(updatedUserMessage, updatedBotResponse, version.timestamp)
        }
    }

    private fun validateChain(chainId: String) {
        val threadId = activeThreadId ?: return
        val thread = messageThreads[threadId] ?: return
        val chain = thread.chains[chainId] ?: return

        // Remove duplicate bot responses
        val uniqueMessages = mutableListOf<Message>()
        val seenBotResponses = mutableSetOf<String>()

        chain.messages.forEach { message ->
            if (message.type == MessageType.BOT) {
                val content = message.content ?: ""
                if (!seenBotResponses.contains(content)) {
                    seenBotResponses.add(content)
                    uniqueMessages.add(message)
                } else {
                    Log.d(TAG, "Removed duplicate bot response: ${content.take(20)}...")
                }
            } else {
                uniqueMessages.add(message)
            }
        }
        // Only replace if we removed messages
        if (uniqueMessages.size < chain.messages.size) {
            Log.d(TAG, "Chain validation removed ${chain.messages.size - uniqueMessages.size} duplicate messages")
            chain.messages.clear()
            chain.messages.addAll(uniqueMessages)
        }
    }

    /**
     * Ensures chain navigation updates UI correctly
     */
    private fun handleChainNavigation(chainId: String): Boolean {
        val threadId = activeThreadId ?: return false
        val thread = messageThreads[threadId] ?: return false
        val chain = thread.chains[chainId] ?: return false

        // Set the active chain
        thread.activeChainId = chainId

        // Get messages from this chain
        val chainMessages = chain.messages.toList()

        // Important: Create a new list to break reference to old list state
        _messages.value = ArrayList(chainMessages)

        // Log the chain switch
        Log.d(TAG, "Switched to chain $chainId with ${chainMessages.size} messages")

        // Log chain structure after navigation
        thread.logChainStructure()
        return true
    }

    /**
     * Updates visible messages to display the specified version's chain
     */
    fun switchToChain(threadId: String, chainId: String): Boolean { 
        val thread = messageThreads[threadId] ?: return false

        // Activate the requested chain
        val chainMessages = thread.activateChain(chainId) ?: return false

        // Update the UI with the messages from this chain
        _messages.value = chainMessages

        Log.d(TAG, "Switched to chain $chainId with ${chainMessages.size} messages")

        // Log chain structure after navigation
        thread.logChainStructure()

        // Save history after switching chains
        saveHistory()

        return true
    }

    fun getMessageThread(threadId: String): MessageThread? {
        return messageThreads[threadId]
    }

    fun getMessageThreads(): Map<String, MessageThread> {
        return messageThreads
    }

    fun getHistoryStats(context: Context): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()

        stats["threadCount"] = messageThreads.size

        val chainCount = messageThreads.values.sumOf { it.chains.size }
        stats["chainCount"] = chainCount

        val messageCount = messageThreads.values.sumOf { thread ->
            thread.chains.values.sumOf { chain -> chain.messages.size }
        }
        stats["messageCount"] = messageCount

        val versionCount = messageThreads.values.sumOf { it.versions.size }
        stats["versionCount"] = versionCount

        // Get the path to the history file
        historyManager?.let { manager ->
            val historyFile = File(context.filesDir, "chat_history.json")
            if (historyFile.exists()) {
                stats["fileSizeKB"] = historyFile.length() / 1024
                stats["lastModified"] = Date(historyFile.lastModified())
                stats["filePath"] = historyFile.absolutePath
            }
        }

        return stats
    }

    override fun onCleared() {
        super.onCleared()
        // Save history one last time when ViewModel is destroyed
        saveHistory()
        // Create backup before exit
        if (!isInitializing && historyManager != null) {
            viewModelScope.launch(Dispatchers.IO) {
                historyManager.createBackup("exit")
            }
        }
    }
}