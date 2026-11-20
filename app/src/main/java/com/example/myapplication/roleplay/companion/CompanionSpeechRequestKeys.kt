package com.example.myapplication.roleplay.companion

import com.example.myapplication.data.repository.ai.TranslationRequestSnapshot
import com.example.myapplication.model.CompanionSpeechSynthesisIdentity
import com.example.myapplication.model.SpeechRequestKey
import com.example.myapplication.model.TranslationRequestKey
import java.nio.ByteBuffer
import java.security.MessageDigest

/** 内存、磁盘和收藏只消费此键；长度前缀避免分隔符/空值碰撞。 */
object CompanionSpeechRequestKeys {
    fun translation(
        snapshot: TranslationRequestSnapshot,
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationRequestKey {
        val config = snapshot.identity
        return TranslationRequestKey(
            hash(
                listOf(
                    "translation",
                    config.builderVersion,
                    text.trim(),
                    sourceLanguage.trim(),
                    targetLanguage.trim(),
                    config.providerId,
                    config.endpoint,
                    config.modelId,
                    config.apiProtocol.name,
                    config.textApiMode.name,
                )
            )
        )
    }

    fun speech(
        text: String,
        sourceLanguage: String,
        languageTag: String,
        identity: CompanionSpeechSynthesisIdentity,
        translationKey: TranslationRequestKey? = null,
    ): SpeechRequestKey = SpeechRequestKey(hash(buildList {
        addAll(
            listOf(
                "speech",
                identity.builderVersion,
                text.trim(),
                sourceLanguage.trim(),
                languageTag.trim(),
                translationKey?.value.orEmpty(),
                identity.endpoint,
                identity.model,
                identity.characterId,
                identity.voiceMode.name,
            )
        )
        identity.parameters.toSortedMap().forEach { (key, value) -> add(key); add(value) }
    }))

    internal fun bytesHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun hash(parts: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
