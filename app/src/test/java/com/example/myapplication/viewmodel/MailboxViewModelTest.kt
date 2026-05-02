package com.example.myapplication.viewmodel

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.DefaultAiSettingsRepository
import com.example.myapplication.data.repository.ai.MailboxPromptService
import com.example.myapplication.data.repository.ai.PromptExtrasCore
import com.example.myapplication.data.repository.ai.tooling.NoOpMemoryWriteService
import com.example.myapplication.data.repository.context.InMemoryPendingMemoryProposalRepository
import com.example.myapplication.data.repository.mailbox.MailboxRepository
import com.example.myapplication.data.repository.phone.EmptyPhoneSnapshotRepository
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MailboxBox
import com.example.myapplication.model.MailboxLetter
import com.example.myapplication.model.MailboxProactiveFrequency
import com.example.myapplication.model.MailboxSenderType
import com.example.myapplication.model.MailboxSettings
import com.example.myapplication.model.MailboxSource
import com.example.myapplication.model.PhoneNoteEntry
import com.example.myapplication.model.PhoneObservationState
import com.example.myapplication.model.PhoneRelationshipHighlight
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayDiaryDraft
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.testutil.FakeConversationStore
import com.example.myapplication.testutil.FakeSettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class MailboxViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun sendLetter_withLocalReply_insertsSentAndIncomingLetters() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val scenario = RoleplayScenario(
            id = "scenario-1",
            title = "雨夜",
            assistantId = "assistant-1",
            characterDisplayNameOverride = "林屿",
        )
        val conversation = Conversation(
            id = "conversation-1",
            title = "雨夜",
            model = "",
            createdAt = 1L,
            updatedAt = 1L,
            assistantId = "assistant-1",
        )
        val settings = AppSettings(
            userDisplayName = "我",
            assistants = listOf(Assistant(id = "assistant-1", name = "林屿")),
            selectedAssistantId = "assistant-1",
        )
        val mailboxRepository = FakeMailboxRepository()
        val viewModel = MailboxViewModel(
            initialScenarioId = scenario.id,
            settingsRepository = DefaultAiSettingsRepository(FakeSettingsStore(settings)),
            conversationRepository = ConversationRepository(
                conversationStore = FakeConversationStore(
                    conversations = listOf(conversation),
                    messagesByConversation = mapOf(
                        conversation.id to listOf(
                            ChatMessage(
                                id = "message-1",
                                conversationId = conversation.id,
                                role = com.example.myapplication.model.MessageRole.USER,
                                content = "昨晚没说完的话还在。",
                                createdAt = 1L,
                            ),
                        ),
                    ),
                ),
            ),
            roleplayRepository = FakeMailboxRoleplayRepository(scenario, RoleplaySession("session-1", scenario.id, conversation.id)),
            phoneSnapshotRepository = EmptyPhoneSnapshotRepository,
            mailboxRepository = mailboxRepository,
            mailboxPromptService = MailboxPromptService(PromptExtrasCore(ApiServiceFactory())),
            pendingMemoryProposalRepository = InMemoryPendingMemoryProposalRepository(),
            memoryWriteService = NoOpMemoryWriteService,
        )
        advanceUntilIdle()

        viewModel.updateDraftSubject("昨晚的话")
        viewModel.updateDraftContent("我想认真把昨天没说完的话写下来。")
        viewModel.sendLetter()
        advanceUntilIdle()

        val letters = mailboxRepository.currentLetters
        assertEquals(2, letters.size)
        assertTrue(letters.any { it.box == MailboxBox.SENT && it.subject == "昨晚的话" })
        assertTrue(letters.any { it.box == MailboxBox.INBOX && it.replyToLetterId.isNotBlank() })
        assertEquals(MailboxBox.INBOX, viewModel.uiState.value.selectedBox)
    }

    @Test
    fun sendLetter_withConfiguredProvider_usesAiReplyAndClearsFilters() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "{\"subject\":\"关于旧车站\",\"content\":\"我认真读完了你的信。\",\"mood\":\"认真\",\"tags\":[\"旧车站\"],\"memoryCandidate\":\"林屿认真回应了旧车站那封信。\"}"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "mailbox-provider",
            name = "信箱模型",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "mailbox-model",
        )
        val mailboxRepository = FakeMailboxRepository()
        val viewModel = createMailboxViewModel(
            mailboxRepository = mailboxRepository,
            settings = defaultSettings().copy(
                providers = listOf(provider),
                selectedProviderId = provider.id,
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
            ),
        )
        advanceUntilIdle()

        viewModel.updateSearchQuery("不会命中")
        viewModel.updateDraftSubject("旧车站")
        viewModel.updateDraftContent("我想认真写完旧车站那晚没说完的话。")
        viewModel.sendLetter()
        advanceUntilIdle()

        val request = server.takeRequest()
        withTimeout(5_000) {
            while (viewModel.uiState.value.searchQuery.isNotBlank()) {
                delay(10)
            }
        }
        val state = viewModel.uiState.value
        assertEquals("/v1/chat/completions", request.path)
        assertTrue(request.body.readUtf8().contains("mailbox-model"))
        assertEquals("", state.searchQuery)
        assertEquals("", state.selectedTag)
        assertEquals(false, state.unreadOnly)
        assertEquals(MailboxBox.INBOX, state.selectedBox)
        assertTrue(mailboxRepository.currentLetters.any {
            it.box == MailboxBox.INBOX &&
                it.subject == "关于旧车站" &&
                it.tags == listOf("旧车站")
        })
    }

    @Test
    fun sendLetter_blocksAutoReplyWhilePreviousReplyIsGenerating() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBodyDelay(250, TimeUnit.MILLISECONDS)
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "{\"subject\":\"上一封回信\",\"content\":\"上一封的回应。\"}"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val provider = ProviderSettings(
            id = "mailbox-provider",
            name = "信箱模型",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "mailbox-model",
        )
        val mailboxRepository = FakeMailboxRepository()
        val viewModel = createMailboxViewModel(
            mailboxRepository = mailboxRepository,
            settings = defaultSettings().copy(
                providers = listOf(provider),
                selectedProviderId = provider.id,
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
            ),
        )
        advanceUntilIdle()
        mailboxRepository.seed(
            MailboxLetter(
                id = "sent-1",
                scenarioId = "scenario-1",
                conversationId = "conversation-1",
                assistantId = "assistant-1",
                box = MailboxBox.SENT,
                subject = "上一封",
                content = "上一封信",
                updatedAt = 1L,
            ),
        )
        viewModel.generateReplyFor("sent-1")
        runCurrent()

        viewModel.updateDraftSubject("第二封")
        viewModel.updateDraftContent("第二封信不应该在上一封回信生成中寄出。")
        viewModel.sendLetter()
        runCurrent()

        assertTrue(viewModel.uiState.value.errorMessage?.contains("上一封回信还在生成") == true)
        assertTrue(mailboxRepository.currentLetters.none { it.subject == "第二封" })
        advanceUntilIdle()
    }

    @Test
    fun filtersBySearchTagAndUnreadOnly() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val mailboxRepository = FakeMailboxRepository()
        val viewModel = createMailboxViewModel(mailboxRepository = mailboxRepository)
        advanceUntilIdle()

        mailboxRepository.seed(
            MailboxLetter(
                id = "station",
                scenarioId = "scenario-1",
                conversationId = "conversation-1",
                assistantId = "assistant-1",
                senderType = MailboxSenderType.CHARACTER,
                box = MailboxBox.INBOX,
                subject = "旧车站那天",
                content = "雨停以后，我们又绕回了站台。",
                tags = listOf("旧车站", "关系推进"),
                isRead = false,
                updatedAt = 3L,
            ),
            MailboxLetter(
                id = "invite",
                scenarioId = "scenario-1",
                conversationId = "conversation-1",
                assistantId = "assistant-1",
                senderType = MailboxSenderType.CHARACTER,
                box = MailboxBox.INBOX,
                subject = "周末要不要去看展",
                content = "这不是很正式的邀请。",
                tags = listOf("邀约"),
                isRead = true,
                updatedAt = 2L,
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("station", "invite"), viewModel.uiState.value.visibleLetters.map { it.id })

        viewModel.updateSearchQuery("车站")
        advanceUntilIdle()
        assertEquals(listOf("station"), viewModel.uiState.value.visibleLetters.map { it.id })

        viewModel.updateSearchQuery("")
        viewModel.selectTag("邀约")
        advanceUntilIdle()
        assertEquals(listOf("invite"), viewModel.uiState.value.visibleLetters.map { it.id })

        viewModel.selectTag("邀约")
        viewModel.updateUnreadOnly(true)
        advanceUntilIdle()
        assertEquals(listOf("station"), viewModel.uiState.value.visibleLetters.map { it.id })

        viewModel.clearFilters()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.visibleLetters.size)
    }

    @Test
    fun relatedContextExposesDiaryAndPhoneClues() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val phoneRepository = FakePhoneSnapshotRepository(
            PhoneSnapshot(
                conversationId = "conversation-1",
                ownerType = PhoneSnapshotOwnerType.CHARACTER,
                relationshipHighlights = listOf(
                    PhoneRelationshipHighlight(
                        id = "rel-1",
                        name = "旧车站",
                        relationLabel = "共同记忆",
                        stance = "柔软",
                        note = "两个人都记得那次共伞。",
                    ),
                ),
                notes = listOf(
                    PhoneNoteEntry(
                        id = "note-1",
                        title = "雨天",
                        summary = "想起站台边的那把伞。",
                        content = "想起站台边的那把伞。",
                        timeLabel = "昨晚",
                    ),
                ),
            ),
        )
        val diaryEntries = listOf(
            RoleplayDiaryEntry(
                id = "diary-1",
                conversationId = "conversation-1",
                scenarioId = "scenario-1",
                title = "那天的伞",
                content = "雨停得很慢。",
                sortOrder = 0,
                createdAt = 2L,
                updatedAt = 4L,
            ),
        )
        val viewModel = createMailboxViewModel(
            roleplayRepository = FakeMailboxRoleplayRepository(
                scenario = defaultScenario(),
                session = RoleplaySession("session-1", "scenario-1", "conversation-1"),
                diaryEntries = diaryEntries,
            ),
            phoneSnapshotRepository = phoneRepository,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.diaryEntryCount)
        assertEquals("那天的伞", state.latestDiaryTitle)
        assertEquals(2, state.phoneClueCount)
        assertTrue(state.phoneClueSummary.contains("旧车站"))
    }

    @Test
    fun mailboxSettings_updateComposerDefaults() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val mailboxRepository = FakeMailboxRepository()
        val viewModel = createMailboxViewModel(mailboxRepository = mailboxRepository)
        advanceUntilIdle()

        viewModel.updateAutoReplySetting(false)
        viewModel.updateDefaultIncludeRecentChatSetting(false)
        viewModel.updateDefaultIncludePhoneCluesSetting(false)
        viewModel.updateDefaultAllowMemorySetting(false)
        advanceUntilIdle()
        viewModel.startCompose()

        val state = viewModel.uiState.value
        assertEquals(false, state.generateReplyAfterSend)
        assertEquals(false, state.includeRecentChat)
        assertEquals(false, state.includePhoneClues)
        assertEquals(false, state.allowMemory)
        assertEquals(false, mailboxRepository.currentSettings.getValue("scenario-1").autoReplyToUserLetters)
    }

    @Test
    fun proactiveFrequency_enabled_generatesIncomingRoleplayEventLetter() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val mailboxRepository = FakeMailboxRepository()
        val viewModel = createMailboxViewModel(mailboxRepository = mailboxRepository)
        advanceUntilIdle()

        viewModel.updateProactiveFrequency(MailboxProactiveFrequency.HIGH)
        advanceUntilIdle()

        val incoming = mailboxRepository.currentLetters.single { it.source == MailboxSource.ROLEPLAY_EVENT }
        assertEquals(MailboxBox.INBOX, incoming.box)
        assertEquals(false, incoming.isRead)
        assertTrue(incoming.tags.contains("主动来信"))
        assertTrue(mailboxRepository.currentSettings.getValue("scenario-1").lastProactiveLetterAt > 0L)
        assertEquals("主动来信已放入收件箱", viewModel.uiState.value.noticeMessage)
    }

    private fun createMailboxViewModel(
        mailboxRepository: FakeMailboxRepository = FakeMailboxRepository(),
        roleplayRepository: RoleplayRepository = FakeMailboxRoleplayRepository(
            scenario = defaultScenario(),
            session = RoleplaySession("session-1", "scenario-1", "conversation-1"),
        ),
        phoneSnapshotRepository: PhoneSnapshotRepository = EmptyPhoneSnapshotRepository,
        settings: AppSettings = defaultSettings(),
    ): MailboxViewModel {
        val conversation = defaultConversation()
        return MailboxViewModel(
            initialScenarioId = "scenario-1",
            settingsRepository = DefaultAiSettingsRepository(FakeSettingsStore(settings)),
            conversationRepository = ConversationRepository(
                conversationStore = FakeConversationStore(
                    conversations = listOf(conversation),
                    messagesByConversation = mapOf(conversation.id to emptyList()),
                ),
            ),
            roleplayRepository = roleplayRepository,
            phoneSnapshotRepository = phoneSnapshotRepository,
            mailboxRepository = mailboxRepository,
            mailboxPromptService = MailboxPromptService(PromptExtrasCore(ApiServiceFactory())),
            pendingMemoryProposalRepository = InMemoryPendingMemoryProposalRepository(),
            memoryWriteService = NoOpMemoryWriteService,
        )
    }
}

private fun defaultSettings(): AppSettings {
    return AppSettings(
        userDisplayName = "我",
        assistants = listOf(Assistant(id = "assistant-1", name = "林屿")),
        selectedAssistantId = "assistant-1",
    )
}

private fun defaultScenario(): RoleplayScenario {
    return RoleplayScenario(
        id = "scenario-1",
        title = "雨夜",
        assistantId = "assistant-1",
        characterDisplayNameOverride = "林屿",
    )
}

private fun defaultConversation(): Conversation {
    return Conversation(
        id = "conversation-1",
        title = "雨夜",
        model = "",
        createdAt = 1L,
        updatedAt = 1L,
        assistantId = "assistant-1",
    )
}

private class FakeMailboxRepository : MailboxRepository {
    private val state = MutableStateFlow<List<MailboxLetter>>(emptyList())
    private val settingsState = MutableStateFlow<Map<String, MailboxSettings>>(emptyMap())
    private var nextId = 0

    val currentLetters: List<MailboxLetter>
        get() = state.value

    val currentSettings: Map<String, MailboxSettings>
        get() = settingsState.value

    fun seed(vararg letters: MailboxLetter) {
        state.value = letters.toList()
    }

    override fun observeLetters(
        scenarioId: String,
        box: MailboxBox,
    ): Flow<List<MailboxLetter>> {
        return state.map { letters ->
            letters
                .filter { it.scenarioId == scenarioId && it.box == box }
                .sortedWith(compareBy<MailboxLetter> { it.isRead }.thenByDescending { it.updatedAt })
        }
    }

    override fun observeLetter(letterId: String): Flow<MailboxLetter?> {
        return state.map { letters -> letters.firstOrNull { it.id == letterId } }
    }

    override fun observeUnreadCount(scenarioId: String): Flow<Int> {
        return state.map { letters ->
            letters.count { it.scenarioId == scenarioId && it.box == MailboxBox.INBOX && !it.isRead }
        }
    }

    override suspend fun getLetter(letterId: String): MailboxLetter? {
        return state.value.firstOrNull { it.id == letterId }
    }

    override suspend fun saveDraft(
        scenarioId: String,
        conversationId: String,
        assistantId: String,
        subject: String,
        content: String,
        replyToLetterId: String,
        allowMemory: Boolean,
        existingLetterId: String,
    ): MailboxLetter {
        val id = existingLetterId.ifBlank { "draft-${++nextId}" }
        val draft = MailboxLetter(
            id = id,
            scenarioId = scenarioId,
            conversationId = conversationId,
            assistantId = assistantId,
            box = MailboxBox.DRAFT,
            subject = subject.trim(),
            content = content.trim(),
            replyToLetterId = replyToLetterId,
            allowMemory = allowMemory,
            createdAt = 1L,
            updatedAt = 1L,
        )
        upsert(draft)
        return draft
    }

    override suspend fun sendLetter(letter: MailboxLetter): MailboxLetter {
        val sent = letter.copy(box = MailboxBox.SENT, sentAt = 2L, updatedAt = 2L)
        upsert(sent)
        return sent
    }

    override suspend fun insertIncomingLetter(
        scenarioId: String,
        conversationId: String,
        assistantId: String,
        subject: String,
        content: String,
        tags: List<String>,
        mood: String,
        replyToLetterId: String,
        allowMemory: Boolean,
        memoryCandidate: String,
        source: MailboxSource,
    ): MailboxLetter {
        val incoming = MailboxLetter(
            id = "incoming-${++nextId}",
            scenarioId = scenarioId,
            conversationId = conversationId,
            assistantId = assistantId,
            box = MailboxBox.INBOX,
            subject = subject,
            content = content,
            tags = tags,
            mood = mood,
            replyToLetterId = replyToLetterId,
            isRead = false,
            allowMemory = allowMemory,
            memoryCandidate = memoryCandidate,
            source = source,
            createdAt = 3L,
            updatedAt = 3L,
        )
        upsert(incoming)
        return incoming
    }

    override suspend fun markRead(letterId: String) {
        state.value = state.value.map { if (it.id == letterId) it.copy(isRead = true) else it }
    }

    override suspend fun archive(letterId: String) {
        state.value = state.value.map { if (it.id == letterId) it.copy(box = MailboxBox.ARCHIVE) else it }
    }

    override suspend fun moveToTrash(letterId: String) {
        state.value = state.value.map { if (it.id == letterId) it.copy(box = MailboxBox.TRASH) else it }
    }

    override suspend fun linkMemory(
        letterId: String,
        memoryId: String,
    ) {
        state.value = state.value.map { if (it.id == letterId) it.copy(linkedMemoryId = memoryId) else it }
    }

    override suspend fun delete(letterId: String) {
        state.value = state.value.filterNot { it.id == letterId }
    }

    override suspend fun deleteLettersForConversation(conversationId: String) {
        state.value = state.value.filterNot { it.conversationId == conversationId }
    }

    override suspend fun deleteScenarioData(scenarioId: String, conversationId: String) {
        state.value = state.value.filterNot { letter ->
            (conversationId.isNotBlank() && letter.conversationId == conversationId) ||
                (scenarioId.isNotBlank() && letter.scenarioId == scenarioId)
        }
        if (scenarioId.isNotBlank()) {
            settingsState.value = settingsState.value - scenarioId
        }
    }

    override fun observeSettings(scenarioId: String): Flow<MailboxSettings> {
        return settingsState.map { settings ->
            settings[scenarioId] ?: MailboxSettings(scenarioId = scenarioId)
        }
    }

    override suspend fun getSettings(scenarioId: String): MailboxSettings {
        return settingsState.value[scenarioId] ?: MailboxSettings(scenarioId = scenarioId)
    }

    override suspend fun updateSettings(settings: MailboxSettings): MailboxSettings {
        val updated = settings.copy(updatedAt = 4L)
        settingsState.value = settingsState.value + (updated.scenarioId to updated)
        return updated
    }

    override suspend fun markProactiveLetterCreated(scenarioId: String): MailboxSettings {
        val now = System.currentTimeMillis()
        val updated = getSettings(scenarioId).copy(
            lastProactiveLetterAt = now,
            updatedAt = now,
        )
        settingsState.value = settingsState.value + (scenarioId to updated)
        return updated
    }

    private fun upsert(letter: MailboxLetter) {
        state.value = state.value.filterNot { it.id == letter.id } + letter
    }
}

private class FakeMailboxRoleplayRepository(
    private val scenario: RoleplayScenario,
    private val session: RoleplaySession,
    private val diaryEntries: List<RoleplayDiaryEntry> = emptyList(),
) : RoleplayRepository {
    override fun observeScenarios(): Flow<List<RoleplayScenario>> = flowOf(listOf(scenario))
    override fun observeChatSummaries(): Flow<List<RoleplayChatSummary>> = flowOf(emptyList())
    override fun observeScenario(scenarioId: String): Flow<RoleplayScenario?> = flowOf(scenario.takeIf { it.id == scenarioId })
    override fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySession?> = flowOf(session.takeIf { it.scenarioId == scenarioId })
    override fun observeSessions(): Flow<List<RoleplaySession>> = flowOf(listOf(session))
    override fun observeConversationMessages(scenarioId: String): Flow<List<ChatMessage>> = flowOf(emptyList())
    override fun observeDiaryEntries(conversationId: String): Flow<List<RoleplayDiaryEntry>> {
        return flowOf(diaryEntries.filter { it.conversationId == conversationId })
    }
    override suspend fun listScenarios(): List<RoleplayScenario> = listOf(scenario)
    override suspend fun getScenario(scenarioId: String): RoleplayScenario? = scenario.takeIf { it.id == scenarioId }
    override suspend fun upsertScenario(scenario: RoleplayScenario) = Unit
    override suspend fun deleteScenario(scenarioId: String) = Unit
    override suspend fun startScenario(scenarioId: String): RoleplaySessionStartResult {
        return RoleplaySessionStartResult(
            session = session,
            reusedExistingSession = true,
            hasHistory = true,
            assistantMismatch = false,
        )
    }
    override suspend fun restartScenario(scenarioId: String): RoleplaySessionStartResult = startScenario(scenarioId)
    override suspend fun getSessionByScenario(scenarioId: String): RoleplaySession? = session.takeIf { it.scenarioId == scenarioId }
    override suspend fun getSession(sessionId: String): RoleplaySession? = session.takeIf { it.id == sessionId }
    override suspend fun listDiaryEntries(conversationId: String): List<RoleplayDiaryEntry> {
        return diaryEntries.filter { it.conversationId == conversationId }
    }
    override suspend fun replaceDiaryEntries(
        conversationId: String,
        scenarioId: String,
        entries: List<RoleplayDiaryDraft>,
    ): List<RoleplayDiaryEntry> = emptyList()
    override suspend fun getOnlineMeta(conversationId: String) = null
    override suspend fun upsertOnlineMeta(meta: com.example.myapplication.model.RoleplayOnlineMeta) = Unit
    override suspend fun deleteOnlineMeta(conversationId: String) = Unit
    override suspend fun deleteDiaryEntriesForConversation(conversationId: String) = Unit
}

private class FakePhoneSnapshotRepository(
    initialSnapshot: PhoneSnapshot? = null,
) : PhoneSnapshotRepository {
    private val state = MutableStateFlow(initialSnapshot)

    override fun observeSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ): Flow<PhoneSnapshot?> {
        return state.map { snapshot ->
            snapshot?.takeIf { it.conversationId == conversationId && it.ownerType == ownerType }
        }
    }

    override suspend fun getSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ): PhoneSnapshot? {
        return state.value?.takeIf { it.conversationId == conversationId && it.ownerType == ownerType }
    }

    override suspend fun upsertSnapshot(snapshot: PhoneSnapshot) {
        state.value = snapshot
    }

    override suspend fun deleteSnapshot(conversationId: String) {
        if (state.value?.conversationId == conversationId) {
            state.value = null
        }
    }

    override suspend fun deleteSnapshot(
        conversationId: String,
        ownerType: PhoneSnapshotOwnerType,
    ) {
        if (state.value?.conversationId == conversationId && state.value?.ownerType == ownerType) {
            state.value = null
        }
    }

    override fun observeObservation(conversationId: String): Flow<PhoneObservationState?> = flowOf(null)

    override suspend fun getObservation(conversationId: String): PhoneObservationState? = null

    override suspend fun upsertObservation(observation: PhoneObservationState) = Unit

    override suspend fun deleteObservation(conversationId: String) = Unit
}
