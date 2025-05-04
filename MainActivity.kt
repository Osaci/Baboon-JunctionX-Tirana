package com.example.baboonchat

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.baboonchat.data.local.ChatHistoryManagerFactory
import com.example.baboonchat.ui.chat.ChatFragment
import com.example.baboonchat.ui.chat.ChatViewModel
import com.example.baboonchat.ui.chat.ChatViewModelFactory
import com.example.baboonchat.ui.theme.ThemeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var themeManager: ThemeManager
    private lateinit var viewModel: ChatViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize theme manager
        themeManager = ThemeManager(this)

        setContentView(R.layout.activity_main)

        // Apply current theme
        themeManager.applyTheme(this)
        
        // Initialize ViewModel with history support
        val factory = ChatViewModelFactory(applicationContext)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
        
        // Log storage paths for debugging
        logStoragePaths()

        // Add chat fragment if this is the first creation
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, ChatFragment())
                .commit()
        }
    }
    
    // Ensures theme is reapplied when re entering the main activity UI
    override fun onResume() {
        super.onResume()
        themeManager.applyTheme(this)
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_history -> {
                showClearHistoryConfirmation()
                true
            }
            R.id.action_backup_history -> {
                createBackup()
                true
            }
            R.id.action_view_stats -> {
                showHistoryStats()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showClearHistoryConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear History")
            .setMessage("Are you sure you want to clear all chat history? This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                viewModel.clearHistory()
                Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun createBackup() {
        viewModel.createBackup()
        Toast.makeText(this, "Backup created", Toast.LENGTH_SHORT).show()
    }
    
    private fun showHistoryStats() {
        val stats = viewModel.getHistoryStats(this)
        
        val threadCount = stats["threadCount"] as? Int ?: 0
        val messageCount = stats["messageCount"] as? Int ?: 0
        val fileSizeKB = stats["fileSizeKB"] as? Long ?: 0
        val lastModified = stats["lastModified"] as? Date
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val lastModifiedStr = lastModified?.let { dateFormat.format(it) } ?: "N/A"
        
        AlertDialog.Builder(this)
            .setTitle("Chat History Statistics")
            .setMessage("""
                Conversation threads: $threadCount
                Total messages: $messageCount
                History file size: $fileSizeKB KB
                Last saved: $lastModifiedStr
                
                Storage path: 
                ${stats["filePath"] ?: "Not saved yet"}
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun logStoragePaths() {
        val internalDir = filesDir.absolutePath
        Log.d("MainActivity", "Internal storage path: $internalDir")
        Log.d("MainActivity", "Chat history should be at: $internalDir/chat_history.json")
        
        // Check if history file exists
        val historyFile = File(filesDir, "chat_history.json")
        if (historyFile.exists()) {
            val fileSize = historyFile.length() / 1024 // KB
            Log.d("MainActivity", "Chat history file exists, size: $fileSize KB")
        } else {
            Log.d("MainActivity", "Chat history file does not exist yet")
        }
    }
}
