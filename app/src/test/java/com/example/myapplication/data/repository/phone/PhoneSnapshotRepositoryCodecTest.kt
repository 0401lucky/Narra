package com.example.myapplication.data.repository.phone

import com.example.myapplication.data.local.phone.PhoneSnapshotEntity
import com.example.myapplication.model.PhoneMessageThread
import com.example.myapplication.model.PhoneNoteEntry
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun entityToDomain_missingSocialPostsFallsBackToEmptyList() {
        val legacyJson = """
            {
              "conversationId": "conversation-legacy",
              "ownerType": "character",
              "scenarioId": "scene-1",
              "assistantId": "assistant-1",
              "contentSemanticsVersion": 1,
              "ownerName": "沈砚清",
              "updatedAt": 66,
              "messageThreads": [
                {
                  "id": "thread-1",
                  "contactName": "lucky",
                  "preview": "今晚见。",
                  "timeLabel": "昨天"
                }
              ],
              "notes": [
                {
                  "id": "note-1",
                  "title": "纪念日",
                  "summary": "别忘记",
                  "content": "完整正文",
                  "timeLabel": "今天"
                }
              ]
            }
        """.trimIndent()
        val entity = PhoneSnapshotEntity(
            conversationId = "conversation-1",
            ownerType = PhoneSnapshotOwnerType.USER.storageValue,
            scenarioId = "scene-2",
            assistantId = "assistant-2",
            updatedAt = 99L,
            snapshotJson = legacyJson,
        )

        val restored = entity.toDomain(Gson())

        assertNotNull(restored)
        assertEquals(PhoneSnapshotOwnerType.USER, restored?.ownerType)
        assertEquals("scene-2", restored?.scenarioId)
        assertEquals("assistant-2", restored?.assistantId)
        assertEquals(99L, restored?.updatedAt)
        assertTrue(restored?.socialPosts?.isEmpty() == true)
        assertEquals("lucky", restored?.messageThreads?.first()?.contactName)
    }
}
