package com.example.myapplication.data.repository.roleplay

import android.app.Application
import com.example.myapplication.data.repository.voice.PrivateCompanionVoiceFilePublisher
import com.example.myapplication.data.repository.voice.syntheticVoiceClip
import com.example.myapplication.data.repository.voice.syntheticVoiceWave
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionReplyProjector
import com.example.myapplication.roleplay.companion.CompanionSpeechSupport
import com.example.myapplication.testutil.CompanionTestFixture
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionHistoryManagementRepositoryTest : CompanionTestFixture() {
    @get:Rule
    val temporary = TemporaryFolder()
    private fun reading() = RoomCompanionReadingRepository(database, archive, roleplay)
    private fun manager() =
        RoomCompanionHistoryManagementRepository(database, reading(), repository)

    private fun at(value: String) = Instant.parse(value).toEpochMilli()
    private fun ChatMessage.ref() = CompanionMessageRevision(id, memoryRevision, conversationId)

    @Test
    fun emptyDirectEncountersRemainRealGroups_withoutInventingLegacyMeetings() = runBlocking<Unit> {
        val context = runtime()
        legacy(context, "unsegmented", 1, at("2026-11-19T08:00:00Z"))
        clock = at("2026-11-19T09:00:00Z")
        val first = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        clock = at("2026-11-19T10:00:00Z")
        val second = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val history = manager()
        val scope = requireNotNull(history.authorize(context.scenarioId, "alice"))
        val page = history.page(scope, CompanionHistoryQuery(date = "2026-11-19"))
        assertEquals(1, page.totalCount)
        assertEquals(
            setOf(first.id, second.id),
            page.groups.filterNot { it.legacy }.mapNotNull { it.encounterId }.toSet()
        )
        assertTrue(page.groups.filterNot { it.legacy }
            .all { it.recordCount == 0 && it.firstMessageId == null })
        assertEquals(1, page.groups.count { it.legacy && it.encounterId == null })
        assertEquals(1, history.page(scope, CompanionHistoryQuery(legacyOnly = true)).groups.size)
    }

    @Test
    fun sameDateAndMidnight_fullAuthorizedHistoryKeepsScenesAndExportsUnloadedRecords() =
        runBlocking<Unit> {
            val current = runtime()
            val other = runtime()
            val expected = mutableSetOf<String>()
            expected += legacy(current, "current", 70, at("2026-11-18T12:00:00Z")).map { it.id }
            expected += legacy(other, "same-space", 55, at("2026-11-18T13:00:00Z")).map { it.id }
            clock = at("2026-11-18T23:50:00Z")
            val first = repository.createEncounter(current, CompanionOpeningMode.DIRECT)
            val midnight = at("2026-11-19T00:00:00Z")
            clock = midnight - 1
            val input = ChatMessage(
                "midnight-input", current.conversationId, MessageRole.USER,
                "原场输入", createdAt = clock, speakerName = "当时用户"
            )
            val turn = repository.beginTurn(first.id, CompanionTurnKind.INPUT, input)
            // canonical 原时间冻结为输入之后，不由迟到补存的时钟决定。
            val frozenReplyTime = maxOf(turn.turn.createdAt, input.createdAt + 1)
            val raw =
                "  <narration>" + "完整舞台原文🙂 é &#65; <literal>  ".repeat(160) + "</narration>\n<observe><detail>原始块</detail></observe>  "
            received(turn, raw)
            clock = at("2026-11-19T00:02:00Z")
            val second = repository.createEncounter(current, CompanionOpeningMode.DIRECT)
            val late = repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
            assertTrue(input.createdAt < midnight)
            assertTrue(frozenReplyTime >= midnight)
            assertEquals(frozenReplyTime, late.replyMessage.createdAt)
            expected += input.id
            expected += late.replyMessage.id
            val next = repository.beginTurn(second.id, CompanionTurnKind.CONTINUE)
            received(next)
            val continued =
                repository.commitAttempt(next.attempt.id) as CompanionCommitResult.Committed
            expected += requireNotNull(continued.inputMessage).id
            expected += continued.replyMessage.id

            legacy(runtime("bob"), "foreign-character", 5, at("2026-11-18T14:00:00Z"))
            val foreignPersona = runtime()
            legacy(foreignPersona, "foreign-persona", 5, at("2026-11-18T14:00:00Z"))
            database.openHelper.writableDatabase.execSQL(
                "UPDATE memory_conversation_bindings SET personaKey = 'mask:other' WHERE conversationId = ?",
                arrayOf(foreignPersona.conversationId)
            )
            database.openHelper.writableDatabase.execSQL(
                "UPDATE memory_source_evidence SET personaKey = 'mask:other' WHERE conversationId = ?",
                arrayOf(foreignPersona.conversationId)
            )
            val group = runtime()
            legacy(group, "foreign-group", 5, at("2026-11-18T14:00:00Z"))
            database.openHelper.writableDatabase.execSQL(
                "UPDATE roleplay_scenarios SET chatType = 'group' WHERE id = ?",
                arrayOf(group.scenarioId)
            )
            val independent = runtime()
            legacy(independent, "foreign-run", 5, at("2026-11-18T14:00:00Z"))
            database.openHelper.writableDatabase.execSQL(
                "UPDATE memory_conversation_bindings SET purpose = 'independent' WHERE conversationId = ?",
                arrayOf(independent.conversationId)
            )

            val history = manager()
            val scope = requireNotNull(history.authorize(current.scenarioId, current.characterId))
            val query = CompanionHistoryQuery(zoneId = "UTC")
            val beforeSession = database.roleplayDao().getSessionByScenario(current.scenarioId)
            val beforeEncounter = repository.getEncounter(second.id)
            val page = history.page(scope, query, limit = 50)
            assertEquals(50, page.records.size)
            assertEquals(expected.size, page.totalCount)
            assertTrue(page.totalCount >= 121)
            val full = history.readAll(scope, query)
            assertEquals(expected, full.map { it.message.id }.toSet())
            assertEquals(full.size, full.map { it.message.id }.distinct().size)
            assertTrue(full.zipWithNext().all { (left, right) -> left.cursor < right.cursor })
            assertEquals(
                first.id,
                full.single { it.message.id == late.replyMessage.id }.encounterId
            )
            assertEquals(
                setOf(first.id, second.id),
                page.groups.mapNotNull { it.encounterId }.toSet()
            )
            assertEquals(
                listOf("2026-11-18", "2026-11-19"),
                page.groups.single { it.encounterId == first.id }.dateLabels
            )
            assertTrue(page.groups.filter { it.legacy }
                .all { "旧记录，未分场" in it.title && it.encounterId == null })
            val nextDate = history.page(scope, query.copy(date = "2026-11-19"), limit = 50)
            assertEquals(
                setOf(first.id, second.id),
                nextDate.groups.mapNotNull { it.encounterId }.toSet()
            )
            assertEquals(3, nextDate.totalCount)
            assertEquals(
                expected.size,
                history.page(scope, query.copy(month = "2026-11"), limit = 50).totalCount
            )
            assertEquals(2, history.readAll(scope, query.copy(encounterId = first.id)).size)
            assertEquals(125, history.readAll(scope, query.copy(legacyOnly = true)).size)

            val output = ByteArrayOutputStream()
            val result = CompanionTextExporter(
                history,
                CompanionTextDocumentWriter { _, bytes -> output.write(bytes) })
                .export(scope, query, "content://synthetic/history.txt")
            assertEquals(CompanionTextExportResult.Saved(expected.size), result)
            val text = output.toString(Charsets.UTF_8.name())
            assertTrue(text.contains("legacy-current-000"))
            assertTrue(text.contains(raw))
            assertTrue(text.contains("类型=CONTINUE"))
            assertTrue(text.contains("USER · 当时用户"))
            assertFalse(text.contains("foreign-character"))
            assertFalse(text.contains("foreign-persona"))
            assertFalse(text.contains("foreign-group"))
            assertFalse(text.contains("foreign-run"))
            assertEquals(
                beforeSession,
                database.roleplayDao().getSessionByScenario(current.scenarioId)
            )
            assertEquals(beforeEncounter, repository.getEncounter(second.id))
            assertEquals(
                current,
                archive.getRuntimeContext(current.conversationId, current.characterId)
            )
        }

    @Test
    fun explicitArchive_isReadOnlyAndAnchoredToCurrentPersonaWithoutRebinding() =
        runBlocking<Unit> {
            val current = runtime()
            val old = independentRuntime("old-history")
            val oldRecords = legacy(old, "old", 6, 10_000L)
            val oldSession = database.roleplayDao().getSessionByScenario(old.scenarioId)
            archive.archiveConversation(old.conversationId)
            assertNull(archive.getRuntimeContext(old.conversationId, "alice"))
            val history = manager()
            val currentScope = requireNotNull(history.authorize(current.scenarioId, "alice"))
            val choice = history.archiveChoices(currentScope).single { it.spaceId == old.spaceId }
            val scope = requireNotNull(
                history.authorize(
                    current.scenarioId,
                    "alice",
                    choice.spaceId,
                    choice.runId
                )
            )
            assertTrue(scope.readOnly)
            assertEquals(current, scope.authorizationContext)
            assertEquals(
                oldRecords.map { it.id },
                history.readAll(scope, CompanionHistoryQuery()).map { it.message.id })
            assertEquals(
                CompanionHistoryMutationResult.ReadOnly,
                history.edit(
                    scope,
                    listOf(CompanionHistoryEdit(oldRecords.first().id, 1, "不允许写入"))
                )
            )
            assertEquals(
                CompanionHistoryMutationResult.ReadOnly,
                history.delete(scope, listOf(oldRecords.first().ref()))
            )
            val located = reading().locate(scope, oldRecords.first().id, 2)
            assertTrue(located.records.any { it.message.id == oldRecords.first().id })
            assertEquals(oldSession, database.roleplayDao().getSessionByScenario(old.scenarioId))
            assertEquals(current, archive.getRuntimeContext(current.conversationId, "alice"))
            assertNull(archive.getRuntimeContext(old.conversationId, "alice"))
            val bob = independentRuntime("bob-old", "bob")
            archive.archiveConversation(bob.conversationId)
            assertNull(history.authorize(current.scenarioId, "alice", bob.spaceId, bob.runId))
            database.openHelper.writableDatabase.execSQL(
                "UPDATE memory_conversation_bindings SET personaKey = 'mask:new' WHERE conversationId = ?",
                arrayOf(current.conversationId)
            )
            assertNull(history.refreshScope(scope))
            assertTrue(runCatching { history.readAll(scope, CompanionHistoryQuery()) }.isFailure)
        }

    @Test
    fun batchEdit_oneStaleRevisionIsAllOrNothing() = runBlocking<Unit> {
        val context = runtime()
        val records = legacy(context, "cas", 2, 10_000)
        val history = manager()
        val scope = requireNotNull(history.authorize(context.scenarioId, "alice"))
        val originals = history.readAll(scope, CompanionHistoryQuery()).map { it.message }
        store.upsertMessages(listOf(originals.last().copy(content = "用户后续修改")))
        val result = history.edit(
            scope,
            originals.map { CompanionHistoryEdit(it.id, it.memoryRevision, "旧界面编辑") })
        assertEquals(CompanionHistoryMutationResult.Conflict, result)
        assertEquals(
            records.first().content,
            database.conversationDao().getMessage(records.first().id)!!.content
        )
        assertEquals(
            "用户后续修改",
            database.conversationDao().getMessage(records.last().id)!!.content
        )
        val after = history.readAll(scope, CompanionHistoryQuery()).map { it.message }
        val applied = history.edit(
            scope,
            after.map { CompanionHistoryEdit(it.id, it.memoryRevision, "真正的批量修订 ${it.id}") })
        assertTrue(applied is CompanionHistoryMutationResult.Applied)
        val edited = history.readAll(scope, CompanionHistoryQuery()).map { it.message }
        assertEquals(after.map { it.createdAt }, edited.map { it.createdAt })
        assertEquals(after.map { it.id }, edited.map { it.id })
        assertTrue(edited.zip(after).all { (new, old) -> new.memoryRevision > old.memoryRevision })
    }

    @Test
    fun batchStorageFailure_rollsBackOriginalsTurnAndSourceInvalidation() = runBlocking<Unit> {
        val context = runtime()
        val turn = prepared(context)
        received(turn, "<dialogue>保留原答。</dialogue>")
        val committed = repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
        val extra = legacy(context, "fault", 1, 10_000).single()
        val history = manager()
        val scope = requireNotNull(history.authorize(context.scenarioId, "alice"))
        val message = history.readAll(scope, CompanionHistoryQuery())
            .single { it.message.id == committed.replyMessage.id }.message
        val source = archive.captureSelectedMessages(
            context,
            MemoryMessageSelection(context.conversationId, listOf(message.id))
        ).sources.single()
        val beforeTurn = repository.getTurn(turn.turn.id)
        val beforeArtifact = repository.getAttempt(turn.attempt.id)?.artifact
        sql("CREATE TRIGGER fail_history_insert BEFORE INSERT ON messages WHEN NEW.id = '${extra.id}' BEGIN SELECT RAISE(ABORT, 'synthetic history failure'); END")
        try {
            val result = runCatching {
                history.edit(
                    scope, listOf(
                        CompanionHistoryEdit(
                            message.id,
                            message.memoryRevision,
                            "<dialogue>不应提交。</dialogue>"
                        ),
                        CompanionHistoryEdit(extra.id, 1, "不应提交第二条")
                    )
                )
            }
            assertTrue(result.isFailure)
            assertEquals(
                message.content,
                database.conversationDao().getMessage(message.id)!!.content
            )
            assertEquals(beforeTurn, repository.getTurn(turn.turn.id))
            assertEquals(beforeArtifact, repository.getAttempt(turn.attempt.id)?.artifact)
            assertEquals(
                MemorySourceStatus.AVAILABLE,
                archive.getSource(context, source.id)?.status
            )
            assertEquals(extra.content, database.conversationDao().getMessage(extra.id)!!.content)
        } finally {
            sql("DROP TRIGGER fail_history_insert")
        }
    }

    @Test
    fun deleteCursorAndStaleBatch_keepStableQueryWithoutPartialDeletion() = runBlocking<Unit> {
        val context = runtime()
        legacy(context, "cursor", 130, 10_000)
        val history = manager()
        val scope = requireNotNull(history.authorize(context.scenarioId, "alice"))
        val query = CompanionHistoryQuery()
        val page = history.page(scope, query, limit = 50)
        val cursor = requireNotNull(page.nextBefore)
        val first = page.records.first().message
        assertTrue(
            history.delete(
                scope,
                listOf(first.ref())
            ) is CompanionHistoryMutationResult.Applied
        )
        val older = history.page(scope, query, cursor, 50)
        assertEquals(50, older.records.size)
        assertTrue(older.records.all { it.cursor < cursor })
        val survivor = older.records.last().message
        assertEquals(
            CompanionHistoryMutationResult.Conflict,
            history.delete(scope, listOf(survivor.ref(), first.ref()))
        )
        assertNotNull(database.conversationDao().getMessage(survivor.id))
        assertEquals(129, history.readAll(scope, query).size)
        assertTrue(
            history.delete(
                scope,
                older.records.map { it.message.ref() }) is CompanionHistoryMutationResult.Applied
        )
        assertEquals(79, history.readAll(scope, query).size)
    }

    @Test
    fun editAndDelete_blockLateGenerationVoiceExtractionButKeepIndependentManualVersionsAndFavorite() =
        runBlocking<Unit> {
            val context = runtime()
            val turn = prepared(context)
            received(turn, "<dialogue>完整旧台词。</dialogue>")
            val original =
                repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
            val message = store.listMessages(context.conversationId)
                .single { it.id == original.replyMessage.id }
            val projected = requireNotNull(CompanionReplyProjector.project(message))
            val units = CompanionSpeechSupport.units(projected)
            val selection = requireNotNull(CompanionSpeechSupport.select(units, units.first().id))
            val clip = syntheticVoiceClip(unit = units.first())
            val voice = RoomCompanionVoiceRepository(
                database,
                PrivateCompanionVoiceFilePublisher(temporary.newFolder("audio")),
                { clock++ })
            val key = SpeechRequestKey(companionRawHash("saved-history-voice"))
            val claim = (voice.claim(key, "saved-voice") as CompanionAudioClaimResult.Granted).claim
            val audio = voice.publish(
                claim,
                syntheticVoiceWave().toByteString().base64(),
                clip
            ) { voice.isSourceCurrent(projected.source) }
            val favorite = voice.favorite(audio)
            voice.releaseOwner("saved-voice")
            val lateClaim = (voice.claim(
                SpeechRequestKey(companionRawHash("late-history-voice")),
                "late-voice"
            ) as CompanionAudioClaimResult.Granted).claim
            val source = archive.captureSelectedMessages(
                context,
                MemoryMessageSelection(context.conversationId, listOf(message.id))
            ).sources.single()
            val machineJob = extractedJob(context, source.fullRef(), "machine")
            val machine = archive.commitJob(
                machineJob.id, "memory-owner", MemoryCommitBatch(
                    records = listOf(
                        MemoryRecordDraft(
                            "dependent",
                            "来源依赖的机器经历",
                            listOf(source.fullRef())
                        ),
                        MemoryRecordDraft(
                            "corrected",
                            "稍后人工修订的机器经历",
                            listOf(source.fullRef())
                        )
                    )
                )
            ) as MemoryWriteResult.Committed
            val correctedId = machine.recordIds.last()
            val corrected = requireNotNull(archive.getRecordDetails(context, correctedId)).record
            assertTrue(
                archive.editRecord(
                    context,
                    correctedId,
                    corrected.revision,
                    "独立人工修订版本"
                ) is MemoryWriteResult.Committed
            )
            val manual = archive.addRecord(
                context,
                ManualMemoryRecordDraft("独立人工记忆")
            ) as MemoryWriteResult.Committed
            val lateJob =
                archive.planSelectedJob(context, "late-extraction", listOf(source.fullRef()))
            assertNotNull(archive.claimJob(lateJob.id, "late-memory"))
            val reroll = requireNotNull(repository.newAttempt(turn.turn.id))
            assertNotNull(repository.claimAttempt(reroll.id, "late-main"))
            assertTrue(
                repository.saveGeneratedArtifact(
                    reroll.id,
                    "late-main",
                    CompanionGeneratedArtifact(
                        "<dialogue>迟到不应恢复。</dialogue>",
                        PromptCompletionStatus.COMPLETED
                    )
                )
            )
            val history = manager()
            val scope = requireNotNull(history.authorize(context.scenarioId, "alice"))
            assertTrue(
                history.edit(
                    scope,
                    listOf(
                        CompanionHistoryEdit(
                            message.id,
                            message.memoryRevision,
                            "<dialogue>合法人工修订。</dialogue>"
                        )
                    )
                ) is CompanionHistoryMutationResult.Applied
            )
            assertTrue(repository.getTurn(turn.turn.id)!!.mutationRevision > turn.turn.mutationRevision)
            assertTrue(repository.commitAttempt(reroll.id) is CompanionCommitResult.Conflict)
            assertFalse(archive.saveExtraction(lateJob.id, "late-memory", ARTIFACT))
            assertTrue(
                archive.commitJob(
                    lateJob.id,
                    "late-memory",
                    MemoryCommitBatch()
                ) is MemoryWriteResult.Conflict
            )
            assertFalse(voice.isSourceCurrent(projected.source))
            assertTrue(runCatching {
                voice.publish(
                    lateClaim,
                    syntheticVoiceWave().toByteString().base64(),
                    clip
                ) { voice.isSourceCurrent(projected.source) }
            }.exceptionOrNull() is CancellationException)
            val edited = history.readAll(scope, CompanionHistoryQuery())
                .single { it.message.id == message.id }.message
            val newUnits =
                CompanionSpeechSupport.units(requireNotNull(CompanionReplyProjector.project(edited)))
            assertFalse(CompanionSpeechSupport.isCurrent(selection, newUnits))
            assertEquals(
                MemoryRecordStatus.STALE,
                archive.getRecordDetails(context, machine.recordIds.first())!!.record.status
            )
            assertTrue(
                history.delete(
                    scope,
                    listOf(edited.ref())
                ) is CompanionHistoryMutationResult.Applied
            )
            assertNull(database.conversationDao().getMessage(message.id))
            assertTrue(repository.commitAttempt(reroll.id) is CompanionCommitResult.Conflict)
            assertEquals(
                "DELETED",
                database.memoryArchiveDao().getRecord(machine.recordIds.first())!!.status
            )
            assertEquals(
                "独立人工修订版本",
                archive.getRecordDetails(context, correctedId)!!.record.content
            )
            assertEquals(
                "独立人工记忆",
                archive.getRecordDetails(context, manual.recordIds.single())!!.record.content
            )
            val surviving = voice.observeFavorites("alice").first().single()
            assertEquals(CompanionVoiceSourceState.UNAVAILABLE, surviving.sourceState)
            assertEquals(favorite.id, surviving.id)
            assertEquals(clip, surviving.audio.clip)
            assertTrue(File(surviving.audio.receipt.path).exists())
            voice.releaseOwner("late-voice")
            voice.clearCache()
            assertEquals(1, voice.usage().favoriteCount)
            assertNotNull(voice.favoriteAudio(favorite.id, "replay"))
            voice.releaseOwner("replay")
        }

    @Test
    fun foreignRolePersonaSpaceAndTypedControl_editsAreRejectedBeforeAnyMutation() =
        runBlocking<Unit> {
            val context = runtime()
            val foreign = runtime("bob")
            val outsider = legacy(foreign, "outsider", 1, 10_000).single()
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val turn = repository.beginTurn(encounter.id, CompanionTurnKind.CONTINUE)
            received(turn)
            val committed =
                repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
            val history = manager()
            val scope = requireNotNull(history.authorize(context.scenarioId, "alice"))
            val control = history.readAll(scope, CompanionHistoryQuery())
                .single { it.message.id == committed.inputMessage!!.id }.message
            assertEquals(
                CompanionHistoryMutationResult.Conflict, history.edit(
                    scope, listOf(
                        CompanionHistoryEdit(control.id, control.memoryRevision, "伪造用户同意")
                    )
                )
            )
            assertEquals(
                CompanionHistoryMutationResult.Conflict,
                history.delete(scope, listOf(outsider.ref()))
            )
            assertEquals(
                COMPANION_CONTINUE_DISPLAY_TEXT,
                database.conversationDao().getMessage(control.id)!!.content
            )
            assertNotNull(database.conversationDao().getMessage(outsider.id))
        }

    @Test
    fun pendingCanonicalAndEditedRecovery_exportFullOriginalsWithoutReaderOrMachineQualification() =
        runBlocking<Unit> {
            val context = runtime()
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val input = ChatMessage(
                "pending-original",
                context.conversationId,
                MessageRole.USER,
                "  未得到回答的原始输入🙂\n<literal>保留原样</literal>  ",
                createdAt = 10_000,
                speakerName = "当时用户"
            )
            val prepared = repository.beginTurn(encounter.id, CompanionTurnKind.INPUT, input)
            val history = manager()
            val scope = requireNotNull(history.authorize(context.scenarioId, "alice"))
            val original = history.readAll(scope, CompanionHistoryQuery()).single()
            assertEquals(input.content, original.message.content)
            assertFalse(original.readerEligible)
            assertTrue(reading().readAll(scope).isEmpty())

            val editedRaw = "\n人工改过、仍未得到回复的完整输入 é <dialogue>字面标签</dialogue>  "
            assertTrue(
                history.edit(
                    scope,
                    listOf(
                        CompanionHistoryEdit(
                            input.id,
                            original.message.memoryRevision,
                            editedRaw
                        )
                    )
                )
                        is CompanionHistoryMutationResult.Applied
            )
            val edited = history.readAll(scope, CompanionHistoryQuery()).single()
            assertEquals(editedRaw, edited.message.content)
            assertTrue(edited.message.memoryRevision > original.message.memoryRevision)
            assertEquals(
                edited.message.memoryRevision,
                repository.getTurn(prepared.turn.id)!!.inputRevision
            )
            assertTrue(
                repository.getAttempt(prepared.attempt.id)!!.state in setOf(
                    CompanionAttemptState.CANCELLED,
                    CompanionAttemptState.UNCERTAIN
                )
            )
            assertFalse(edited.readerEligible)
            assertEquals(
                CompanionSourceEligibility.UNAVAILABLE,
                edited.message.companionSourceEligibility()
            )

            val continued = repository.beginTurn(encounter.id, CompanionTurnKind.CONTINUE)
            val all = history.readAll(scope, CompanionHistoryQuery())
            assertEquals(
                setOf(input.id, continued.turn.inputMessageId!!),
                all.map { it.message.id }.toSet()
            )
            assertTrue(all.none { it.readerEligible })
            assertTrue(reading().readAll(scope).isEmpty())
            assertTrue(runCatching { reading().locate(scope, input.id) }.isFailure)
            assertTrue(
                archive.captureSelectedMessages(
                    context, MemoryMessageSelection(
                        context.conversationId,
                        all.map { it.message.id })
                ).sources.isEmpty()
            )
            val page = history.page(scope, CompanionHistoryQuery())
            assertEquals(2, page.totalCount)
            assertNull(page.groups.single { it.encounterId == encounter.id }.firstMessageId)

            val output = ByteArrayOutputStream()
            assertEquals(
                CompanionTextExportResult.Saved(2), CompanionTextExporter(
                    history,
                    CompanionTextDocumentWriter { _, bytes -> output.write(bytes) }).export(
                    scope, CompanionHistoryQuery(),
                    "content://synthetic/pending.txt"
                )
            )
            val text = output.toString(Charsets.UTF_8.name())
            assertTrue(text.contains(editedRaw))
            assertTrue(text.contains(COMPANION_CONTINUE_DISPLAY_TEXT))
            assertTrue(text.contains("类型=INPUT"))
            assertTrue(text.contains("类型=CONTINUE"))
            assertTrue(text.contains("回合待完成"))
            assertEquals(context, archive.getRuntimeContext(context.conversationId, "alice"))
        }

    @Test
    fun pendingCanonicalQuery_rejectsForeignPersonaRunPreviewAndCorruptInputGraph() =
        runBlocking<Unit> {
            val context = runtime()
            val pending = prepared(context, text = "当前授权待完成原文")
            val foreign = prepared(runtime("bob"), text = "其它角色秘密")
            val persona = prepared(runtime(), text = "其它身份秘密")
            database.openHelper.writableDatabase.execSQL(
                "UPDATE memory_conversation_bindings SET personaKey = 'mask:other' WHERE conversationId = ?",
                arrayOf(persona.attempt.context.conversationId)
            )
            database.openHelper.writableDatabase.execSQL(
                "UPDATE memory_source_evidence SET personaKey = 'mask:other' WHERE conversationId = ?",
                arrayOf(persona.attempt.context.conversationId)
            )
            val independent =
                prepared(independentRuntime("pending-independent"), text = "独立运行秘密")
            val draft = prepared(context, CompanionTurnKind.OPENING, CompanionOpeningMode.APPROACH)
            received(draft, "<dialogue>未接受的开场产物，不能导出。</dialogue>")
            val history = manager()
            val scope = requireNotNull(history.authorize(context.scenarioId, "alice"))
            assertEquals(
                listOf(pending.turn.inputMessageId!!),
                history.readAll(scope, CompanionHistoryQuery()).map { it.message.id })
            assertFalse(history.readAll(scope, CompanionHistoryQuery()).any {
                it.message.id in setOf(
                    foreign.turn.inputMessageId!!,
                    persona.turn.inputMessageId!!,
                    independent.turn.inputMessageId!!
                )
            })
            assertTrue(reading().readAll(scope).isEmpty())
            val output = ByteArrayOutputStream()
            CompanionTextExporter(
                history,
                CompanionTextDocumentWriter { _, bytes -> output.write(bytes) })
                .export(scope, CompanionHistoryQuery(), "content://synthetic/only-authorized.txt")
            assertFalse(output.toString(Charsets.UTF_8.name()).contains("未接受的开场产物"))

            val messageId = pending.turn.inputMessageId!!
            val inputRevision = repository.getTurn(pending.turn.id)!!.inputRevision!!
            database.openHelper.writableDatabase.execSQL(
                "UPDATE companion_turns SET inputRevision = ? WHERE id = ?",
                arrayOf(inputRevision + 1, pending.turn.id)
            )
            assertTrue(history.readAll(scope, CompanionHistoryQuery()).isEmpty())
            database.openHelper.writableDatabase.execSQL(
                "UPDATE companion_turns SET inputRevision = ? WHERE id = ?",
                arrayOf(inputRevision, pending.turn.id)
            )
            database.openHelper.writableDatabase.execSQL(
                "UPDATE messages SET companionTurnId = 'missing-link' WHERE id = ?",
                arrayOf(messageId)
            )
            assertTrue(history.readAll(scope, CompanionHistoryQuery()).isEmpty())
            database.openHelper.writableDatabase.execSQL(
                "UPDATE messages SET companionTurnId = ?, companionMemoryState = 'legacy' WHERE id = ?",
                arrayOf(pending.turn.id, messageId)
            )
            assertTrue(history.readAll(scope, CompanionHistoryQuery()).isEmpty())
            database.openHelper.writableDatabase.execSQL(
                "UPDATE messages SET companionMemoryState = 'waiting_turn' WHERE id = ?",
                arrayOf(messageId)
            )
            database.openHelper.writableDatabase.execSQL(
                "UPDATE companion_turns SET kind = 'FUTURE_KIND' WHERE id = ?",
                arrayOf(pending.turn.id)
            )
            assertTrue(history.readAll(scope, CompanionHistoryQuery()).isEmpty())
            assertTrue(reading().readAll(scope).isEmpty())
            database.openHelper.writableDatabase.execSQL(
                "UPDATE companion_turns SET kind = 'INPUT' WHERE id = ?",
                arrayOf(pending.turn.id)
            )
            database.openHelper.writableDatabase.execSQL(
                "UPDATE companion_encounters SET state = 'FUTURE_STATE' WHERE id = ?",
                arrayOf(pending.turn.encounterId)
            )
            assertTrue(history.readAll(scope, CompanionHistoryQuery()).isEmpty())
            assertTrue(history.page(scope, CompanionHistoryQuery()).groups.isEmpty())
        }

    @Test
    fun archivedDailyOriginals_remainInDefaultReaderButMixedMutationIsReadOnly() =
        runBlocking<Unit> {
            val current = runtime()
            val old = runtime()
            assertEquals(current.spaceId, old.spaceId)
            val oldRows = legacy(old, "daily-archived", 2, 10_000)
            val currentRows = legacy(current, "daily-current", 1, 20_000)
            archive.archiveConversation(old.conversationId)
            assertNull(archive.getRuntimeContext(old.conversationId, "alice"))
            val history = manager()
            val scope = requireNotNull(history.authorize(current.scenarioId, "alice"))
            val records = reading().readAll(scope)
            assertEquals(
                (oldRows + currentRows).map { it.id }.toSet(),
                records.map { it.message.id }.toSet()
            )
            assertTrue(records.filter { it.message.id in oldRows.map { row -> row.id } }
                .all { it.binding.readOnly })
            assertTrue(records.filter { it.message.id in currentRows.map { row -> row.id } }
                .none { it.binding.readOnly })
            assertEquals(CompanionHistoryMutationResult.ReadOnly, history.edit(scope, records.map {
                CompanionHistoryEdit(it.message.id, it.message.memoryRevision, "不能部分覆盖")
            }))
            assertEquals(
                CompanionHistoryMutationResult.ReadOnly,
                history.delete(scope, records.map { it.message.ref() })
            )
            assertEquals(
                currentRows.single().content,
                database.conversationDao().getMessage(currentRows.single().id)!!.content
            )
            assertTrue(oldRows.all { database.conversationDao().getMessage(it.id) != null })
            assertEquals(current, archive.getRuntimeContext(current.conversationId, "alice"))
        }

    @Test
    fun pendingInputDeletion_rejectsStoredLateReplyWithoutResurrectingSource() = runBlocking<Unit> {
        val context = runtime()
        val pending = prepared(context, text = "删除前尚未提交的输入")
        received(pending, "<dialogue>已经收到但不能复活的结果。</dialogue>")
        val history = manager()
        val scope = requireNotNull(history.authorize(context.scenarioId, "alice"))
        val source = history.readAll(scope, CompanionHistoryQuery()).single()
        assertFalse(source.readerEligible)
        assertTrue(
            history.delete(
                scope,
                listOf(source.message.ref())
            ) is CompanionHistoryMutationResult.Applied
        )
        assertNull(database.conversationDao().getMessage(source.message.id))
        assertTrue(repository.commitAttempt(pending.attempt.id) is CompanionCommitResult.Conflict)
        assertTrue(history.readAll(scope, CompanionHistoryQuery()).isEmpty())
        assertTrue(reading().readAll(scope).isEmpty())
        assertTrue(
            database.memoryArchiveDao()
                .listMessageSources(context.conversationId, source.message.id)
                .all { it.status == "DELETED" })
    }

    @Test
    fun textBatchEdit_preservesLegacyAndPendingMediaAndUnknownAttachmentFields() =
        runBlocking<Unit> {
            val context = runtime()
            val image =
                imageMessagePart("content://synthetic/retained-photo").copy(fileName = "原图片.png")
            val imageOnly = ChatMessage(
                "legacy-image-original", context.conversationId, MessageRole.USER,
                "", createdAt = 10_000, parts = listOf(image)
            )
            store.upsertMessages(listOf(imageOnly))
            val initialEntity = database.conversationDao().getMessage(imageOnly.id)!!
            val imageJson =
                com.google.gson.JsonParser.parseString(initialEntity.partsJson).asJsonArray
            imageJson[0].asJsonObject.addProperty(
                "futureAttachmentHint",
                "retain-existing-extension"
            )
            database.openHelper.writableDatabase.execSQL(
                "UPDATE messages SET partsJson = ? WHERE id = ?",
                arrayOf(imageJson.toString(), imageOnly.id)
            )
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val pending = repository.beginTurn(
                encounter.id, CompanionTurnKind.INPUT, ChatMessage(
                    "pending-media-original",
                    context.conversationId,
                    MessageRole.USER,
                    "待完成文字",
                    createdAt = 20_000,
                    parts = listOf(textMessagePart("待完成文字"), image)
                )
            )
            val pendingEntity =
                database.conversationDao().getMessage(pending.turn.inputMessageId!!)!!
            val history = manager()
            val scope = requireNotNull(history.authorize(context.scenarioId, "alice"))
            val originals = history.readAll(scope, CompanionHistoryQuery())
            assertEquals(
                setOf(imageOnly.id, pendingEntity.id),
                originals.map { it.message.id }.toSet()
            )
            val imageRecord = originals.single { it.message.id == imageOnly.id }
            assertTrue(CompanionTextExportContent.raw(imageRecord).contains("IMAGE"))
            assertEquals("", CompanionTextExportContent.editableText(imageRecord))
            val edits = originals.map {
                CompanionHistoryEdit(
                    it.message.id,
                    it.message.memoryRevision,
                    "只修改文字 ${it.message.id}"
                )
            }
            assertTrue(history.edit(scope, edits) is CompanionHistoryMutationResult.Applied)
            val after = history.readAll(scope, CompanionHistoryQuery())
            assertEquals(originals.map { it.message.id }, after.map { it.message.id })
            after.forEach { record ->
                assertEquals("只修改文字 ${record.message.id}", record.message.content)
                assertEquals(
                    listOf(image),
                    record.message.parts.filter { it.type == ChatMessagePartType.IMAGE })
                assertEquals(
                    record.message.content,
                    record.message.parts.single { it.type == ChatMessagePartType.TEXT }.text
                )
                assertTrue(record.message.memoryRevision > originals.single { it.message.id == record.message.id }.message.memoryRevision)
            }
            val preserved = com.google.gson.JsonParser.parseString(
                database.conversationDao().getMessage(imageOnly.id)!!.partsJson
            ).asJsonArray
                .first { it.asJsonObject.get("type").asString == "IMAGE" }.asJsonObject
            assertEquals(
                "retain-existing-extension",
                preserved.get("futureAttachmentHint").asString
            )
            assertFalse(after.single { it.message.id == pendingEntity.id }.readerEligible)
            assertEquals(listOf(imageOnly.id), reading().readAll(scope).map { it.message.id })
        }

    @Test
    fun knownTurnLinks_cannotBecomeLegacyOrMutateThroughBrokenMessageTags() = runBlocking<Unit> {
        val context = runtime()
        val prepared = prepared(context, text = "有关联的原始输入")
        received(prepared, "<dialogue>有关联的原始回答。</dialogue>")
        val committed =
            repository.commitAttempt(prepared.attempt.id) as CompanionCommitResult.Committed
        val history = manager()
        val scope = requireNotNull(history.authorize(context.scenarioId, "alice"))
        val originals = history.readAll(scope, CompanionHistoryQuery()).map { it.message }
        assertEquals(
            setOf(committed.inputMessage!!.id, committed.replyMessage.id),
            originals.map { it.id }.toSet()
        )
        val originalTurn = repository.getTurn(prepared.turn.id)

        for (message in originals) {
            val entity = database.conversationDao().getMessage(message.id)!!
            for (brokenLink in listOf("missing-turn-link", "")) {
                val brokenState =
                    if (brokenLink.isEmpty()) "legacy" else entity.companionMemoryState
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE messages SET companionTurnId = ?, companionMemoryState = ? WHERE id = ?",
                    arrayOf(brokenLink, brokenState, message.id)
                )

                assertFalse(reading().readAll(scope).any { it.message.id == message.id })
                assertFalse(
                    history.readAll(scope, CompanionHistoryQuery())
                        .any { it.message.id == message.id })
                assertFalse(
                    repository.mutateMessages(
                        listOf(
                            CompanionMessageMutation(
                                message.ref(),
                                "不能覆盖损坏关联的原文"
                            )
                        )
                    )
                )
                assertFalse(repository.mutateMessages(listOf(CompanionMessageMutation(message.ref()))))
                assertEquals(
                    message.content,
                    database.conversationDao().getMessage(message.id)!!.content
                )
                assertEquals(
                    message.memoryRevision,
                    database.conversationDao().getMessage(message.id)!!.memoryRevision
                )
                assertEquals(originalTurn, repository.getTurn(prepared.turn.id))

                database.openHelper.writableDatabase.execSQL(
                    "UPDATE messages SET companionTurnId = ?, companionMemoryState = ? WHERE id = ?",
                    arrayOf(entity.companionTurnId, entity.companionMemoryState, message.id)
                )
            }
        }
        assertEquals(
            originals.map { it.id },
            history.readAll(scope, CompanionHistoryQuery()).map { it.message.id })
    }

    private suspend fun legacy(
        context: MemoryRuntimeContext,
        prefix: String,
        count: Int,
        base: Long
    ): List<ChatMessage> =
        (0 until count).map { index ->
            ChatMessage(
                "legacy-$prefix-${index.toString().padStart(3, '0')}",
                context.conversationId,
                if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                "合成完整原文 $prefix $index",
                createdAt = base + index / 3,
                speakerId = if (index % 2 == 0) "" else context.characterId
            )
        }
            .also { store.upsertMessages(it) }

    private suspend fun independentRuntime(
        id: String,
        character: String = "alice"
    ): MemoryRuntimeContext {
        roleplay.upsertScenario(
            RoleplayScenario(
                id = id,
                title = "合成旧档",
                assistantId = character,
                memoryPurpose = MemoryPurpose.INDEPENDENT
            )
        )
        val session = roleplay.startScenario(id).session
        return requireNotNull(archive.getRuntimeContext(session.conversationId, character))
    }

    private suspend fun extractedJob(
        context: MemoryRuntimeContext,
        ref: MemorySourceRef,
        key: String
    ): MemoryProcessingJob {
        val job = archive.planSelectedJob(context, key, listOf(ref))
        assertNotNull(archive.claimJob(job.id, "memory-owner"))
        assertTrue(archive.saveExtraction(job.id, "memory-owner", ARTIFACT))
        return job
    }

    companion object {
        private const val ARTIFACT =
            "{\"schema_version\":2,\"memories\":[],\"corrections\":[],\"state_updates\":[],\"resident_candidates\":[]}"
    }
}
