package com.example.myapplication.data.repository

import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.TransferStatus

data class ParsedAssistantSpecialOutput(
    val content: String,
    val parts: List<ChatMessagePart>,
    val transferUpdates: List<TransferUpdateDirective> = emptyList(),
)

data class TransferUpdateDirective(
    val refId: String,
    val status: TransferStatus,
)

data class StructuredMemoryExtractionResult(
    val persistentMemories: List<String> = emptyList(),
    val sceneStateMemories: List<String> = emptyList(),
)

enum class RoleplayMemoryCondenseMode {
    CHARACTER,
    SCENE,
}

data class ImageGenerationResult(
    val b64Data: String,
    val url: String,
    val revisedPrompt: String,
)
