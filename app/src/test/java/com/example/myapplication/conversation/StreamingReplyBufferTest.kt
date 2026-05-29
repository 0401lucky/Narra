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
        buffer.appendContent("a".repeat(220))

        val advanced = buffer.advanceFrame(streamCompleted = true)

        assertTrue(advanced)
        assertEquals(160, buffer.visibleContent().length)
        assertTrue(buffer.hasPending())
    }

    @Test
    fun safeSurrogateBoundary_backsOffWhenSplittingSurrogatePair() {
        // 高代理在 index 1（"a😀" -> a, high, low），切点 2 会拆开代理对，应回退到 1
        assertEquals(1, safeSurrogateBoundary("a😀", 2))
        // 切点 3 落在低代理之后，是完整边界，不回退
        assertEquals(3, safeSurrogateBoundary("a😀", 3))
        // 切点 1 落在高代理之前，是完整边界，不回退
        assertEquals(1, safeSurrogateBoundary("a😀", 1))
        // 整帧只剩半个代理对：切点 1 会拆开，回退到 0（本帧不揭示）
        assertEquals(0, safeSurrogateBoundary("😀", 1))
        // 普通 BMP 字符不受影响
        assertEquals(2, safeSurrogateBoundary("你好", 2))
        // 边界保护：endIndex 为 0 或等于长度时原样返回
        assertEquals(0, safeSurrogateBoundary("a😀", 0))
        assertEquals(3, safeSurrogateBoundary("a😀", 3))
    }

    @Test
    fun advanceFrame_neverRevealsLoneHighSurrogateInContent() {
        val buffer = StreamingReplyBuffer()
        // 15 个普通字符 + Emoji，使批量大小 16 的切点恰好落在代理对中间
        val source = "a".repeat(15) + "😀" + "你好🎉世界"
        buffer.appendContent(source)

        while (buffer.hasPending()) {
            buffer.advanceFrame(streamCompleted = false)
            val visible = buffer.visibleContent()
            assertFalse(
                "可见内容不应包含替换字符 U+FFFD: $visible",
                visible.contains('�'),
            )
            assertFalse(
                "可见内容末尾不应是孤立高代理: $visible",
                visible.isNotEmpty() && Character.isHighSurrogate(visible.last()),
            )
        }

        assertEquals(source, buffer.visibleContent())
    }

    @Test
    fun advanceFrame_neverRevealsLoneHighSurrogateInReasoning() {
        val buffer = StreamingReplyBuffer()
        val source = "a".repeat(15) + "😀" + "推理🎉过程"
        buffer.startReasoningStep(stepId = "reasoning-1", createdAt = 10L)
        buffer.appendReasoningStepDelta(stepId = "reasoning-1", value = source)
        buffer.completeReasoningStep(stepId = "reasoning-1", finishedAt = 40L)

        while (buffer.hasPending()) {
            buffer.advanceFrame(streamCompleted = false)
            val visible = buffer.visibleReasoning()
            assertFalse(
                "可见推理不应包含替换字符 U+FFFD: $visible",
                visible.contains('�'),
            )
            assertFalse(
                "可见推理末尾不应是孤立高代理: $visible",
                visible.isNotEmpty() && Character.isHighSurrogate(visible.last()),
            )
        }

        assertEquals(source, buffer.visibleReasoning())
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
