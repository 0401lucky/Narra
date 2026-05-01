package com.example.myapplication.context

import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.BUILTIN_PRESETS
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
import com.example.myapplication.model.ContextLogSourceType
import com.example.myapplication.model.DEFAULT_PRESET_ID
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.example.myapplication.testutil.FakePresetRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptContextAssemblerTest {
    private val assembler = DefaultPromptContextAssembler()

    @Test
    fun assemble_returnsEmptyPromptWhenAssistantMissing() = runBlocking {
        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = null,
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "你好",
            recentMessages = emptyList(),
        )

        assertEquals("", result.systemPrompt)
        assertTrue(result.debugDump.contains("世界书命中数：0"))
    }

    @Test
    fun assemble_appendsScenarioAfterSystemPrompt() = runBlocking {
        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "侦探助手",
                systemPrompt = "你是一名冷静的侦探顾问。",
                scenario = "当前场景是一间维多利亚风格的事务所。",
                greeting = "晚上好，先把案情讲给我听。",
                exampleDialogues = listOf(
                    "用户：嫌疑人昨晚出现过吗？\n助手：先核对门卫记录，再交叉比对时间线。",
                ),
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "帮我分析案情",
            recentMessages = emptyList(),
        )

        assertTrue(result.systemPrompt.startsWith("你是一名冷静的侦探顾问。"))
        assertTrue(result.systemPrompt.contains("【场景设定】"))
        assertTrue(result.systemPrompt.contains("当前场景是一间维多利亚风格的事务所。"))
        assertTrue(result.systemPrompt.contains("【开场白参考】"))
        assertTrue(result.systemPrompt.contains("晚上好，先把案情讲给我听。"))
        assertTrue(result.systemPrompt.contains("【示例对话】"))
        assertTrue(result.systemPrompt.contains("用户：嫌疑人昨晚出现过吗？"))
        assertTrue(result.debugDump.contains("侦探助手"))
        assertTrue(result.debugDump.contains("世界书命中数：0"))
    }

    @Test
    fun assemble_includesMatchedWorldBookEntries() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            worldBookRepository = object : WorldBookRepository {
                override fun observeEntries() = kotlinx.coroutines.flow.flowOf(emptyList<WorldBookEntry>())

                override suspend fun listEntries(): List<WorldBookEntry> = emptyList()

                override suspend fun listEnabledEntries(): List<WorldBookEntry> {
                    return listOf(
                        WorldBookEntry(
                            id = "entry-1",
                            title = "白塔城",
                            content = "白塔城是北境最大的贸易都会。",
                            keywords = listOf("白塔城"),
                        ),
                    )
                }

                override suspend fun listAccessibleEnabledEntries(
                    assistant: Assistant?,
                    conversation: Conversation?,
                ): List<WorldBookEntry> = listEnabledEntries()

                override suspend fun getEntry(entryId: String): WorldBookEntry? = null

                override suspend fun upsertEntry(entry: WorldBookEntry) = Unit

                override suspend fun deleteEntry(entryId: String) = Unit

                override suspend fun renameBook(bookId: String, newBookName: String) = Unit

                override suspend fun deleteBook(bookId: String) = Unit
            },
        )

        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "设定助手",
                systemPrompt = "你是设定整理专家。",
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "介绍一下白塔城",
            recentMessages = emptyList(),
        )

        assertTrue(result.systemPrompt.contains("【世界书 · 白塔城】"))
        assertTrue(result.systemPrompt.contains("白塔城"))
        assertTrue(result.debugDump.contains("世界书命中数：1"))
        assertTrue(result.debugDump.contains("白塔城：白塔城是北境最大的贸易都会。"))
    }

    @Test
    fun assemble_includesSelectedMemoriesWhenEnabled() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            memoryRepository = object : com.example.myapplication.data.repository.context.MemoryRepository {
                override fun observeEntries() = kotlinx.coroutines.flow.flowOf(emptyList<MemoryEntry>())

                override suspend fun listEntries(): List<MemoryEntry> {
                    return listOf(
                        MemoryEntry(
                            id = "memory-1",
                            scopeType = MemoryScopeType.ASSISTANT,
                            scopeId = "assistant-1",
                            content = "用户喜欢把回复写成短句。",
                            pinned = true,
                            importance = 80,
                        ),
                    )
                }

                override suspend fun findEntryBySourceMessage(
                    scopeType: MemoryScopeType,
                    scopeId: String,
                    sourceMessageId: String,
                ): MemoryEntry? = null

                override suspend fun upsertEntry(entry: MemoryEntry) = Unit

                override suspend fun deleteEntry(entryId: String) = Unit

                override suspend fun pruneToCapacity(capacity: Int) = Unit

                override suspend fun markEntriesUsed(entryIds: List<String>, timestamp: Long) = Unit
            },
        )

        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "记忆助手",
                systemPrompt = "你要保持上下文一致。",
                memoryEnabled = true,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续聊",
            recentMessages = emptyList(),
        )

        assertTrue(result.systemPrompt.contains("【已知信息（记忆）】"))
        assertTrue(result.systemPrompt.contains("稳定事实、偏好或约束"))
        assertTrue(result.systemPrompt.contains("用户喜欢把回复写成短句。"))
        assertTrue(result.debugDump.contains("记忆注入数：1"))
    }

    @Test
    fun assemble_roleplayMemorySectionAddsStrictConsistencyInstruction() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            memoryRepository = object : com.example.myapplication.data.repository.context.MemoryRepository {
                override fun observeEntries() = kotlinx.coroutines.flow.flowOf(emptyList<MemoryEntry>())

                override suspend fun listEntries(): List<MemoryEntry> {
                    return listOf(
                        MemoryEntry(
                            id = "memory-0",
                            scopeType = MemoryScopeType.GLOBAL,
                            scopeId = "",
                            content = "角色一贯会先试探，再决定是否交底。",
                            importance = 75,
                        ),
                        MemoryEntry(
                            id = "memory-1",
                            scopeType = MemoryScopeType.CONVERSATION,
                            scopeId = "c1",
                            content = "当前剧情里，角色已经承认自己知道密门位置。",
                            importance = 80,
                        ),
                    )
                }

                override suspend fun findEntryBySourceMessage(
                    scopeType: MemoryScopeType,
                    scopeId: String,
                    sourceMessageId: String,
                ): MemoryEntry? = null

                override suspend fun upsertEntry(entry: MemoryEntry) = Unit

                override suspend fun deleteEntry(entryId: String) = Unit

                override suspend fun pruneToCapacity(capacity: Int) = Unit

                override suspend fun markEntriesUsed(entryIds: List<String>, timestamp: Long) = Unit
            },
        )

        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "霜岚",
                systemPrompt = "你要始终维持角色设定。",
                memoryEnabled = true,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续逼问她",
            recentMessages = emptyList(),
            promptMode = com.example.myapplication.model.PromptMode.ROLEPLAY,
        )

        assertTrue(result.systemPrompt.contains("【角色长期记忆】"))
        assertTrue(result.systemPrompt.contains("【当前剧情约束】"))
        assertTrue(result.systemPrompt.contains("必须保持一致"))
        assertTrue(result.systemPrompt.contains("不要与其冲突"))
    }

    @Test
    fun assemble_includesConversationSummary() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            conversationSummaryRepository = object : com.example.myapplication.data.repository.context.ConversationSummaryRepository {
                override fun observeSummary(conversationId: String) = kotlinx.coroutines.flow.flowOf(
                    ConversationSummary(
                        conversationId = conversationId,
                        assistantId = "assistant-1",
                        summary = "用户正在调查白塔城的失窃案，已确认嫌疑人与北境商会有关。",
                        coveredMessageCount = 18,
                        updatedAt = 10L,
                    ),
                )

                override fun observeSummaries() = kotlinx.coroutines.flow.flowOf(
                    listOf(
                        ConversationSummary(
                            conversationId = "c1",
                            assistantId = "assistant-1",
                            summary = "用户正在调查白塔城的失窃案，已确认嫌疑人与北境商会有关。",
                            coveredMessageCount = 18,
                            updatedAt = 10L,
                        ),
                    ),
                )

                override suspend fun getSummary(conversationId: String): ConversationSummary? {
                    return ConversationSummary(
                        conversationId = conversationId,
                        assistantId = "assistant-1",
                        summary = "用户正在调查白塔城的失窃案，已确认嫌疑人与北境商会有关。",
                        coveredMessageCount = 18,
                        updatedAt = 10L,
                    )
                }

                override suspend fun listSummaries(): List<ConversationSummary> = emptyList()

                override suspend fun upsertSummary(summary: ConversationSummary) = Unit

                override suspend fun deleteSummary(conversationId: String) = Unit
            },
        )

        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "摘要助手",
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续调查",
            recentMessages = emptyList(),
        )

        assertTrue(result.systemPrompt.contains("【对话摘要】"))
        assertTrue(result.systemPrompt.contains("用户正在调查白塔城的失窃案"))
        assertTrue(result.debugDump.contains("摘要注入：是（覆盖 18 条"))
    }

    @Test
    fun assemble_includesRecentSummarySegmentsInPromptAndContextLog() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            conversationSummaryRepository = FakeConversationSummaryRepository(
                initialSummaries = listOf(
                    ConversationSummary(
                        conversationId = "c1",
                        assistantId = "assistant-1",
                        summary = "总摘要：两人已经进入白塔。",
                        coveredMessageCount = 12,
                    ),
                ),
                initialSegments = listOf(
                    ConversationSummarySegment(
                        id = "seg-1",
                        conversationId = "c1",
                        assistantId = "assistant-1",
                        startMessageId = "m1",
                        endMessageId = "m6",
                        startCreatedAt = 1L,
                        endCreatedAt = 6L,
                        messageCount = 6,
                        summary = "第一段：发现白塔入口。",
                    ),
                    ConversationSummarySegment(
                        id = "seg-2",
                        conversationId = "c1",
                        assistantId = "assistant-1",
                        startMessageId = "m7",
                        endMessageId = "m12",
                        startCreatedAt = 7L,
                        endCreatedAt = 12L,
                        messageCount = 6,
                        summary = "第二段：确认北境商会的暗号。",
                    ),
                ),
            ),
        )

        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "霜岚",
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续调查",
            recentMessages = emptyList(),
            promptMode = PromptMode.ROLEPLAY,
        )

        assertTrue(result.systemPrompt.contains("【剧情摘要】"))
        assertTrue(result.systemPrompt.contains("【近期剧情分段】"))
        assertTrue(result.systemPrompt.contains("第二段：确认北境商会的暗号。"))
        assertEquals(2, result.summarySegmentCount)
        assertTrue(
            result.contextSections.any { section ->
                section.sourceType == ContextLogSourceType.SUMMARY &&
                    section.title == "近期剧情分段" &&
                    section.content.contains("旧聊天原文的分段压缩")
            },
        )
    }

    @Test
    fun assemble_roleplayIncludesDescriptionAndCreatorNotes() = runBlocking {
        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "余罪",
                systemPrompt = "你要一直维持角色口吻。",
                description = "嘴硬、警惕、反应快，遇到逼问时会先试探再回击。",
                creatorNotes = "重点写出余罪那种压着火气、边试探边推进局势的感觉。",
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续逼问他",
            recentMessages = emptyList(),
            promptMode = PromptMode.ROLEPLAY,
        )

        assertTrue(result.systemPrompt.contains("【角色核心设定】"))
        assertTrue(result.systemPrompt.contains("嘴硬、警惕、反应快"))
        assertTrue(result.systemPrompt.contains("【创作者导演说明】"))
        assertTrue(result.systemPrompt.contains("边试探边推进局势"))
    }

    @Test
    fun assemble_withPresetShowsDynamicContentOnlyUnderOriginalSources() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            presetRepository = FakePresetRepository(BUILTIN_PRESETS),
        )

        val result = assembler.assemble(
            settings = AppSettings(
                defaultPresetId = DEFAULT_PRESET_ID,
                userDisplayName = "lucky",
            ),
            assistant = Assistant(
                id = "assistant-1",
                name = "陆承渊",
                systemPrompt = "系统规则正文",
                description = "角色设定正文",
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "今晚为什么来这里？",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "今晚为什么来这里？",
                ),
            ),
            promptMode = PromptMode.ROLEPLAY,
        )

        val presetContent = result.contextSections
            .filter { it.sourceType == ContextLogSourceType.PROMPT_PRESET }
            .joinToString(separator = "\n") { it.content }
        val allContextContent = result.contextSections.joinToString(separator = "\n") { it.content }

        assertEquals(DEFAULT_PRESET_ID, result.activePresetId)
        assertEquals("Narra 默认预设", result.activePresetName)
        assertTrue(result.systemPrompt.contains("角色设定正文"))
        assertTrue(result.systemPrompt.contains("系统规则正文"))
        assertTrue(
            result.contextSections.any {
                it.sourceType == ContextLogSourceType.ROLE_CARD && it.content.contains("角色设定正文")
            },
        )
        assertTrue(
            result.contextSections.any {
                it.sourceType == ContextLogSourceType.ROLE_CARD && it.content.contains("系统规则正文")
            },
        )
        assertTrue(
            result.contextSections.any {
                it.sourceType == ContextLogSourceType.CHAT_HISTORY && it.content.contains("今晚为什么来这里？")
            },
        )
        assertTrue(
            result.contextSections.any {
                it.sourceType == ContextLogSourceType.PROMPT_PRESET && it.title.contains("插入点")
            },
        )
        assertFalse(presetContent.contains("角色设定正文"))
        assertFalse(presetContent.contains("系统规则正文"))
        assertEquals(1, countOccurrences(allContextContent, "角色设定正文"))
        assertEquals(1, countOccurrences(allContextContent, "系统规则正文"))
    }

    @Test
    fun assemble_roleplayIncludesGlobalUserPersonaPrompt() = runBlocking {
        val result = assembler.assemble(
            settings = AppSettings(userPersonaPrompt = "lucky是个表面轻佻、实际很会试探边界的人。"),
            assistant = Assistant(
                id = "assistant-1",
                name = "沈砚清",
                systemPrompt = "你要一直维持角色口吻。",
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续聊",
            recentMessages = emptyList(),
            promptMode = PromptMode.ROLEPLAY,
        )

        assertTrue(result.systemPrompt.contains("【对话者设定】"))
        assertTrue(result.systemPrompt.contains("lucky是个表面轻佻"))
    }

    @Test
    fun assemble_replacesTavernStylePlaceholders() = runBlocking {
        val result = assembler.assemble(
            settings = AppSettings(userDisplayName = "lucky"),
            assistant = Assistant(
                id = "assistant-1",
                name = "金乘",
                systemPrompt = "{{char}} 没有看向{{User}}，只是低声应了一句。",
                scenario = "{{user}} 站在门边，{{char}} 正在整理警服领口。",
                greeting = "晚上好，{{user}}。",
                exampleDialogues = listOf("{{char}}：别再试探我了，{{user}}。"),
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续",
            recentMessages = emptyList(),
        )

        assertTrue(result.systemPrompt.contains("金乘 没有看向lucky"))
        assertTrue(result.systemPrompt.contains("lucky 站在门边，金乘 正在整理警服领口"))
        assertTrue(result.systemPrompt.contains("晚上好，lucky"))
        assertTrue(result.systemPrompt.contains("金乘：别再试探我了，lucky"))
    }

    @Test
    fun assemble_usesUserInjectionPromptWhenProvided() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            memoryRepository = singleMemoryRepository(
                MemoryEntry(
                    id = "memory-1",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                    content = "用户喜欢深夜聊天。",
                    importance = 80,
                ),
            ),
        )

        val result = assembler.assemble(
            settings = AppSettings(
                userDisplayName = "小明",
                memoryInjectionPrompt = "请 {{char}} 在与 {{user}} 互动时遵守以下记忆约束：\n{{memories}}",
            ),
            assistant = Assistant(
                id = "assistant-1",
                name = "白月",
                systemPrompt = "保持上下文一致。",
                memoryEnabled = true,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续",
            recentMessages = emptyList(),
        )

        assertTrue(
            "应应用用户自定义记忆注入模板",
            result.systemPrompt.contains("请 白月 在与 小明 互动时遵守以下记忆约束："),
        )
        assertTrue(result.systemPrompt.contains("- 用户喜欢深夜聊天。"))
        assertTrue(
            "用户自定义模板生效后不应保留默认开头",
            !result.systemPrompt.contains("【已知信息（记忆）】"),
        )
    }

    @Test
    fun assemble_appendsMemoriesWhenInjectionPromptHasNoPlaceholder() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            memoryRepository = singleMemoryRepository(
                MemoryEntry(
                    id = "memory-1",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                    content = "用户偏好简短回复。",
                    importance = 70,
                ),
            ),
        )

        val result = assembler.assemble(
            settings = AppSettings(
                memoryInjectionPrompt = "记忆注入说明：请严格遵守以下记忆。",
            ),
            assistant = Assistant(
                id = "assistant-1",
                name = "助手",
                systemPrompt = "保持一致。",
                memoryEnabled = true,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续",
            recentMessages = emptyList(),
        )

        assertTrue(result.systemPrompt.contains("记忆注入说明：请严格遵守以下记忆。"))
        assertTrue("缺少 {{memories}} 占位符时应自动追加记忆条目", result.systemPrompt.contains("- 用户偏好简短回复。"))
    }

    @Test
    fun assemble_blankInjectionPromptFallsBackToDefaultTemplate() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            memoryRepository = singleMemoryRepository(
                MemoryEntry(
                    id = "memory-1",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                    content = "用户在调研白塔城。",
                    importance = 60,
                ),
            ),
        )

        val result = assembler.assemble(
            settings = AppSettings(memoryInjectionPrompt = "   "),
            assistant = Assistant(
                id = "assistant-1",
                name = "助手",
                systemPrompt = "保持一致。",
                memoryEnabled = true,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续",
            recentMessages = emptyList(),
        )

        assertTrue("纯空白模板应走默认", result.systemPrompt.contains("【已知信息（记忆）】"))
        assertTrue(result.systemPrompt.contains("用户在调研白塔城。"))
    }

    @Test
    fun assemble_roleplayUnaffectedByInjectionPromptOverride() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            memoryRepository = singleMemoryRepository(
                MemoryEntry(
                    id = "memory-1",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                    content = "角色一贯先试探。",
                    importance = 75,
                ),
                MemoryEntry(
                    id = "memory-2",
                    scopeType = MemoryScopeType.CONVERSATION,
                    scopeId = "c1",
                    content = "角色已承认知道密门位置。",
                    importance = 80,
                ),
            ),
        )

        val result = assembler.assemble(
            settings = AppSettings(
                memoryInjectionPrompt = "请遵守：{{memories}}",
            ),
            assistant = Assistant(
                id = "assistant-1",
                name = "霜岚",
                systemPrompt = "维持角色。",
                memoryEnabled = true,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续逼问",
            recentMessages = emptyList(),
            promptMode = PromptMode.ROLEPLAY,
        )

        assertTrue("ROLEPLAY 模式应保持系统三段模板", result.systemPrompt.contains("【角色长期记忆】"))
        assertTrue(result.systemPrompt.contains("【当前剧情约束】"))
        assertTrue(
            "ROLEPLAY 模式不应应用用户注入 prompt",
            !result.systemPrompt.contains("请遵守："),
        )
    }

    @Test
    fun assemble_memoryInjectionPosition_afterWorldBookKeepsLegacyOrder() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            memoryRepository = singleMemoryRepository(
                MemoryEntry(
                    id = "memory-pos-1",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-pos",
                    content = "用户偏好直接给结论。",
                    importance = 70,
                ),
            ),
        )

        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-pos",
                name = "记忆助手",
                systemPrompt = "你要保持上下文一致。",
                memoryEnabled = true,
            ),
            conversation = Conversation(id = "c-pos", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续聊",
            recentMessages = emptyList(),
        )

        val systemPromptIndex = result.systemPrompt.indexOf("你要保持上下文一致。")
        val memoryIndex = result.systemPrompt.indexOf("用户偏好直接给结论。")
        assertTrue("默认应在系统提示词之后插入记忆", memoryIndex > systemPromptIndex)
        assertTrue(memoryIndex > 0)
    }

    @Test
    fun assemble_memoryInjectionPosition_beforePromptMovesMemoryEarly() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            memoryRepository = singleMemoryRepository(
                MemoryEntry(
                    id = "memory-pos-2",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-pos",
                    content = "用户喜欢列表式回答。",
                    importance = 70,
                ),
            ),
        )

        val result = assembler.assemble(
            settings = AppSettings(
                memoryInjectionPosition = com.example.myapplication.model.MemoryInjectionPosition.BEFORE_PROMPT,
            ),
            assistant = Assistant(
                id = "assistant-pos",
                name = "记忆助手",
                systemPrompt = "你是设定整理专家。",
                description = "你需要保持中立。",
                memoryEnabled = true,
            ),
            conversation = Conversation(id = "c-pos", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续聊",
            recentMessages = emptyList(),
        )

        val systemPromptIndex = result.systemPrompt.indexOf("你是设定整理专家。")
        val memoryIndex = result.systemPrompt.indexOf("用户喜欢列表式回答。")
        val roleCardIndex = result.systemPrompt.indexOf("【助手简介】")

        assertTrue("BEFORE_PROMPT 时记忆应在系统提示词之后", memoryIndex > systemPromptIndex)
        assertTrue("BEFORE_PROMPT 时记忆应在角色卡之前", memoryIndex < roleCardIndex)
    }

    @Test
    fun assemble_memoryInjectionPosition_atEndMovesMemoryLast() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            memoryRepository = singleMemoryRepository(
                MemoryEntry(
                    id = "memory-pos-3",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-pos",
                    content = "用户讨厌啰嗦的引子。",
                    importance = 70,
                ),
            ),
        )

        val result = assembler.assemble(
            settings = AppSettings(
                memoryInjectionPosition = com.example.myapplication.model.MemoryInjectionPosition.AT_END,
            ),
            assistant = Assistant(
                id = "assistant-pos",
                name = "记忆助手",
                systemPrompt = "你要保持上下文一致。",
                description = "你必须避免冗长开场白。",
                memoryEnabled = true,
            ),
            conversation = Conversation(id = "c-pos", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续聊",
            recentMessages = emptyList(),
        )

        val roleCardIndex = result.systemPrompt.indexOf("【助手简介】")
        val memoryIndex = result.systemPrompt.indexOf("用户讨厌啰嗦的引子。")

        assertTrue("AT_END 时记忆应位于角色卡之后", memoryIndex > roleCardIndex)
        // 末尾位置：截断最后一行就是记忆。
        val tail = result.systemPrompt.trimEnd().lines().last()
        assertTrue("AT_END 时 systemPrompt 末尾应当属于记忆段", tail.contains("用户讨厌啰嗦的引子。"))
    }

    private fun singleMemoryRepository(
        vararg entries: MemoryEntry,
    ): com.example.myapplication.data.repository.context.MemoryRepository {
        val list = entries.toList()
        return object : com.example.myapplication.data.repository.context.MemoryRepository {
            override fun observeEntries() = kotlinx.coroutines.flow.flowOf(emptyList<MemoryEntry>())

            override suspend fun listEntries(): List<MemoryEntry> = list

            override suspend fun findEntryBySourceMessage(
                scopeType: MemoryScopeType,
                scopeId: String,
                sourceMessageId: String,
            ): MemoryEntry? = null

            override suspend fun upsertEntry(entry: MemoryEntry) = Unit

            override suspend fun deleteEntry(entryId: String) = Unit

            override suspend fun pruneToCapacity(capacity: Int) = Unit

            override suspend fun markEntriesUsed(entryIds: List<String>, timestamp: Long) = Unit
        }
    }

    private fun countOccurrences(text: String, needle: String): Int {
        return Regex(Regex.escape(needle)).findAll(text).count()
    }
}
