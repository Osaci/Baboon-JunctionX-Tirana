package com.example.baboonchat.network

import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketManager(private val listener: WebSocketListener) {
    private val TAG = "WebSocketManager"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
    
    fun connect(url: String, sessionToken: String) {
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                
                // Identify as mobile client
                val identifyMessage = JSONObject().apply {
                    put("type", "mobile_client")
                    put("session_token", sessionToken)
                    put("user_info", JSONObject().apply {
                        put("name", "Mobile User")
                        put("device", "Android")
                    })
                }
                
                webSocket.send(identifyMessage.toString())
                listener.onConnected()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: $text")
                handleMessage(text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                listener.onDisconnected()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                listener.onError(t)
            }
        })
    }
    
    private fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.getString("type")) {
                "queued" -> {
                    val position = json.getInt("position")
                    listener.onQueued(position)
                }
                "representative_message" -> {
                    val content = json.getString("content")
                    listener.onRepresentativeMessage(content)
                }
                "chat_assigned" -> {
                    val repName = json.optString("rep_name", "Support Representative")
                    listener.onChatAssigned(repName)
                }
                "chat_ended" -> {
                    listener.onChatEnded()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }
    
    fun sendMessage(message: String) {
        val messageJson = JSONObject().apply {
            put("type", "message")
            put("content", message)
        }
        webSocket?.send(messageJson.toString())
    }
    
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
    }
    
    interface WebSocketListener {
        fun onConnected()
        fun onDisconnected()
        fun onError(error: Throwable)
        fun onQueued(position: Int)
        fun onRepresentativeMessage(message: String)
        fun onChatAssigned(repName: String)
        fun onChatEnded()
    }
}