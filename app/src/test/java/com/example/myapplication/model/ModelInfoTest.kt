package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelInfoTest {

    // ── Vision ──

    @Test
    fun vision_gpt4o() {
        assertTrue(inferModelAbilities("gpt-4o").contains(ModelAbility.VISION))
    }

    @Test
    fun vision_gpt4Turbo() {
        assertTrue(inferModelAbilities("gpt-4-turbo").contains(ModelAbility.VISION))
    }

    @Test
    fun vision_gpt5() {
        assertTrue(inferModelAbilities("gpt-5").contains(ModelAbility.VISION))
    }

    @Test
    fun vision_claude3Opus() {
        assertTrue(inferModelAbilities("claude-3-opus-20240229").contains(ModelAbility.VISION))
    }

    @Test
    fun vision_claude4() {
        assertTrue(inferModelAbilities("claude-4-sonnet").contains(ModelAbility.VISION))
    }

    @Test
    fun vision_gemini() {
        assertTrue(inferModelAbilities("gemini-1.5-pro").contains(ModelAbility.VISION))
    }

    @Test
    fun vision_qwenVl() {
        assertTrue(inferModelAbilities("qwen-vl-plus").contains(ModelAbility.VISION))
    }

    @Test
    fun vision_qwenMax() {
        assertTrue(inferModelAbilities("qwen-max").contains(ModelAbility.VISION))
    }

    @Test
    fun vision_qwen3() {
        assertTrue(inferModelAbilities("qwen3-235b").contains(ModelAbility.VISION))
    }

    @Test
    fun vision_o3Mini() {
        assertTrue(inferModelAbilities("o3-mini").contains(ModelAbility.VISION))
    }

    @Test
    fun noVision_gpt35() {
        assertFalse(inferModelAbilities("gpt-3.5-turbo").contains(ModelAbility.VISION))
    }

    @Test
    fun noVision_deepseekChat() {
        assertFalse(inferModelAbilities("deepseek-chat").contains(ModelAbility.VISION))
    }

    // ── Reasoning ──

    @Test
    fun reasoning_o1() {
        assertTrue(inferModelAbilities("o1-preview").contains(ModelAbility.REASONING))
    }

    @Test
    fun reasoning_o3() {
        assertTrue(inferModelAbilities("o3-mini").contains(ModelAbility.REASONING))
    }

    @Test
    fun reasoning_deepseekR1() {
        assertTrue(inferModelAbilities("deepseek-r1").contains(ModelAbility.REASONING))
    }

    @Test
    fun reasoning_qwq() {
        assertTrue(inferModelAbilities("qwq-32b").contains(ModelAbility.REASONING))
    }

    @Test
    fun reasoning_qwen3() {
        assertTrue(inferModelAbilities("qwen3-max").contains(ModelAbility.REASONING))
    }

    @Test
    fun reasoning_claudeOpus() {
        assertTrue(inferModelAbilities("claude-opus-4-20250514").contains(ModelAbility.REASONING))
    }

    @Test
    fun reasoning_claudeSonnet() {
        assertTrue(inferModelAbilities("claude-sonnet-4-20250514").contains(ModelAbility.REASONING))
    }

    @Test
    fun reasoning_claudeHaiku45() {
        assertTrue(inferModelAbilities("claude-haiku-4-5-20251001").contains(ModelAbility.REASONING))
    }

    @Test
    fun reasoning_geminiThinking() {
        assertTrue(inferModelAbilities("gemini-2.5-pro-thinking").contains(ModelAbility.REASONING))
    }

    @Test
    fun reasoning_gemini25() {
        assertTrue(inferModelAbilities("gemini-2.5-pro").contains(ModelAbility.REASONING))
    }

    @Test
    fun reasoning_gpt5() {
        assertTrue(inferModelAbilities("gpt-5").contains(ModelAbility.REASONING))
    }

    @Test
    fun reasoning_grok4Beta() {
        assertTrue(inferModelAbilities("grok-4.2beta").contains(ModelAbility.REASONING))
    }

    @Test
    fun noReasoning_gpt4o() {
        assertFalse(inferModelAbilities("gpt-4o").contains(ModelAbility.REASONING))
    }

    @Test
    fun noReasoning_deepseekChat() {
        assertFalse(inferModelAbilities("deepseek-chat").contains(ModelAbility.REASONING))
    }

    @Test
    fun noReasoning_grok2() {
        assertFalse(inferModelAbilities("grok-2").contains(ModelAbility.REASONING))
    }

    @Test
    fun noReasoning_claude35Sonnet() {
        assertFalse(inferModelAbilities("claude-3.5-sonnet").contains(ModelAbility.REASONING))
    }

    // ── Tool (function calling) ──

    @Test
    fun tool_gpt35Turbo() {
        assertTrue(inferModelAbilities("gpt-3.5-turbo").contains(ModelAbility.TOOL))
    }

    @Test
    fun tool_gpt4o() {
        assertTrue(inferModelAbilities("gpt-4o").contains(ModelAbility.TOOL))
    }

    @Test
    fun tool_claude3Sonnet() {
        assertTrue(inferModelAbilities("claude-3-sonnet").contains(ModelAbility.TOOL))
    }

    @Test
    fun tool_geminiPro() {
        assertTrue(inferModelAbilities("gemini-1.5-pro").contains(ModelAbility.TOOL))
    }

    @Test
    fun tool_qwenMax() {
        assertTrue(inferModelAbilities("qwen-max").contains(ModelAbility.TOOL))
    }

    @Test
    fun tool_deepseekChat() {
        assertTrue(inferModelAbilities("deepseek-chat").contains(ModelAbility.TOOL))
    }

    @Test
    fun tool_mistralLarge() {
        assertTrue(inferModelAbilities("mistral-large-latest").contains(ModelAbility.TOOL))
    }

    @Test
    fun tool_grok() {
        assertTrue(inferModelAbilities("grok-2").contains(ModelAbility.TOOL))
    }

    @Test
    fun tool_llama3() {
        assertTrue(inferModelAbilities("llama-3.1-70b-instruct").contains(ModelAbility.TOOL))
    }

    @Test
    fun tool_moonshot() {
        assertTrue(inferModelAbilities("moonshot-v1-8k").contains(ModelAbility.TOOL))
    }

    @Test
    fun imageGeneration_grokImagineVersionedModel() {
        assertTrue(inferModelAbilities("grok-imagine-1.0").contains(ModelAbility.IMAGE_GENERATION))
    }

    @Test
    fun imageGeneration_gpt5Image() {
        assertTrue(inferModelAbilities("gpt-5-image").contains(ModelAbility.IMAGE_GENERATION))
    }

    @Test
    fun imageEditing_gptImage1() {
        val abilities = inferModelAbilities("gpt-image-1")
        val imageEditingAbility = ModelAbility.valueOf("IMAGE_EDITING")
        assertTrue(abilities.contains(ModelAbility.IMAGE_GENERATION))
        assertTrue(abilities.contains(imageEditingAbility))
    }

    @Test
    fun imageGeneration_gemini25FlashImagePreview() {
        assertTrue(
            inferModelAbilities("google/gemini-2.5-flash-image-preview:free")
                .contains(ModelAbility.IMAGE_GENERATION),
        )
    }

    // ── 组合能力 ──

    @Test
    fun combined_gpt4o_visionAndTool() {
        val abilities = inferModelAbilities("gpt-4o")
        assertTrue(abilities.contains(ModelAbility.VISION))
        assertTrue(abilities.contains(ModelAbility.TOOL))
        assertFalse(abilities.contains(ModelAbility.REASONING))
    }

    @Test
    fun combined_o3_reasoningAndTool() {
        val abilities = inferModelAbilities("o3-mini")
        assertTrue(abilities.contains(ModelAbility.REASONING))
        assertTrue(abilities.contains(ModelAbility.TOOL))
    }

    @Test
    fun combined_claudeSonnet_allThree() {
        val abilities = inferModelAbilities("claude-sonnet-4-20250514")
        assertTrue(abilities.contains(ModelAbility.VISION))
        assertTrue(abilities.contains(ModelAbility.REASONING))
        assertTrue(abilities.contains(ModelAbility.TOOL))
    }

    @Test
    fun combined_qwen3_allThree() {
        val abilities = inferModelAbilities("qwen3-235b")
        assertTrue(abilities.contains(ModelAbility.VISION))
        assertTrue(abilities.contains(ModelAbility.REASONING))
        assertTrue(abilities.contains(ModelAbility.TOOL))
    }

    @Test
    fun noAbilities_unknownModel() {
        val abilities = inferModelAbilities("unknown-model-xyz")
        assertTrue(abilities.isEmpty())
    }

    // ── ProviderSettings resolved 方法 ──

    @Test
    fun providerSettings_resolvedModels_fromModelInfosWhenPresent() {
        val modelInfos = listOf(
            ModelInfo(
                modelId = "gpt-4o",
                abilities = setOf(ModelAbility.VISION),
                abilitiesCustomized = true,
            ),
        )
        val settings = ProviderSettings(
            availableModels = listOf("gpt-4o", "gpt-3.5-turbo"),
            models = modelInfos,
        )
        assertEquals(modelInfos, settings.resolvedModels())
    }

    @Test
    fun providerSettings_resolvedModels_infersFromAvailableModelsWhenModelsNull() {
        val settings = ProviderSettings(
            availableModels = listOf("gpt-4o", "deepseek-r1"),
            models = null,
        )
        val resolved = settings.resolvedModels()
        assertEquals(2, resolved.size)
        assertEquals("gpt-4o", resolved[0].modelId)
        assertTrue(resolved[0].abilities.contains(ModelAbility.VISION))
        assertEquals("deepseek-r1", resolved[1].modelId)
        assertTrue(resolved[1].abilities.contains(ModelAbility.REASONING))
    }

    @Test
    fun providerSettings_resolvedModelIds_prefersModelsField() {
        val settings = ProviderSettings(
            availableModels = listOf("a", "b"),
            models = listOf(ModelInfo(modelId = "x"), ModelInfo(modelId = "y")),
        )
        assertEquals(listOf("x", "y"), settings.resolvedModelIds())
    }

    @Test
    fun providerSettings_resolvedModelIds_fallsBackToAvailableModels() {
        val settings = ProviderSettings(
            availableModels = listOf("a", "b"),
            models = null,
        )
        assertEquals(listOf("a", "b"), settings.resolvedModelIds())
    }

    @Test
    fun providerSettings_resolveModelAbilities_prefersStoredModelInfo() {
        val settings = ProviderSettings(
            models = listOf(
                ModelInfo(
                    modelId = "gpt-4o",
                    abilities = emptySet(),
                    abilitiesCustomized = true,
                ),
            ),
        )

        assertTrue(settings.resolveModelAbilities("gpt-4o").isEmpty())
    }

    @Test
    fun providerSettings_resolveModelAbilities_fallsBackToRegistry() {
        val settings = ProviderSettings(
            availableModels = listOf("grok-4.2beta"),
            models = null,
        )

        assertTrue(settings.resolveModelAbilities("grok-4.2beta").contains(ModelAbility.REASONING))
    }

    @Test
    fun modelInfo_withAbilityOverride_marksCustomized() {
        val modelInfo = inferredModelInfo("gpt-4o").withAbilityOverride(setOf(ModelAbility.REASONING))

        assertTrue(modelInfo.abilitiesCustomized)
        assertEquals(setOf(ModelAbility.REASONING), modelInfo.resolvedAbilities())
    }

    @Test
    fun modelInfo_withAbilityOverride_nullRestoresAutoInference() {
        val modelInfo = ModelInfo(
            modelId = "gpt-4o",
            abilities = setOf(ModelAbility.REASONING),
            abilitiesCustomized = true,
        ).withAbilityOverride(null)

        assertFalse(modelInfo.abilitiesCustomized)
        assertTrue(modelInfo.resolvedAbilities().contains(ModelAbility.VISION))
        assertTrue(modelInfo.resolvedAbilities().contains(ModelAbility.TOOL))
        assertFalse(modelInfo.resolvedAbilities().contains(ModelAbility.REASONING))
    }

    @Test
    fun mergeModelInfosPreservingOverrides_keepsCustomizedAbilities() {
        val merged = mergeModelInfosPreservingOverrides(
            fetchedModels = listOf(inferredModelInfo("grok-4.20-beta")),
            previousModels = listOf(
                ModelInfo(
                    modelId = "grok-4.20-beta",
                    abilities = setOf(ModelAbility.REASONING, ModelAbility.TOOL),
                    abilitiesCustomized = true,
                ),
            ),
        )

        assertEquals(setOf(ModelAbility.REASONING, ModelAbility.TOOL), merged.first().resolvedAbilities())
        assertTrue(merged.first().abilitiesCustomized)
    }
}
