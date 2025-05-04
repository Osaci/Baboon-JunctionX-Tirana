package com.example.baboonchat.data.remote

import android.util.Log
import com.example.baboonchat.data.model.Message
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MessageRepository {
    private val TAG = "MessageRepository"
    private val apiService: ChatApiService

    init {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://lilotest.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ChatApiService::class.java)
    }

    /**
     * Initiates chat handover to human representative
     */
    suspend fun initiateHandover(chatHistory: List<Map<String, String>>): HandoverResponse {
        val request = HandoverRequest(chatHistory = chatHistory)
        val response = apiService.initiateHandover(request)
    
        if (response.isSuccessful) {
            return response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("Failed to initiate handover: ${response.code()}")
        }
    }

    /**
     * Sends a user message to the Gemini API with conversation history context
     *
     * @param message The user's current message
     * @param conversationHistory List of previous messages in the conversation
     * @param activeChainId ID of the active message chain
     * @return The model's response as a string
     */
    suspend fun sendMessage(
        message: String,
        conversationHistory: List<Message> = emptyList(),
        activeChainId: String? = null
    ): String {
        // Get only messages relevant to the current chain
        val relevantMessages = if (activeChainId != null) {
            PromptBuilder.getRelevantMessagesForPrompt(conversationHistory, activeChainId)
        } else {
            emptyList()
        }

        // Build a prompt that includes conversation history
        val promptWithHistory = PromptBuilder.buildPromptWithHistory(message, relevantMessages)

        // Log the prepared prompt for debugging
        Log.d(TAG, "Sending message with history context, total messages: ${relevantMessages.size}")

        // Send the enriched prompt to the API
        val response = apiService.sendMessage(MessageRequest(promptWithHistory))

        if (response.isSuccessful) {
            val messageResponse = response.body()
            return messageResponse?.response?.text ?: "Empty response"
        } else {
            throw Exception("Failed to send message: ${response.code()} - ${response.message()}")
        }
    }
}