package com.example.myapplication.conversation

import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatReasoningStep
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.normalizeChatReasoningSteps
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.reasoningStepsToContent
import com.example.myapplication.model.textMessagePart

private const val streamSmallBatchSize = 16
private const val streamMediumBatchSize = 32
private const val streamLargeBatchSize = 64
private const val streamFinishBatchSize = 160

class StreamingReplyBuffer {
    private sealed interface PendingVisualDelta {
        data class Text(
            val value: StringBuilder,
        ) : PendingVisualDelta

        data class Image(
            val part: ChatMessagePart,
        ) : PendingVisualDelta
    }

    private sealed interface PendingReasoningDelta {
        data class Start(
            val step: ChatReasoningStep,
        ) : PendingReasoningDelta

        data class Delta(
            val stepId: String,
            val value: StringBuilder,
        ) : PendingReasoningDelta

        data class Complete(
            val stepId: String,
            val finishedAt: Long,
        ) : PendingReasoningDelta
    }

    private val lock = Any()
    private val fullContent = StringBuilder()
    private val visibleContent = StringBuilder()
    private val fullParts = mutableListOf<ChatMessagePart>()
    private val visibleParts = mutableListOf<ChatMessagePart>()
    private val pendingVisuals = ArrayDeque<PendingVisualDelta>()
    private val fullReasoningSteps = mutableListOf<ChatReasoningStep>()
    private val visibleReasoningSteps = mutableListOf<ChatReasoningStep>()
    private val pendingReasoning = ArrayDeque<PendingReasoningDelta>()
    private var citations: List<MessageCitation> = emptyList()

    val pendingContentLength: Int
        get() = synchronized(lock) {
            pendingVisuals.sumOf { delta ->
                when (delta) {
                    is PendingVisualDelta.Text -> delta.value.length
                    is PendingVisualDelta.Image -> 0
                }
            }
        }

    val pendingReasoningLength: Int
        get() = synchronized(lock) {
            pendingReasoning.sumOf { delta ->
                when (delta) {
                    is PendingReasoningDelta.Delta -> delta.value.length
                    is PendingReasoningDelta.Start -> 0
                    is PendingReasoningDelta.Complete -> 0
                }
            }
        }

    fun appendContent(value: String) {
        if (value.isEmpty()) return
        synchronized(lock) {
            fullContent.append(value)
            pendingVisuals.addLast(PendingVisualDelta.Text(StringBuilder(value)))
            fullParts.appendText(value)
        }
    }

    fun appendImage(part: ChatMessagePart) {
        val normalizedPart = normalizeChatMessageParts(listOf(part)).firstOrNull() ?: return
        synchronized(lock) {
            fullParts += normalizedPart
            pendingVisuals.addLast(PendingVisualDelta.Image(normalizedPart))
        }
    }

    fun startReasoningStep(
        stepId: String,
        createdAt: Long,
    ) {
        synchronized(lock) {
            if (fullReasoningSteps.any { it.id == stepId }) {
                return
            }
            val step = ChatReasoningStep(
                id = stepId,
                text = "",
                createdAt = createdAt,
                finishedAt = null,
            )
            fullReasoningSteps += step
            pendingReasoning.addLast(PendingReasoningDelta.Start(step))
        }
    }

    fun appendReasoningStepDelta(
        stepId: String,
        value: String,
    ) {
        if (value.isEmpty()) return
        synchronized(lock) {
            val index = fullReasoningSteps.indexOfLast { it.id == stepId }
            if (index == -1) {
                return
            }
            val step = fullReasoningSteps[index]
            fullReasoningSteps[index] = step.copy(
                text = step.text + value,
            )
            pendingReasoning.addLast(
                PendingReasoningDelta.Delta(
                    stepId = stepId,
                    value = StringBuilder(value),
                ),
            )
        }
    }

    fun completeReasoningStep(
        stepId: String,
        finishedAt: Long,
    ) {
        synchronized(lock) {
            val index = fullReasoningSteps.indexOfLast { it.id == stepId }
            if (index == -1) {
                return
            }
            val step = fullReasoningSteps[index]
            fullReasoningSteps[index] = step.copy(finishedAt = finishedAt)
            pendingReasoning.addLast(
                PendingReasoningDelta.Complete(
                    stepId = stepId,
                    finishedAt = finishedAt,
                ),
            )
        }
    }

    fun advanceFrame(streamCompleted: Boolean): Boolean {
        val contentAdvanced = advanceContent(
            step = resolveStreamingBatchSize(
                pendingLength = pendingContentLength,
                streamCompleted = streamCompleted,
            ),
        )
        val reasoningAdvanced = advanceReasoning(
            step = resolveStreamingBatchSize(
                pendingLength = pendingReasoningLength,
                streamCompleted = streamCompleted,
            ),
        )
        return contentAdvanced || reasoningAdvanced
    }

    fun hasPending(): Boolean = synchronized(lock) {
        pendingVisuals.isNotEmpty() || pendingReasoning.isNotEmpty()
    }

    fun content(): String = synchronized(lock) { fullContent.toString() }

    fun reasoning(): String = synchronized(lock) { reasoningStepsToContent(fullReasoningSteps) }

    fun visibleContent(): String = synchronized(lock) { visibleContent.toString() }

    fun visibleReasoning(): String = synchronized(lock) { reasoningStepsToContent(visibleReasoningSteps) }

    fun parts(): List<ChatMessagePart> = synchronized(lock) { fullParts.toList() }

    fun visibleParts(): List<ChatMessagePart> = synchronized(lock) { visibleParts.toList() }

    fun reasoningSteps(): List<ChatReasoningStep> = synchronized(lock) {
        normalizeChatReasoningSteps(fullReasoningSteps)
    }

    fun visibleReasoningSteps(): List<ChatReasoningStep> = synchronized(lock) {
        normalizeChatReasoningSteps(visibleReasoningSteps)
    }

    fun citations(): List<MessageCitation> = synchronized(lock) { citations }

    fun setCitations(value: List<MessageCitation>) {
        synchronized(lock) {
            citations = value.distinctBy(MessageCitation::url)
        }
    }

    private fun advanceContent(step: Int): Boolean = synchronized(lock) {
        drainPendingVisuals(step)
    }

    private fun advanceReasoning(step: Int): Boolean = synchronized(lock) {
        drainPendingReasoning(step)
    }

    private fun drainPendingVisuals(step: Int): Boolean {
        var remaining = step
        var advanced = false

        while (pendingVisuals.isNotEmpty()) {
            when (val next = pendingVisuals.first()) {
                is PendingVisualDelta.Image -> {
                    visibleParts += next.part
                    pendingVisuals.removeFirst()
                    advanced = true
                }

                is PendingVisualDelta.Text -> {
                    if (remaining <= 0) {
                        return advanced
                    }

                    val endIndex = remaining.coerceAtMost(next.value.length)
                    if (endIndex <= 0) {
                        return advanced
                    }

                    val chunk = next.value.substring(0, endIndex)
                    visibleContent.append(chunk)
                    visibleParts.appendText(chunk)
                    next.value.delete(0, endIndex)
                    remaining -= endIndex
                    advanced = true

                    if (next.value.isEmpty()) {
                        pendingVisuals.removeFirst()
                    }

                    if (remaining <= 0) {
                        return advanced
                    }
                }
            }
        }

        return advanced
    }

    private fun drainPendingReasoning(step: Int): Boolean {
        var remaining = step
        var advanced = false

        while (pendingReasoning.isNotEmpty()) {
            when (val next = pendingReasoning.first()) {
                is PendingReasoningDelta.Start -> {
                    if (visibleReasoningSteps.none { it.id == next.step.id }) {
                        visibleReasoningSteps += next.step
                        advanced = true
                    }
                    pendingReasoning.removeFirst()
                }

                is PendingReasoningDelta.Complete -> {
                    val index = visibleReasoningSteps.indexOfLast { it.id == next.stepId }
                    if (index != -1) {
                        visibleReasoningSteps[index] = visibleReasoningSteps[index].copy(
                            finishedAt = next.finishedAt,
                        )
                        advanced = true
                    }
                    pendingReasoning.removeFirst()
                }

                is PendingReasoningDelta.Delta -> {
                    if (remaining <= 0) {
                        return advanced
                    }
                    val index = visibleReasoningSteps.indexOfLast { it.id == next.stepId }
                    if (index == -1) {
                        pendingReasoning.removeFirst()
                        continue
                    }
                    val endIndex = remaining.coerceAtMost(next.value.length)
                    if (endIndex <= 0) {
                        return advanced
                    }
                    val chunk = next.value.substring(0, endIndex)
                    val currentStep = visibleReasoningSteps[index]
                    visibleReasoningSteps[index] = currentStep.copy(
                        text = currentStep.text + chunk,
                    )
                    next.value.delete(0, endIndex)
                    remaining -= endIndex
                    advanced = true

                    if (next.value.isEmpty()) {
                        pendingReasoning.removeFirst()
                    }

                    if (remaining <= 0) {
                        return advanced
                    }
                }
            }
        }

        return advanced
    }

    private fun MutableList<ChatMessagePart>.appendText(text: String) {
        if (text.isEmpty()) {
            return
        }

        val lastPart = lastOrNull()
        if (lastPart?.type == ChatMessagePartType.TEXT) {
            this[lastIndex] = lastPart.copy(text = lastPart.text + text)
        } else {
            add(textMessagePart(text))
        }
    }

    private fun resolveStreamingBatchSize(
        pendingLength: Int,
        streamCompleted: Boolean,
    ): Int {
        return when {
            pendingLength <= 0 -> 0
            streamCompleted -> pendingLength.coerceAtMost(streamFinishBatchSize)
            pendingLength >= 200 -> streamLargeBatchSize
            pendingLength >= 80 -> streamMediumBatchSize
            else -> streamSmallBatchSize
        }
    }
}
