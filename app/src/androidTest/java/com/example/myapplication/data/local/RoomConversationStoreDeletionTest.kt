package com.example.myapplication.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.conversation.MessageDeletionResult
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.ConversationSummaryEntity
import com.example.myapplication.data.local.memory.ConversationSummarySegmentEntity
import com.example.myapplication.data.local.memory.MemoryEntryEntity
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.textMessagePart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomConversationStoreDeletionTest {
    private lateinit var database: ChatDatabase
    private lateinit var store: RoomConversationStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomConversationStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteMessageWithAssociations_commitsCleanupAndKeepsAttachmentFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val attachmentFile = File(
            context.cacheDir,
            "message-delete-${UUID.randomUUID()}.txt",
        ).apply {
            writeText("附件内容")
        }
        try {
            val conversation = testConversation()
            val target = targetMessage(
                attachment = MessageAttachment(
                    uri = attachmentFile.toURI().toString(),
                    mimeType = "text/plain",
                    fileName = attachmentFile.name,
                ),
            )
            val reply = referencedMessage()
            store.upsertConversationWithMessages(conversation, listOf(target, reply))
            seedAssociations()

            assertEquals(listOf("target", "reply"), store.observeMessages(CONVERSATION_ID).first().map(ChatMessage::id))

            val result = store.deleteMessageWithAssociations(
                conversation = conversation.copy(updatedAt = 99L),
                conversationId = CONVERSATION_ID,
                messageId = TARGET_MESSAGE_ID,
            )

            val deleted = result as MessageDeletionResult.Deleted
            assertEquals(listOf("reply"), deleted.updatedMessages.map(ChatMessage::id))
            val persistedMessages = store.observeMessages(CONVERSATION_ID).first()
            assertEquals(listOf("reply"), persistedMessages.map(ChatMessage::id))
            val persistedReply = persistedMessages.single()
            assertEquals("", persistedReply.replyToMessageId)
            assertEquals("", persistedReply.replyToPreview)
            assertEquals("", persistedReply.replyToSpeakerName)
            assertEquals("", persistedReply.parts[0].replyToMessageId)
            assertEquals("", persistedReply.parts[0].replyToPreview)
            assertEquals("", persistedReply.parts[0].replyToSpeakerName)
            assertEquals("other", persistedReply.parts[1].replyToMessageId)

            val remainingMemories = database.memoryDao().listMemoryEntries()
            assertEquals(listOf("unrelated-memory"), remainingMemories.map { it.id })
            assertEquals(null, database.memoryDao().getConversationSummary(CONVERSATION_ID))
            assertTrue(database.memoryDao().listConversationSummarySegments(CONVERSATION_ID).isEmpty())
            assertEquals(
                conversation.copy(updatedAt = 99L),
                store.getConversation(CONVERSATION_ID),
            )
            assertTrue(attachmentFile.exists())
        } finally {
            attachmentFile.delete()
        }
    }

    @Test
    fun deleteMessageWithAssociations_missingTargetPerformsNoWrites() = runBlocking {
        val conversation = testConversation()
        val target = targetMessage()
        val reply = referencedMessage()
        store.upsertConversationWithMessages(conversation, listOf(target, reply))
        seedAssociations()

        val result = store.deleteMessageWithAssociations(
            conversation = conversation.copy(updatedAt = 99L),
            conversationId = CONVERSATION_ID,
            messageId = "missing",
        )

        assertSame(MessageDeletionResult.NotFound, result)
        assertEquals(conversation, store.getConversation(CONVERSATION_ID))
        assertEquals(listOf(target, reply), store.listMessages(CONVERSATION_ID))
        assertEquals(
            listOf("character-memory", "conversation-memory", "global-memory", "unrelated-memory"),
            database.memoryDao().listMemoryEntries().map { it.id }.sorted(),
        )
        assertNotNull(database.memoryDao().getConversationSummary(CONVERSATION_ID))
        assertEquals(1, database.memoryDao().listConversationSummarySegments(CONVERSATION_ID).size)
    }

    @Test
    fun deleteMessageWithAssociations_associationFailureRollsBackEverything() = runBlocking {
        val conversation = testConversation()
        val target = targetMessage()
        val reply = referencedMessage()
        store.upsertConversationWithMessages(conversation, listOf(target, reply))
        seedAssociations()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_message_association_delete
            BEFORE DELETE ON memory_entries
            WHEN OLD.sourceMessageId = '$TARGET_MESSAGE_ID'
            BEGIN
                SELECT RAISE(ABORT, 'forced association failure');
            END
            """.trimIndent(),
        )

        val failure = try {
            runCatching {
                store.deleteMessageWithAssociations(
                    conversation = conversation.copy(updatedAt = 99L),
                    conversationId = CONVERSATION_ID,
                    messageId = TARGET_MESSAGE_ID,
                )
            }.exceptionOrNull()
        } finally {
            database.openHelper.writableDatabase.execSQL(
                "DROP TRIGGER IF EXISTS fail_message_association_delete",
            )
        }

        assertNotNull(failure)
        assertEquals(conversation, store.getConversation(CONVERSATION_ID))
        assertEquals(listOf(target, reply), store.listMessages(CONVERSATION_ID))
        assertEquals(
            listOf("character-memory", "conversation-memory", "global-memory", "unrelated-memory"),
            database.memoryDao().listMemoryEntries().map { it.id }.sorted(),
        )
        assertNotNull(database.memoryDao().getConversationSummary(CONVERSATION_ID))
        assertEquals(1, database.memoryDao().listConversationSummarySegments(CONVERSATION_ID).size)
    }

    private suspend fun seedAssociations() {
        listOf(
            MemoryEntryEntity(
                id = "global-memory",
                scopeType = "global",
                sourceMessageId = TARGET_MESSAGE_ID,
            ),
            MemoryEntryEntity(
                id = "character-memory",
                scopeType = "character",
                scopeId = "assistant-1",
                characterId = "assistant-1",
                sourceMessageId = TARGET_MESSAGE_ID,
            ),
            MemoryEntryEntity(
                id = "conversation-memory",
                scopeType = "conversation",
                scopeId = CONVERSATION_ID,
                sourceMessageId = TARGET_MESSAGE_ID,
            ),
            MemoryEntryEntity(
                id = "unrelated-memory",
                scopeType = "conversation",
                scopeId = CONVERSATION_ID,
                sourceMessageId = "other",
            ),
        ).forEach { database.memoryDao().upsertMemoryEntry(it) }
        database.memoryDao().upsertConversationSummary(
            ConversationSummaryEntity(
                conversationId = CONVERSATION_ID,
                assistantId = "assistant-1",
                summary = "旧摘要",
                coveredMessageCount = 2,
                updatedAt = 20L,
            ),
        )
        database.memoryDao().upsertConversationSummarySegment(
            ConversationSummarySegmentEntity(
                id = "segment-1",
                conversationId = CONVERSATION_ID,
                assistantId = "assistant-1",
                startMessageId = TARGET_MESSAGE_ID,
                endMessageId = "reply",
                startCreatedAt = 10L,
                endCreatedAt = 11L,
                messageCount = 2,
                summary = "旧分段摘要",
                createdAt = 20L,
                updatedAt = 20L,
            ),
        )
    }

    private fun testConversation(): Conversation {
        return Conversation(
            id = CONVERSATION_ID,
            title = "保留标题",
            model = "keep-model",
            createdAt = 1L,
            updatedAt = 2L,
            assistantId = "assistant-1",
            searchEnabled = true,
        )
    }

    private fun targetMessage(
        attachment: MessageAttachment? = null,
    ): ChatMessage {
        return ChatMessage(
            id = TARGET_MESSAGE_ID,
            conversationId = CONVERSATION_ID,
            role = MessageRole.ASSISTANT,
            content = "删除目标",
            attachments = listOfNotNull(attachment),
            createdAt = 10L,
        )
    }

    private fun referencedMessage(): ChatMessage {
        return ChatMessage(
            id = "reply",
            conversationId = CONVERSATION_ID,
            role = MessageRole.USER,
            content = "保留消息",
            parts = listOf(
                textMessagePart(
                    text = "命中分段引用",
                    replyToMessageId = TARGET_MESSAGE_ID,
                    replyToPreview = "目标预览",
                    replyToSpeakerName = "角色",
                ),
                textMessagePart(
                    text = "保留其他引用",
                    replyToMessageId = "other",
                    replyToPreview = "其他预览",
                    replyToSpeakerName = "其他人",
                ),
            ),
            replyToMessageId = TARGET_MESSAGE_ID,
            replyToPreview = "目标预览",
            replyToSpeakerName = "角色",
            createdAt = 11L,
        )
    }

    private companion object {
        const val CONVERSATION_ID = "conversation-1"
        const val TARGET_MESSAGE_ID = "target"
    }
}
