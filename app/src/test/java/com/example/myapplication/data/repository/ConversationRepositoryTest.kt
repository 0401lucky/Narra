package com.example.myapplication.data.repository

import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.model.PhoneObservationState
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.DEFAULT_CONVERSATION_TITLE
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.testutil.FakeConversationStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ConversationRepositoryTest {
    @Test
    fun ensureActiveConversation_createsDefaultConversationWhenStoreIsEmpty() = runBlocking {
        val store = FakeConversationStore()
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 100L },
        )

        val conversation = repository.ensureActiveConversation(currentConversationId = null)

        assertEquals(DEFAULT_CONVERSATION_TITLE, conversation.title)
        assertEquals(100L, conversation.createdAt)
        assertEquals(listOf(conversation), store.listConversations())
    }

    @Test
    fun replaceConversationSnapshot_updatesTitleAndModel() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "old-model",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 200L },
        )
        val messages = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "这是一个非常长的首条消息，用来验证标题会被截断到二十个字符以内",
                createdAt = 10L,
            ),
        )

        val updatedConversation = repository.replaceConversationSnapshot(
            conversationId = "c1",
            messages = messages,
            selectedModel = "new-model",
        )

        assertEquals("这是一个非常长的首条消息，用来验证标题会", updatedConversation.title)
        assertEquals("new-model", updatedConversation.model)
        assertEquals(200L, updatedConversation.updatedAt)
        assertEquals(messages, store.listMessages("c1"))
        assertEquals(1, store.replaceConversationSnapshotCount)
        assertEquals(0, store.upsertConversationWithMessagesCount)
    }

    @Test
    fun clearConversation_onlyClearsCurrentMessagesAndResetsTitle() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "旧标题",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
                Conversation(
                    id = "c2",
                    title = "另一个会话",
                    model = "model-b",
                    createdAt = 2L,
                    updatedAt = 2L,
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "hello",
                        createdAt = 10L,
                    ),
                ),
                "c2" to listOf(
                    ChatMessage(
                        id = "m2",
                        conversationId = "c2",
                        role = MessageRole.USER,
                        content = "world",
                        createdAt = 20L,
                    ),
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 300L },
        )

        val updatedConversation = repository.clearConversation(
            conversationId = "c1",
            selectedModel = "model-c",
        )

        assertEquals(DEFAULT_CONVERSATION_TITLE, updatedConversation.title)
        assertEquals("model-c", updatedConversation.model)
        assertTrue(store.listMessages("c1").isEmpty())
        assertEquals(1, store.listMessages("c2").size)
    }

    @Test
    fun clearConversation_deletesPhoneSnapshot() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "旧标题",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val phoneSnapshotRepository = RecordingPhoneSnapshotRepository()
        val repository = ConversationRepository(
            conversationStore = store,
            phoneSnapshotRepository = phoneSnapshotRepository,
            nowProvider = { 300L },
        )

        repository.clearConversation(
            conversationId = "c1",
            selectedModel = "model-c",
        )

        assertEquals(listOf("c1"), phoneSnapshotRepository.deletedConversationIds.distinct())
    }

    @Test
    fun deleteConversation_returnsRemainingConversation() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "会话1",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 20L,
                ),
                Conversation(
                    id = "c2",
                    title = "会话2",
                    model = "model-b",
                    createdAt = 2L,
                    updatedAt = 10L,
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 400L },
        )

        val nextConversation = repository.deleteConversation(
            conversationId = "c1",
            selectedModel = "model-c",
        )

        assertEquals("c2", nextConversation.id)
        assertEquals(listOf("c2"), store.listConversations().map { it.id })
    }

    @Test
    fun deleteConversation_createsNewConversationWhenLastOneRemoved() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "唯一会话",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 500L },
        )

        val nextConversation = repository.deleteConversation(
            conversationId = "c1",
            selectedModel = "model-z",
        )

        assertEquals(DEFAULT_CONVERSATION_TITLE, nextConversation.title)
        assertEquals("model-z", nextConversation.model)
        assertEquals(1, store.listConversations().size)
    }

    @Test
    fun deleteConversation_deletesPhoneSnapshot() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "唯一会话",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val phoneSnapshotRepository = RecordingPhoneSnapshotRepository()
        val repository = ConversationRepository(
            conversationStore = store,
            phoneSnapshotRepository = phoneSnapshotRepository,
            nowProvider = { 500L },
        )

        repository.deleteConversation(
            conversationId = "c1",
            selectedModel = "model-z",
        )

        assertEquals(listOf("c1"), phoneSnapshotRepository.deletedConversationIds.distinct())
    }

    @Test
    fun appendMessages_appendsIncrementallyWithoutReplacingSnapshot() = runBlocking {
        val existingMessages = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "你好",
                createdAt = 10L,
            ),
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "old-model",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            messagesByConversation = mapOf("c1" to existingMessages),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 600L },
        )

        repository.appendMessages(
            conversationId = "c1",
            messages = listOf(
                ChatMessage(
                    id = "m2",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 11L,
                ),
            ),
            selectedModel = "new-model",
        )

        assertEquals(0, store.replaceConversationSnapshotCount)
        assertEquals(1, store.upsertConversationWithMessagesCount)
    }

    @Test
    fun replaceConversationSnapshot_replacesSnapshotWhenMessagesAreRewound() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "旧标题",
                    model = "old-model",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "第一条",
                        createdAt = 10L,
                    ),
                    ChatMessage(
                        id = "m2",
                        conversationId = "c1",
                        role = MessageRole.ASSISTANT,
                        content = "第二条",
                        createdAt = 11L,
                    ),
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 700L },
        )

        repository.replaceConversationSnapshot(
            conversationId = "c1",
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "第一条",
                    createdAt = 10L,
                ),
            ),
            selectedModel = "new-model",
        )

        assertEquals(1, store.replaceConversationSnapshotCount)
        assertEquals(0, store.upsertConversationWithMessagesCount)
    }

    @Test
    fun appendMessages_doesNotReadExistingMessagesOnHotPath() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "old-model",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 800L },
        )

        repository.appendMessages(
            conversationId = "c1",
            messages = listOf(
                ChatMessage(
                    id = "m-user",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "新的首条消息会更新标题",
                    createdAt = 10L,
                ),
                ChatMessage(
                    id = "m-loading",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 11L,
                ),
            ),
            selectedModel = "new-model",
        )

        assertEquals(0, store.listMessagesCount)
        assertEquals(1, store.upsertConversationWithMessagesCount)
        assertEquals("新的首条消息会更新标题", store.listConversations().single().title)
    }

    @Test
    fun upsertMessages_doesNotReadExistingMessagesOnHotPath() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "已存在标题",
                    model = "old-model",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "assistant-1",
                        conversationId = "c1",
                        role = MessageRole.ASSISTANT,
                        content = "",
                        createdAt = 10L,
                    ),
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 900L },
        )

        repository.upsertMessages(
            conversationId = "c1",
            messages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "最终回复",
                    createdAt = 10L,
                ),
            ),
            selectedModel = "new-model",
        )

        assertEquals(0, store.listMessagesCount)
        assertEquals(1, store.upsertConversationWithMessagesCount)
        assertEquals("最终回复", store.listMessages("c1").single().content)
    }

    @Test
    fun applyTransferUpdates_readsCurrentMessagesAsSingleException() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "会话",
                    model = "old-model",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "转账",
                        createdAt = 10L,
                        parts = listOf(
                            transferMessagePart(
                                id = "transfer-1",
                                direction = TransferDirection.USER_TO_ASSISTANT,
                                status = TransferStatus.PENDING,
                                counterparty = "陆宴清",
                                amount = "88.00",
                                note = "买奶茶",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 1000L },
        )

        val updatedMessages = repository.applyTransferUpdates(
            conversationId = "c1",
            updates = listOf(
                TransferUpdateDirective(
                    refId = "transfer-1",
                    status = TransferStatus.RECEIVED,
                ),
            ),
            selectedModel = "new-model",
        )

        assertEquals(0, store.listMessagesCount)
        assertEquals(1, store.updateConversationMessagesCount)
        assertEquals(0, store.upsertConversationWithMessagesCount)
        assertEquals(
            TransferStatus.RECEIVED,
            updatedMessages.single().parts.single().specialStatus,
        )
    }

    @Test
    fun applyTransferUpdates_skipsWriteWhenMessageStateDoesNotChange() = runBlocking {
        val originalMessage = ChatMessage(
            id = "m1",
            conversationId = "c1",
            role = MessageRole.USER,
            content = "转账",
            createdAt = 10L,
            parts = listOf(
                transferMessagePart(
                    id = "transfer-1",
                    direction = TransferDirection.USER_TO_ASSISTANT,
                    status = TransferStatus.RECEIVED,
                    counterparty = "陆宴清",
                    amount = "88.00",
                    note = "买奶茶",
                ),
            ),
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "会话",
                    model = "old-model",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            messagesByConversation = mapOf("c1" to listOf(originalMessage)),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 1001L },
        )

        val updatedMessages = repository.applyTransferUpdates(
            conversationId = "c1",
            updates = listOf(
                TransferUpdateDirective(
                    refId = "transfer-1",
                    status = TransferStatus.RECEIVED,
                ),
            ),
            selectedModel = "new-model",
        )

        assertEquals(listOf(originalMessage), updatedMessages)
        assertEquals(1, store.updateConversationMessagesCount)
        assertEquals(0, store.upsertConversationWithMessagesCount)
        assertEquals(0, store.upsertMessagesCount)
    }

    @Test
    fun createConversation_persistsWithDefaultTitle() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "已有会话",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 200L },
        )

        val newConversation = repository.createConversation(selectedModel = "model-b")

        assertEquals(DEFAULT_CONVERSATION_TITLE, newConversation.title)
        assertEquals("model-b", newConversation.model)
        assertEquals(200L, newConversation.createdAt)
        assertEquals(2, store.listConversations().size)
        assertTrue(store.listConversations().any { it.id == newConversation.id })
    }

    @Test
    fun ensureActiveConversation_treatsLegacyBlankAssistantAsDefaultAssistant() = runBlocking {
        val legacyConversation = Conversation(
            id = "legacy",
            title = "旧会话",
            model = "model-a",
            createdAt = 1L,
            updatedAt = 1L,
            assistantId = "",
        )
        val store = FakeConversationStore(
            conversations = listOf(legacyConversation),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 200L },
        )

        val resolved = repository.ensureActiveConversation(
            currentConversationId = null,
            assistantId = DEFAULT_ASSISTANT_ID,
        )

        assertEquals("legacy", resolved.id)
    }

    private class RecordingPhoneSnapshotRepository : PhoneSnapshotRepository {
        val deletedConversationIds = mutableListOf<String>()

        override fun observeSnapshot(
            conversationId: String,
            ownerType: PhoneSnapshotOwnerType,
        ): Flow<PhoneSnapshot?> = flowOf(null)

        override suspend fun getSnapshot(
            conversationId: String,
            ownerType: PhoneSnapshotOwnerType,
        ): PhoneSnapshot? = null

        override suspend fun upsertSnapshot(snapshot: PhoneSnapshot) = Unit

        override suspend fun deleteSnapshot(conversationId: String) {
            deletedConversationIds += conversationId
        }

        override fun observeObservation(conversationId: String): Flow<PhoneObservationState?> = flowOf(null)

        override suspend fun getObservation(conversationId: String): PhoneObservationState? = null

        override suspend fun upsertObservation(observation: PhoneObservationState) = Unit

        override suspend fun deleteObservation(conversationId: String) {
            deletedConversationIds += conversationId
        }
    }
}
