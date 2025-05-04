package com.example.baboonchat.ui.chat

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.baboonchat.R
import com.example.baboonchat.data.model.Message
import com.example.baboonchat.data.model.MessageType
import com.example.baboonchat.ui.theme.ThemeManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Fragment that handles the chat interface
 */
class ChatFragment : Fragment() {

    private val TAG = "ChatFragment"
    
    // Views
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: TextInputEditText
    private lateinit var messageInputLayout: TextInputLayout
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateText: TextView
    private lateinit var representativeInfoContainer: LinearLayout
    private lateinit var representativeNameText: TextView
    private lateinit var connectionStatusText: TextView
    
    // Adapter
    private lateinit var messageAdapter: MessageAdapter
    
    // ViewModel - shared with the activity
    private lateinit var viewModel: ChatViewModel
    
    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        initViews(view)
        
        // Get ViewModel from activity to ensure it's shared
        viewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
        
        // Initialize adapter
        setupAdapter()
        
        // Observe messages from ViewModel
        observeViewModel()
        
        // Set up listeners
        setupListeners()
    }
    
    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.messages_recycler_view)
        messageInput = view.findViewById(R.id.message_input)
        messageInputLayout = view.findViewById(R.id.message_input_layout)
        sendButton = view.findViewById(R.id.send_button)
        progressBar = view.findViewById(R.id.progress_bar)
        emptyStateText = view.findViewById(R.id.empty_state_text)
        representativeInfoContainer = view.findViewById(R.id.representative_info_container)
        representativeNameText = view.findViewById(R.id.representative_name_text)
        connectionStatusText = view.findViewById(R.id.connection_status_text)
        
        // Set input field colors
        messageInput.setTextColor(Color.BLACK)
        messageInput.setBackgroundColor(Color.WHITE)
        
        // Alternative: Use XML styling by setting background and text color in the layout file
        // Or programmatically:
        messageInputLayout.boxBackgroundColor = Color.WHITE
        messageInput.setHintTextColor(Color.GRAY)
        
        // Initially hide representative info
        representativeInfoContainer.visibility = View.GONE

        // Configure RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true // Messages should stack from bottom
        }
    }
    
    private fun setupAdapter() {
        // Get theme manager from activity
        val themeManager = ThemeManager(requireContext())

        // Create instance of the factory
        val viewHolderFactory = MessageViewHolderFactory()

        // Create adapter with the factory
        messageAdapter = MessageAdapter(viewHolderFactory, themeManager)
    
        // Set the view model for the adapter
        messageAdapter.setChatViewModel(viewModel)

        // Add callback for representative button click
        messageAdapter.onRepresentativeClick = {
            viewModel.connectToRepresentative()
        }

        // Set edit message listener
        messageAdapter.setEditMessageListener(object : MessageAdapter.EditMessageListener {
            override fun onMessageEdited(messageId: String, newContent: String) {
                viewModel.editMessage(messageId, newContent)
            }
        })

        // Set up version navigation listener
        messageAdapter.setVersionNavigationListener(object : MessageAdapter.MessageVersionNavigationListener {
            override fun onPreviousVersion(threadId: String) {
                viewModel.navigateToPreviousVersion(threadId)
            }
        
            override fun onNextVersion(threadId: String) {
                viewModel.navigateToNextVersion(threadId)
            }
        
            override fun navigateToSpecificVersion(threadId: String, versionIndex: Int) {
                viewModel.navigateToSpecificVersion(threadId, versionIndex)
            }
        })

        recyclerView.adapter = messageAdapter
    }

    private fun observeViewModel() {
        // Observe messages
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            if (messages.isEmpty()) {
                showEmptyState(true)
            } else {
                showEmptyState(false)
                messageAdapter.submitList(messages)
                // Scroll to bottom after a short delay to ensure adapter has updated
                recyclerView.postDelayed({
                    recyclerView.scrollToPosition(messages.size - 1)
                }, 100)
            }
        }
        
        // Observe WebSocket connection state
        viewModel.isConnectedToRepresentative.observe(viewLifecycleOwner) { isConnected ->
            if (isConnected) {
                updateUIForRepresentativeChat()
            } else {
                updateUIForAIChat()
            }
        }
        
        // Observe representative name
        viewModel.representativeName.observe(viewLifecycleOwner) { name ->
            showRepresentativeInfo(name)
        }
        
        // Observe support contact responses
        viewModel.supportContact.observe(viewLifecycleOwner) { supportContact ->
            supportContact?.let {
                // The adapter will handle showing the button
                // Just make sure the message is displayed
                Log.d(TAG, "Support contact response received")
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            
            // Disable send button during loading
            sendButton.isEnabled = !isLoading
            messageInput.isEnabled = !isLoading
        }
    }
    
    private fun setupListeners() {
        // Send button click
        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                messageInput.text?.clear()
            }
        }
        
        // Enter key press in input field
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val message = messageInput.text.toString().trim()
                if (message.isNotEmpty()) {
                    sendMessage(message)
                    messageInput.text?.clear()
                }
                true
            } else {
                false
            }
        }
    }
    
    private fun sendMessage(content: String) {
        viewModel.sendMessage(content)
    }
    
    private fun showEmptyState(show: Boolean) {
        if (show) {
            emptyStateText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun updateUIForRepresentativeChat() {
        // Update UI elements to indicate representative chat
        messageInput.hint = "Chatting with support..."
        representativeInfoContainer.visibility = View.VISIBLE
        connectionStatusText.text = "Connected to representative"
        connectionStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
        
        // Optionally change send button appearance
        sendButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.support_blue))
    }
    
    private fun updateUIForAIChat() {
        messageInput.hint = "Type a message..."
        representativeInfoContainer.visibility = View.GONE
        
        // Restore normal send button appearance
        sendButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_color))
    }
    
    private fun showRepresentativeInfo(name: String) {
        representativeNameText.text = "Chatting with: $name"
    }
}