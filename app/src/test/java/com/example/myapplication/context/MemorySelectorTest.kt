package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.PromptMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MemorySelectorTest {
    private val selector = MemorySelector()

    @Test
    fun select_returnsEmptyWhenAssistantMemoryDisabled() {
        val result = selector.select(
            entries = listOf(
                MemoryEntry(
                    id = "m1",
                    content = "记忆 A",
                    pinned = true,
                ),
            ),
            assistant = Assistant(id = "assistant-1", memoryEnabled = false),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
        )

        assertEquals(emptyList<MemoryEntry>(), result)
    }

    @Test
    fun select_prioritizesPinnedAndScopedEntries() {
        val result = selector.select(
            entries = listOf(
                MemoryEntry(
                    id = "global",
                    content = "全局记忆",
                    pinned = false,
                    importance = 50,
                    scopeType = MemoryScopeType.GLOBAL,
                ),
                MemoryEntry(
                    id = "assistant",
                    content = "助手记忆",
                    pinned = true,
                    importance = 40,
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                ),
                MemoryEntry(
                    id = "conversation",
                    content = "会话记忆",
                    pinned = true,
                    importance = 30,
                    scopeType = MemoryScopeType.CONVERSATION,
                    scopeId = "c1",
                ),
                MemoryEntry(
                    id = "other",
                    content = "其他助手记忆",
                    pinned = true,
                    importance = 100,
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-2",
                ),
            ),
            assistant = Assistant(
                id = "assistant-1",
                memoryEnabled = true,
                memoryMaxItems = 3,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
        )

        assertEquals(
            listOf("助手记忆", "会话记忆", "全局记忆"),
            result.map { it.content },
        )
    }

    @Test
    fun select_roleplayPrioritizesConversationContextAndRelevance() {
        val result = selector.select(
            entries = listOf(
                MemoryEntry(
                    id = "c1",
                    content = "当前地点在旧钟楼顶层。",
                    scopeType = MemoryScopeType.CONVERSATION,
                    scopeId = "c1",
                    importance = 60,
                ),
                MemoryEntry(
                    id = "c2",
                    content = "当前任务是找到钟楼钥匙。",
                    scopeType = MemoryScopeType.CONVERSATION,
                    scopeId = "c1",
                    importance = 50,
                ),
                MemoryEntry(
                    id = "c3",
                    content = "两人刚刚在雨夜争执后短暂沉默。",
                    scopeType = MemoryScopeType.CONVERSATION,
                    scopeId = "c1",
                    importance = 40,
                ),
                MemoryEntry(
                    id = "a1",
                    content = "角色习惯先试探再给答案。",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                    importance = 70,
                ),
                MemoryEntry(
                    id = "a2",
                    content = "角色很在意用户是否撒谎。",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                    importance = 65,
                ),
                MemoryEntry(
                    id = "g1",
                    content = "世界规则是钟楼夜里鸣响时，密门会被短暂开启。",
                    scopeType = MemoryScopeType.GLOBAL,
                    importance = 55,
                ),
                MemoryEntry(
                    id = "g2",
                    content = "无关的咖啡口味偏好。",
                    scopeType = MemoryScopeType.GLOBAL,
                    importance = 100,
                ),
            ),
            assistant = Assistant(
                id = "assistant-1",
                memoryEnabled = true,
                memoryMaxItems = 6,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            promptMode = PromptMode.ROLEPLAY,
            userInputText = "钟楼刚才响了，密门是不是已经开了？",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    role = MessageRole.USER,
                    content = "我听见钟楼响了，密门应该快开了吧？",
                ),
            ),
        )

        assertEquals(
            listOf(
                "当前地点在旧钟楼顶层。",
                "当前任务是找到钟楼钥匙。",
                "两人刚刚在雨夜争执后短暂沉默。",
                "角色习惯先试探再给答案。",
                "角色很在意用户是否撒谎。",
                "世界规则是钟楼夜里鸣响时，密门会被短暂开启。",
            ),
            result.map { it.content },
        )
    }

    @Test
    fun select_chatPrefersRelevantEntryOverRecentlyUsedWhenTruncated() {
        val result = selector.select(
            entries = listOf(
                MemoryEntry(
                    id = "recent1",
                    content = "用户喜欢喝美式咖啡。",
                    scopeType = MemoryScopeType.GLOBAL,
                    importance = 10,
                    lastUsedAt = 1_000L,
                ),
                MemoryEntry(
                    id = "recent2",
                    content = "用户养了一只橘猫。",
                    scopeType = MemoryScopeType.GLOBAL,
                    importance = 10,
                    lastUsedAt = 900L,
                ),
                MemoryEntry(
                    id = "relevant",
                    content = "用户正在学习吉他指弹。",
                    scopeType = MemoryScopeType.GLOBAL,
                    importance = 10,
                    lastUsedAt = 1L,
                ),
            ),
            assistant = Assistant(
                id = "assistant-1",
                memoryEnabled = true,
                memoryMaxItems = 2,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            promptMode = PromptMode.CHAT,
            userInputText = "帮我推荐一首适合练习指弹吉他的曲子",
        )

        assertEquals(2, result.size)
        assertEquals(true, result.any { it.id == "relevant" })
        assertEquals("relevant", result.first().id)
    }

    @Test
    fun select_chatKeepsPinnedEntryEvenWhenIrrelevant() {
        val result = selector.select(
            entries = listOf(
                MemoryEntry(
                    id = "pinned",
                    content = "用户偏好简洁直接的回答风格。",
                    scopeType = MemoryScopeType.GLOBAL,
                    importance = 5,
                    pinned = true,
                    lastUsedAt = 1L,
                ),
                MemoryEntry(
                    id = "relevant",
                    content = "用户正在学习吉他指弹。",
                    scopeType = MemoryScopeType.GLOBAL,
                    importance = 10,
                    pinned = false,
                    lastUsedAt = 1_000L,
                ),
                MemoryEntry(
                    id = "filler",
                    content = "用户养了一只橘猫。",
                    scopeType = MemoryScopeType.GLOBAL,
                    importance = 50,
                    pinned = false,
                    lastUsedAt = 900L,
                ),
            ),
            assistant = Assistant(
                id = "assistant-1",
                memoryEnabled = true,
                memoryMaxItems = 1,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            promptMode = PromptMode.CHAT,
            userInputText = "帮我推荐一首适合练习指弹吉他的曲子",
        )

        assertEquals(listOf("pinned"), result.map { it.id })
    }

    @Test
    fun select_ignoresAssistantScopedLongTermMemoriesWhenUsingGlobalMemory() {
        val result = selector.select(
            entries = listOf(
                MemoryEntry(
                    id = "assistant",
                    content = "助手隔离记忆",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                    importance = 80,
                ),
                MemoryEntry(
                    id = "global",
                    content = "全局共享记忆",
                    scopeType = MemoryScopeType.GLOBAL,
                    importance = 60,
                ),
            ),
            assistant = Assistant(
                id = "assistant-1",
                memoryEnabled = true,
                useGlobalMemory = true,
                memoryMaxItems = 2,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            promptMode = PromptMode.CHAT,
        )

        assertEquals(listOf("全局共享记忆"), result.map { it.content })
    }
}
