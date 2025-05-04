package com.example.baboonchat.data.local

import android.content.Context
import android.util.Log
import com.example.baboonchat.data.model.Message
import com.example.baboonchat.data.model.MessageChain
import com.example.baboonchat.data.model.MessageThread
import com.example.baboonchat.data.model.MessageType
import com.example.baboonchat.data.model.MessageVersion
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.Date
import java.util.concurrent.Executors

/**
 * Serializable data classes for JSON storage
 */
data class StoredThread(
    val id: String,
    val versions: List<StoredVersion>,
    val currentVersionIndex: Int,
    val chains: Map<String, StoredChain>,
    val activeChainId: String?
)

data class StoredVersion(
    val userMessage: StoredMessage,
    val botResponse: StoredMessage?,
    val timestamp: Long
)

data class StoredChain(
    val chainId: String,
    val fromVersionIndex: Int,
    val messages: List<StoredMessage>,
    val timestamp: Long
)

data class StoredMessage(
    val id: String,
    val content: String?,
    val type: String, // "USER", "BOT", "ERROR"
    val timestamp: Long,
    val threadId: String?,
    val chainId: String?,
    val editLineageId: String?,
    val originalMessageId: String?,
    val versionNumber: Int,
    val hasVersionHistory: Boolean,
    val containsImage: Boolean,
    val imageUrl: String?,
    val imageData: String?
)

/**
 * Manager for saving and loading chat history to/from local storage
 */
class ChatHistoryManager(private val context: Context) {
    private val TAG = "ChatHistoryManager"
    private val HISTORY_FILENAME = "chat_history.json"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val executorService = Executors.newSingleThreadExecutor()
    
    /**
     * Saves all message threads to local storage
     */
    fun saveHistory(threads: Map<String, MessageThread>) {
        executorService.execute {
            try {
                val startTime = System.currentTimeMillis()
                val storedThreads = threads.map { (id, thread) -> convertToStoredThread(thread) }
                val historyFile = getHistoryFile()
                
                // Get previous file size for logging
                val previousSize = if (historyFile.exists()) {
                    historyFile.length() / 1024 // Size in KB
                } else {
                    0
                }
                
                FileWriter(historyFile).use { writer ->
                    gson.toJson(storedThreads, writer)
                }
                
                val newSize = historyFile.length() / 1024 // Size in KB
                val saveTime = System.currentTimeMillis() - startTime
                
                // Detailed logging
                Log.d(TAG, "✓ Chat history saved successfully")
                Log.d(TAG, "  └─ Path: ${historyFile.absolutePath}")
                Log.d(TAG, "  └─ Threads: ${threads.size}, Chains: ${countAllChains(threads)}")
                Log.d(TAG, "  └─ Messages: ${countAllMessages(threads)}")
                Log.d(TAG, "  └─ Size: $newSize KB (${if (newSize > previousSize) "+" else ""}${newSize - previousSize} KB)")
                Log.d(TAG, "  └─ Time: $saveTime ms")
                
                // Log breakdown of each thread
                threads.forEach { (id, thread) ->
                    val threadMessages = thread.chains.values.sumOf { it.messages.size }
                    Log.d(TAG, "  └─ Thread $id: ${thread.versions.size} versions, ${thread.chains.size} chains, $threadMessages messages")
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "✗ Error saving chat history", e)
            }
        }
    }
    
    /**
     * Loads all message threads from local storage
     */
    fun loadHistory(): Map<String, MessageThread> {
        val historyFile = getHistoryFile()
        
        if (!historyFile.exists()) {
            Log.d(TAG, "✗ No chat history file found at ${historyFile.absolutePath}")
            return emptyMap()
        }
        
        try {
            val startTime = System.currentTimeMillis()
            
            FileReader(historyFile).use { reader ->
                val threadListType = object : TypeToken<List<StoredThread>>() {}.type
                val storedThreads: List<StoredThread> = gson.fromJson(reader, threadListType)
                
                val threads = storedThreads.associate { 
                    it.id to convertToMessageThread(it)
                }
                
                val loadTime = System.currentTimeMillis() - startTime
                val fileSize = historyFile.length() / 1024 // Size in KB
                
                // Detailed logging
                Log.d(TAG, "✓ Chat history loaded successfully")
                Log.d(TAG, "  └─ Path: ${historyFile.absolutePath}")
                Log.d(TAG, "  └─ Threads: ${threads.size}, Chains: ${countAllChains(threads)}")
                Log.d(TAG, "  └─ Messages: ${countAllMessages(threads)}")
                Log.d(TAG, "  └─ Size: $fileSize KB")
                Log.d(TAG, "  └─ Time: $loadTime ms")
                
                // Log breakdown of each thread
                threads.forEach { (id, thread) ->
                    val threadMessages = thread.chains.values.sumOf { it.messages.size }
                    Log.d(TAG, "  └─ Thread $id: ${thread.versions.size} versions, ${thread.chains.size} chains, $threadMessages messages")
                    
                    // Log active chain
                    thread.activeChainId?.let { activeChainId ->
                        val activeChain = thread.chains[activeChainId]
                        val activeChainMessages = activeChain?.messages?.size ?: 0
                        Log.d(TAG, "     └─ Active chain: $activeChainId ($activeChainMessages messages)")
                    }
                }
                
                return threads
            }
        } catch (e: IOException) {
            Log.e(TAG, "✗ Error reading chat history: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "✗ Error parsing chat history JSON: ${e.message}")
        }
        
        return emptyMap()
    }
    
    /**
     * Clears all chat history
     */
    fun clearHistory() {
        executorService.execute {
            try {
                val historyFile = getHistoryFile()
                if (historyFile.exists()) {
                    // Create backup before clearing
                    createBackup("pre_clear")
                    
                    // Delete the file
                    val fileSize = historyFile.length() / 1024 // Size in KB
                    historyFile.delete()
                    
                    Log.d(TAG, "✓ Chat history cleared")
                    Log.d(TAG, "  └─ Deleted file: ${historyFile.absolutePath}")
                    Log.d(TAG, "  └─ Size freed: $fileSize KB")
                    Log.d(TAG, "  └─ Backup created before clearing")
                } else {
                    Log.d(TAG, "✓ No chat history to clear")
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error clearing chat history", e)
            }
        }
    }
    
    /**
     * Gets the history file in the app's data directory
     */
    private fun getHistoryFile(): File {
        val directory = context.filesDir
        return File(directory, HISTORY_FILENAME)
    }
    
    /**
     * Creates a backup of the current chat history with optional label
     */
    fun createBackup(label: String = "auto") {
        executorService.execute {
            try {
                val historyFile = getHistoryFile()
                if (!historyFile.exists()) {
                    return@execute
                }
                
                val backupDir = File(context.filesDir, "backups")
                if (!backupDir.exists()) {
                    backupDir.mkdir()
                }
                
                // Create a timestamped backup file
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(Date())
                val backupFile = File(backupDir, "${HISTORY_FILENAME}.${label}_$timestamp")
                
                // Copy the current history file to the backup
                historyFile.copyTo(backupFile)
                
                val fileSize = backupFile.length() / 1024 // Size in KB
                
                Log.d(TAG, "✓ Chat history backup created: ${backupFile.name}")
                Log.d(TAG, "  └─ Path: ${backupFile.absolutePath}")
                Log.d(TAG, "  └─ Size: $fileSize KB")
                
                // Keep only the 5 most recent backups
                val backups = backupDir.listFiles()?.filter {
                    it.name.startsWith(HISTORY_FILENAME)
                }?.sortedByDescending { it.lastModified() }
                
                backups?.drop(5)?.forEach { it.delete() }
                
            } catch (e: IOException) {
                Log.e(TAG, "✗ Error creating chat history backup", e)
            }
        }
    }
    
    /**
     * Restores a backup file
     */
    fun restoreBackup(backupFilename: String): Boolean {
        val backupDir = File(context.filesDir, "backups")
        val backupFile = File(backupDir, backupFilename)
        
        if (!backupFile.exists()) {
            Log.e(TAG, "✗ Backup file not found: $backupFilename")
            return false
        }
        
        try {
            // Create backup of current state before restoring
            createBackup("pre_restore")
            
            // Copy backup to main history file
            val historyFile = getHistoryFile()
            backupFile.copyTo(historyFile, overwrite = true)
            
            Log.d(TAG, "✓ Backup restored successfully: $backupFilename")
            Log.d(TAG, "  └─ Size: ${backupFile.length() / 1024} KB")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error restoring backup", e)
            return false
        }
    }
    
    /**
     * List all available backups
     */
    fun listBackups(): List<Pair<String, Long>> {
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) {
            return emptyList()
        }
        
        return backupDir.listFiles()
            ?.filter { it.name.startsWith(HISTORY_FILENAME) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { Pair(it.name, it.lastModified()) }
            ?: emptyList()
    }
    
    /**
     * Count total messages in all threads
     */
    private fun countAllMessages(threads: Map<String, MessageThread>): Int {
        return threads.values.sumOf { thread ->
            thread.chains.values.sumOf { chain ->
                chain.messages.size
            }
        }
    }
    
    /**
     * Count total chains in all threads
     */
    private fun countAllChains(threads: Map<String, MessageThread>): Int {
        return threads.values.sumOf { thread -> thread.chains.size }
    }
    
    /**
     * Converts a MessageThread to a StoredThread for JSON serialization
     */
    private fun convertToStoredThread(thread: MessageThread): StoredThread {
        val storedVersions = thread.versions.map { version ->
            StoredVersion(
                userMessage = convertToStoredMessage(version.userMessage),
                botResponse = version.botResponse?.let { convertToStoredMessage(it) },
                timestamp = version.timestamp.time
            )
        }
        
        val storedChains = thread.chains.mapValues { (_, chain) ->
            StoredChain(
                chainId = chain.chainId,
                fromVersionIndex = chain.fromVersionIndex,
                messages = chain.messages.map { convertToStoredMessage(it) },
                timestamp = chain.timestamp.time
            )
        }
        
        return StoredThread(
            id = thread.id,
            versions = storedVersions,
            currentVersionIndex = thread.currentVersionIndex,
            chains = storedChains,
            activeChainId = thread.activeChainId
        )
    }
    
    /**
     * Converts a Message to a StoredMessage for JSON serialization
     */
    private fun convertToStoredMessage(message: Message): StoredMessage {
        return StoredMessage(
            id = message.id,
            content = message.content,
            type = message.type.name,
            timestamp = message.timestamp.time,
            threadId = message.threadId,
            chainId = message.chainId,
            editLineageId = message.editLineageId,
            originalMessageId = message.originalMessageId,
            versionNumber = message.versionNumber,
            hasVersionHistory = message.hasVersionHistory,
            containsImage = message.containsImage,
            imageUrl = message.imageUrl,
            imageData = message.imageData
        )
    }
    
    /**
     * Converts a StoredThread back to a MessageThread
     */
    private fun convertToMessageThread(storedThread: StoredThread): MessageThread {
        val thread = MessageThread(storedThread.id)
        
        // First restore the versions without updating chains
        storedThread.versions.forEach { storedVersion ->
            val userMessage = convertToMessage(storedVersion.userMessage)
            val botResponse = storedVersion.botResponse?.let { convertToMessage(it) }
            
            thread.versions.add(
                MessageVersion(
                    userMessage = userMessage,
                    botResponse = botResponse,
                    timestamp = Date(storedVersion.timestamp)
                )
            )
        }
        
        // Set the current version index
        thread.currentVersionIndex = storedThread.currentVersionIndex
        
        // Restore chains
        storedThread.chains.forEach { (chainId, storedChain) ->
            val chain = MessageChain(
                chainId = chainId,
                fromVersionIndex = storedChain.fromVersionIndex,
                timestamp = Date(storedChain.timestamp)
            )
            
            // Add messages to chain
            storedChain.messages.forEach { storedMessage ->
                chain.messages.add(convertToMessage(storedMessage))
            }
            
            // Add chain to thread
            thread.chains[chainId] = chain
        }
        
        // Set active chain
        thread.activeChainId = storedThread.activeChainId
        
        return thread
    }
    
    /**
     * Converts a StoredMessage back to a Message
     */
    private fun convertToMessage(storedMessage: StoredMessage): Message {
        return Message(
            id = storedMessage.id,
            content = storedMessage.content,
            type = MessageType.valueOf(storedMessage.type),
            timestamp = Date(storedMessage.timestamp),
            threadId = storedMessage.threadId,
            chainId = storedMessage.chainId,
            editLineageId = storedMessage.editLineageId,
            originalMessageId = storedMessage.originalMessageId,
            versionNumber = storedMessage.versionNumber,
            hasVersionHistory = storedMessage.hasVersionHistory,
            containsImage = storedMessage.containsImage,
            imageUrl = storedMessage.imageUrl,
            imageData = storedMessage.imageData
        )
    }
}