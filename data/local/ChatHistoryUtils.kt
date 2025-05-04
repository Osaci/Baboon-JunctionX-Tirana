package com.example.baboonchat.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Utility class for exporting and importing chat history
 */
class ChatHistoryUtils(private val context: Context) {
    private val TAG = "ChatHistoryUtils"
    private val HISTORY_FILENAME = "chat_history.json"
    private val executorService = Executors.newSingleThreadExecutor()
    
    /**
     * Export chat history to external storage in a zip file
     * Returns the file Uri if successful, null otherwise
     */
    fun exportChatHistory(listener: (Uri?) -> Unit) {
        executorService.execute {
            try {
                // Create a timestamped filename for the export
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val exportFilename = "baboonchat_chat_history_$timestamp.zip"
                
                // Create a file in the app's cache directory
                val exportFile = File(context.cacheDir, exportFilename)
                if (exportFile.exists()) {
                    exportFile.delete()
                }
                
                // Get the source history file
                val historyFile = File(context.filesDir, HISTORY_FILENAME)
                if (!historyFile.exists()) {
                    Log.d(TAG, "No chat history file found to export")
                    listener(null)
                    return@execute
                }
                
                // Create a zip file containing the chat history
                ZipOutputStream(FileOutputStream(exportFile)).use { zipOut ->
                    FileInputStream(historyFile).use { fileIn ->
                        // Add the history file to the zip
                        val zipEntry = ZipEntry(HISTORY_FILENAME)
                        zipOut.putNextEntry(zipEntry)
                        
                        val buffer = ByteArray(1024)
                        var len: Int
                        while (fileIn.read(buffer).also { len = it } > 0) {
                            zipOut.write(buffer, 0, len)
                        }
                        
                        zipOut.closeEntry()
                    }
                }
                
                // Create a content URI for the file using FileProvider
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )
                
                Log.d(TAG, "Chat history exported successfully to $uri")
                listener(uri)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting chat history", e)
                listener(null)
            }
        }
    }
    
    /**
     * Import chat history from a zip file
     * Returns true if successful, false otherwise
     */
    fun importChatHistory(uri: Uri, listener: (Boolean) -> Unit) {
        executorService.execute {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipIn ->
                        var zipEntry = zipIn.nextEntry
                        
                        while (zipEntry != null) {
                            if (zipEntry.name == HISTORY_FILENAME) {
                                // Create a temporary file to store the extracted JSON
                                val tempFile = File(context.cacheDir, "temp_$HISTORY_FILENAME")
                                if (tempFile.exists()) {
                                    tempFile.delete()
                                }
                                
                                // Extract the JSON file
                                FileOutputStream(tempFile).use { fileOut ->
                                    val buffer = ByteArray(1024)
                                    var len: Int
                                    while (zipIn.read(buffer).also { len = it } > 0) {
                                        fileOut.write(buffer, 0, len)
                                    }
                                }
                                
                                // Validate the JSON
                                val isValid = validateJsonFormat(tempFile)
                                if (!isValid) {
                                    Log.e(TAG, "Invalid chat history JSON format")
                                    listener(false)
                                    return@execute
                                }
                                
                                // Replace the existing history file
                                val historyFile = File(context.filesDir, HISTORY_FILENAME)
                                tempFile.copyTo(historyFile, true)
                                
                                Log.d(TAG, "Chat history imported successfully")
                                listener(true)
                                return@execute
                            }
                            
                            zipIn.closeEntry()
                            zipEntry = zipIn.nextEntry
                        }
                        
                        Log.e(TAG, "Chat history file not found in zip")
                        listener(false)
                    }
                } ?: run {
                    Log.e(TAG, "Could not open input stream for URI")
                    listener(false)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error importing chat history", e)
                listener(false)
            }
        }
    }
    
    /**
     * Check if the imported JSON is valid
     */
    private fun validateJsonFormat(jsonFile: File): Boolean {
        try {
            val gson = GsonBuilder().create()
            InputStreamReader(FileInputStream(jsonFile)).use { reader ->
                // Try to parse as a list of StoredThread objects
                gson.fromJson(reader, Array<StoredThread>::class.java)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating JSON format", e)
            return false
        }
    }
    
    /**
     * Create a backup of the current chat history
     */
    fun createBackup() {
        executorService.execute {
            try {
                val historyFile = File(context.filesDir, HISTORY_FILENAME)
                if (!historyFile.exists()) {
                    return@execute
                }
                
                val backupDir = File(context.filesDir, "backups")
                if (!backupDir.exists()) {
                    backupDir.mkdir()
                }
                
                // Create a timestamped backup file
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val backupFile = File(backupDir, "${HISTORY_FILENAME}.backup.$timestamp")
                
                // Copy the current history file to the backup
                historyFile.copyTo(backupFile)
                
                // Keep only the 5 most recent backups
                val backups = backupDir.listFiles()?.filter {
                    it.name.startsWith("${HISTORY_FILENAME}.backup")
                }?.sortedByDescending { it.lastModified() }
                
                backups?.drop(5)?.forEach { it.delete() }
                
                Log.d(TAG, "Chat history backup created: ${backupFile.name}")
                
            } catch (e: IOException) {
                Log.e(TAG, "Error creating chat history backup", e)
            }
        }
    }
}