package com.example.myapplication.context

import com.example.myapplication.model.ContextLogSection
import com.example.myapplication.model.ContextLogSourceType
import com.example.myapplication.model.Preset
import com.example.myapplication.model.PresetPromptEntry
import com.example.myapplication.model.PresetPromptEntryKind
import com.example.myapplication.model.PresetPromptRole
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.PromptEnvelopeMessage

data class PresetPromptRenderInput(
    val preset: Preset,
    val userName: String,
    val characterName: String,
    val slotValues: Map<String, String>,
)

data class RenderedPresetPrompt(
    val systemPrompt: String,
    val promptEnvelope: PromptEnvelope,
    val contextSections: List<ContextLogSection>,
)

class PresetPromptRenderer {
    fun render(input: PresetPromptRenderInput): RenderedPresetPrompt {
        val preset = input.preset.normalized()
        val beforeSystemBlocks = mutableListOf<String>()
        val preHistoryMessages = mutableListOf<PromptEnvelopeMessage>()
        val postHistoryMessages = mutableListOf<PromptEnvelopeMessage>()
        val contextSections = mutableListOf<ContextLogSection>()
        var reachedChatHistory = false

        preset.entries
            .filter(PresetPromptEntry::enabled)
            .sortedWith(compareBy<PresetPromptEntry> { it.order }.thenBy { it.title.lowercase() })
            .forEach { entry ->
                if (entry.kind == PresetPromptEntryKind.CHAT_HISTORY) {
                    reachedChatHistory = true
                    contextSections += ContextLogSection(
                        sourceType = ContextLogSourceType.PROMPT_PRESET,
                        title = "${entry.title} · 插入点",
                        content = "聊天历史会从这里插入。",
                        tokenEstimate = 0,
                    )
                    return@forEach
                }

                val renderedContent = renderEntryContent(entry, input)
                if (renderedContent.isBlank()) {
                    return@forEach
                }
                contextSections += ContextLogSection(
                    sourceType = entry.sourceType(),
                    title = entry.title,
                    content = renderedContent,
                )
                if (!reachedChatHistory && entry.role == PresetPromptRole.SYSTEM) {
                    beforeSystemBlocks += renderedContent
                } else {
                    val target = if (reachedChatHistory) postHistoryMessages else preHistoryMessages
                    target += PromptEnvelopeMessage(
                        role = entry.role,
                        content = renderedContent,
                    )
                }
            }

        return RenderedPresetPrompt(
            systemPrompt = beforeSystemBlocks.joinToString(separator = "\n\n").trim(),
            promptEnvelope = PromptEnvelope(
                preHistoryMessages = preHistoryMessages,
                postHistoryMessages = postHistoryMessages,
                sampler = preset.sampler,
                stopSequences = preset.stopSequences,
                statusCardsEnabled = preset.renderConfig.statusCardsEnabled,
                hideStatusBlocksInBubble = preset.renderConfig.hideStatusBlocksInBubble,
            ),
            contextSections = contextSections.filter { it.content.isNotBlank() },
        )
    }

    private fun renderEntryContent(
        entry: PresetPromptEntry,
        input: PresetPromptRenderInput,
    ): String {
        val template = entry.content.ifBlank {
            input.slotValues[entry.kind.defaultSlotKey()].orEmpty()
        }
        return ContextPlaceholderResolver.resolveTemplate(
            text = template,
            values = input.slotValues,
            userName = input.userName,
            characterName = input.characterName,
        ).trim()
    }

    private fun PresetPromptEntryKind.defaultSlotKey(): String {
        return when (this) {
            PresetPromptEntryKind.MAIN_PROMPT -> "main_prompt"
            PresetPromptEntryKind.CONTEXT_TEMPLATE -> "context"
            PresetPromptEntryKind.CHARACTER_DESCRIPTION -> "description"
            PresetPromptEntryKind.CHARACTER_PROMPT -> "char_prompt"
            PresetPromptEntryKind.USER_PERSONA -> "persona"
            PresetPromptEntryKind.SCENARIO -> "scenario"
            PresetPromptEntryKind.EXAMPLE_DIALOGUE -> "example_dialogue"
            PresetPromptEntryKind.WORLD_INFO_BEFORE -> "world_info"
            PresetPromptEntryKind.LONG_MEMORY -> "long_memory"
            PresetPromptEntryKind.SUMMARY -> "summary"
            PresetPromptEntryKind.PHONE_CONTEXT -> "phone_context"
            PresetPromptEntryKind.POST_HISTORY -> "post_history"
            PresetPromptEntryKind.STATUS_RULES -> "status_rules"
            PresetPromptEntryKind.CHAT_HISTORY,
            PresetPromptEntryKind.CUSTOM,
            -> "custom"
        }
    }

    private fun PresetPromptEntry.sourceType(): ContextLogSourceType {
        return when (kind) {
            PresetPromptEntryKind.CHARACTER_DESCRIPTION,
            PresetPromptEntryKind.CHARACTER_PROMPT,
            -> ContextLogSourceType.ROLE_CARD
            PresetPromptEntryKind.USER_PERSONA -> ContextLogSourceType.USER_PERSONA
            PresetPromptEntryKind.SCENARIO,
            PresetPromptEntryKind.EXAMPLE_DIALOGUE,
            -> ContextLogSourceType.ROLE_EXTRAS
            PresetPromptEntryKind.WORLD_INFO_BEFORE -> ContextLogSourceType.WORLD_BOOK
            PresetPromptEntryKind.LONG_MEMORY -> ContextLogSourceType.LONG_MEMORY
            PresetPromptEntryKind.SUMMARY -> ContextLogSourceType.SUMMARY
            PresetPromptEntryKind.PHONE_CONTEXT -> ContextLogSourceType.PHONE_CONTEXT
            PresetPromptEntryKind.MAIN_PROMPT,
            PresetPromptEntryKind.CONTEXT_TEMPLATE,
            PresetPromptEntryKind.POST_HISTORY,
            PresetPromptEntryKind.STATUS_RULES,
            PresetPromptEntryKind.CHAT_HISTORY,
            PresetPromptEntryKind.CUSTOM,
            -> ContextLogSourceType.PROMPT_PRESET
        }
    }
}
