package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.StreamedAssistantPayload
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.toContentMirror

/**
 * 构建“取消生成”后落库的助手消息。
 *
 * 产品决策：取消 = 落 [MessageStatus.COMPLETED] 并保留已生成的部分内容（不标红、不被重试逻辑当成失败项）。
 * 内容优先取已流式生成的文本，其次取 parts 的内容镜像，二者皆空时回退为“已取消”。
 */
internal fun buildCancelledAssistantMessage(
    payload: StreamedAssistantPayload,
    loading: ChatMessage,
): ChatMessage {
    val partialContent = payload.content.ifBlank {
        payload.parts.toContentMirror(specialFallback = "特殊玩法")
    }
    return loading.copy(
        content = partialContent.ifBlank { "已取消" },
        status = MessageStatus.COMPLETED,
        reasoningContent = payload.reasoning,
        reasoningSteps = payload.reasoningSteps,
        parts = payload.parts,
        citations = payload.citations,
    )
}
