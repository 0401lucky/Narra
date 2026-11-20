package com.example.myapplication.context

import com.example.myapplication.model.MEMORY_UNKNOWN_PERSONA
import com.example.myapplication.model.RoleplayScenario
import java.security.MessageDigest
import java.util.UUID

object MemoryArchiveIdentity {
    fun dailySpaceKey(characterId: String): String = id("daily", characterId)

    fun id(vararg components: String): String = UUID.nameUUIDFromBytes(
        components.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8),
    ).toString()

    fun fingerprint(content: String): String = MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun personaKey(scenario: RoleplayScenario): String = when {
        scenario.userPersonaOverride.isNotBlank() -> "override:${fingerprint(scenario.userPersonaOverride)}"
        scenario.userPersonaMaskId.isNotBlank() -> "mask:${scenario.userPersonaMaskId.trim()}"
        else -> MEMORY_UNKNOWN_PERSONA
    }
}
