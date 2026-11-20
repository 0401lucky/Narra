package com.example.myapplication.data.repository.roleplay

import android.app.Application
import com.example.myapplication.model.*
import com.example.myapplication.testutil.CompanionTestFixture
import com.example.myapplication.roleplay.companion.CompanionReadingSupport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionReadingPaginationTest : CompanionTestFixture() {
    private fun reader() = RoomCompanionReadingRepository(database, archive, roleplay)

    @Test
    fun mixedPages_filterBeforePagingAndKeepAllCommittedSources() = runBlocking<Unit> {
        val first = runtime()
        val second = runtime()
        assertEquals(first.spaceId, second.spaceId)
        val expected = mutableSetOf<String>()
        expected += legacy(first, "a", 41).map { it.id }
        expected += legacy(second, "b", 39).map { it.id }
        val firstEncounter = repository.createEncounter(first, CompanionOpeningMode.DIRECT)
        val secondEncounter = repository.createEncounter(second, CompanionOpeningMode.DIRECT)
        for ((context, encounter) in listOf(first to firstEncounter, second to secondEncounter)) {
            repeat(12) { index ->
                val input = ChatMessage(
                    "input-${context.conversationId}-$index",
                    context.conversationId,
                    MessageRole.USER,
                    "合成输入 $index",
                    createdAt = 11_000L + index
                )
                val turn = repository.beginTurn(encounter.id, CompanionTurnKind.INPUT, input)
                received(
                    turn,
                    "<narration>一起散步。</narration><dialogue sprite=\"calm\">合成回答 $index。</dialogue>"
                )
                val result = repository.commitAttempt(turn.attempt.id)
                assertTrue(result is CompanionCommitResult.Committed)
                expected += input.id
                expected += requireNotNull(repository.getTurn(turn.turn.id)?.replyMessageId)
            }
        }
        repeat(3) {
            val turn = repository.beginTurn(firstEncounter.id, CompanionTurnKind.CONTINUE)
            received(turn)
            assertTrue(repository.commitAttempt(turn.attempt.id) is CompanionCommitResult.Committed)
            val saved = requireNotNull(repository.getTurn(turn.turn.id))
            expected += requireNotNull(saved.inputMessageId)
            expected += requireNotNull(saved.replyMessageId)
        }
        val pending = repository.beginTurn(
            firstEncounter.id, CompanionTurnKind.INPUT,
            ChatMessage(
                "waiting",
                first.conversationId,
                MessageRole.USER,
                "等待回答",
                createdAt = 11_050L
            )
        )
        assertNull(repository.getTurn(pending.turn.id)?.selectedAttemptId)
        val broken = repository.beginTurn(
            secondEncounter.id, CompanionTurnKind.INPUT,
            ChatMessage(
                "broken-input",
                second.conversationId,
                MessageRole.USER,
                "坏关联",
                createdAt = 11_051L
            )
        )
        received(broken)
        assertTrue(repository.commitAttempt(broken.attempt.id) is CompanionCommitResult.Committed)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE companion_attempts SET state = 'FAILED' WHERE id = ?",
            arrayOf(broken.attempt.id)
        )

        val foreignPersona = runtime()
        legacy(foreignPersona, "persona", 7)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE memory_conversation_bindings SET personaKey = 'other-persona' WHERE conversationId = ?",
            arrayOf(foreignPersona.conversationId)
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE memory_source_evidence SET personaKey = 'other-persona' WHERE conversationId = ?",
            arrayOf(foreignPersona.conversationId)
        )
        val group = runtime()
        legacy(group, "group", 7)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE roleplay_scenarios SET chatType = 'group' WHERE id = ?",
            arrayOf(group.scenarioId)
        )
        for (purpose in listOf(MemoryPurpose.INDEPENDENT, MemoryPurpose.LEGACY_COMPAT)) {
            val candidate = runtime()
            legacy(candidate, purpose.storageValue, 7)
            assertEquals(first.spaceId, candidate.spaceId)
            database.openHelper.writableDatabase.execSQL(
                "UPDATE memory_conversation_bindings SET purpose = ? WHERE conversationId = ?",
                arrayOf(purpose.storageValue, candidate.conversationId)
            )
        }
        val otherCharacter = runtime("bob")
        legacy(otherCharacter, "bob", 7)
        val excludedId = "legacy-a-000"
        database.openHelper.writableDatabase.execSQL(
            "UPDATE memory_source_evidence SET messageRevision = 99 WHERE messageId = ?",
            arrayOf(excludedId)
        )
        expected -= excludedId
        val reading = reader()
        val scope = requireNotNull(reading.authorize(first.scenarioId, first.characterId))
        assertEquals(
            setOf(first.conversationId, second.conversationId),
            scope.bindings.map { it.conversationId }.toSet()
        )
        val before =
            (store.listMessages(first.conversationId) + store.listMessages(second.conversationId)).associate { it.id to (it.content to it.memoryRevision) }
        val selected = database.roleplayDao().getSessionByScenario(first.scenarioId)
        val active = repository.getEncounter(firstEncounter.id)
        val records = pages(reading, scope, 17)
        assertTrue(records.size >= 121)
        assertEquals(expected, records.map { it.message.id }.toSet())
        assertEquals(records.size, records.map { it.message.id }.distinct().size)
        assertTrue(records.zipWithNext().all { (left, right) -> left.cursor < right.cursor })
        assertEquals(
            3,
            records.count { it.message.companionSourceEligibility() == CompanionSourceEligibility.CONTROL_NOOP })
        assertEquals(
            3,
            records.count { it.message.role == MessageRole.ASSISTANT && it.message.content == "<dialogue>好，就坐一会儿。</dialogue>" })
        val replies =
            records.filter { it.message.role == MessageRole.ASSISTANT && it.message.companionTurnId.isNotBlank() }
        assertTrue(replies.all { it.message.companionReplyMetadata?.matches(it.message) == true })
        assertEquals(
            before,
            (store.listMessages(first.conversationId) + store.listMessages(second.conversationId)).associate { it.id to (it.content to it.memoryRevision) })
        assertEquals(selected, database.roleplayDao().getSessionByScenario(first.scenarioId))
        assertEquals(active, repository.getEncounter(firstEncounter.id))
    }

    @Test
    fun deletedCursor_keepsOlderPageAndExpandedWindow() = runBlocking<Unit> {
        val context = runtime()
        val data = legacy(context, "window", 130)
        val reading = reader()
        val scope = requireNotNull(reading.authorize(context.scenarioId, context.characterId))
        val first = reading.page(scope, limit = 40)
        val cursor = requireNotNull(first.nextBefore)
        delete(context, cursor.messageId)
        assertNull(database.conversationDao().getMessage(cursor.messageId))
        val older = reading.page(scope, cursor, 40)
        val expectedOlder =
            data.filter { CompanionReadingCursor(it.createdAt, it.id) < cursor }.takeLast(40)
                .map { it.id }
        assertEquals(expectedOlder, older.records.map { it.message.id })
        val boundary = requireNotNull(older.records.firstOrNull()?.cursor)
        delete(context, boundary.messageId)
        val edited = requireNotNull(
            store.listMessages(context.conversationId).firstOrNull { it.id == data.last().id })
        store.upsertMessages(listOf(edited.copy(content = "修订后的原文")))
        val appended = ChatMessage(
            "window-new",
            context.conversationId,
            MessageRole.USER,
            "新回答后仍保留已展开页",
            createdAt = 20_000
        )
        store.upsertMessages(listOf(appended))
        val window = reading.readWindow(scope, boundary)
        assertEquals(
            (data.filter { CompanionReadingCursor(it.createdAt, it.id) >= boundary }
            .map { it.id }.toSet() - cursor.messageId - boundary.messageId) + appended.id,
            window.map { it.message.id }.toSet()
        )
        assertEquals("修订后的原文", window.last { it.message.id == edited.id }.message.content)
        assertTrue(window.last { it.message.id == edited.id }.message.memoryRevision > edited.memoryRevision)
        assertTrue(window.size > 40)
        assertEquals(window.size, window.map { it.message.id }.distinct().size)
    }

    @Test
    fun unicodeCursor_keepsExpandedWindowInSqlBinaryOrder() = runBlocking<Unit> {
        val context = runtime()
        store.upsertMessages(listOf("m-a", "m-\uE000", "m-😀").map {
            ChatMessage(
                it,
                context.conversationId,
                MessageRole.USER,
                "并列记录",
                createdAt = 10_000
            )
        })
        val reading = reader()
        val scope = requireNotNull(reading.authorize(context.scenarioId, context.characterId))
        val latest = reading.page(scope, limit = 1)
        assertEquals("m-😀", latest.records.single().message.id)
        val older = reading.page(scope, latest.nextBefore, 1)
        assertEquals("m-\uE000", older.records.single().message.id)
        val boundary = minOf(requireNotNull(latest.nextBefore), requireNotNull(older.nextBefore))
        assertEquals("m-\uE000", boundary.messageId)
        assertEquals(
            listOf("m-\uE000", "m-😀"),
            reading.readWindow(scope, boundary).map { it.message.id })
        assertEquals(
            listOf("m-a", "m-\uE000", "m-😀"),
            CompanionReadingSupport.refreshedWindow(
                latest.records + older.records + reading.page(
                    scope,
                    older.nextBefore,
                    1
                ).records
            ).map { it.message.id })
        delete(context, boundary.messageId)
        assertEquals("m-a", reading.page(scope, boundary, 1).records.single().message.id)
        assertEquals(listOf("m-😀"), reading.readWindow(scope, boundary).map { it.message.id })
    }

    @Test
    fun independentAndLegacyScopes_neverReadOtherRunsInTheSameSpace() = runBlocking<Unit> {
        for (purpose in listOf(MemoryPurpose.INDEPENDENT, MemoryPurpose.LEGACY_COMPAT)) {
            val scene = RoleplayScenario(
                id = "own-${purpose.storageValue}",
                title = "独立读取",
                assistantId = "alice",
                memoryPurpose = purpose
            )
            roleplay.upsertScenario(scene)
            val session = roleplay.startScenario(scene.id).session
            val context = requireNotNull(archive.getRuntimeContext(session.conversationId, "alice"))
            val current = legacy(context, purpose.storageValue, 3)
            val other = runtime()
            val outsiders = legacy(other, "outsider-${purpose.storageValue}", 3)
            database.openHelper.writableDatabase.execSQL(
                "UPDATE memory_conversation_bindings SET spaceId = ?, purpose = ? WHERE conversationId = ?",
                arrayOf(context.spaceId, purpose.storageValue, other.conversationId)
            )
            database.openHelper.writableDatabase.execSQL(
                "UPDATE memory_source_evidence SET spaceId = ? WHERE conversationId = ?",
                arrayOf(context.spaceId, other.conversationId)
            )
            val reading = reader()
            val scope = requireNotNull(reading.authorize(context.scenarioId, context.characterId))
            assertEquals(listOf(context.conversationId), scope.bindings.map { it.conversationId })
            assertEquals(
                current.map { it.id }.toSet(),
                pages(reading, scope, 2).map { it.message.id }.toSet()
            )
            assertTrue(
                pages(
                    reading,
                    scope,
                    2
                ).none { it.message.id in outsiders.map { row -> row.id } })
            val originalRun = context.runId
            database.openHelper.writableDatabase.execSQL(
                "UPDATE memory_conversation_bindings SET runId = 'replacement-run' WHERE conversationId = ?",
                arrayOf(context.conversationId)
            )
            assertNull(reading.refreshScope(scope))
            assertTrue(runCatching { reading.page(scope, limit = 2) }.isFailure)
            assertEquals(originalRun, scope.context.runId)
        }
    }

    @Test
    fun imageOnlyRecord_hasAVisibleStableItemAndReadingDoesNotRebind() = runBlocking<Unit> {
        val context = runtime()
        val image = ChatMessage(
            "image-only", context.conversationId, MessageRole.USER, "", createdAt = 10_000,
            parts = listOf(imageMessagePart("content://synthetic-only/photo"))
        )
        store.upsertMessages(listOf(image))
        val scope = requireNotNull(reader().authorize(context.scenarioId, context.characterId))
        val records = pages(reader(), scope, 5)
        assertEquals(listOf(image.id), records.map { it.message.id })
        val settings =
            AppSettings(assistants = listOf(Assistant(id = "alice")), selectedAssistantId = "alice")
        assertTrue(
            CompanionReadingSupport.items(records, settings)
                .any { it.record.message.id == image.id })
        assertEquals(
            context,
            archive.getRuntimeContext(context.conversationId, context.characterId)
        )
    }

    private suspend fun legacy(
        context: MemoryRuntimeContext,
        prefix: String,
        count: Int
    ): List<ChatMessage> = (0 until count).map { index ->
        ChatMessage(
            "legacy-$prefix-${index.toString().padStart(3, '0')}",
            context.conversationId,
            if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
            "合成原文 $index",
            createdAt = 10_000L + index / 3,
            speakerId = if (index % 2 == 0) "" else context.characterId
        )
    }.also { store.upsertMessages(it) }

    private suspend fun pages(
        reading: CompanionReadingRepository,
        scope: CompanionReadingScope,
        size: Int
    ): List<CompanionReadingRecord> {
        val output = mutableListOf<CompanionReadingRecord>()
        var cursor: CompanionReadingCursor? = null
        do {
            val page = reading.page(scope, cursor, size)
            assertTrue(page.records.size <= size)
            if (cursor != null) assertTrue(page.records.all { it.cursor < cursor!! })
            output += page.records
            cursor = page.nextBefore
        } while (page.hasOlder)
        return output.sortedWith { first, second -> first.cursor.compareTo(second.cursor) }
    }

    private suspend fun delete(context: MemoryRuntimeContext, id: String) {
        store.deleteMessageWithAssociations(
            requireNotNull(store.getConversation(context.conversationId)),
            context.conversationId,
            id
        )
    }
}
