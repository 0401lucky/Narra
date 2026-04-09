package com.example.myapplication.context

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.PhoneMessageThread
import com.example.myapplication.model.PhoneNoteEntry
import com.example.myapplication.model.PhoneRelationshipHighlight
import com.example.myapplication.model.PhoneSearchDetail
import com.example.myapplication.model.PhoneSearchEntry
import com.example.myapplication.model.PhoneSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSnapshotPromptInjectorTest {
    private val injector = PhoneSnapshotPromptInjector()

    @Test
    fun selectRelevantItems_returnsMatchesForMentionedContactAndSearch() {
        val snapshot = PhoneSnapshot(
            conversationId = "conversation-1",
            relationshipHighlights = listOf(
                PhoneRelationshipHighlight(
                    id = "r1",
                    name = "沈砚清",
                    relationLabel = "恋人",
                    stance = "隐忍",
                    note = "对公开关系很谨慎",
                ),
            ),
            messageThreads = listOf(
                PhoneMessageThread(
                    id = "t1",
                    contactName = "沈砚清",
                    relationLabel = "恋人",
                    preview = "今晚别再躲我了。",
                    timeLabel = "昨天",
                ),
            ),
            notes = listOf(
                PhoneNoteEntry(
                    id = "n1",
                    title = "和沈砚清的纪念日",
                    summary = "不要忘记下周的日子",
                    content = "完整正文",
                    timeLabel = "昨天",
                ),
            ),
            searchHistory = listOf(
                PhoneSearchEntry(
                    id = "s1",
                    query = "古典文学中的克制美学",
                    timeLabel = "今天 14:30",
                    detail = PhoneSearchDetail(
                        title = "古典文学中的克制美学",
                        summary = "以隐忍、留白和节制表达情感。",
                        content = "完整内容",
                    ),
                ),
            ),
        )

        val result = injector.selectRelevantItems(
            snapshot = snapshot,
            userInputText = "你之前是不是搜过克制美学，而且还和沈砚清聊过这件事？",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    role = MessageRole.USER,
                    content = "我想再问问沈砚清那条消息。",
                ),
            ),
        )

        assertEquals(3, result.size)
        assertTrue(result.any { it.contains("沈砚清") })
        assertTrue(result.any { it.contains("克制美学") })
    }
}
