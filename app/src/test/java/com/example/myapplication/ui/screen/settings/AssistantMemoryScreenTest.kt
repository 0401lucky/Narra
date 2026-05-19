package com.example.myapplication.ui.screen.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryScreenTest {
    @Test
    fun shouldShowAssistantMemoryContentToggle_returnsTrueForLongContent() {
        val longContent = "这是一条需要完整查看的长记忆。".repeat(20)

        assertTrue(
            shouldShowAssistantMemoryContentToggle(
                content = longContent,
                collapsedLineCount = 4,
            ),
        )
    }

    @Test
    fun shouldShowAssistantMemoryContentToggle_returnsTrueForManyLines() {
        val multiLineContent = listOf(
            "第一段",
            "第二段",
            "第三段",
            "第四段",
            "第五段",
        ).joinToString(separator = "\n")

        assertTrue(
            shouldShowAssistantMemoryContentToggle(
                content = multiLineContent,
                collapsedLineCount = 4,
            ),
        )
    }

    @Test
    fun shouldShowAssistantMemoryContentToggle_returnsFalseForShortContent() {
        assertFalse(
            shouldShowAssistantMemoryContentToggle(
                content = "短记忆",
                collapsedLineCount = 4,
            ),
        )
    }
}
