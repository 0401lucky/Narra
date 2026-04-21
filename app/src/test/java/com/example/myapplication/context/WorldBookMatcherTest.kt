package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookMatchMode
import com.example.myapplication.model.WorldBookScopeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBookMatcherTest {
    private val matcher = WorldBookMatcher()

    @Test
    fun match_hitsKeywordAndAlwaysActiveEntries() {
        val result = matcher.match(
            entries = listOf(
                WorldBookEntry(
                    id = "entry-1",
                    title = "白塔城",
                    content = "北境最大的贸易都会。",
                    keywords = listOf("白塔城"),
                    priority = 4,
                ),
                WorldBookEntry(
                    id = "entry-2",
                    title = "王都礼仪",
                    content = "进入王都前需持有通行印记。",
                    alwaysActive = true,
                    priority = 1,
                ),
            ),
            assistant = Assistant(id = "assistant-1", worldBookMaxEntries = 8),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "我准备去白塔城做生意",
            recentMessages = emptyList(),
        )

        assertEquals(listOf("王都礼仪", "白塔城"), result.entries.map { it.title })
        assertTrue(result.sourceText.contains("白塔城"))
    }

    @Test
    fun match_filtersByScope() {
        val result = matcher.match(
            entries = listOf(
                WorldBookEntry(
                    id = "global-entry",
                    title = "通用设定",
                    content = "所有会话可用。",
                    alwaysActive = true,
                    scopeType = WorldBookScopeType.GLOBAL,
                ),
                WorldBookEntry(
                    id = "assistant-entry",
                    title = "专属助手设定",
                    content = "仅某个助手可用。",
                    alwaysActive = true,
                    scopeType = WorldBookScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                ),
                WorldBookEntry(
                    id = "conversation-entry",
                    title = "专属会话设定",
                    content = "仅某个会话可用。",
                    alwaysActive = true,
                    scopeType = WorldBookScopeType.CONVERSATION,
                    scopeId = "c1",
                ),
                WorldBookEntry(
                    id = "other-assistant-entry",
                    title = "其他助手设定",
                    content = "不应命中。",
                    alwaysActive = true,
                    scopeType = WorldBookScopeType.ASSISTANT,
                    scopeId = "assistant-2",
                ),
            ),
            assistant = Assistant(id = "assistant-1"),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "继续当前剧情",
                    createdAt = 1L,
                ),
            ),
        )

        assertEquals(
            listOf("通用设定", "专属助手设定", "专属会话设定"),
            result.entries.map { it.title },
        )
    }

    @Test
    fun match_respectsAssistantLimitAndPriority() {
        val result = matcher.match(
            entries = listOf(
                WorldBookEntry(id = "entry-1", title = "低优先级", content = "A", alwaysActive = true, priority = 1),
                WorldBookEntry(id = "entry-2", title = "高优先级", content = "B", alwaysActive = true, priority = 9),
                WorldBookEntry(id = "entry-3", title = "中优先级", content = "C", alwaysActive = true, priority = 5),
            ),
            assistant = Assistant(id = "assistant-1", worldBookMaxEntries = 2),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "",
            recentMessages = emptyList(),
        )

        assertEquals(listOf("高优先级", "中优先级"), result.entries.map { it.title })
    }

    @Test
    fun match_supportsRegexAndSelectiveSecondaryKeywords() {
        val result = matcher.match(
            entries = listOf(
                WorldBookEntry(
                    id = "entry-1",
                    title = "璃珠都市",
                    content = "璃珠都市位于北部沿海。",
                    keywords = listOf("/璃珠(都|城)市/i"),
                    insertionOrder = 10,
                ),
                WorldBookEntry(
                    id = "entry-2",
                    title = "夜巡守则",
                    content = "午夜前后不要靠近旧钟楼。",
                    keywords = listOf("夜巡"),
                    secondaryKeywords = listOf("午夜", "钟楼"),
                    selective = true,
                    insertionOrder = 20,
                ),
            ),
            assistant = Assistant(id = "assistant-1", worldBookMaxEntries = 8),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "今晚夜巡时我会经过璃珠都市的旧钟楼，已经接近午夜。",
            recentMessages = emptyList(),
        )

        assertEquals(listOf("璃珠都市", "夜巡守则"), result.entries.map { it.title })
    }

    @Test
    fun match_supportsCommaSeparatedTavernKeywordsAndPrefersLatestUserInput() {
        val result = matcher.match(
            entries = listOf(
                WorldBookEntry(
                    id = "entry-weapon",
                    title = "余罪的配枪",
                    content = "他随身带着一把黑星手枪。",
                    keywords = listOf("枪, 武器, 掏出"),
                    scopeType = WorldBookScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                ),
            ),
            assistant = Assistant(
                id = "assistant-1",
                worldBookMaxEntries = 8,
                worldBookScanDepth = 0,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "你身上带武器了吗？",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "昨晚那把枪还在你身上吗？",
                    createdAt = 1L,
                ),
            ),
        )

        assertEquals(listOf("余罪的配枪"), result.entries.map { it.title })
        assertTrue(result.sourceText.contains("你身上带武器了吗？"))
    }

    @Test
    fun match_dropsPreviousHitWhenLatestUserInputNoLongerMatches() {
        val result = matcher.match(
            entries = listOf(
                WorldBookEntry(
                    id = "entry-weapon",
                    title = "余罪的配枪",
                    content = "他随身带着一把黑星手枪。",
                    keywords = listOf("枪, 武器, 掏出"),
                    scopeType = WorldBookScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                ),
            ),
            assistant = Assistant(
                id = "assistant-1",
                worldBookMaxEntries = 8,
                worldBookScanDepth = 0,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "今天天气不错。",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "你身上带武器了吗？",
                    createdAt = 1L,
                ),
            ),
        )

        assertTrue(result.entries.isEmpty())
        assertTrue(result.sourceText.contains("今天天气不错"))
    }

    @Test
    fun match_attachableEntryOnlyHitsWhenExplicitlyLinked() {
        val attachableEntry = WorldBookEntry(
            id = "entry-attachable",
            title = "旧港夜路",
            content = "旧港的夜路总有巡逻队出没。",
            keywords = listOf("旧港"),
            scopeType = WorldBookScopeType.ATTACHABLE,
        )

        val withoutLink = matcher.match(
            entries = listOf(attachableEntry),
            assistant = Assistant(id = "assistant-1"),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "今晚要去旧港。",
            recentMessages = emptyList(),
        )
        val withLink = matcher.match(
            entries = listOf(attachableEntry),
            assistant = Assistant(
                id = "assistant-1",
                linkedWorldBookIds = listOf("entry-attachable"),
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "今晚要去旧港。",
            recentMessages = emptyList(),
        )

        assertTrue(withoutLink.entries.isEmpty())
        assertEquals(listOf("旧港夜路"), withLink.entries.map { it.title })
    }

    @Test
    fun match_attachableEntryHitsWhenBookIsLinked() {
        val attachableEntry = WorldBookEntry(
            id = "entry-attachable",
            bookId = "book-1",
            title = "旧港夜路",
            content = "旧港的夜路总有巡逻队出没。",
            keywords = listOf("旧港"),
            sourceBookName = "港区档案",
            scopeType = WorldBookScopeType.ATTACHABLE,
        )

        val result = matcher.match(
            entries = listOf(attachableEntry),
            assistant = Assistant(
                id = "assistant-1",
                linkedWorldBookBookIds = listOf("book-1"),
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "今晚要去旧港。",
            recentMessages = emptyList(),
        )

        assertEquals(listOf("旧港夜路"), result.entries.map { it.title })
    }

    @Test
    fun match_scanDepthZero_onlyConsidersCurrentInput() {
        val entry = WorldBookEntry(
            id = "entry-1",
            title = "旧港夜路",
            content = "A",
            keywords = listOf("旧港"),
        )
        val result = matcher.match(
            entries = listOf(entry),
            assistant = Assistant(id = "assistant-1", worldBookScanDepth = 0),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "今晚看书",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "昨晚去了旧港",
                    createdAt = 1L,
                ),
            ),
        )
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun match_scanDepthTwo_includesPreviousAssistantMessage() {
        val entry = WorldBookEntry(
            id = "entry-1",
            title = "旧港夜路",
            content = "A",
            keywords = listOf("旧港"),
        )
        val result = matcher.match(
            entries = listOf(entry),
            assistant = Assistant(id = "assistant-1", worldBookScanDepth = 2),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续讲故事",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "在旧港夜巷你看到...",
                    createdAt = 1L,
                ),
                ChatMessage(
                    id = "m2",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "然后呢",
                    createdAt = 2L,
                ),
            ),
        )
        assertEquals(listOf("旧港夜路"), result.entries.map { it.title })
    }

    @Test
    fun match_scanDepthOne_onlyIncludesLatestMessage() {
        val entry = WorldBookEntry(
            id = "entry-1",
            title = "白塔城",
            content = "A",
            keywords = listOf("白塔城"),
        )
        val result = matcher.match(
            entries = listOf(entry),
            assistant = Assistant(id = "assistant-1", worldBookScanDepth = 1),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "晚上好",
                    createdAt = 1L,
                ),
                ChatMessage(
                    id = "m2",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "我去白塔城",
                    createdAt = 2L,
                ),
            ),
        )
        assertEquals(listOf("白塔城"), result.entries.map { it.title })
    }

    @Test
    fun match_scanDepthOne_doesNotIncludeEarlierMessages() {
        val entry = WorldBookEntry(
            id = "entry-1",
            title = "白塔城",
            content = "A",
            keywords = listOf("白塔城"),
        )
        val result = matcher.match(
            entries = listOf(entry),
            assistant = Assistant(id = "assistant-1", worldBookScanDepth = 1),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "我去白塔城",
                    createdAt = 1L,
                ),
                ChatMessage(
                    id = "m2",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "晚上好",
                    createdAt = 2L,
                ),
            ),
        )
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun match_regexWithCaseSensitiveFalse_autoAddsIgnoreCase() {
        val entry = WorldBookEntry(
            id = "entry-1",
            title = "匹配 Foo",
            content = "A",
            keywords = listOf("/Foo/"),
            caseSensitive = false,
        )
        val result = matcher.match(
            entries = listOf(entry),
            assistant = Assistant(id = "assistant-1", worldBookScanDepth = 0),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "talking about foo bar",
            recentMessages = emptyList(),
        )
        assertEquals(listOf("匹配 Foo"), result.entries.map { it.title })
    }

    @Test
    fun match_regexWithCaseSensitiveTrue_staysCaseSensitive() {
        val entry = WorldBookEntry(
            id = "entry-1",
            title = "匹配 Foo",
            content = "A",
            keywords = listOf("/Foo/"),
            caseSensitive = true,
        )
        val result = matcher.match(
            entries = listOf(entry),
            assistant = Assistant(id = "assistant-1", worldBookScanDepth = 0),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "talking about foo bar",
            recentMessages = emptyList(),
        )
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun match_regexExplicitFlagIOverridesEntryCaseSensitivity() {
        val entry = WorldBookEntry(
            id = "entry-1",
            title = "匹配 Bar",
            content = "A",
            keywords = listOf("/Bar/i"),
            caseSensitive = true,
        )
        val result = matcher.match(
            entries = listOf(entry),
            assistant = Assistant(id = "assistant-1", worldBookScanDepth = 0),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "bar is here",
            recentMessages = emptyList(),
        )
        assertEquals(listOf("匹配 Bar"), result.entries.map { it.title })
    }

    @Test
    fun match_matchModeContains_keepsSubstringBehavior() {
        val entry = WorldBookEntry(
            id = "entry-1",
            title = "foo 条目",
            content = "A",
            keywords = listOf("foo"),
            caseSensitive = false,
            matchMode = WorldBookMatchMode.CONTAINS,
        )
        val result = matcher.match(
            entries = listOf(entry),
            assistant = Assistant(id = "assistant-1", worldBookScanDepth = 0),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "football league",
            recentMessages = emptyList(),
        )
        assertEquals(listOf("foo 条目"), result.entries.map { it.title })
    }

    @Test
    fun match_matchModeWordCjk_requiresBoundaryForLatin() {
        val entry = WorldBookEntry(
            id = "entry-1",
            title = "foo 条目",
            content = "A",
            keywords = listOf("foo"),
            caseSensitive = false,
            matchMode = WorldBookMatchMode.WORD_CJK,
        )
        val result = matcher.match(
            entries = listOf(entry),
            assistant = Assistant(id = "assistant-1", worldBookScanDepth = 0),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "football league",
            recentMessages = emptyList(),
        )
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun match_matchModeRegex_treatsKeywordAsRegex() {
        val entry = WorldBookEntry(
            id = "entry-1",
            title = "正则条目",
            content = "A",
            keywords = listOf("foo|bar"),
            caseSensitive = false,
            matchMode = WorldBookMatchMode.REGEX,
        )
        val result = matcher.match(
            entries = listOf(entry),
            assistant = Assistant(id = "assistant-1", worldBookScanDepth = 0),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "BAR at dusk",
            recentMessages = emptyList(),
        )
        assertEquals(listOf("正则条目"), result.entries.map { it.title })
    }

    @Test
    fun previewHit_withEmptySourceText_returnsNotMatchedWithReason() {
        val preview = matcher.previewHit(
            entry = WorldBookEntry(id = "e1", title = "t", content = "c", keywords = listOf("白塔城")),
            sourceText = "   ",
        )

        assertFalse(preview.overallMatched)
        assertTrue(preview.primaryHits.isEmpty())
        assertTrue(preview.secondaryHits.isEmpty())
        assertEquals("待测文本为空", preview.reasonIfNotMatched)
    }

    @Test
    fun previewHit_primaryKeywordMatches_returnsMatched() {
        val preview = matcher.previewHit(
            entry = WorldBookEntry(
                id = "e1",
                title = "t",
                content = "c",
                keywords = listOf("白塔城", "北境"),
                matchMode = WorldBookMatchMode.CONTAINS,
            ),
            sourceText = "我准备去白塔城做生意",
        )

        assertTrue(preview.overallMatched)
        assertEquals(listOf("白塔城"), preview.primaryHits)
        assertTrue(preview.secondaryHits.isEmpty())
    }

    @Test
    fun previewHit_noPrimaryMatch_returnsMissReason() {
        val preview = matcher.previewHit(
            entry = WorldBookEntry(
                id = "e1",
                title = "t",
                content = "c",
                keywords = listOf("白塔城"),
                matchMode = WorldBookMatchMode.CONTAINS,
            ),
            sourceText = "去了另一个城镇",
        )

        assertFalse(preview.overallMatched)
        assertTrue(preview.primaryHits.isEmpty())
        assertEquals("主关键词均未命中", preview.reasonIfNotMatched)
    }

    @Test
    fun previewHit_selectiveWithoutSecondaryHit_returnsNotMatched() {
        val preview = matcher.previewHit(
            entry = WorldBookEntry(
                id = "e1",
                title = "t",
                content = "c",
                keywords = listOf("白塔城"),
                secondaryKeywords = listOf("生意"),
                selective = true,
                matchMode = WorldBookMatchMode.CONTAINS,
            ),
            sourceText = "我准备去白塔城看看风景",
        )

        assertFalse(preview.overallMatched)
        assertEquals(listOf("白塔城"), preview.primaryHits)
        assertTrue(preview.secondaryHits.isEmpty())
        assertTrue(preview.reasonIfNotMatched?.contains("次级") == true)
    }

    @Test
    fun previewHit_selectiveWithSecondaryHit_returnsMatched() {
        val preview = matcher.previewHit(
            entry = WorldBookEntry(
                id = "e1",
                title = "t",
                content = "c",
                keywords = listOf("白塔城"),
                secondaryKeywords = listOf("生意", "商会"),
                selective = true,
                matchMode = WorldBookMatchMode.CONTAINS,
            ),
            sourceText = "我准备去白塔城做生意",
        )

        assertTrue(preview.overallMatched)
        assertEquals(listOf("白塔城"), preview.primaryHits)
        assertEquals(listOf("生意"), preview.secondaryHits)
    }

    @Test
    fun previewHit_alwaysActiveBypassesPrimaryKeywordMiss() {
        val preview = matcher.previewHit(
            entry = WorldBookEntry(
                id = "e1",
                title = "t",
                content = "c",
                keywords = listOf("白塔城"),
                alwaysActive = true,
                matchMode = WorldBookMatchMode.CONTAINS,
            ),
            sourceText = "完全不相关的内容",
        )

        assertTrue("alwaysActive 条目不应该因为主关键词未命中被判 miss", preview.overallMatched)
        assertTrue(preview.primaryHits.isEmpty())
    }

    @Test
    fun previewHit_alwaysActiveOverridesSelectiveSecondaryMiss() {
        // 回归 T15-D3 自审发现的语义 bug：Matcher.match 里 alwaysActive
        // 与 hasKeywordHit 是 OR 关系，alwaysActive=true 即使 selective
        // + 次级未命中也会被注入；previewHit 必须保持一致。
        val preview = matcher.previewHit(
            entry = WorldBookEntry(
                id = "e1",
                title = "t",
                content = "c",
                keywords = listOf("白塔城"),
                secondaryKeywords = listOf("生意"),
                selective = true,
                alwaysActive = true,
                matchMode = WorldBookMatchMode.CONTAINS,
            ),
            sourceText = "我准备去白塔城看看风景",
        )

        assertTrue(
            "alwaysActive=true 必须覆盖 selective 次级未命中，" +
                "和 Matcher.match 一致",
            preview.overallMatched,
        )
        assertEquals(listOf("白塔城"), preview.primaryHits)
        assertTrue(
            "alwaysActive 短路时仍应按实际匹配填 secondaryHits，让用户看到\"关掉 alwaysActive 会不会命中\"",
            preview.secondaryHits.isEmpty(),
        )
    }
}
