package com.example.myapplication.context

import com.example.myapplication.data.repository.context.EmptyWorldBookRepository
import com.example.myapplication.data.repository.context.EmptyMemoryRepository
import com.example.myapplication.data.repository.context.EmptyConversationSummaryRepository
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.WorldBookEntry

data class PromptContextResult(
    val systemPrompt: String,
    val debugDump: String = "",
    val summaryCoveredMessageCount: Int = 0,
    val worldBookHitCount: Int = 0,
    val memoryInjectionCount: Int = 0,
)

interface PromptContextAssembler {
    suspend fun assemble(
        settings: AppSettings,
        assistant: Assistant?,
        conversation: Conversation,
        userInputText: String,
        recentMessages: List<ChatMessage>,
        promptMode: PromptMode = PromptMode.CHAT,
    ): PromptContextResult
}

class DefaultPromptContextAssembler(
    private val worldBookRepository: WorldBookRepository = EmptyWorldBookRepository,
    private val worldBookMatcher: WorldBookMatcher = WorldBookMatcher(),
    private val memoryRepository: MemoryRepository = EmptyMemoryRepository,
    private val memorySelector: MemorySelector = MemorySelector(),
    private val conversationSummaryRepository: ConversationSummaryRepository = EmptyConversationSummaryRepository,
) : PromptContextAssembler {
    override suspend fun assemble(
        settings: AppSettings,
        assistant: Assistant?,
        conversation: Conversation,
        userInputText: String,
        recentMessages: List<ChatMessage>,
        promptMode: PromptMode,
    ): PromptContextResult {
        val matchedWorldBookEntries = worldBookMatcher.match(
            entries = worldBookRepository.listEnabledEntries(),
            assistant = assistant,
            conversation = conversation,
            userInputText = userInputText,
            recentMessages = recentMessages,
        ).entries
        val selectedMemories = memorySelector.select(
            entries = memoryRepository.listEntries(),
            assistant = assistant,
            conversation = conversation,
            promptMode = promptMode,
        )
        val conversationSummary = conversationSummaryRepository.getSummary(conversation.id)
        memoryRepository.markEntriesUsed(
            entryIds = selectedMemories.map { it.id },
            timestamp = System.currentTimeMillis(),
        )
        val sections = buildList {
            assistant?.systemPrompt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::add)

            assistant?.scenario
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { scenario ->
                    add(
                        buildString {
                            append("【场景设定】\n")
                            append(scenario)
                        },
                    )
                }

            formatGreetingSection(assistant?.greeting)
                ?.let(::add)

            formatExampleDialoguesSection(assistant?.exampleDialogues.orEmpty())
                ?.let(::add)

            formatSummarySection(conversationSummary)
                ?.let(::add)

            formatWorldBookSection(matchedWorldBookEntries)
                ?.let(::add)

            formatMemorySection(selectedMemories)
                ?.let(::add)
        }

        val systemPrompt = sections.joinToString(separator = "\n\n").trim()
        return PromptContextResult(
            systemPrompt = systemPrompt,
            debugDump = buildDebugDump(
                assistant = assistant,
                matchedWorldBookEntries = matchedWorldBookEntries,
                selectedMemories = selectedMemories,
                conversationSummary = conversationSummary,
                systemPrompt = systemPrompt,
            ),
            summaryCoveredMessageCount = conversationSummary?.coveredMessageCount ?: 0,
            worldBookHitCount = matchedWorldBookEntries.size,
            memoryInjectionCount = selectedMemories.size,
        )
    }

    private fun formatGreetingSection(
        greeting: String?,
    ): String? {
        val greetingText = greeting
            ?.trim()
            .orEmpty()
        if (greetingText.isBlank()) {
            return null
        }
        return buildString {
            append("【开场白参考】\n")
            append(limitEntryContent(greetingText))
        }
    }

    private fun formatExampleDialoguesSection(
        dialogues: List<String>,
    ): String? {
        val normalizedDialogues = dialogues
            .mapNotNull { dialogue ->
                dialogue.trim().takeIf { it.isNotEmpty() }
            }
        if (normalizedDialogues.isEmpty()) {
            return null
        }
        return buildString {
            append("【示例对话】")
            normalizedDialogues.forEachIndexed { index, dialogue ->
                append("\n示例 ")
                append(index + 1)
                append("：\n")
                append(limitEntryContent(dialogue))
            }
        }
    }

    private fun formatWorldBookSection(
        entries: List<WorldBookEntry>,
    ): String? {
        if (entries.isEmpty()) {
            return null
        }
        return buildString {
            append("【背景设定（世界书）】")
            entries.forEach { entry ->
                append("\n- ")
                append(entry.title.ifBlank { "未命名条目" })
                append("：")
                append(limitEntryContent(entry.content))
            }
        }
    }

    private fun buildDebugDump(
        assistant: Assistant?,
        matchedWorldBookEntries: List<WorldBookEntry>,
        selectedMemories: List<MemoryEntry>,
        conversationSummary: ConversationSummary?,
        systemPrompt: String,
    ): String {
        return buildString {
            append("【上下文调试】")
            append("\n- 助手：")
            append(assistant?.name?.ifBlank { assistant.id }.orEmpty().ifBlank { "未选择" })
            append("\n- 摘要注入：")
            append(
                if (conversationSummary?.summary?.isNotBlank() == true) {
                    "是（覆盖 ${conversationSummary.coveredMessageCount} 条）"
                } else {
                    "否"
                },
            )
            append("\n- 世界书命中数：")
            append(matchedWorldBookEntries.size)
            matchedWorldBookEntries.forEach { entry ->
                append("\n  • ")
                append(entry.title.ifBlank { entry.id })
            }
            append("\n- 记忆注入数：")
            append(selectedMemories.size)
            selectedMemories.forEach { entry ->
                append("\n  • ")
                append(limitEntryContent(entry.content))
            }
            if (systemPrompt.isNotBlank()) {
                append("\n\n")
                append(systemPrompt)
            }
        }
    }

    private fun formatSummarySection(
        conversationSummary: ConversationSummary?,
    ): String? {
        val summaryText = conversationSummary?.summary
            ?.trim()
            .orEmpty()
        if (summaryText.isBlank()) {
            return null
        }
        return buildString {
            append("【对话摘要】\n")
            append(summaryText)
        }
    }

    private fun formatMemorySection(
        entries: List<MemoryEntry>,
    ): String? {
        if (entries.isEmpty()) {
            return null
        }
        return buildString {
            append("【已知信息（记忆）】")
            entries.forEach { entry ->
                append("\n- ")
                append(limitEntryContent(entry.content))
            }
        }
    }

    private fun limitEntryContent(content: String): String {
        val normalizedContent = content.trim()
        return if (normalizedContent.length <= 600) {
            normalizedContent
        } else {
            normalizedContent.take(600) + "…"
        }
    }
}
