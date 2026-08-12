package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.RoleplayContentType

/**
 * 为一条底层消息生成稳定的 UI 分段 identity。
 *
 * 计数器只在当前 source message 内按固定类型递增，不受整张消息列表的插入、删除影响。
 * action / special 仅采用底层消息中唯一且已经持久化的 ID；重复 ID 或解析阶段临时生成的 ID
 * 会回退到局部序号，避免把出现次数伪装成可靠的持久 identity。
 */
internal class RoleplayMessageUiIdentityScope private constructor(
    private val identitySourceMessageId: String,
    private val persistentActionIds: Set<String>,
    private val persistentSpecialIds: Set<String>,
) {
    private val nextOrdinalByKind = mutableMapOf<String, Int>()
    private val emittedSegmentCounts = mutableMapOf<String, Int>()

    fun next(contentType: RoleplayContentType): String {
        return nextSegment(contentType.name.lowercase())
    }

    fun nextNamed(kind: String): String {
        return nextSegment(kind)
    }

    fun streamingPreview(): String {
        return uniqueUiItemId("streaming-preview")
    }

    fun action(part: ChatMessagePart): String {
        val persistentId = part.actionId.trim().takeIf(persistentActionIds::contains)
        return if (persistentId != null) {
            uniqueUiItemId("action-id:${component(persistentId)}")
        } else {
            nextSegment("action")
        }
    }

    fun special(part: ChatMessagePart): String {
        val persistentId = part.specialId.trim().takeIf(persistentSpecialIds::contains)
        return if (persistentId != null) {
            uniqueUiItemId("special-id:${component(persistentId)}")
        } else {
            nextSegment("special")
        }
    }

    private fun nextSegment(kind: String): String {
        val ordinal = nextOrdinalByKind[kind] ?: 0
        nextOrdinalByKind[kind] = ordinal + 1
        return uniqueUiItemId("$kind:$ordinal")
    }

    private fun uniqueUiItemId(segmentId: String): String {
        val occurrence = emittedSegmentCounts[segmentId] ?: 0
        emittedSegmentCounts[segmentId] = occurrence + 1
        val uniqueSegmentId = if (occurrence == 0) {
            segmentId
        } else {
            "$segmentId:duplicate-$occurrence"
        }
        return "roleplay-ui:${component(identitySourceMessageId)}:${component(uniqueSegmentId)}"
    }

    private fun component(value: String): String = "${value.length}:$value"

    companion object {
        fun forMessage(message: ChatMessage): RoleplayMessageUiIdentityScope {
            val uniquePersistentActionIds = message.parts
                .map { part -> part.actionId.trim() }
                .filter(String::isNotBlank)
                .groupingBy { it }
                .eachCount()
                .filterValues { count -> count == 1 }
                .keys
            val uniquePersistentSpecialIds = message.parts
                .map { part -> part.specialId.trim() }
                .filter(String::isNotBlank)
                .groupingBy { it }
                .eachCount()
                .filterValues { count -> count == 1 }
                .keys
            return RoleplayMessageUiIdentityScope(
                identitySourceMessageId = message.id,
                persistentActionIds = uniquePersistentActionIds,
                persistentSpecialIds = uniquePersistentSpecialIds,
            )
        }

        fun forStreaming(loadingMessageId: String?): RoleplayMessageUiIdentityScope {
            return RoleplayMessageUiIdentityScope(
                identitySourceMessageId = loadingMessageId?.takeIf { it.isNotBlank() } ?: "streaming",
                persistentActionIds = emptySet(),
                persistentSpecialIds = emptySet(),
            )
        }
    }
}
