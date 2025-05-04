package com.example.baboonchat.data.remote

data class HandoverRequest(val chatHistory: List<Map<String, String>>)
data class HandoverResponse(
    val sessionToken: String?,
    val websocketUrl: String,
    val status: String
)