package com.example.myapplication.data.repository.mailbox

import com.example.myapplication.data.local.mailbox.MailboxDao
import com.example.myapplication.data.local.mailbox.MailboxLetterEntity
import com.example.myapplication.data.local.mailbox.MailboxSettingsEntity
import com.example.myapplication.model.MailboxBox
import com.example.myapplication.model.MailboxProactiveFrequency
import com.example.myapplication.model.MailboxSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailboxRepositoryTest {
    @Test
    fun saveDraft_thenSend_movesLetterToSent() = runTest {
        val dao = FakeMailboxDao()
        val repository = RoomMailboxRepository(
            mailboxDao = dao,
            nowProvider = { 100L },
        )

        val draft = repository.saveDraft(
            scenarioId = "scenario-1",
            conversationId = "conversation-1",
            assistantId = "assistant-1",
            subject = "  昨晚的话  ",
            content = "我想把昨天没说完的话写下来。\n雨停以后，我还在路口站了一会儿。",
        )
        val sent = repository.sendLetter(draft)

        assertEquals(MailboxBox.SENT, sent.box)
        assertEquals("昨晚的话", sent.subject)
        assertEquals(100L, sent.sentAt)
        assertTrue(sent.excerpt.contains("雨停以后"))
        assertEquals(listOf(sent.id), dao.currentLetters.map { it.id })
    }

    @Test
    fun incomingLetter_markRead_archiveAndDeleteConversation_updatesStorage() = runTest {
        var now = 10L
        val dao = FakeMailboxDao()
        val repository = RoomMailboxRepository(
            mailboxDao = dao,
            nowProvider = { now },
        )
        val incoming = repository.insertIncomingLetter(
            scenarioId = "scenario-1",
            conversationId = "conversation-1",
            assistantId = "assistant-1",
            subject = "关于旧车站",
            content = "我记得那天雨很大。",
            tags = listOf("  旧车站  ", "关系推进", "关系推进"),
            mood = "认真",
        )

        assertEquals(1, repository.observeUnreadCount("scenario-1").first())
        assertEquals(listOf("旧车站", "关系推进"), incoming.tags)

        now = 20L
        repository.markRead(incoming.id)
        val read = repository.getLetter(incoming.id)
        assertTrue(read?.isRead == true)
        assertEquals(20L, read?.readAt)
        assertEquals(0, repository.observeUnreadCount("scenario-1").first())

        now = 30L
        repository.archive(incoming.id)
        assertEquals(MailboxBox.ARCHIVE, repository.getLetter(incoming.id)?.box)

        repository.deleteLettersForConversation("conversation-1")
        assertTrue(dao.currentLetters.isEmpty())
    }

    @Test
    fun deleteScenarioData_removesLettersAndSettings() = runTest {
        val dao = FakeMailboxDao()
        val repository = RoomMailboxRepository(
            mailboxDao = dao,
            nowProvider = { 100L },
        )
        repository.insertIncomingLetter(
            scenarioId = "scenario-1",
            conversationId = "conversation-1",
            assistantId = "assistant-1",
            subject = "旧信",
            content = "正文",
            tags = emptyList(),
            mood = "",
        )
        repository.updateSettings(MailboxSettings(scenarioId = "scenario-1"))

        repository.deleteScenarioData("scenario-1", "conversation-1")

        assertTrue(dao.currentLetters.isEmpty())
        assertEquals(null, dao.getSettings("scenario-1"))
    }


    @Test
    fun moveToTrash_keepsLetterOutOfVisibleBoxes() = runTest {
        val dao = FakeMailboxDao()
        val repository = RoomMailboxRepository(
            mailboxDao = dao,
            nowProvider = { 1L },
        )
        val incoming = repository.insertIncomingLetter(
            scenarioId = "scenario-1",
            conversationId = "conversation-1",
            assistantId = "assistant-1",
            subject = "一封信",
            content = "正文",
            tags = emptyList(),
            mood = "",
        )

        repository.moveToTrash(incoming.id)

        assertEquals(MailboxBox.TRASH, repository.getLetter(incoming.id)?.box)
        assertTrue(repository.observeLetters("scenario-1", MailboxBox.INBOX).first().isEmpty())
        assertFalse(dao.currentLetters.isEmpty())
    }

    @Test
    fun settings_roundTripAndMarkProactiveLetterTime() = runTest {
        var now = 50L
        val dao = FakeMailboxDao()
        val repository = RoomMailboxRepository(
            mailboxDao = dao,
            nowProvider = { now },
        )

        repository.updateSettings(
            MailboxSettings(
                scenarioId = "scenario-1",
                autoReplyToUserLetters = false,
                proactiveFrequency = MailboxProactiveFrequency.NORMAL,
            ),
        )

        val saved = repository.observeSettings("scenario-1").first()
        assertEquals(false, saved.autoReplyToUserLetters)
        assertEquals(MailboxProactiveFrequency.NORMAL, saved.proactiveFrequency)
        assertEquals(50L, saved.updatedAt)

        now = 80L
        val touched = repository.markProactiveLetterCreated("scenario-1")
        assertEquals(80L, touched.lastProactiveLetterAt)
        assertEquals(80L, repository.getSettings("scenario-1").lastProactiveLetterAt)
    }
}

private class FakeMailboxDao(
    letters: List<MailboxLetterEntity> = emptyList(),
) : MailboxDao {
    private val state = MutableStateFlow(letters)
    private val settingsState = MutableStateFlow<Map<String, MailboxSettingsEntity>>(emptyMap())

    val currentLetters: List<MailboxLetterEntity>
        get() = state.value

    override fun observeLetters(
        scenarioId: String,
        box: String,
    ): Flow<List<MailboxLetterEntity>> {
        return state.map { letters ->
            letters
                .filter { it.scenarioId == scenarioId && it.box == box }
                .sortedWith(compareBy<MailboxLetterEntity> { it.isRead }.thenByDescending { it.updatedAt })
        }
    }

    override fun observeLetter(letterId: String): Flow<MailboxLetterEntity?> {
        return state.map { letters -> letters.firstOrNull { it.id == letterId } }
    }

    override suspend fun getLetter(letterId: String): MailboxLetterEntity? {
        return state.value.firstOrNull { it.id == letterId }
    }

    override fun observeUnreadCount(scenarioId: String): Flow<Int> {
        return state.map { letters ->
            letters.count { it.scenarioId == scenarioId && it.box == "inbox" && !it.isRead }
        }
    }

    override suspend fun upsertLetter(letter: MailboxLetterEntity) {
        state.value = state.value.filterNot { it.id == letter.id } + letter
    }

    override suspend fun markRead(
        letterId: String,
        readAt: Long,
    ) {
        state.value = state.value.map {
            if (it.id == letterId) {
                it.copy(isRead = true, readAt = readAt, updatedAt = readAt)
            } else {
                it
            }
        }
    }

    override suspend fun moveToBox(
        letterId: String,
        box: String,
        updatedAt: Long,
    ) {
        state.value = state.value.map {
            if (it.id == letterId) it.copy(box = box, updatedAt = updatedAt) else it
        }
    }

    override suspend fun linkMemory(
        letterId: String,
        memoryId: String,
        updatedAt: Long,
    ) {
        state.value = state.value.map {
            if (it.id == letterId) it.copy(linkedMemoryId = memoryId, updatedAt = updatedAt) else it
        }
    }

    override suspend fun deleteLetter(letterId: String) {
        state.value = state.value.filterNot { it.id == letterId }
    }

    override suspend fun deleteLettersForConversation(conversationId: String) {
        state.value = state.value.filterNot { it.conversationId == conversationId }
    }

    override suspend fun deleteLettersForScenario(scenarioId: String) {
        state.value = state.value.filterNot { it.scenarioId == scenarioId }
    }

    override fun observeSettings(scenarioId: String): Flow<MailboxSettingsEntity?> {
        return settingsState.map { settings -> settings[scenarioId] }
    }

    override suspend fun getSettings(scenarioId: String): MailboxSettingsEntity? {
        return settingsState.value[scenarioId]
    }

    override suspend fun upsertSettings(settings: MailboxSettingsEntity) {
        settingsState.value = settingsState.value + (settings.scenarioId to settings)
    }

    override suspend fun deleteSettingsForScenario(scenarioId: String) {
        settingsState.value = settingsState.value - scenarioId
    }
}
