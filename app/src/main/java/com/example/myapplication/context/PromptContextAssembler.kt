package com.example.myapplication.context

import com.example.myapplication.data.repository.context.EmptyWorldBookRepository
import com.example.myapplication.data.repository.context.EmptyMemoryRepository
import com.example.myapplication.data.repository.context.EmptyConversationSummaryRepository
import com.example.myapplication.data.repository.context.EmptyPresetRepository
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.PresetRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.data.repository.phone.EmptyPhoneSnapshotRepository
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ContextGovernanceItem
import com.example.myapplication.model.ContextLogSection
import com.example.myapplication.model.ContextLogSourceType
import com.example.myapplication.model.DEFAULT_PRESET_ID
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryInjectionPosition
import com.example.myapplication.model.MemoryPromptDefaults
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.toPlainText

data class PromptContextResult(
    val systemPrompt: String,
    val promptEnvelope: PromptEnvelope = PromptEnvelope(),
    val activePresetId: String = "",
    val activePresetName: String = "",
    val debugDump: String = "",
    val summaryCoveredMessageCount: Int = 0,
    val summarySegmentCount: Int = 0,
    val worldBookHitCount: Int = 0,
    val memoryInjectionCount: Int = 0,
    val summaryPreview: String = "",
    val worldBookItems: List<ContextGovernanceItem> = emptyList(),
    val memoryItems: List<ContextGovernanceItem> = emptyList(),
    val contextSections: List<ContextLogSection> = emptyList(),
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
    private val presetRepository: PresetRepository = EmptyPresetRepository,
    private val presetPromptRenderer: PresetPromptRenderer = PresetPromptRenderer(),
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
            entries = worldBookRepository.listAccessibleEnabledEntries(
                assistant = assistant,
                conversation = conversation,
            ),
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
        val recentSummarySegments = conversationSummaryRepository.listSummarySegments(conversation.id)
            .filter { segment -> segment.summary.isNotBlank() }
            .sortedWith(compareBy({ it.startCreatedAt }, { it.endCreatedAt }))
            .takeLast(RECENT_SUMMARY_SEGMENT_LIMIT)
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
        val memorySection = formatMemorySection(
            entries = selectedMemories,
            promptMode = promptMode,
            injectionPromptOverride = settings.memoryInjectionPrompt,
            userName = resolvedUserName,
            characterName = resolvedCharacterName,
        )
        val assistantSystemPromptSection = assistant?.systemPrompt
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { prompt ->
                ContextPlaceholderResolver.resolve(
                    text = prompt,
                    userName = resolvedUserName,
                    characterName = resolvedCharacterName,
                )
            }
        val roleSection = formatAssistantRoleSection(
            assistant = assistant,
            promptMode = promptMode,
            userName = resolvedUserName,
            characterName = resolvedCharacterName,
        )
        val userPersonaSection = formatUserPersonaSection(
            userPersonaPrompt = settings.userPersonaPrompt,
            promptMode = promptMode,
            userName = resolvedUserName,
            characterName = resolvedCharacterName,
        )
        val scenarioSection = assistant?.scenario
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { scenario ->
                buildString {
                    append("【场景设定】\n")
                    append(
                        ContextPlaceholderResolver.resolve(
                            text = scenario,
                            userName = resolvedUserName,
                            characterName = resolvedCharacterName,
                        ),
                    )
                }
            }
        val greetingSection = formatGreetingSection(
            greeting = assistant?.greeting,
            userName = resolvedUserName,
            characterName = resolvedCharacterName,
        )
        val exampleDialoguesSection = formatExampleDialoguesSection(
            dialogues = assistant?.exampleDialogues.orEmpty(),
            userName = resolvedUserName,
            characterName = resolvedCharacterName,
        )
        val summarySection = formatSummarySection(
            conversationSummary = conversationSummary,
            promptMode = promptMode,
        )
        val summarySegmentsSection = formatSummarySegmentsSection(
            summarySegments = recentSummarySegments,
            promptMode = promptMode,
        )
        val worldBookSections = formatWorldBookSections(matchedWorldBookEntries)
        val phoneSnapshotSection = formatPhoneSnapshotSection(phoneSnapshotItems)
        val phoneObservationSection = formatPhoneObservationSection(phoneObservation.takeIf { shouldIncludePhoneObservation })
        val phoneContextSection = listOfNotNull(phoneSnapshotSection, phoneObservationSection)
            .joinToString(separator = "\n\n")
            .takeIf { it.isNotBlank() }
        val sections = buildList {
            assistantSystemPromptSection?.let(::add)
            if (settings.memoryInjectionPosition == MemoryInjectionPosition.BEFORE_PROMPT) {
                memorySection?.let(::add)
            }

            roleSection?.let(::add)
            userPersonaSection?.let(::add)
            scenarioSection?.let(::add)
            greetingSection?.let(::add)
            exampleDialoguesSection?.let(::add)
            summarySection?.let(::add)
            summarySegmentsSection?.let(::add)

            addAll(worldBookSections)

            if (settings.memoryInjectionPosition == MemoryInjectionPosition.AFTER_WORLD_BOOK) {
                memorySection?.let(::add)
            }

            phoneSnapshotSection?.let(::add)
            phoneObservationSection?.let(::add)

            if (settings.memoryInjectionPosition == MemoryInjectionPosition.AT_END) {
                memorySection?.let(::add)
            }
        }

        val legacySystemPrompt = sections.joinToString(separator = "\n\n").trim()
        val baseContextSections = buildContextLogSections(
            settings = settings,
            assistant = assistant,
            promptMode = promptMode,
            recentMessages = recentMessages,
            matchedWorldBookEntries = matchedWorldBookEntries,
            selectedMemories = selectedMemories,
            conversationSummary = conversationSummary,
            summarySegments = recentSummarySegments,
            phoneSnapshotItems = phoneSnapshotItems,
            phoneObservation = phoneObservation.takeIf { shouldIncludePhoneObservation },
        )
        val activePresetId = assistant?.defaultPresetId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: settings.defaultPresetId.trim().ifBlank { DEFAULT_PRESET_ID }
        val activePreset = presetRepository.getPreset(activePresetId)
            ?: DEFAULT_PRESET_ID
                .takeIf { activePresetId != it }
                ?.let { presetRepository.getPreset(it) }
        val renderedPreset = activePreset?.let { preset ->
            presetPromptRenderer.render(
                PresetPromptRenderInput(
                    preset = preset,
                    userName = resolvedUserName,
                    characterName = resolvedCharacterName,
                    slotValues = mapOf(
                        "main_prompt" to assistantSystemPromptSection.orEmpty(),
                        "description" to roleSection.orEmpty(),
                        "char_prompt" to assistantSystemPromptSection.orEmpty(),
                        "persona" to userPersonaSection.orEmpty(),
                        "scenario" to scenarioSection.orEmpty(),
                        "example_dialogue" to exampleDialoguesSection.orEmpty(),
                        "summary" to listOfNotNull(summarySection, summarySegmentsSection)
                            .joinToString(separator = "\n\n"),
                        "world_info" to worldBookSections.joinToString(separator = "\n\n"),
                        "long_memory" to memorySection.orEmpty(),
                        "phone_context" to phoneContextSection.orEmpty(),
                        "context" to legacySystemPrompt,
                        "post_history" to "",
                        "status_rules" to "",
                    ),
                ),
            )
        }
        val systemPrompt = renderedPreset?.systemPrompt ?: legacySystemPrompt
        val promptEnvelope = renderedPreset?.promptEnvelope ?: PromptEnvelope()
        val contextSections = if (renderedPreset == null) {
            baseContextSections
        } else {
            mergePresetContextSections(
                presetSections = renderedPreset.contextSections,
                chatHistorySections = baseContextSections.filter { section ->
                    section.sourceType == ContextLogSourceType.CHAT_HISTORY
                },
            )
        }
        return PromptContextResult(
            systemPrompt = systemPrompt,
            promptEnvelope = promptEnvelope,
            activePresetId = activePreset?.id.orEmpty(),
            activePresetName = activePreset?.name.orEmpty(),
            debugDump = buildDebugDump(
                assistant = assistant,
                matchedWorldBookEntries = matchedWorldBookEntries,
                selectedMemories = selectedMemories,
                conversationSummary = conversationSummary,
                summarySegments = recentSummarySegments,
                phoneSnapshotItems = phoneSnapshotItems,
                phoneObservationText = phoneObservation?.eventText.orEmpty(),
                systemPrompt = systemPrompt,
            ),
            summaryCoveredMessageCount = conversationSummary?.coveredMessageCount ?: 0,
            summarySegmentCount = recentSummarySegments.size,
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
            contextSections = contextSections,
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
                normalizePromptContent(
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
                    normalizePromptContent(
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
                    normalizePromptContent(
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
                append(normalizePromptContent(dialogue))
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
                normalizePromptContent(
                    ContextPlaceholderResolver.resolve(
                        text = normalizedPrompt,
                        userName = userName,
                        characterName = characterName,
                    ),
                ),
            )
        }
    }

    private fun formatWorldBookSections(
        entries: List<WorldBookEntry>,
    ): List<String> {
        if (entries.isEmpty()) {
            return emptyList()
        }
        return entries.map { entry ->
            buildString {
                append("【世界书 · ")
                append(entry.title.ifBlank { "未命名条目" })
                append("】\n")
                append(normalizePromptContent(entry.content))
            }
        }
    }

    private fun buildDebugDump(
        assistant: Assistant?,
        matchedWorldBookEntries: List<WorldBookEntry>,
        selectedMemories: List<MemoryEntry>,
        conversationSummary: ConversationSummary?,
        summarySegments: List<ConversationSummarySegment>,
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
                    "是（覆盖 ${conversationSummary.coveredMessageCount} 条，分段 ${summarySegments.size} 段）"
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

    private fun formatSummarySegmentsSection(
        summarySegments: List<ConversationSummarySegment>,
        promptMode: PromptMode,
    ): String? {
        val usableSegments = summarySegments.filter { it.summary.isNotBlank() }
        if (usableSegments.isEmpty()) {
            return null
        }
        return buildString {
            append(
                if (promptMode == PromptMode.ROLEPLAY) {
                    "【近期剧情分段】\n"
                } else {
                    "【近期摘要分段】\n"
                },
            )
            append("以下内容来自旧聊天原文的分段压缩，用于承接未随最近原文发送的历史内容：\n")
            usableSegments.forEachIndexed { index, segment ->
                append(index + 1)
                append(". 覆盖 ")
                append(segment.messageCount)
                append(" 条消息：")
                append(normalizePromptContent(segment.summary))
                append('\n')
            }
        }.trim()
    }

    private fun formatMemorySection(
        entries: List<MemoryEntry>,
        promptMode: PromptMode,
        injectionPromptOverride: String = "",
        userName: String = "用户",
        characterName: String = "角色",
    ): String? {
        if (entries.isEmpty()) {
            return null
        }
        if (promptMode == PromptMode.ROLEPLAY) {
            val mentalStateEntries = entries.filter { entry ->
                entry.content.startsWith("【心境】")
            }
            val regularEntries = entries.filterNot { entry ->
                entry.content.startsWith("【心境】")
            }
            val longTermEntries = regularEntries.filter { entry ->
                entry.scopeType == com.example.myapplication.model.MemoryScopeType.ASSISTANT ||
                    entry.scopeType == com.example.myapplication.model.MemoryScopeType.GLOBAL
            }
            val sceneEntries = regularEntries.filter { entry ->
                entry.scopeType == com.example.myapplication.model.MemoryScopeType.CONVERSATION
            }
            return buildString {
                if (longTermEntries.isNotEmpty()) {
                    append("【角色长期记忆】\n")
                    append("以下是角色与用户之间已经稳定成立的长期事实、偏好、关系或约束。")
                    append("回复时必须保持一致，不要与其冲突。\n")
                    longTermEntries.forEach { entry ->
                        append("- ")
                        append(normalizePromptContent(entry.content))
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
                        append(normalizePromptContent(entry.content))
                        append('\n')
                    }
                }
                if (mentalStateEntries.isNotEmpty()) {
                    if (isNotBlank()) {
                        append('\n')
                    }
                    append("【角色当前心境】\n")
                    append("以下是角色此刻的内在状态，用于指导回复的情绪基调和态度方向，而不是直接说出来。\n")
                    mentalStateEntries.forEach { entry ->
                        append("- ")
                        append(normalizePromptContent(entry.content.removePrefix("【心境】").trim()))
                        append('\n')
                    }
                }
            }.trim()
                .takeIf { it.isNotBlank() }
        }
        val templateRaw = injectionPromptOverride.trim()
        val template = templateRaw.ifEmpty { MemoryPromptDefaults.INJECTION_PROMPT_TEMPLATE }
        val memoriesBlock = buildString {
            entries.forEachIndexed { index, entry ->
                append("- ")
                append(normalizePromptContent(entry.content))
                if (index != entries.lastIndex) {
                    append('\n')
                }
            }
        }
        val containsMemoriesToken = MEMORIES_PLACEHOLDER_REGEX.containsMatchIn(template)
        var rendered = template.replace(MEMORIES_PLACEHOLDER_REGEX, memoriesBlock)
        rendered = ContextPlaceholderResolver.resolve(
            text = rendered,
            userName = userName,
            characterName = characterName,
        )
        // 用户模板未声明 memories 占位符时尾部自动追加，避免没把记忆条目喂给模型。
        if (!containsMemoriesToken) {
            rendered = buildString {
                append(rendered.trimEnd())
                append('\n')
                append(memoriesBlock)
            }
        }
        return rendered.trim().takeIf { it.isNotBlank() }
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
                append(normalizePromptContent(item))
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
                    append(normalizePromptContent(finding))
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

    private fun buildContextLogSections(
        settings: AppSettings,
        assistant: Assistant?,
        promptMode: PromptMode,
        recentMessages: List<ChatMessage>,
        matchedWorldBookEntries: List<WorldBookEntry>,
        selectedMemories: List<MemoryEntry>,
        conversationSummary: ConversationSummary?,
        summarySegments: List<ConversationSummarySegment>,
        phoneSnapshotItems: List<String>,
        phoneObservation: com.example.myapplication.model.PhoneObservationState?,
    ): List<ContextLogSection> {
        val userName = settings.resolvedUserDisplayName()
        val characterName = assistant?.name?.trim().orEmpty().ifBlank { "角色" }
        val sections = mutableListOf<ContextLogSection>()

        val memoryLogSection = formatMemorySection(
            entries = selectedMemories,
            promptMode = promptMode,
            injectionPromptOverride = settings.memoryInjectionPrompt,
            userName = userName,
            characterName = characterName,
        )?.let { memoryPrompt ->
            ContextLogSection(
                sourceType = ContextLogSourceType.LONG_MEMORY,
                title = if (promptMode == PromptMode.ROLEPLAY) "长记忆提示词" else "记忆提示词",
                content = memoryPrompt,
            )
        }

        assistant?.systemPrompt
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { prompt ->
                sections += ContextLogSection(
                    sourceType = ContextLogSourceType.SYSTEM_RULE,
                    title = "系统规则",
                    content = normalizePromptContent(
                        ContextPlaceholderResolver.resolve(
                            text = prompt,
                            userName = userName,
                            characterName = characterName,
                        ),
                    ),
                )
            }

        if (settings.memoryInjectionPosition == MemoryInjectionPosition.BEFORE_PROMPT) {
            memoryLogSection?.let(sections::add)
        }

        formatAssistantRoleSection(
            assistant = assistant,
            promptMode = promptMode,
            userName = userName,
            characterName = characterName,
        )?.let { roleCard ->
            sections += ContextLogSection(
                sourceType = ContextLogSourceType.ROLE_CARD,
                title = if (promptMode == PromptMode.ROLEPLAY) "角色卡" else "助手设定",
                content = roleCard,
            )
        }

        formatUserPersonaSection(
            userPersonaPrompt = settings.userPersonaPrompt,
            promptMode = promptMode,
            userName = userName,
            characterName = characterName,
        )?.let { userPersona ->
            sections += ContextLogSection(
                sourceType = ContextLogSourceType.USER_PERSONA,
                title = "用户身份",
                content = userPersona,
            )
        }

        assistant?.scenario
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { scenario ->
                sections += ContextLogSection(
                    sourceType = ContextLogSourceType.ROLE_EXTRAS,
                    title = "场景设定",
                    content = buildString {
                        append("【场景设定】\n")
                        append(
                            ContextPlaceholderResolver.resolve(
                                text = scenario,
                                userName = userName,
                                characterName = characterName,
                            ),
                        )
                    },
                )
            }

        formatGreetingSection(
            greeting = assistant?.greeting,
            userName = userName,
            characterName = characterName,
        )?.let { greeting ->
            sections += ContextLogSection(
                sourceType = ContextLogSourceType.ROLE_EXTRAS,
                title = "开场白参考",
                content = greeting,
            )
        }

        formatExampleDialoguesSection(
            dialogues = assistant?.exampleDialogues.orEmpty(),
            userName = userName,
            characterName = characterName,
        )?.let { exampleDialogues ->
            sections += ContextLogSection(
                sourceType = ContextLogSourceType.ROLE_EXTRAS,
                title = "示例对话",
                content = exampleDialogues,
            )
        }

        formatSummarySection(
            conversationSummary = conversationSummary,
            promptMode = promptMode,
        )?.let { summary ->
            sections += ContextLogSection(
                sourceType = ContextLogSourceType.SUMMARY,
                title = if (promptMode == PromptMode.ROLEPLAY) "剧情摘要" else "对话摘要",
                content = summary,
            )
        }

        formatSummarySegmentsSection(
            summarySegments = summarySegments,
            promptMode = promptMode,
        )?.let { summarySegmentsSection ->
            sections += ContextLogSection(
                sourceType = ContextLogSourceType.SUMMARY,
                title = if (promptMode == PromptMode.ROLEPLAY) "近期剧情分段" else "近期摘要分段",
                content = summarySegmentsSection,
            )
        }

        matchedWorldBookEntries.forEach { entry ->
            sections += ContextLogSection(
                sourceType = ContextLogSourceType.WORLD_BOOK,
                title = entry.title.ifBlank { "未命名世界书条目" },
                content = normalizePromptContent(entry.content),
            )
        }

        if (settings.memoryInjectionPosition == MemoryInjectionPosition.AFTER_WORLD_BOOK) {
            memoryLogSection?.let(sections::add)
        }

        formatPhoneSnapshotSection(phoneSnapshotItems)?.let { phoneSnapshot ->
            sections += ContextLogSection(
                sourceType = ContextLogSourceType.PHONE_CONTEXT,
                title = "手机快照",
                content = phoneSnapshot,
            )
        }

        formatPhoneObservationSection(phoneObservation)?.let { observationSection ->
            sections += ContextLogSection(
                sourceType = ContextLogSourceType.PHONE_CONTEXT,
                title = "最近手机查看事件",
                content = observationSection,
            )
        }

        if (settings.memoryInjectionPosition == MemoryInjectionPosition.AT_END) {
            memoryLogSection?.let(sections::add)
        }

        sections += buildChatHistorySections(recentMessages)
        return sections.filter { it.content.isNotBlank() }
    }

    private fun buildChatHistorySections(
        recentMessages: List<ChatMessage>,
    ): List<ContextLogSection> {
        return recentMessages.mapNotNull { message ->
            val messageText = message.parts.toPlainText()
                .ifBlank { message.content }
                .trim()
            if (messageText.isBlank()) {
                return@mapNotNull null
            }
            ContextLogSection(
                sourceType = ContextLogSourceType.CHAT_HISTORY,
                title = when (message.role) {
                    com.example.myapplication.model.MessageRole.USER -> "聊天历史 · 用户"
                    com.example.myapplication.model.MessageRole.ASSISTANT -> "聊天历史 · 角色"
                },
                content = messageText,
            )
        }
    }

    private fun mergePresetContextSections(
        presetSections: List<ContextLogSection>,
        chatHistorySections: List<ContextLogSection>,
    ): List<ContextLogSection> {
        if (chatHistorySections.isEmpty()) {
            return presetSections
        }
        var inserted = false
        return buildList {
            presetSections.forEach { section ->
                add(section)
                if (!inserted && section.sourceType == ContextLogSourceType.PROMPT_PRESET &&
                    section.title.contains("插入点")
                ) {
                    addAll(chatHistorySections)
                    inserted = true
                }
            }
            if (!inserted) {
                addAll(chatHistorySections)
            }
        }
    }

    private fun normalizePromptContent(content: String): String {
        return content.trim()
    }

    private fun limitEntryContent(content: String): String {
        val normalizedContent = content.trim()
        return if (normalizedContent.length <= 600) {
            normalizedContent
        } else {
            normalizedContent.take(600) + "…"
        }
    }

    companion object {
        private const val RECENT_SUMMARY_SEGMENT_LIMIT = 3
        private val MEMORIES_PLACEHOLDER_REGEX =
            Regex("""\{\{\s*memories\s*\}\}""", RegexOption.IGNORE_CASE)
    }
}
