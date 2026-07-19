package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetSelectionTest {
    @Test
    fun resolve_prefersScenarioThenAssistantThenGlobal() {
        assertEquals(
            "scenario-preset",
            resolveActivePresetId(
                globalDefaultPresetId = "global-preset",
                assistantDefaultPresetId = "assistant-preset",
                scenarioPresetId = "scenario-preset",
            ),
        )
        assertEquals(
            "assistant-preset",
            resolveActivePresetId(
                globalDefaultPresetId = "global-preset",
                assistantDefaultPresetId = "assistant-preset",
                scenarioPresetId = "",
            ),
        )
        assertEquals(
            "global-preset",
            resolveActivePresetId(
                globalDefaultPresetId = "global-preset",
                assistantDefaultPresetId = "",
                scenarioPresetId = null,
            ),
        )
        assertEquals(
            DEFAULT_PRESET_ID,
            resolveActivePresetId(
                globalDefaultPresetId = "",
                assistantDefaultPresetId = null,
                scenarioPresetId = "   ",
            ),
        )
    }

    @Test
    fun resolveChain_includesFallbackLevelsForMissingPresetLookup() {
        assertEquals(
            listOf("deleted-session", "assistant-preset", "global-preset", DEFAULT_PRESET_ID),
            resolveActivePresetIdChain(
                globalDefaultPresetId = "global-preset",
                assistantDefaultPresetId = "assistant-preset",
                scenarioPresetId = "deleted-session",
            ),
        )
        // 全局已是内置时不重复追加
        assertEquals(
            listOf("assistant-preset", DEFAULT_PRESET_ID),
            resolveActivePresetIdChain(
                globalDefaultPresetId = DEFAULT_PRESET_ID,
                assistantDefaultPresetId = "assistant-preset",
                scenarioPresetId = null,
            ),
        )
    }

    @Test
    fun isScenarioPresetFollowingAssistant_whenBlank() {
        assertTrue(isScenarioPresetFollowingAssistant(""))
        assertTrue(isScenarioPresetFollowingAssistant(null))
        assertTrue(isScenarioPresetFollowingAssistant("   "))
        assertFalse(isScenarioPresetFollowingAssistant("custom"))
    }

    @Test
    fun isAssistantPresetFollowingGlobal_unchanged() {
        assertTrue(
            isAssistantPresetFollowingGlobal(
                globalDefaultPresetId = "global-preset",
                assistantDefaultPresetId = "",
            ),
        )
        assertFalse(
            isAssistantPresetFollowingGlobal(
                globalDefaultPresetId = "global-preset",
                assistantDefaultPresetId = "assistant-preset",
            ),
        )
    }

    @Test
    fun scenarioPresetSummary_coversSourcesAndMissingPreset() {
        val presets = listOf(
            Preset(id = "global-preset", name = "全局默认"),
            Preset(id = "assistant-preset", name = "角色默认"),
            Preset(id = "session-preset", name = "会话专用"),
        )
        assertEquals(
            "会话专用 · 本会话",
            scenarioPresetSummary(
                scenarioPresetId = "session-preset",
                assistantDefaultPresetId = "assistant-preset",
                globalDefaultPresetId = "global-preset",
                presets = presets,
            ),
        )
        assertEquals(
            "已失效，回退默认",
            scenarioPresetSummary(
                scenarioPresetId = "deleted-preset",
                assistantDefaultPresetId = "assistant-preset",
                globalDefaultPresetId = "global-preset",
                presets = presets,
            ),
        )
        assertEquals(
            "角色默认 · 跟随角色",
            scenarioPresetSummary(
                scenarioPresetId = "",
                assistantDefaultPresetId = "assistant-preset",
                globalDefaultPresetId = "global-preset",
                presets = presets,
            ),
        )
        assertEquals(
            "全局默认 · 跟随全局",
            scenarioPresetSummary(
                scenarioPresetId = null,
                assistantDefaultPresetId = "",
                globalDefaultPresetId = "global-preset",
                presets = presets,
            ),
        )
    }
}
