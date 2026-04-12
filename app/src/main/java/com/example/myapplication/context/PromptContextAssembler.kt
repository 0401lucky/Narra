package com.example.myapplication.context

import com.example.myapplication.data.repository.context.EmptyWorldBookRepository
import com.example.myapplication.data.repository.context.EmptyMemoryRepository
import com.example.myapplication.data.repository.context.EmptyConversationSummaryRepository
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.data.repository.phone.EmptyPhoneSnapshotRepository
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ContextGovernanceItem
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.toPlainText

data class PromptContextResult(
    val systemPrompt: String,
    val debugDump: String = "",
    val summaryCoveredMessageCount: Int = 0,
    val worldBookHitCount: Int = 0,
    val memoryInjectionCount: Int = 0,
    val summaryPreview: String = "",
    val worldBookItems: List<ContextGovernanceItem> = emptyList(),
    val memoryItems: List<ContextGovernanceItem> = emptyList(),
)

interface PromptContextAssembler {
    suspend fun assemble(
        settings: AppSettings,
        assistant: Assistant?,
        conversation: Conversation,
        userInputText: String,
        recentMessages: List<ChatMessage>,
        promptMode: PromptMode = PromptMode.CHAT,
        includePhoneSnapshot: Boolean = true,
    ): PromptContextResult
}

class DefaultPromptContextAssembler(
    private val worldBookRepository: WorldBookRepository = EmptyWorldBookRepository,
    private val worldBookMatcher: WorldBookMatcher = WorldBookMatcher(),
    private val memoryRepository: MemoryRepository = EmptyMemoryRepository,
    private val memorySelector: MemorySelector = MemorySelector(),
    private val conversationSummaryRepository: ConversationSummaryRepository = EmptyConversationSummaryRepository,
    private val phoneSnapshotRepository: PhoneSnapshotRepository = EmptyPhoneSnapshotRepository,
    private val phoneSnapshotPromptInjector: PhoneSnapshotPromptInjector = PhoneSnapshotPromptInjector(),
) : PromptContextAssembler {
    override suspend fun assemble(
        settings: AppSettings,
        assistant: Assistant?,
        conversation: Conversation,
        userInputText: String,
        recentMessages: List<ChatMessage>,
        promptMode: PromptMode,
        includePhoneSnapshot: Boolean,
    ): PromptContextResult {
        val resolvedUserName = settings.resolvedUserDisplayName()
        val resolvedCharacterName = assistant?.name?.trim().orEmpty().ifBlank { "角色" }
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
            userInputText = userInputText,
            recentMessages = recentMessages,
        )
        val conversationSummary = conversationSummaryRepository.getSummary(conversation.id)
        val phoneObservation = if (promptMode == PromptMode.ROLEPLAY) {
            phoneSnapshotRepository.getObservation(conversation.id)
        } else {
            null
        }
        val shouldIncludePhoneObservation = phoneObservation != null && (
            !phoneObservation.hasVisibleFeedback || isPhoneObservationTopic(userInputText, recentMessages)
        )
        val phoneSnapshotItems = if (includePhoneSnapshot) {
            phoneSnapshotRepository.getSnapshot(
                conversationId = conversation.id,
                ownerType = PhoneSnapshotOwnerType.CHARACTER,
            )
                ?.let { snapshot ->
                    phoneSnapshotPromptInjector.selectRelevantItems(
                        snapshot = snapshot,
                        userInputText = userInputText,
                        recentMessages = recentMessages,
                    )
                }
                .orEmpty()
        } else {
            emptyList()
        }
        memoryRepository.markEntriesUsed(
            entryIds = selectedMemories.map { it.id },
            timestamp = System.currentTimeMillis(),
        )
        val sections = buildList {
            assistant?.systemPrompt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { prompt ->
                    add(
                        ContextPlaceholderResolver.resolve(
                            text = prompt,
                            userName = resolvedUserName,
                            characterName = resolvedCharacterName,
                        ),
                    )
                }

            formatAssistantRoleSection(
                assistant = assistant,
                promptMode = promptMode,
                userName = resolvedUserName,
                characterName = resolvedCharacterName,
            )?.let(::add)

            formatUserPersonaSection(
                userPersonaPrompt = settings.userPersonaPrompt,
                promptMode = promptMode,
                userName = resolvedUserName,
                characterName = resolvedCharacterName,
            )?.let(::add)

            assistant?.scenario
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { scenario ->
                    add(
                        buildString {
                            append("【场景设定】\n")
                            append(
                                ContextPlaceholderResolver.resolve(
                                    text = scenario,
                                    userName = resolvedUserName,
                                    characterName = resolvedCharacterName,
                                ),
                            )
                        },
                    )
                }

            formatGreetingSection(
                greeting = assistant?.greeting,
                userName = resolvedUserName,
                characterName = resolvedCharacterName,
            )
                ?.let(::add)

            formatExampleDialoguesSection(
                dialogues = assistant?.exampleDialogues.orEmpty(),
                userName = resolvedUserName,
                characterName = resolvedCharacterName,
            )
                ?.let(::add)

            formatSummarySection(
                conversationSummary = conversationSummary,
                promptMode = promptMode,
            )
                ?.let(::add)

            formatWorldBookSection(matchedWorldBookEntries)
                ?.let(::add)

            formatMemorySection(
                entries = selectedMemories,
                promptMode = promptMode,
            )
                ?.let(::add)

            formatPhoneSnapshotSection(phoneSnapshotItems)
                ?.let(::add)

            formatPhoneObservationSection(phoneObservation.takeIf { shouldIncludePhoneObservation })
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
                phoneSnapshotItems = phoneSnapshotItems,
                phoneObservationText = phoneObservation?.eventText.orEmpty(),
                systemPrompt = systemPrompt,
            ),
            summaryCoveredMessageCount = conversationSummary?.coveredMessageCount ?: 0,
            worldBookHitCount = matchedWorldBookEntries.size,
            memoryInjectionCount = selectedMemories.size,
            summaryPreview = conversationSummary?.summary
                ?.trim()
                .orEmpty()
                .let(::limitEntryContent)
                .takeIf { it.isNotBlank() }
                .orEmpty(),
            worldBookItems = matchedWorldBookEntries.map { entry ->
                ContextGovernanceItem(
                    title = entry.title.ifBlank { "未命名条目" },
                    content = limitEntryContent(entry.content).ifBlank { "（无内容）" },
                )
            },
            memoryItems = selectedMemories.map { entry ->
                ContextGovernanceItem(
                    title = resolveMemoryGovernanceTitle(entry, promptMode),
                    content = limitEntryContent(entry.content).ifBlank { "（无内容）" },
                )
            },
        )
    }

    private fun formatGreetingSection(
        greeting: String?,
        userName: String,
        characterName: String,
    ): String? {
        val greetingText = greeting
            ?.trim()
            .orEmpty()
        if (greetingText.isBlank()) {
            return null
        }
        return buildString {
            append("【开场白参考】\n")
            append(
                limitEntryContent(
                    ContextPlaceholderResolver.resolve(
                        text = greetingText,
                        userName = userName,
                        characterName = characterName,
                    ),
                ),
            )
        }
    }

    private fun formatAssistantRoleSection(
        assistant: Assistant?,
        promptMode: PromptMode,
        userName: String,
        characterName: String,
    ): String? {
        val roleDescription = assistant?.description
            ?.trim()
            .orEmpty()
        val creatorNotes = assistant?.creatorNotes
            ?.trim()
            .orEmpty()
        if (roleDescription.isBlank() && (promptMode != PromptMode.ROLEPLAY || creatorNotes.isBlank())) {
            return null
        }
        return buildString {
            if (roleDescription.isNotBlank()) {
                append(
                    if (promptMode == PromptMode.ROLEPLAY) {
                        "【角色核心设定】\n"
                    } else {
                        "【助手简介】\n"
                    },
                )
                append(
                    limitEntryContent(
                        ContextPlaceholderResolver.resolve(
                            text = roleDescription,
                            userName = userName,
                            characterName = characterName,
                        ),
                    ),
                )
            }
            if (promptMode == PromptMode.ROLEPLAY && creatorNotes.isNotBlank()) {
                if (isNotBlank()) {
                    append("\n\n")
                }
                append("【创作者导演说明】\n")
                append("以下内容仅供你在内部把握角色与剧情节奏时遵循，不要直接复述给用户。\n")
                append(
                    limitEntryContent(
                        ContextPlaceholderResolver.resolve(
                            text = creatorNotes,
                            userName = userName,
                            characterName = characterName,
                        ),
                    ),
                )
            }
        }.trim()
            .takeIf { it.isNotBlank() }
    }

    private fun formatExampleDialoguesSection(
        dialogues: List<String>,
        userName: String,
        characterName: String,
    ): String? {
        val normalizedDialogues = dialogues
            .mapNotNull { dialogue ->
                ContextPlaceholderResolver.resolve(
                    text = dialogue.trim(),
                    userName = userName,
                    characterName = characterName,
                ).takeIf { it.isNotEmpty() }
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

    private fun formatUserPersonaSection(
        userPersonaPrompt: String,
        promptMode: PromptMode,
        userName: String,
        characterName: String,
    ): String? {
        val normalizedPrompt = userPersonaPrompt
            .replace("\r\n", "\n")
            .trim()
        if (normalizedPrompt.isBlank() || promptMode != PromptMode.ROLEPLAY) {
            return null
        }
        return buildString {
            append("【对话者设定】\n")
            append(
                limitEntryContent(
                    ContextPlaceholderResolver.resolve(
                        text = normalizedPrompt,
                        userName = userName,
                        characterName = characterName,
                    ),
                ),
            )
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
        phoneSnapshotItems: List<String>,
        phoneObservationText: String,
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
                entry.content
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { content ->
                        append("：")
                        append(limitEntryContent(content))
                    }
            }
            append("\n- 记忆注入数：")
            append(selectedMemories.size)
            selectedMemories.forEach { entry ->
                append("\n  • ")
                append(limitEntryContent(entry.content))
            }
            append("\n- 手机快照命中数：")
            append(phoneSnapshotItems.size)
            phoneSnapshotItems.forEach { item ->
                append("\n  • ")
                append(limitEntryContent(item))
            }
            append("\n- 最近手机查看事件：")
            append(phoneObservationText.ifBlank { "无" })
            if (systemPrompt.isNotBlank()) {
                append("\n\n")
                append(systemPrompt)
            }
        }
    }

    private fun formatSummarySection(
        conversationSummary: ConversationSummary?,
        promptMode: PromptMode,
    ): String? {
        val summaryText = conversationSummary?.summary
            ?.trim()
            .orEmpty()
        if (summaryText.isBlank()) {
            return null
        }
        return buildString {
            append(
                if (promptMode == PromptMode.ROLEPLAY) {
                    "【剧情摘要】\n"
                } else {
                    "【对话摘要】\n"
                },
            )
            append(summaryText)
        }
    }

    private fun formatMemorySection(
        entries: List<MemoryEntry>,
        promptMode: PromptMode,
    ): String? {
        if (entries.isEmpty()) {
            return null
        }
        if (promptMode == PromptMode.ROLEPLAY) {
            val longTermEntries = entries.filter { entry ->
                entry.scopeType == com.example.myapplication.model.MemoryScopeType.ASSISTANT ||
                    entry.scopeType == com.example.myapplication.model.MemoryScopeType.GLOBAL
            }
            val sceneEntries = entries.filter { entry ->
                entry.scopeType == com.example.myapplication.model.MemoryScopeType.CONVERSATION
            }
            return buildString {
                if (longTermEntries.isNotEmpty()) {
                    append("【角色长期记忆】\n")
                    append("以下是角色与用户之间已经稳定成立的长期事实、偏好、关系或约束。")
                    append("回复时必须保持一致，不要与其冲突。\n")
                    longTermEntries.forEach { entry ->
                        append("- ")
                        append(limitEntryContent(entry.content))
                        append('\n')
                    }
                }
                if (sceneEntries.isNotEmpty()) {
                    if (isNotBlank()) {
                        append('\n')
                    }
                    append("【当前剧情约束】\n")
                    append("以下是当前剧情线已经确认的状态、线索、任务进度或关系变化。")
                    append("本轮回复必须延续这些信息，不要忽略或自相矛盾。\n")
                    sceneEntries.forEach { entry ->
                        append("- ")
                        append(limitEntryContent(entry.content))
                        append('\n')
                    }
                }
            }.trim()
                .takeIf { it.isNotBlank() }
        }
        return buildString {
            append("【已知信息（记忆）】\n")
            append("以下信息是当前对话已经确认的稳定事实、偏好或约束，回答时不要与之冲突。")
            entries.forEach { entry ->
                append("\n- ")
                append(limitEntryContent(entry.content))
            }
        }
    }

    private fun formatPhoneSnapshotSection(
        items: List<String>,
    ): String? {
        if (items.isEmpty()) {
            return null
        }
        return buildString {
            append("【查手机已知线索】\n")
            append("以下内容来自当前会话里已经固定下来的手机快照，仅在与本轮话题相关时参考，不要与其冲突。\n")
            items.forEach { item ->
                append("- ")
                append(limitEntryContent(item))
                append('\n')
            }
        }.trim()
    }

    private fun formatPhoneObservationSection(
        observation: com.example.myapplication.model.PhoneObservationState?,
    ): String? {
        if (observation == null) {
            return null
        }
        return buildString {
            append("【最近手机查看事件】\n")
            append(observation.eventText.trim())
            if (observation.keyFindings.isNotEmpty()) {
                append("\n关键发现：\n")
                observation.keyFindings.forEach { finding ->
                    append("- ")
                    append(limitEntryContent(finding))
                    append('\n')
                }
            }
            append("如果这件事已经自然回应过，就不要每轮机械重复；只有在话题相关、情绪顺势或用户追问时再带出来。")
        }.trim()
    }

    private fun isPhoneObservationTopic(
        userInputText: String,
        recentMessages: List<ChatMessage>,
    ): Boolean {
        val sourceText = buildString {
            appendLine(userInputText)
            recentMessages.takeLast(4).forEach { message ->
                appendLine(message.parts.toPlainText().ifBlank { message.content })
            }
        }
        val normalized = sourceText.lowercase()
        return listOf(
            "手机",
            "相册",
            "备忘录",
            "搜索",
            "购物",
            "截图",
            "你在看我手机",
            "你看了我手机",
        ).any { keyword ->
            keyword.lowercase() in normalized
        }
    }

    private fun resolveMemoryGovernanceTitle(
        entry: MemoryEntry,
        promptMode: PromptMode,
    ): String {
        if (promptMode == PromptMode.ROLEPLAY) {
            return when (entry.scopeType) {
                com.example.myapplication.model.MemoryScopeType.CONVERSATION -> "剧情约束"
                com.example.myapplication.model.MemoryScopeType.ASSISTANT,
                com.example.myapplication.model.MemoryScopeType.GLOBAL,
                -> "长期记忆"
            }
        }
        return "记忆 · ${entry.scopeType.label}"
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
