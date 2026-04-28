package com.example.myapplication.model

import java.util.UUID

const val USER_PROFILE_PERSONA_MASK_ID = "user-profile-persona-mask"

data class UserPersonaMask(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val avatarUri: String = "",
    val avatarUrl: String = "",
    val personaPrompt: String = "",
    val note: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class ResolvedUserPersona(
    val displayName: String,
    val personaPrompt: String,
    val avatarUri: String,
    val avatarUrl: String,
    val sourceMaskId: String = "",
)

fun UserPersonaMask.normalized(now: Long = System.currentTimeMillis()): UserPersonaMask {
    val created = createdAt.takeIf { it > 0L } ?: now
    val updated = updatedAt.takeIf { it > 0L } ?: now
    return copy(
        id = id.trim().ifBlank { UUID.randomUUID().toString() },
        name = name.trim().ifBlank { DEFAULT_USER_DISPLAY_NAME },
        avatarUri = avatarUri.trim(),
        avatarUrl = avatarUrl.trim(),
        personaPrompt = personaPrompt.replace("\r\n", "\n").trim(),
        note = note.replace("\r\n", "\n").trim(),
        tags = tags.map(String::trim).filter(String::isNotEmpty).distinct(),
        createdAt = created,
        updatedAt = updated,
    )
}
