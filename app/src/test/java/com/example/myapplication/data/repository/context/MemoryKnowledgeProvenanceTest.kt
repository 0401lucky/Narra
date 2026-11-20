package com.example.myapplication.data.repository.context

import android.app.Application
import com.example.myapplication.data.local.memory.MemoryDerivedLinkEntity
import com.example.myapplication.data.local.memory.MemoryResidentEntryEntity
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryKnowledgeProvenanceTest {
    @Test
    fun archiveUnknownLegacy_keepsReferencesAndSourceDeletionScrubsBody() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val messageId = "legacy-archive-source"
            val record = f.remember(context, "旧声明：喜欢栀子花", messageId)
            val dao = f.database.memoryArchiveDao()
            val ref =
                requireNotNull(f.archive.getRecordDetails(context, record.id)).sourceRefs.single()
            val source = requireNotNull(dao.getSource(ref.sourceId))
            dao.upsertSource(source.copy(status = "UNKNOWN"))
            dao.upsertResidentEntry(
                MemoryResidentEntryEntity(
                    "legacy-archive", context.spaceId, context.characterId, "USER",
                    context.personaKey, "待核实旧认知：喜欢栀子花", 3, "READY", false,
                    null, false, f.now, f.now
                )
            )
            dao.upsertDerivedLink(
                MemoryDerivedLinkEntity(
                    "RESIDENT",
                    "legacy-archive",
                    record.id,
                    record.revision
                )
            )

            assertTrue(
                f.archive.setResidentArchived(
                    context,
                    "legacy-archive",
                    3,
                    true
                ) is MemoryKnowledgeWriteResult.Written
            )
            val archived = requireNotNull(f.archive.getResidentDetails(context, "legacy-archive"))
            assertEquals("ARCHIVED", archived.entry.status)
            assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).residents.isEmpty())
            assertEquals(MemorySourceStatus.UNKNOWN, archived.sources.single().status)

            f.store.deleteMessageWithAssociations(
                requireNotNull(f.store.getConversation(context.conversationId)),
                context.conversationId,
                messageId
            )
            val deleted = requireNotNull(f.archive.getResidentDetails(context, "legacy-archive"))
            assertEquals("", deleted.entry.content)
            assertTrue(deleted.revisions.all { it.content.isEmpty() })
            assertEquals(listOf(ref), archived.revisions.first().sourceRefs)
            assertEquals(
                listOf(MemoryRecordVersion(record.id, 1)),
                archived.revisions.first().supportingRecords
            )
            assertEquals(MemorySourceStatus.DELETED, deleted.sources.single().status)
        }
    }

    @Test
    fun retiredResident_keepsOriginalProvenanceUntilSourceIsDeleted() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val original = f.prepareKnowledge(context, "我喜欢栀子花") {
                knowledgeResponse(residents = listOf(residentCandidate("喜欢栀子花")))
            }
            f.commitKnowledge(original)
            val resident = f.archive.listResidentEntries(context).single()
            val retirement = f.prepareKnowledge(context, "上述偏好已经不适用") {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate(
                            "退役旧偏好",
                            operation = "retire",
                            targetRef = "R0",
                            expectedRevision = 1
                        )
                    )
                )
            }
            f.commitKnowledge(retirement)
            val retired = requireNotNull(f.archive.getResidentDetails(context, resident.id))
            assertEquals("ARCHIVED", retired.entry.status)
            assertEquals(resident.content, retired.entry.content)

            f.store.deleteMessageWithAssociations(
                requireNotNull(f.store.getConversation(context.conversationId)),
                context.conversationId,
                original.message.id
            )
            val deleted = requireNotNull(f.archive.getResidentDetails(context, resident.id))
            assertEquals("", deleted.entry.content)
            assertTrue(deleted.revisions.all { it.content.isEmpty() })
            assertEquals(
                setOf(
                    original.snapshot.sources.single().ref,
                    retirement.snapshot.sources.single().ref
                ),
                retired.revisions.first().sourceRefs.toSet()
            )
            assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).residents.isEmpty())
        }
    }
}
