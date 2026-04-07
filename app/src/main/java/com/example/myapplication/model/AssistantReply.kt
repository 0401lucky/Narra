package com.example.myapplication.model

data class AssistantReply(
    val content: String,
    val reasoningContent: String = "",
    val reasoningSteps: List<ChatReasoningStep> = emptyList(),
    val parts: List<ChatMessagePart> = emptyList(),
    val citations: List<MessageCitation> = emptyList(),
)

data class GatewayToolingOptions(
    val enabledToolNames: Set<String> = emptySet(),
    val runtimeContext: GatewayToolRuntimeContext? = null,
) {
    val searchEnabled: Boolean
        get() = "search_web" in enabledToolNames

    companion object {
        fun searchOnly(
            enabled: Boolean,
        ): GatewayToolingOptions {
            return GatewayToolingOptions(
                enabledToolNames = if (enabled) setOf("search_web") else emptySet(),
            )
        }

        fun chat(
            searchEnabled: Boolean,
            runtimeContext: GatewayToolRuntimeContext,
        ): GatewayToolingOptions {
            return GatewayToolingOptions(
                enabledToolNames = if (searchEnabled) setOf("search_web") else emptySet(),
                runtimeContext = runtimeContext,
            )
        }

        fun localContextOnly(
            runtimeContext: GatewayToolRuntimeContext,
        ): GatewayToolingOptions {
            return GatewayToolingOptions(
                runtimeContext = runtimeContext,
            )
        }
    }
}

sealed interface ChatStreamEvent {
    data class ContentDelta(val value: String) : ChatStreamEvent

    data class ReasoningStepStarted(
        val stepId: String,
        val createdAt: Long,
    ) : ChatStreamEvent

    data class ReasoningStepDelta(
        val stepId: String,
        val value: String,
    ) : ChatStreamEvent

    data class ReasoningStepCompleted(
        val stepId: String,
        val finishedAt: Long,
    ) : ChatStreamEvent

    // 兼容底层 provider 的旧 delta 事件，统一会在网关层转成 reasoning step 事件。
    data class ReasoningDelta(val value: String) : ChatStreamEvent

    data class ImageDelta(val part: ChatMessagePart) : ChatStreamEvent

    data class Citations(val items: List<MessageCitation>) : ChatStreamEvent

    data object Completed : ChatStreamEvent
}
