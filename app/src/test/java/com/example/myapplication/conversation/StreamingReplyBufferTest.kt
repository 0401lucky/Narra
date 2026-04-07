package com.example.myapplication.conversation

import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatReasoningStep
import com.example.myapplication.model.imageMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingReplyBufferTest {
    @Test
    fun advanceFrame_revealsContentReasoningAndImages() {
        val buffer = StreamingReplyBuffer()

        buffer.startReasoningStep(
            stepId = "reasoning-1",
            createdAt = 10L,
        )
        buffer.appendReasoningStepDelta(
            stepId = "reasoning-1",
            value = "先分析",
        )
        buffer.completeReasoningStep(
            stepId = "reasoning-1",
            finishedAt = 40L,
        )
        buffer.appendContent("你好")
        buffer.appendImage(imageMessagePart(uri = "https://cdn.example.com/test.png"))

        val advanced = buffer.advanceFrame(streamCompleted = false)

        assertTrue(advanced)
        assertEquals("你好", buffer.visibleContent())
        assertEquals("先分析", buffer.visibleReasoning())
        assertEquals(
            listOf(
                ChatReasoningStep(
                    id = "reasoning-1",
                    text = "先分析",
                    createdAt = 10L,
                    finishedAt = 40L,
                ),
            ),
            buffer.visibleReasoningSteps(),
        )
        assertEquals(2, buffer.visibleParts().size)
        assertEquals(ChatMessagePartType.TEXT, buffer.visibleParts()[0].type)
        assertEquals(ChatMessagePartType.IMAGE, buffer.visibleParts()[1].type)
        assertFalse(buffer.hasPending())
    }

    @Test
    fun advanceFrame_usesLargerBatchWhenStreamCompleted() {
        val buffer = StreamingReplyBuffer()
        buffer.appendContent("a".repeat(120))

        val advanced = buffer.advanceFrame(streamCompleted = true)

        assertTrue(advanced)
        assertEquals(96, buffer.visibleContent().length)
        assertTrue(buffer.hasPending())
    }

    @Test
    fun advanceFrame_revealsMultipleReasoningStepsAndCompletion() {
        val buffer = StreamingReplyBuffer()

        buffer.startReasoningStep(stepId = "reasoning-1", createdAt = 100L)
        buffer.appendReasoningStepDelta(stepId = "reasoning-1", value = "**分析目标**\n先看输入。")
        buffer.completeReasoningStep(stepId = "reasoning-1", finishedAt = 180L)
        buffer.startReasoningStep(stepId = "reasoning-2", createdAt = 200L)
        buffer.appendReasoningStepDelta(stepId = "reasoning-2", value = "**拟定回复**\n准备输出。")
        buffer.completeReasoningStep(stepId = "reasoning-2", finishedAt = 260L)

        while (buffer.hasPending()) {
            buffer.advanceFrame(streamCompleted = true)
        }

        assertEquals(2, buffer.reasoningSteps().size)
        assertEquals("reasoning-1", buffer.reasoningSteps()[0].id)
        assertEquals(180L, buffer.reasoningSteps()[0].finishedAt)
        assertEquals("reasoning-2", buffer.reasoningSteps()[1].id)
        assertEquals(260L, buffer.reasoningSteps()[1].finishedAt)
        assertEquals(
            "**分析目标**\n先看输入。\n\n**拟定回复**\n准备输出。",
            buffer.reasoning(),
        )
    }
}
