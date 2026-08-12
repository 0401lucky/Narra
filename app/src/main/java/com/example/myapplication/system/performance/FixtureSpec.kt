package com.example.myapplication.system.performance

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object BenchmarkFixtureIds {
    private const val ROLEPLAY_MESSAGE_ID_PREFIX = "benchmark-roleplay-message-"

    const val CHAT_CONVERSATION_ID = "benchmark-chat-conversation-v1"
    const val ROLEPLAY_CONVERSATION_ID = "benchmark-roleplay-conversation-v1"
    const val ROLEPLAY_SCENARIO_ID = "benchmark-roleplay-scenario-v1"
    const val ROLEPLAY_SESSION_ID = "benchmark-roleplay-session-v1"
    const val ASSISTANT_ID = "default-assistant"
    const val FIXED_CLOCK_EPOCH_MILLIS = 1_700_000_000_000L
    const val FIXED_CONSENT_EPOCH_MILLIS = 1_700_000_100_000L

    fun isRoleplayMessageId(messageId: String): Boolean {
        return messageId.startsWith(ROLEPLAY_MESSAGE_ID_PREFIX)
    }
}

data class FixtureMessageRecord(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val status: String,
    val createdAtEpochMillis: Long,
    val attachmentsJson: String = "[]",
    val partsJson: String = "[]",
    val speakerId: String = "",
    val speakerName: String = "",
    val speakerAvatarUri: String = "",
    val roleplayInteractionMode: String = "",
)

data class FixtureDataset(
    val fixtureId: BenchmarkFixtureId,
    val chatMessages: List<FixtureMessageRecord>,
    val roleplayMessages: List<FixtureMessageRecord>,
) {
    fun safeSummary(): FixtureSafeSummary {
        val fingerprintLines = buildList {
            chatMessages.forEachIndexed { index, message ->
                add(message.fingerprintLine("chat", index))
            }
            roleplayMessages.forEachIndexed { index, message ->
                add(message.fingerprintLine("roleplay", index))
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(fingerprintLines.joinToString("\n").toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return FixtureSafeSummary(
            fixtureVersion = BenchmarkContract.FIXTURE_VERSION,
            fixtureId = fixtureId,
            chatMessageCount = chatMessages.size,
            roleplayMessageCount = roleplayMessages.size,
            safeSha256 = digest,
        )
    }

    private fun FixtureMessageRecord.fingerprintLine(path: String, index: Int): String {
        return listOf(path, index, id, role, content.length).joinToString("|")
    }
}

data class FixtureSafeSummary(
    val fixtureVersion: String,
    val fixtureId: BenchmarkFixtureId,
    val chatMessageCount: Int,
    val roleplayMessageCount: Int,
    val safeSha256: String,
)

object DeterministicFixtureFactory {
    fun create(fixtureId: BenchmarkFixtureId): FixtureDataset {
        return FixtureDataset(
            fixtureId = fixtureId,
            chatMessages = messages(
                count = fixtureId.chatMessageCount,
                idPrefix = "benchmark-chat-message",
                conversationId = BenchmarkFixtureIds.CHAT_CONVERSATION_ID,
                contentPrefix = "普通基准消息",
                roleplay = false,
            ),
            roleplayMessages = messages(
                count = fixtureId.roleplayMessageCount,
                idPrefix = "benchmark-roleplay-message",
                conversationId = BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID,
                contentPrefix = "角色扮演基准消息",
                roleplay = true,
            ),
        )
    }

    private fun messages(
        count: Int,
        idPrefix: String,
        conversationId: String,
        contentPrefix: String,
        roleplay: Boolean,
    ): List<FixtureMessageRecord> {
        return List(count) { index ->
            val assistantTurn = index % 2 == 1
            val displayIndex = index.toString().padStart(3, '0')
            FixtureMessageRecord(
                id = "$idPrefix-$displayIndex",
                conversationId = conversationId,
                role = if (assistantTurn) "assistant" else "user",
                content = "$contentPrefix-$displayIndex：固定长度、固定顺序、仅供离线性能验证。",
                status = "COMPLETED",
                createdAtEpochMillis = BenchmarkFixtureIds.FIXED_CLOCK_EPOCH_MILLIS + index,
                speakerId = if (roleplay && assistantTurn) BenchmarkFixtureIds.ASSISTANT_ID else "",
                speakerName = if (roleplay && assistantTurn) "基准角色" else "",
                roleplayInteractionMode = if (roleplay) "offline_dialogue" else "",
            )
        }
    }
}

data class BenchmarkOfflineReplaySpec(
    val chunkDelayMillis: Long,
    val chunks: List<String>,
) {
    init {
        require(chunkDelayMillis > 0L) { "离线重放间隔必须大于 0" }
        require(chunks.isNotEmpty()) { "离线重放内容不能为空" }
        require(chunks.all(String::isNotBlank)) { "离线重放 chunk 不能为空白" }
    }

    val seedContent: String
        get() = chunks.first()

    val completedContent: String
        get() = chunks.joinToString(separator = "")

    fun matchesFixedFixture(
        scenarioId: String?,
        conversationId: String?,
    ): Boolean {
        return scenarioId == BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID &&
            conversationId == BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID
    }

    fun persistedMessageCount(
        totalMessageCount: Int,
        streamingMessageCount: Int,
    ): Int {
        return (totalMessageCount - streamingMessageCount).coerceAtLeast(0)
    }

    fun hasExpectedPersistedMessageCount(count: Int): Boolean {
        return count == BenchmarkFixtureId.S2_POPULATED.roleplayMessageCount
    }

    fun isInitialContent(content: String): Boolean = content == seedContent

    fun isCompletedContent(content: String): Boolean = content == completedContent

    companion object {
        val DEFAULT = BenchmarkOfflineReplaySpec(
            chunkDelayMillis = 48L,
            chunks = listOf(
                "这",
                "是",
                "固定",
                "离线",
                "流式",
                "重放",
                "内容",
                "。",
            ),
        )
    }
}
