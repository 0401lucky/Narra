package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneSnapshotTest {
    @Test
    fun mergeSections_onlyReplacesRequestedSections() {
        val original = PhoneSnapshot(
            conversationId = "conversation-1",
            contentSemanticsVersion = PhoneSnapshot.USER_PHONE_CONTENT_SEMANTICS_VERSION,
            relationshipHighlights = listOf(
                PhoneRelationshipHighlight(
                    id = "r1",
                    name = "沈砚清",
                    relationLabel = "恋人",
                    stance = "克制",
                    note = "最近明显在回避公开关系",
                ),
            ),
            messageThreads = listOf(
                PhoneMessageThread(
                    id = "m1",
                    contactName = "沈砚清",
                    preview = "晚上见面再聊。",
                    timeLabel = "昨天",
                ),
            ),
            notes = listOf(
                PhoneNoteEntry(
                    id = "n1",
                    title = "旧备忘录",
                    summary = "旧摘要",
                    content = "旧正文",
                    timeLabel = "昨天",
                ),
            ),
        )

        val merged = original.mergeSections(
            sections = PhoneSnapshotSections(
                notes = listOf(
                    PhoneNoteEntry(
                        id = "n2",
                        title = "新备忘录",
                        summary = "新摘要",
                        content = "新正文",
                        timeLabel = "今天",
                    ),
                ),
            ),
            requestedSections = setOf(PhoneSnapshotSection.NOTES),
            updatedAt = 99L,
        )

        assertEquals(99L, merged.updatedAt)
        assertEquals(PhoneSnapshot.USER_PHONE_CONTENT_SEMANTICS_VERSION, merged.contentSemanticsVersion)
        assertEquals("旧备忘录", original.notes.first().title)
        assertEquals("新备忘录", merged.notes.first().title)
        assertEquals("沈砚清", merged.messageThreads.first().contactName)
        assertEquals("恋人", merged.relationshipHighlights.first().relationLabel)
    }

    @Test
    fun withSearchDetail_onlyUpdatesTargetEntry() {
        val original = PhoneSnapshot(
            conversationId = "conversation-1",
            searchHistory = listOf(
                PhoneSearchEntry(
                    id = "s1",
                    query = "古典文学中的克制美学",
                    timeLabel = "今天 14:30",
                ),
                PhoneSearchEntry(
                    id = "s2",
                    query = "如何让爱人更开心",
                    timeLabel = "今天 10:15",
                ),
            ),
        )

        val updated = original.withSearchDetail(
            entryId = "s2",
            detail = PhoneSearchDetail(
                title = "如何让爱人更开心",
                summary = "先接住情绪，再给出具体行动。",
                content = "内容正文",
            ),
            updatedAt = 108L,
        )

        assertNull(updated.searchHistory.first().detail)
        assertEquals("如何让爱人更开心", updated.searchHistory[1].detail?.title)
        assertEquals(108L, updated.updatedAt)
    }
}
