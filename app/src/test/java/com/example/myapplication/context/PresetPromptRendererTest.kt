package com.example.myapplication.context

import com.example.myapplication.model.ContextLogSourceType
import com.example.myapplication.model.Preset
import com.example.myapplication.model.PresetPromptEntry
import com.example.myapplication.model.PresetPromptEntryKind
import com.example.myapplication.model.PresetPromptRole
import com.example.myapplication.model.PresetSamplerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetPromptRendererTest {
    private val renderer = PresetPromptRenderer()

    @Test
    fun render_respectsChatHistoryInsertionPointAndPostHistoryInstruction() {
        val preset = Preset(
            id = "preset-1",
            name = "测试预设",
            sampler = PresetSamplerConfig(temperature = 0.6f),
            stopSequences = listOf("</status>"),
            entries = listOf(
                PresetPromptEntry(
                    id = "main",
                    title = "Main Prompt",
                    role = PresetPromptRole.SYSTEM,
                    kind = PresetPromptEntryKind.MAIN_PROMPT,
                    content = "你是 {{char}}，正在和 {{user}} 对话。",
                    order = 0,
                ),
                PresetPromptEntry(
                    id = "history",
                    title = "Chat History",
                    kind = PresetPromptEntryKind.CHAT_HISTORY,
                    order = 10,
                ),
                PresetPromptEntry(
                    id = "post",
                    title = "Post-History",
                    role = PresetPromptRole.SYSTEM,
                    kind = PresetPromptEntryKind.POST_HISTORY,
                    content = "最后不要跳出角色。",
                    order = 20,
                ),
            ),
        )

        val rendered = renderer.render(
            PresetPromptRenderInput(
                preset = preset,
                userName = "小夏",
                characterName = "林秋",
                slotValues = emptyMap(),
            ),
        )

        assertEquals("你是 林秋，正在和 小夏 对话。", rendered.systemPrompt)
        assertEquals(1, rendered.promptEnvelope.postHistoryMessages.size)
        assertEquals("最后不要跳出角色。", rendered.promptEnvelope.postHistoryMessages.single().content)
        assertEquals(0.6f, rendered.promptEnvelope.sampler.temperature)
        assertEquals(listOf("</status>"), rendered.promptEnvelope.stopSequences)
    }

    @Test
    fun render_skipsDisabledEntriesAndReplacesDynamicSlots() {
        val preset = Preset(
            id = "preset-2",
            name = "测试预设",
            entries = listOf(
                PresetPromptEntry(
                    id = "description",
                    title = "角色描述",
                    kind = PresetPromptEntryKind.CHARACTER_DESCRIPTION,
                    content = "{{description}}",
                    order = 0,
                ),
                PresetPromptEntry(
                    id = "disabled",
                    title = "关闭条目",
                    content = "不应出现",
                    enabled = false,
                    order = 10,
                ),
            ),
        )

        val rendered = renderer.render(
            PresetPromptRenderInput(
                preset = preset,
                userName = "用户",
                characterName = "角色",
                slotValues = mapOf("description" to "【角色核心设定】\n沉稳、敏锐。"),
            ),
        )

        assertTrue(rendered.systemPrompt.contains("沉稳、敏锐"))
        assertFalse(rendered.systemPrompt.contains("不应出现"))
    }

    @Test
    fun render_mapsDynamicPresetSlotsBackToOriginalContextSources() {
        val preset = Preset(
            id = "preset-3",
            name = "测试预设",
            entries = listOf(
                PresetPromptEntry(
                    id = "main",
                    title = "核心任务",
                    role = PresetPromptRole.SYSTEM,
                    kind = PresetPromptEntryKind.MAIN_PROMPT,
                    content = "规则正文",
                    order = 0,
                ),
                PresetPromptEntry(
                    id = "description",
                    title = "角色卡",
                    kind = PresetPromptEntryKind.CHARACTER_DESCRIPTION,
                    content = "{{description}}",
                    order = 10,
                ),
                PresetPromptEntry(
                    id = "world",
                    title = "世界书",
                    kind = PresetPromptEntryKind.WORLD_INFO_BEFORE,
                    content = "{{world_info}}",
                    order = 20,
                ),
                PresetPromptEntry(
                    id = "memory",
                    title = "长记忆",
                    kind = PresetPromptEntryKind.LONG_MEMORY,
                    content = "{{long_memory}}",
                    order = 30,
                ),
                PresetPromptEntry(
                    id = "history",
                    title = "Chat History",
                    kind = PresetPromptEntryKind.CHAT_HISTORY,
                    order = 40,
                ),
                PresetPromptEntry(
                    id = "post",
                    title = "续写规则",
                    kind = PresetPromptEntryKind.POST_HISTORY,
                    content = "不要重复上一条回复。",
                    order = 50,
                ),
            ),
        )

        val rendered = renderer.render(
            PresetPromptRenderInput(
                preset = preset,
                userName = "用户",
                characterName = "角色",
                slotValues = mapOf(
                    "description" to "角色设定正文",
                    "world_info" to "世界书正文",
                    "long_memory" to "长记忆正文",
                ),
            ),
        )

        assertEquals(
            ContextLogSourceType.PROMPT_PRESET,
            rendered.contextSections.first { it.title == "核心任务" }.sourceType,
        )
        assertEquals(
            ContextLogSourceType.ROLE_CARD,
            rendered.contextSections.first { it.title == "角色卡" }.sourceType,
        )
        assertEquals(
            ContextLogSourceType.WORLD_BOOK,
            rendered.contextSections.first { it.title == "世界书" }.sourceType,
        )
        assertEquals(
            ContextLogSourceType.LONG_MEMORY,
            rendered.contextSections.first { it.title == "长记忆" }.sourceType,
        )
        assertEquals(
            ContextLogSourceType.PROMPT_PRESET,
            rendered.contextSections.first { it.title == "续写规则" }.sourceType,
        )

        val insertionPoint = rendered.contextSections.first { it.title.contains("插入点") }
        assertEquals(ContextLogSourceType.PROMPT_PRESET, insertionPoint.sourceType)
        assertEquals(0, insertionPoint.tokenEstimate)
    }
}
