package com.example.myapplication.data.repository.phone

import com.example.myapplication.model.PhoneMessageThread
import com.example.myapplication.model.PhoneNoteEntry
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PhoneSnapshotRepositoryCodecTest {
    @Test
    fun entityRoundTrip_preservesSnapshotContent() {
        val snapshot = PhoneSnapshot(
            conversationId = "conversation-1",
            ownerType = PhoneSnapshotOwnerType.CHARACTER,
            scenarioId = "scene-1",
            assistantId = "assistant-1",
            contentSemanticsVersion = PhoneSnapshot.USER_PHONE_CONTENT_SEMANTICS_VERSION,
            ownerName = "沈砚清",
            updatedAt = 88L,
            messageThreads = listOf(
                PhoneMessageThread(
                    id = "thread-1",
                    contactName = "lucky",
                    preview = "今晚见。",
                    timeLabel = "昨天",
                ),
            ),
            notes = listOf(
                PhoneNoteEntry(
                    id = "note-1",
                    title = "纪念日",
                    summary = "别忘记",
                    content = "完整正文",
                    timeLabel = "今天",
                ),
            ),
        )

        val entity = snapshot.toEntity(Gson())
        val restored = entity.toDomain(Gson())

        assertNotNull(restored)
        assertEquals("沈砚清", restored?.ownerName)
        assertEquals(PhoneSnapshot.USER_PHONE_CONTENT_SEMANTICS_VERSION, restored?.contentSemanticsVersion)
        assertEquals("lucky", restored?.messageThreads?.first()?.contactName)
        assertEquals("纪念日", restored?.notes?.first()?.title)
    }
}
