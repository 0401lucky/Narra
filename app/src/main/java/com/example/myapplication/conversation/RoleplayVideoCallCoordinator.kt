package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplayOnlineMeta
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.textMessagePart

/**
 * 封装线上视频通话相关的纯数据操作：
 * - 构造系统事件消息
 * - 读写 [RoleplayOnlineMeta] 的通话字段（保留其他字段不变）
 * - 时长格式化
 *
 * ViewModel 保留对 suggestion/sendingJob 的编排与 UiState 更新，这里只负责
 * "落盘 + 返回结构化结果"。
 */
class RoleplayVideoCallCoordinator(
    private val conversationRepository: ConversationRepository,
    private val roleplayRepository: RoleplayRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    data class ActiveCall(
        val callSessionId: String,
        val startedAt: Long,
    ) {
        val isActive: Boolean get() = callSessionId.isNotBlank() && startedAt > 0L
    }

    data class StartOutcome(
        val callSessionId: String,
        val startedAt: Long,
        /** true 表示此前已存在活跃通话，调用方无需追加系统事件。 */
        val alreadyActive: Boolean,
        val refreshedMessages: List<ChatMessage>?,
    )

    data class HangupOutcome(
        val endedAt: Long,
        val durationText: String,
        val wasActive: Boolean,
        val refreshedMessages: List<ChatMessage>?,
    )

    /**
     * 启动一次视频通话。若数据库中已有活跃通话则原样返回，供调用方同步 UiState。
     */
    suspend fun startCall(
        conversationId: String,
        selectedModel: String,
    ): StartOutcome {
        val meta = roleplayRepository.getOnlineMeta(conversationId)
        val existingSessionId = meta?.activeVideoCallSessionId.orEmpty()
        val existingStartedAt = meta?.activeVideoCallStartedAt ?: 0L
        if (existingSessionId.isNotBlank() && existingStartedAt > 0L) {
            return StartOutcome(
                callSessionId = existingSessionId,
                startedAt = existingStartedAt,
                alreadyActive = true,
                refreshedMessages = null,
            )
        }

        val startedAt = nowProvider()
        val callSessionId = "video-call-$conversationId-$startedAt"
        conversationRepository.appendSystemEventMessage(
            conversationId = conversationId,
            message = buildSystemMessage(
                conversationId = conversationId,
                createdAt = startedAt,
                content = "已接通视频通话",
                eventKind = RoleplayOnlineEventKind.VIDEO_CALL_CONNECTED,
            ),
            selectedModel = selectedModel,
        )
        val refreshed = conversationRepository.listMessages(conversationId)
        roleplayRepository.upsertOnlineMeta(
            meta.withUpdatedCall(
                conversationId = conversationId,
                activeVideoCallSessionId = callSessionId,
                activeVideoCallStartedAt = startedAt,
                updatedAt = startedAt,
            ),
        )
        return StartOutcome(
            callSessionId = callSessionId,
            startedAt = startedAt,
            alreadyActive = false,
            refreshedMessages = refreshed,
        )
    }

    /**
     * 挂断当前通话，写入结束系统事件并清空 meta 里的通话字段。
     * 当数据库与调用方 UiState 同时没有活跃通话时返回 wasActive=false。
     */
    suspend fun hangupCall(
        conversationId: String,
        selectedModel: String,
        fallbackSessionId: String,
        fallbackStartedAt: Long,
    ): HangupOutcome {
        val meta = roleplayRepository.getOnlineMeta(conversationId)
        val activeSessionId = meta?.activeVideoCallSessionId.orEmpty()
            .ifBlank { fallbackSessionId }
        val activeStartedAt = (meta?.activeVideoCallStartedAt ?: 0L)
            .takeIf { it > 0L }
            ?: fallbackStartedAt
        val endedAt = nowProvider()

        val wasActive = activeSessionId.isNotBlank() && activeStartedAt > 0L
        val durationText = formatDuration(endedAt - activeStartedAt)
        var refreshed: List<ChatMessage>? = null
        if (wasActive) {
            conversationRepository.appendSystemEventMessage(
                conversationId = conversationId,
                message = buildSystemMessage(
                    conversationId = conversationId,
                    createdAt = endedAt,
                    content = "视频通话已结束，通话时长 $durationText",
                    eventKind = RoleplayOnlineEventKind.VIDEO_CALL_ENDED,
                ),
                selectedModel = selectedModel,
            )
            refreshed = conversationRepository.listMessages(conversationId)
        }
        roleplayRepository.upsertOnlineMeta(
            meta.withUpdatedCall(
                conversationId = conversationId,
                activeVideoCallSessionId = "",
                activeVideoCallStartedAt = 0L,
                updatedAt = endedAt,
            ),
        )
        return HangupOutcome(
            endedAt = endedAt,
            durationText = durationText,
            wasActive = wasActive,
            refreshedMessages = refreshed,
        )
    }

    /**
     * 从数据库读取当前活跃通话状态，用于 session 切换时同步 UiState。
     */
    suspend fun fetchActiveCall(conversationId: String): ActiveCall {
        val meta = roleplayRepository.getOnlineMeta(conversationId)
        return ActiveCall(
            callSessionId = meta?.activeVideoCallSessionId.orEmpty(),
            startedAt = meta?.activeVideoCallStartedAt ?: 0L,
        )
    }

    fun formatDuration(durationMillis: Long): String {
        val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun buildSystemMessage(
        conversationId: String,
        createdAt: Long,
        content: String,
        eventKind: RoleplayOnlineEventKind,
    ): ChatMessage {
        return ChatMessage(
            id = "online-event-${eventKind.storageValue}-$conversationId-$createdAt",
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = content,
            createdAt = createdAt,
            parts = listOf(textMessagePart(content)),
            systemEventKind = eventKind,
            roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
            roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )
    }
}

/**
 * 更新视频通话字段同时保留其他 meta 字段。null 视为新建。
 */
private fun RoleplayOnlineMeta?.withUpdatedCall(
    conversationId: String,
    activeVideoCallSessionId: String,
    activeVideoCallStartedAt: Long,
    updatedAt: Long,
): RoleplayOnlineMeta {
    return RoleplayOnlineMeta(
        conversationId = conversationId,
        lastCompensationBucket = this?.lastCompensationBucket.orEmpty(),
        lastConsumedObservationUpdatedAt = this?.lastConsumedObservationUpdatedAt ?: 0L,
        lastSystemEventToken = this?.lastSystemEventToken.orEmpty(),
        activeVideoCallSessionId = activeVideoCallSessionId,
        activeVideoCallStartedAt = activeVideoCallStartedAt,
        updatedAt = updatedAt,
    )
}
