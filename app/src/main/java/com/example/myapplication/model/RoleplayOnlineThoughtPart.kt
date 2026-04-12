package com.example.myapplication.model

private const val ONLINE_THOUGHT_PREFIX = "[[rp_online_thought]]"

fun thoughtMessagePart(
    content: String,
): ChatMessagePart {
    return textMessagePart(encodeOnlineThoughtContent(content))
}

fun encodeOnlineThoughtContent(
    content: String,
): String {
    val normalized = content.trim()
    return if (normalized.isBlank()) {
        ""
    } else {
        "$ONLINE_THOUGHT_PREFIX$normalized"
    }
}

fun isOnlineThoughtText(
    text: String,
): Boolean {
    return text.trimStart().startsWith(ONLINE_THOUGHT_PREFIX)
}

fun decodeOnlineThoughtText(
    text: String,
): String {
    return text.trimStart()
        .removePrefix(ONLINE_THOUGHT_PREFIX)
        .trim()
}

fun ChatMessagePart.isOnlineThoughtPart(): Boolean {
    return type == ChatMessagePartType.TEXT && isOnlineThoughtText(text)
}

fun ChatMessagePart.onlineThoughtContent(): String {
    return decodeOnlineThoughtText(text)
}
