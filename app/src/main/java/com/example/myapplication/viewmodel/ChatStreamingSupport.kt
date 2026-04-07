package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.StreamingReplyBuffer
import com.example.myapplication.model.ChatMessagePart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal object ChatStreamingSupport {
    private const val STREAM_FRAME_DELAY_MILLIS = 32L

    suspend fun collectStreamingReply(
        streamBuffer: StreamingReplyBuffer,
        streamEvents: Flow<com.example.myapplication.model.ChatStreamEvent>,
        publishFrame: (content: String, reasoning: String, parts: List<ChatMessagePart>) -> Unit,
    ) = coroutineScope {
        var streamCompleted = false
        val uiPumpJob: Job = launch {
            while (true) {
                val advanced = streamBuffer.advanceFrame(streamCompleted)
                if (advanced) {
                    publishFrame(
                        streamBuffer.visibleContent(),
                        streamBuffer.visibleReasoning(),
                        streamBuffer.visibleParts(),
                    )
                }
                if (streamCompleted && !streamBuffer.hasPending()) {
                    break
                }
                delay(STREAM_FRAME_DELAY_MILLIS)
            }
            publishFrame(
                streamBuffer.content(),
                streamBuffer.reasoning(),
                streamBuffer.parts(),
            )
        }

        try {
            streamEvents.collect { event ->
                when (event) {
                    is com.example.myapplication.model.ChatStreamEvent.ContentDelta -> streamBuffer.appendContent(event.value)
                    is com.example.myapplication.model.ChatStreamEvent.ImageDelta -> streamBuffer.appendImage(event.part)
                    is com.example.myapplication.model.ChatStreamEvent.ReasoningDelta -> streamBuffer.appendReasoning(event.value)
                    is com.example.myapplication.model.ChatStreamEvent.Citations -> streamBuffer.setCitations(event.items)
                    com.example.myapplication.model.ChatStreamEvent.Completed -> streamCompleted = true
                }
            }
            streamCompleted = true
            uiPumpJob.join()
        } catch (throwable: Throwable) {
            streamCompleted = true
            uiPumpJob.cancelAndJoin()
            throw throwable
        }
    }
}
