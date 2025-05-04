package com.example.baboonchat.ui.history

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.baboonchat.R
import com.example.baboonchat.data.local.ChatHistoryUtils
import com.example.baboonchat.ui.chat.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File

/**
 * Fragment for managing chat history operations
 */
class ChatHistoryFragment : Fragment() {
    private val TAG = "ChatHistoryFragment"
    private lateinit var viewModel: ChatViewModel
    private lateinit var historyUtils: ChatHistoryUtils
    
    private lateinit var statusTextView: TextView
    private lateinit var exportButton: Button
    private lateinit var importButton: Button
    private lateinit var clearButton: Button
    
    // Register for activity result to handle file selection
    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importChatHistory(uri)
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_chat_history, container, false)
        
        statusTextView = root.findViewById(R.id.history_status_text)
        exportButton = root.findViewById(R.id.export_history_button)
        importButton = root.findViewById(R.id.import_history_button)
        clearButton = root.findViewById(R.id.clear_history_button)
        
        return root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
        historyUtils = ChatHistoryUtils(requireContext())
        
        setupButtons()
        updateHistoryStatus()
    }
    
    private fun setupButtons() {
        exportButton.setOnClickListener {
            exportChatHistory()
        }
        
        importButton.setOnClickListener {
            openFileChooser()
        }
        
        clearButton.setOnClickListener {
            // Show confirmation dialog
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Clear Chat History")
                .setMessage("Are you sure you want to delete all chat history? This action cannot be undone.")
                .setPositiveButton("Clear") { _, _ ->
                    clearChatHistory()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun updateHistoryStatus() {
        val historyFile = File(requireContext().filesDir, "chat_history.json")
        
        if (historyFile.exists()) {
            val lastModified = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                .format(Date(historyFile.lastModified()))
            
            val fileSize = historyFile.length() / 1024 // Size in KB
            
            statusTextView.text = "Chat history: $fileSize KB\nLast modified: $lastModified"
            
            // Enable clear and export buttons
            clearButton.isEnabled = true
            exportButton.isEnabled = true
        } else {
            statusTextView.text = "No chat history found"
            
            // Disable clear and export buttons
            clearButton.isEnabled = false
            exportButton.isEnabled = false
        }
    }
    
    private fun exportChatHistory() {
        exportButton.isEnabled = false
        statusTextView.text = "Exporting chat history..."
        
        historyUtils.exportChatHistory { uri ->
            requireActivity().runOnUiThread {
                if (uri != null) {
                    // Create share intent
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "application/zip"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    // Start the share activity
                    startActivity(Intent.createChooser(shareIntent, "Share Chat History"))
                    
                    Toast.makeText(context, "Chat history exported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to export chat history", Toast.LENGTH_SHORT).show()
                }
                
                updateHistoryStatus()
                exportButton.isEnabled = true
            }
        }
    }
    
    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        
        importLauncher.launch(intent)
    }
    
    private fun importChatHistory(uri: android.net.Uri) {
        importButton.isEnabled = false
        statusTextView.text = "Importing chat history..."
        
        historyUtils.importChatHistory(uri) { success ->
            requireActivity().runOnUiThread {
                if (success) {
                    Toast.makeText(context, "Chat history imported successfully", Toast.LENGTH_SHORT).show()
                    
                    // Reload the view model data
                    viewModel.reloadHistory()
                } else {
                    Toast.makeText(context, "Failed to import chat history", Toast.LENGTH_SHORT).show()
                }
                
                updateHistoryStatus()
                importButton.isEnabled = true
            }
        }
    }
    
    private fun clearChatHistory() {
        clearButton.isEnabled = false
        statusTextView.text = "Clearing chat history..."
        
        viewModel.clearHistory()
        
        // Update UI after clearing
        updateHistoryStatus()
        Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh status when fragment becomes visible
        updateHistoryStatus()
    }
}