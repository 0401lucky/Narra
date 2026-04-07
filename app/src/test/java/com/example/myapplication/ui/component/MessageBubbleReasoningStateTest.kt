package com.example.myapplication.ui.component

import com.example.myapplication.model.ChatReasoningStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageBubbleReasoningStateTest {
    @Test
    fun resolveReasoningCardDisplayState_returnsPreviewDuringReasoningPhase() {
        assertEquals(
            ReasoningCardDisplayState.Preview,
            resolveReasoningCardDisplayState(
                hasReasoningContent = true,
                userToggledReasoning = null,
                isReasoningPhase = true,
                reasoningExpandedByDefault = false,
                showThinkingContent = true,
                autoCollapseThinking = true,
            ),
        )
    }

    @Test
    fun resolveReasoningCardDisplayState_returnsCollapsedAfterReasoningWhenAutoCollapseEnabled() {
        assertEquals(
            ReasoningCardDisplayState.Collapsed,
            resolveReasoningCardDisplayState(
                hasReasoningContent = true,
                userToggledReasoning = null,
                isReasoningPhase = false,
                reasoningExpandedByDefault = true,
                showThinkingContent = true,
                autoCollapseThinking = true,
            ),
        )
    }

    @Test
    fun visibleReasoningTimelineSteps_keepsLastTwoWhenCollapsed() {
        val steps = listOf(
            ChatReasoningStep(id = "1", text = "第一步", createdAt = 1L, finishedAt = 2L),
            ChatReasoningStep(id = "2", text = "第二步", createdAt = 3L, finishedAt = 4L),
            ChatReasoningStep(id = "3", text = "第三步", createdAt = 5L, finishedAt = 6L),
        )

        val visible = visibleReasoningTimelineSteps(
            reasoningSteps = steps,
            expanded = false,
        )

        assertEquals(listOf("2", "3"), visible.map { it.id })
    }

    @Test
    fun extractReasoningStepTitle_returnsLastStandaloneBoldLine() {
        assertEquals(
            "拟定回复",
            extractReasoningStepTitle(
                """
                **分析目标**
                先看输入。

                **拟定回复**
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun extractReasoningStepTitle_returnsNullWhenNoStandaloneBoldLine() {
        assertNull(extractReasoningStepTitle("先看输入，再组织回答。"))
    }
}
