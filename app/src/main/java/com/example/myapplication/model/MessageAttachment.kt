package com.example.myapplication.model

import androidx.compose.runtime.Immutable

enum class AttachmentType {
    IMAGE,
    FILE,
}

@Immutable
data class MessageAttachment(
    val type: AttachmentType = AttachmentType.IMAGE,
    val uri: String = "",
    val mimeType: String = "image/*",
    val fileName: String = "",
)
