package com.example.myapplication.system.performance

import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.parseAssistantSpecialOutputLocally
import com.example.myapplication.model.AssistantReply
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderSettings
import kotlinx.coroutines.flow.Flow
import java.lang.reflect.Proxy

internal enum class BenchmarkForbiddenAiOperation(
    val safeName: String,
) {
    AI_GATEWAY("aiGateway"),
    AI_PROMPT_EXTRAS_SERVICE("aiPromptExtrasService"),
}

internal class BenchmarkAiCallBlockedException(
    val benchmarkSessionId: String,
    val operation: BenchmarkForbiddenAiOperation,
) : IllegalStateException("F3 离线重放禁止 AI 外调：${operation.safeName}")

internal data class BenchmarkAiViolationSnapshot(
    val sessionId: String,
    val violationCount: Int,
)

/**
 * 计数器只属于单个有效 F3 会话，不使用进程级全局状态，避免不同基准运行互相污染。
 */
class BenchmarkAiViolationTracker internal constructor(
    val sessionId: String,
) {
    private val lock = Any()
    private var violationCount: Int = 0
    private var nextListenerId: Long = 0L
    private val listeners = linkedMapOf<Long, (Int) -> Unit>()

    init {
        require(sessionId.isNotBlank()) { "F3 benchmark sessionId 不能为空" }
    }

    internal fun snapshot(): BenchmarkAiViolationSnapshot = synchronized(lock) {
        BenchmarkAiViolationSnapshot(
            sessionId = sessionId,
            violationCount = violationCount,
        )
    }

    internal fun hasNoViolations(): Boolean = synchronized(lock) {
        violationCount == 0
    }

    internal fun addViolationListener(listener: (Int) -> Unit): () -> Unit {
        val listenerId: Long
        val currentCount: Int
        synchronized(lock) {
            listenerId = ++nextListenerId
            listeners[listenerId] = listener
            currentCount = violationCount
        }
        if (currentCount > 0) {
            runCatching { listener(currentCount) }
        }
        return {
            synchronized(lock) {
                listeners.remove(listenerId)
            }
        }
    }

    internal fun block(operation: BenchmarkForbiddenAiOperation): Nothing {
        val updatedCount: Int
        val currentListeners: List<(Int) -> Unit>
        synchronized(lock) {
            violationCount += 1
            updatedCount = violationCount
            currentListeners = listeners.values.toList()
        }
        currentListeners.forEach { listener ->
            // 门禁异常必须保持稳定且安全，观察者失败不能替换最终抛出的安全异常。
            runCatching { listener(updatedCount) }
        }
        throw BenchmarkAiCallBlockedException(
            benchmarkSessionId = sessionId,
            operation = operation,
        )
    }
}

internal data class BenchmarkRoleplayAiDependencies(
    val aiGateway: AiGateway,
    val aiPromptExtrasService: AiPromptExtrasService,
    val violationTracker: BenchmarkAiViolationTracker?,
)

internal object F3BenchmarkAiDependencyPolicy {
    fun resolve(
        runtimePolicy: BenchmarkRuntimePolicy?,
        aiGatewayProvider: () -> AiGateway,
        aiPromptExtrasServiceProvider: () -> AiPromptExtrasService,
    ): BenchmarkRoleplayAiDependencies {
        if (runtimePolicy?.useOfflineRoleplayReplay != true) {
            return BenchmarkRoleplayAiDependencies(
                aiGateway = aiGatewayProvider(),
                aiPromptExtrasService = aiPromptExtrasServiceProvider(),
                violationTracker = null,
            )
        }

        val tracker = BenchmarkAiViolationTracker(
            sessionId = requireNotNull(runtimePolicy.sessionId) {
                "有效 F3 benchmark 会话缺少 sessionId"
            },
        )
        return BenchmarkRoleplayAiDependencies(
            aiGateway = FailClosedF3AiGateway(tracker),
            aiPromptExtrasService = createFailClosedPromptExtrasService(tracker),
            violationTracker = tracker,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun createFailClosedPromptExtrasService(
        tracker: BenchmarkAiViolationTracker,
    ): AiPromptExtrasService {
        return Proxy.newProxyInstance(
            AiPromptExtrasService::class.java.classLoader,
            arrayOf(AiPromptExtrasService::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FailClosedF3AiPromptExtrasService"
                else -> tracker.block(BenchmarkForbiddenAiOperation.AI_PROMPT_EXTRAS_SERVICE)
            }
        } as AiPromptExtrasService
    }
}

private class FailClosedF3AiGateway(
    private val tracker: BenchmarkAiViolationTracker,
) : AiGateway {
    override suspend fun generateImage(
        prompt: String,
        modelId: String,
    ): List<ImageGenerationResult> {
        tracker.block(BenchmarkForbiddenAiOperation.AI_GATEWAY)
    }

    override suspend fun generateImageWithProvider(
        prompt: String,
        provider: ProviderSettings,
        modelId: String,
    ): List<ImageGenerationResult> {
        tracker.block(BenchmarkForbiddenAiOperation.AI_GATEWAY)
    }

    override suspend fun editImage(
        prompt: String,
        images: List<MessageAttachment>,
        modelId: String,
    ): List<ImageGenerationResult> {
        tracker.block(BenchmarkForbiddenAiOperation.AI_GATEWAY)
    }

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String,
        promptEnvelope: PromptEnvelope,
        toolingOptions: GatewayToolingOptions,
    ): AssistantReply {
        tracker.block(BenchmarkForbiddenAiOperation.AI_GATEWAY)
    }

    override fun sendMessageStream(
        messages: List<ChatMessage>,
        systemPrompt: String,
        promptMode: PromptMode,
        promptEnvelope: PromptEnvelope,
        toolingOptions: GatewayToolingOptions,
    ): Flow<ChatStreamEvent> {
        tracker.block(BenchmarkForbiddenAiOperation.AI_GATEWAY)
    }

    override fun parseAssistantSpecialOutput(
        content: String,
        existingParts: List<ChatMessagePart>,
        statusCardsEnabled: Boolean,
        hideStatusBlocksInBubble: Boolean,
    ): ParsedAssistantSpecialOutput {
        return parseAssistantSpecialOutputLocally(
            content = content,
            existingParts = existingParts,
            statusCardsEnabled = statusCardsEnabled,
            hideStatusBlocksInBubble = hideStatusBlocksInBubble,
        )
    }
}
