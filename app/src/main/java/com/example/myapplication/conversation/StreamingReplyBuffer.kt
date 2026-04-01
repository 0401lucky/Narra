package com.example.myapplication.conversation

import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart

private const val streamSmallBatchSize = 10
private const val streamMediumBatchSize = 20
private const val streamLargeBatchSize = 40
private const val streamFinishBatchSize = 96

class StreamingReplyBuffer {
    private sealed interface PendingVisualDelta {
        data class Text(
            val value: StringBuilder,
        ) : PendingVisualDelta

        data class Image(
            val part: ChatMessagePart,
        ) : PendingVisualDelta
    }

    private val lock = Any()
    private val fullContent = StringBuilder()
    private val fullReasoning = StringBuilder()
    private val visibleContent = StringBuilder()
    private val visibleReasoning = StringBuilder()
    private val pendingReasoning = StringBuilder()
    private val fullParts = mutableListOf<ChatMessagePart>()
    private val visibleParts = mutableListOf<ChatMessagePart>()
    private val pendingVisuals = ArrayDeque<PendingVisualDelta>()
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
        get() = synchronized(lock) { pendingReasoning.length }

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

    fun appendReasoning(value: String) {
        if (value.isEmpty()) return
        synchronized(lock) {
            fullReasoning.append(value)
            pendingReasoning.append(value)
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

    fun reasoning(): String = synchronized(lock) { fullReasoning.toString() }

    fun visibleContent(): String = synchronized(lock) { visibleContent.toString() }

    fun visibleReasoning(): String = synchronized(lock) { visibleReasoning.toString() }

    fun parts(): List<ChatMessagePart> = synchronized(lock) { fullParts.toList() }

    fun visibleParts(): List<ChatMessagePart> = synchronized(lock) { visibleParts.toList() }

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
        drainLeadingText(
            source = pendingReasoning,
            target = visibleReasoning,
            step = step,
        )
    }

    private fun drainLeadingText(
        source: StringBuilder,
        target: StringBuilder,
        step: Int,
    ): Boolean {
        if (source.isEmpty() || step <= 0) {
            return false
        }
        val endIndex = step.coerceAtMost(source.length)
        target.append(source.substring(0, endIndex))
        source.delete(0, endIndex)
        return true
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
