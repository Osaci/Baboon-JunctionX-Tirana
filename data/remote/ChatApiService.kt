package com.example.baboonchat.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ChatApiService {

    /**
     * Sends a message to the Gemini generation model
     * 
     * @param message The message request containing the prompt with history
     * @return The response from the Gemini model
     */    
    @POST("send-message")
    suspend fun sendMessage(@Body message: MessageRequest): Response<MessageResponse>

    @POST("api/initiate_handover")
    suspend fun initiateHandover(@Body request: HandoverRequest): Response<HandoverResponse>
}
/**
 * Request model for sending messages to the API
 * 
 * @param message The full prompt message including conversation history
 */
data class MessageRequest(val message: String)

/**
 * Response model from the Gemini API
 */
data class MessageResponse(
    val response: ResponseContent,
    val session_id: String
)

/**
 * Content of the response from Gemini
 */
data class ResponseContent(
    val type: String,
    val text: String
)