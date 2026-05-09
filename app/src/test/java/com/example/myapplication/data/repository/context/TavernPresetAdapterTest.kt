package com.example.myapplication.data.repository.context

import com.example.myapplication.model.PresetPromptEntryKind
import com.example.myapplication.model.PresetPromptInjectionPosition
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernPresetAdapterTest {
    private val adapter = TavernPresetAdapter()

    @Test
    fun decodeAsBundle_mapsSillyTavernChatPreset() {
        val bundle = adapter.decodeAsBundle(defaultLikeSillyTavernPreset(), "Default.json")
        val preset = bundle?.presets?.single()

        requireNotNull(preset)
        assertEquals("Default", preset.name)
        assertEquals(1.0f, preset.sampler.temperature)
        assertEquals(1.0f, preset.sampler.topP)
        assertEquals(300, preset.sampler.maxOutputTokens)
        assertEquals(null, preset.sampler.topK)
        assertEquals(null, preset.sampler.minP)
        assertEquals(null, preset.sampler.repetitionPenalty)

        val identifiers = preset.entries.map { it.sourceIdentifier }
        assertEquals(
            listOf("main", "worldInfoBefore", "charDescription", "chatHistory", "jailbreak"),
            identifiers,
        )
        val charDescription = preset.entries.first { it.sourceIdentifier == "charDescription" }
        assertTrue(charDescription.marker)
        assertEquals(PresetPromptEntryKind.CHARACTER_DESCRIPTION, charDescription.kind)
        assertEquals("{{description}}", charDescription.content)
        assertFalse("100000 全局顺序不应带入 100001 的 personaDescription", "personaDescription" in identifiers)
    }

    @Test
    fun decodeAsBundle_preservesUnknownFieldsAndAbsoluteInjection() {
        val raw = """
            {
              "name": "绝对插入预设",
              "temperature": 0.7,
              "custom_root": "keep",
              "prompts": [
                {
                  "identifier": "memoryHint",
                  "name": "Memory Hint",
                  "role": "system",
                  "content": "插在最近历史前。",
                  "enabled": true,
                  "injection_position": 1,
                  "injection_depth": 1,
                  "injection_order": 200,
                  "injection_trigger": ["normal", "continue"],
                  "custom_prompt": 42
                },
                {
                  "identifier": "chatHistory",
                  "name": "Chat History",
                  "marker": true,
                  "enabled": true
                }
              ],
              "prompt_order": [
                {
                  "character_id": 100000,
                  "order": [
                    { "identifier": "memoryHint", "enabled": true },
                    { "identifier": "chatHistory", "enabled": true }
                  ],
                  "custom_order": "kept"
                }
              ]
            }
        """.trimIndent()

        val preset = adapter.decodeAsBundle(raw, "absolute.json")!!.presets.single()
        val entry = preset.entries.first { it.sourceIdentifier == "memoryHint" }

        assertEquals(PresetPromptInjectionPosition.ABSOLUTE, entry.injectionPosition)
        assertEquals(1, entry.injectionDepth)
        assertEquals(200, entry.injectionOrder)
        assertEquals(listOf("normal", "continue"), entry.injectionTriggers)
        assertTrue(entry.extrasJson.contains("custom_prompt"))
        assertTrue(preset.compatMetadata.rootExtrasJson.contains("custom_root"))
        assertTrue(preset.compatMetadata.promptOrderExtrasJson.contains("custom_order"))
    }

    @Test
    fun encodePreset_roundTripsKeyFields() {
        val preset = adapter.decodeAsBundle(defaultLikeSillyTavernPreset(), "Default.json")!!.presets.single()

        val exported = adapter.encodePreset(preset)
        val root = JsonParser.parseString(exported).asJsonObject
        val roundTripPreset = adapter.decodeAsBundle(exported, "roundtrip.json")!!.presets.single()

        assertTrue(root.has("prompts"))
        assertTrue(root.has("prompt_order"))
        assertNotNull(root.getAsJsonArray("prompts").firstOrNull {
            it.asJsonObject.get("identifier").asString == "main"
        })
        assertEquals(
            preset.entries.map { it.sourceIdentifier },
            roundTripPreset.entries.map { it.sourceIdentifier },
        )
        assertEquals(preset.sampler.maxOutputTokens, roundTripPreset.sampler.maxOutputTokens)
    }

    private fun defaultLikeSillyTavernPreset(): String {
        return """
            {
              "chat_completion_source": "openai",
              "name": "Default",
              "temperature": 1,
              "top_p": 1,
              "top_k": 0,
              "min_p": 0,
              "repetition_penalty": 1,
              "openai_max_tokens": 300,
              "prompts": [
                { "identifier": "main", "name": "Main Prompt", "role": "system", "content": "Write as {{char}}.", "system_prompt": true, "enabled": true },
                { "identifier": "worldInfoBefore", "name": "World Info (before)", "marker": true, "enabled": true },
                { "identifier": "charDescription", "name": "Char Description", "marker": true, "enabled": true },
                { "identifier": "personaDescription", "name": "Persona Description", "marker": true, "enabled": true },
                { "identifier": "chatHistory", "name": "Chat History", "marker": true, "enabled": true },
                { "identifier": "jailbreak", "name": "Post-History Instructions", "role": "system", "content": "Stay in character.", "system_prompt": true, "enabled": true }
              ],
              "prompt_order": [
                {
                  "character_id": 100000,
                  "order": [
                    { "identifier": "main", "enabled": true },
                    { "identifier": "worldInfoBefore", "enabled": true },
                    { "identifier": "charDescription", "enabled": true },
                    { "identifier": "chatHistory", "enabled": true },
                    { "identifier": "jailbreak", "enabled": true }
                  ]
                },
                {
                  "character_id": 100001,
                  "order": [
                    { "identifier": "main", "enabled": true },
                    { "identifier": "personaDescription", "enabled": true }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
