package com.example.myapplication.ui.screen.chat

sealed interface ChatMessagePreviewPayload {
    val title: String

    data class MessageHtmlPreview(
        override val title: String,
        val html: String,
        val baseUrl: String = "https://narra.local/",
    ) : ChatMessagePreviewPayload

    data class ExternalUrlPreview(
        override val title: String,
        val url: String,
    ) : ChatMessagePreviewPayload
}

data class ChatMessageSelectionPayload(
    val title: String,
    val content: String,
)

data class ChatPreviewImageItem(
    val source: String,
    val fileName: String,
    val mimeType: String = "",
)

data class ChatImagePreviewPayload(
    val title: String,
    val images: List<ChatPreviewImageItem>,
    val initialIndex: Int = 0,
)

data class ChatMessageExportPayload(
    val fileName: String,
    val markdown: String,
)
