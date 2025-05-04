package com.example.baboonchat.data.model

import java.util.Date
import java.util.UUID


data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String? = null,
    val type: MessageType,
    val timestamp: Date = Date(),
    val containsImage: Boolean = false,
    val imageUrl: String? = null,
    val imageData: String? = null,
    val threadId: String? = null,
    val isCurrentVersion: Boolean = true,
    val versionNumber: Int = 0,
    val hasVersionHistory: Boolean = false,
    val chainId: String? = null,
    val isDisplayed: Boolean = false,
    val originalMessageId: String? = null,
    val editLineageId: String? = null
    //val editVersion: Int = 1,
    //val totalEditVersions: Int = 1
)