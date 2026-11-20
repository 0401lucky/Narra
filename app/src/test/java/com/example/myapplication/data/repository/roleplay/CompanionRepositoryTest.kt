package com.example.myapplication.data.repository.roleplay

import android.app.Application
import com.example.myapplication.conversation.ConversationSummaryCoverageSupport
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.repository.context.RoomMemoryRepository
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.CompanionTestFixture
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionRepositoryTest : CompanionTestFixture() {
    @Test
    fun pending_isSavedButNotMachineSource() = runBlocking {
        val context = runtime()
        val turn = prepared(context)
        val input = store.listMessages(context.conversationId).single()
        assertEquals(MessageStatus.COMPLETED, input.status)
        assertEquals("一起坐一会儿", input.content)
        assertEquals(CompanionSourceEligibility.UNAVAILABLE, input.companionSourceEligibility())
        assertTrue(archive.listPendingIngress(context).isEmpty())
        assertTrue(RoleplayTranscriptFormatter.memorySemanticSegments(input).isEmpty())
        assertFalse(ConversationSummaryCoverageSupport.canSummarize(input))
        val manual = archive.captureSelectedMessages(
            context,
            MemoryMessageSelection(context.conversationId, listOf(input.id))
        )
        assertTrue(manual.sources.isEmpty())
        assertEquals(CompanionMemoryState.WAITING_TURN, input.companionMemoryState)
        assertEquals(
            CompanionAttemptState.NOT_STARTED,
            repository.getAttempt(turn.attempt.id)!!.state
        )
    }

    @Test
    fun completed_promotesOriginalInputAndQueuesOnce() = runBlocking {
        val context = runtime()
        val turn = prepared(context)
        val original = store.listMessages(context.conversationId).single()
        received(turn)
        val first = repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
        assertEquals(original.id, first.inputMessage!!.id)
        assertEquals(original.content, first.inputMessage!!.content)
        assertTrue(first.inputMessage!!.memoryRevision > original.memoryRevision)
        assertEquals(
            CompanionSourceEligibility.TEXT_ALLOWED,
            first.inputMessage!!.companionSourceEligibility()
        )
        assertEquals(
            CompanionSourceEligibility.TEXT_ALLOWED,
            first.replyMessage.companionSourceEligibility()
        )
        val events = archive.listPendingIngress(context)
        assertEquals(2, events.size)
        assertTrue(events.all { it.source.personaKey == context.personaKey })
        val again = repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
        assertTrue(again.alreadyCommitted)
        assertEquals(first.replyMessage.id, again.replyMessage.id)
        assertEquals(2, store.listMessages(context.conversationId).size)
        assertEquals(2, archive.listPendingIngress(context).size)
    }

    @Test
    fun twoOwners_claimExactlyOnce() = runBlocking {
        val turn = prepared()
        val other = RoomCompanionRepository(database, archive, { clock++ })
        val results = coroutineScope {
            listOf(
                async { repository.claimAttempt(turn.attempt.id, "one") },
                async { other.claimAttempt(turn.attempt.id, "two") }).awaitAll()
        }
        assertEquals(1, results.count { it != null })
        assertNull(repository.claimAttempt(turn.attempt.id, "third"))
    }

    @Test
    fun sameText_canBeSeparateTurns() = runBlocking {
        val first = prepared()
        received(first)
        repository.commitAttempt(first.attempt.id)
        val context = first.attempt.context
        val second = repository.beginTurn(
            first.turn.encounterId, CompanionTurnKind.INPUT,
            ChatMessage(
                "second",
                context.conversationId,
                MessageRole.USER,
                "一起坐一会儿",
                createdAt = clock++
            )
        )
        assertNotEquals(first.turn.id, second.turn.id)
        assertEquals(first.turn.ordinal + 1, second.turn.ordinal)
    }

    @Test
    fun transactionFailure_keepsArtifactAndOldCanonical() = runBlocking {
        useFileDatabase()
        val turn = prepared()
        received(turn, "原答")
        val original = repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
        val reroll = repository.newAttempt(turn.turn.id)!!
        repository.claimAttempt(reroll.id, "reroll")
        repository.saveGeneratedArtifact(
            reroll.id,
            "reroll",
            CompanionGeneratedArtifact("替换答", PromptCompletionStatus.COMPLETED)
        )
        sql("CREATE TRIGGER fail_commit BEFORE UPDATE ON companion_turns WHEN NEW.committedRevision > OLD.committedRevision BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        assertTrue(runCatching { repository.commitAttempt(reroll.id) }.isFailure)
        assertEquals("原答", store.listMessages(turn.attempt.context.conversationId).last().content)
        assertEquals(CompanionAttemptState.RESULT_READY, repository.getAttempt(reroll.id)!!.state)
        sql("DROP TRIGGER fail_commit")
        reopen()
        val retried = repository.commitAttempt(reroll.id) as CompanionCommitResult.Committed
        assertEquals(original.replyMessage.id, retried.replyMessage.id)
        assertEquals(original.replyMessage.createdAt, retried.replyMessage.createdAt)
        assertEquals("替换答", retried.replyMessage.content)
        assertTrue(retried.replyMessage.memoryRevision > original.replyMessage.memoryRevision)
    }

    @Test
    fun externalEdit_rejectsRerollAndKeepsNewText() = runBlocking {
        val turn = prepared()
        received(turn, "原答")
        val original = repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
        val reroll = repository.newAttempt(turn.turn.id)!!
        repository.claimAttempt(reroll.id, "owner")
        repository.saveGeneratedArtifact(
            reroll.id,
            "owner",
            CompanionGeneratedArtifact("迟到答", PromptCompletionStatus.COMPLETED)
        )
        store.upsertMessages(listOf(original.replyMessage.copy(content = "用户修订")))
        assertTrue(repository.commitAttempt(reroll.id) is CompanionCommitResult.Conflict)
        assertEquals(
            "用户修订",
            store.listMessages(turn.attempt.context.conversationId).last().content
        )
        assertEquals(
            CompanionSourceEligibility.TEXT_ALLOWED,
            store.listMessages(turn.attempt.context.conversationId).last()
                .companionSourceEligibility()
        )
        assertNull(database.companionDao().getAttempt(reroll.id)!!.artifactJson)
    }

    @Test
    fun pendingEdit_retriesSameTurnWithNewInputRevision() = runBlocking<Unit> {
        useFileDatabase()
        for (requested in listOf(false, true)) {
            val context = runtime()
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val input = ChatMessage(
                "pending-$requested", context.conversationId,
                MessageRole.USER, "原输入", createdAt = clock++
            )
            val turn = repository.beginTurn(
                encounter.id, CompanionTurnKind.INPUT, input,
                CompanionRequestDescriptor(
                    sourceMessages = listOf(
                        CompanionMessageRevision(input.id, 1, context.conversationId)
                    )
                )
            )
            if (requested) assertNotNull(repository.claimAttempt(turn.attempt.id, "old-owner"))
            assertTrue(repository.editMessage(input.id, turn.turn.inputRevision!!, "修改后的输入"))
            val edited = repository.messages(encounter.id).single()
            assertTrue(edited.memoryRevision > turn.turn.inputRevision!!)
            assertEquals(
                CompanionSourceEligibility.UNAVAILABLE,
                edited.companionSourceEligibility()
            )
            assertNull(repository.claimAttempt(turn.attempt.id, "new-owner"))
            assertFalse(
                repository.saveGeneratedArtifact(
                    turn.attempt.id, "old-owner",
                    CompanionGeneratedArtifact("过期回复", PromptCompletionStatus.COMPLETED)
                )
            )
            assertTrue(repository.commitAttempt(turn.attempt.id) is CompanionCommitResult.Conflict)
            assertEquals(1, database.companionDao().listAttempts(turn.turn.id).size)
            reopen()
            if (requested) assertNull(repository.newAttempt(turn.turn.id))
            val retry = repository.newAttempt(turn.turn.id, allowUncertainRetry = requested)
            assertNotNull("修改输入后应能显式重试同一回合", retry)
            retry!!
            assertEquals(turn.turn.id, retry.turnId)
            assertEquals(edited.memoryRevision, retry.expectedInputRevision)
            assertEquals(edited.memoryRevision, retry.descriptor.sourceMessages.single().revision)
            assertEquals(CompanionAttemptState.NOT_STARTED, retry.state)
            assertNull(retry.requestedAt)
            assertEquals(2, database.companionDao().listAttempts(turn.turn.id).size)
            received(
                CompanionPreparedTurn(repository.getTurn(turn.turn.id)!!, retry),
                "新输入的回复"
            )
            val saved = repository.commitAttempt(retry.id) as CompanionCommitResult.Committed
            assertEquals(input.id, saved.inputMessage!!.id)
            assertEquals("修改后的输入", saved.inputMessage!!.content)
            assertEquals("新输入的回复", saved.replyMessage.content)
            assertEquals(2, repository.messages(encounter.id).size)
        }
        val deleted = prepared()
        val original = repository.messages(deleted.turn.encounterId).single()
        assertTrue(repository.editMessage(original.id, original.memoryRevision, "待删除输入"))
        val edited = repository.messages(deleted.turn.encounterId).single()
        assertTrue(repository.deleteMessage(edited.id, edited.memoryRevision))
        assertNull(repository.newAttempt(deleted.turn.id, allowUncertainRetry = true))
        assertTrue(repository.messages(deleted.turn.encounterId).isEmpty())
    }

    @Test
    fun deletingSource_preventsResurrection() = runBlocking {
        val turn = prepared()
        received(turn, "旧正文")
        val first = repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
        val reroll = repository.newAttempt(turn.turn.id)!!
        repository.claimAttempt(reroll.id, "owner")
        repository.saveGeneratedArtifact(
            reroll.id,
            "owner",
            CompanionGeneratedArtifact("晚到", PromptCompletionStatus.COMPLETED)
        )
        assertTrue(
            repository.deleteMessage(
                first.replyMessage.id,
                first.replyMessage.memoryRevision
            )
        )
        assertTrue(repository.commitAttempt(reroll.id) is CompanionCommitResult.Conflict)
        assertEquals(1, store.listMessages(turn.attempt.context.conversationId).size)
        assertNull(database.companionDao().getAttempt(turn.attempt.id)!!.artifactJson)
    }

    @Test
    fun endedAndOnline_canCommitReceivedWithoutReactivation() = runBlocking {
        val context = runtime()
        val turn = prepared(context)
        received(turn, "本场已收到的回复")
        repository.endEncounter(turn.turn.encounterId)
        val next = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val scenario = roleplay.getScenario(context.scenarioId)!!
        roleplay.upsertScenario(scenario.copy(interactionMode = RoleplayInteractionMode.ONLINE_PHONE))
        val result = repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
        assertEquals("本场已收到的回复", result.replyMessage.content)
        assertEquals(
            RoleplayInteractionMode.OFFLINE_DIALOGUE,
            result.replyMessage.roleplayInteractionMode
        )
        assertEquals(
            CompanionEncounterState.ENDED,
            repository.getEncounter(turn.turn.encounterId)!!.state
        )
        assertEquals(CompanionEncounterState.ACTIVE, repository.getEncounter(next.id)!!.state)
        assertTrue(repository.messages(next.id).isEmpty())
        assertNull(repository.newAttempt(turn.turn.id))
    }

    @Test
    fun acceptedOpeningReroll_canBeSavedAfterLeaving() = runBlocking<Unit> {
        val context = runtime()
        val opening = prepared(context, CompanionTurnKind.OPENING, CompanionOpeningMode.INVITE)
        received(opening, "已接受开场")
        val first =
            repository.commitAttempt(opening.attempt.id, true) as CompanionCommitResult.Committed
        val reroll = repository.newAttempt(opening.turn.id)!!
        repository.claimAttempt(reroll.id, "reroll")
        repository.saveGeneratedArtifact(
            reroll.id, "reroll",
            CompanionGeneratedArtifact("替换已接受开场", PromptCompletionStatus.COMPLETED)
        )
        repository.endEncounter(opening.turn.encounterId)
        val newer = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        roleplay.upsertScenario(
            roleplay.getScenario(context.scenarioId)!!.copy(
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE
            )
        )
        val result = repository.commitAttempt(reroll.id, true) as CompanionCommitResult.Committed
        assertEquals(first.replyMessage.id, result.replyMessage.id)
        assertEquals("替换已接受开场", result.replyMessage.content)
        assertEquals(
            CompanionEncounterState.ENDED,
            repository.getEncounter(opening.turn.encounterId)!!.state
        )
        assertEquals(CompanionEncounterState.ACTIVE, repository.getEncounter(newer.id)!!.state)
    }

    @Test
    fun historyCrud_worksAfterReturningOnline() = runBlocking<Unit> {
        val context = runtime()
        val turn = prepared(context)
        received(turn)
        val first = repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
        roleplay.upsertScenario(
            roleplay.getScenario(context.scenarioId)!!.copy(
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE
            )
        )
        assertTrue(
            repository.editMessage(
                first.replyMessage.id,
                first.replyMessage.memoryRevision,
                "线上回看时修订"
            )
        )
        val updated =
            store.listMessages(context.conversationId).single { it.id == first.replyMessage.id }
        assertEquals("线上回看时修订", updated.content)
        assertTrue(repository.deleteMessage(updated.id, updated.memoryRevision))
        assertEquals(1, repository.messages(turn.turn.encounterId).size)
    }

    @Test
    fun newerScene_supersedesOldDraftPermanently() = runBlocking {
        val context = runtime()
        val draft = prepared(context, CompanionTurnKind.OPENING, CompanionOpeningMode.APPROACH)
        received(draft, "废弃开场")
        val newer = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        assertTrue(
            repository.commitAttempt(
                draft.attempt.id,
                acceptOpening = true
            ) is CompanionCommitResult.Conflict
        )
        assertEquals(CompanionEncounterState.ACTIVE, repository.getEncounter(newer.id)!!.state)
        assertTrue(store.listMessages(context.conversationId).isEmpty())
        assertTrue(archive.listPendingIngress(context).isEmpty())
    }

    @Test
    fun preview_onlySelectedAcceptedArtifactEntersHistory() = runBlocking {
        val context = runtime()
        val first = prepared(context, CompanionTurnKind.OPENING, CompanionOpeningMode.INVITE)
        received(first, "首个预览")
        assertTrue(store.listMessages(context.conversationId).isEmpty())
        assertTrue(repository.commitAttempt(first.attempt.id) is CompanionCommitResult.Conflict)
        val next = repository.newAttempt(first.turn.id)!!
        repository.claimAttempt(next.id, "next")
        repository.saveGeneratedArtifact(
            next.id,
            "next",
            CompanionGeneratedArtifact("选中开场", PromptCompletionStatus.COMPLETED)
        )
        assertTrue(
            repository.commitAttempt(
                first.attempt.id,
                true
            ) is CompanionCommitResult.Conflict
        )
        val result = repository.commitAttempt(next.id, true) as CompanionCommitResult.Committed
        assertNull(result.inputMessage)
        assertEquals("选中开场", result.replyMessage.content)
        assertEquals(1, archive.listPendingIngress(context).size)
        assertEquals(1, store.listMessages(context.conversationId).size)
    }

    @Test
    fun uncertain_requiresExplicitRetryAndNeverReclaims() = runBlocking {
        useFileDatabase()
        val turn = prepared()
        repository.claimAttempt(turn.attempt.id, "owner", 1000)
        clock += 10_000
        reopen()
        assertEquals(
            CompanionAttemptState.UNCERTAIN,
            repository.recoverEncounter(turn.turn.encounterId).single().state
        )
        assertNull(repository.claimAttempt(turn.attempt.id, "new"))
        assertNull(repository.newAttempt(turn.turn.id))
        assertNotNull(repository.newAttempt(turn.turn.id, allowUncertainRetry = true))
    }

    @Test
    fun changedPersona_refusesLateArtifact() = runBlocking {
        val context = runtime()
        val turn = prepared(context)
        repository.claimAttempt(turn.attempt.id, "owner")
        val scenario = roleplay.getScenario(context.scenarioId)!!
        roleplay.upsertScenario(scenario.copy(userPersonaOverride = "另一个合成身份"))
        roleplay.startScenario(scenario.id)
        assertFalse(
            repository.saveGeneratedArtifact(
                turn.attempt.id, "owner",
                CompanionGeneratedArtifact("旧身份晚到答", PromptCompletionStatus.COMPLETED)
            )
        )
        assertEquals(1, store.listMessages(context.conversationId).size)
        assertTrue(archive.listPendingIngress(context).isEmpty())
    }

    @Test
    fun stoppedOrUnknown_neverPublishesReadySources() = runBlocking {
        for (status in listOf(
            PromptCompletionStatus.UNKNOWN, PromptCompletionStatus.TRUNCATED,
            PromptCompletionStatus.FILTERED, PromptCompletionStatus.CANCELLED
        )) {
            val turn = prepared()
            received(turn, "未确认完整的片段", status)
            assertEquals(
                CompanionCommitResult.Conflict(CompanionConflict.INCOMPLETE_RESULT),
                repository.commitAttempt(turn.attempt.id)
            )
            assertTrue(archive.listPendingIngress(turn.attempt.context).isEmpty())
        }
    }

    @Test
    fun corruptArtifact_doesNotCommit() = runBlocking {
        val turn = prepared()
        received(turn)
        sql("UPDATE companion_attempts SET artifactHash = 'bad'")
        assertEquals(
            CompanionCommitResult.Conflict(CompanionConflict.INVALID_ARTIFACT),
            repository.commitAttempt(turn.attempt.id)
        )
        assertTrue(archive.listPendingIngress(turn.attempt.context).isEmpty())
    }

    @Test
    fun consentBeforeClaim_preventsRequestedState() = runBlocking {
        val turn = prepared()
        allowed = false
        assertNull(repository.claimAttempt(turn.attempt.id, "owner"))
        assertEquals(
            CompanionAttemptState.NOT_STARTED,
            repository.getAttempt(turn.attempt.id)!!.state
        )
    }

    @Test
    fun continue_hasNoopCoverageButNoUserFacts() = runBlocking {
        val context = runtime()
        val turn = prepared(context, CompanionTurnKind.CONTINUE)
        received(turn)
        val result = repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
        val control = result.inputMessage!!
        assertEquals(CompanionSourceEligibility.CONTROL_NOOP, control.companionSourceEligibility())
        assertTrue(RoleplayTranscriptFormatter.memorySemanticSegments(control).isEmpty())
        assertEquals(1, archive.listPendingIngress(context).size)
        val sources =
            ConversationSummaryCoverageSupport.sourceSnapshot(listOf(control, result.replyMessage))
        assertEquals(ConversationSummaryCoverageSupport.CONTROL_NOOP, sources.first().kind)
        assertEquals(0, sources.first().endOffset)
        val input = ConversationSummaryCoverageSupport.completeInput(
            listOf(control, result.replyMessage), result.replyMessage.content
        )
        assertFalse(input.contains(COMPANION_CONTINUE_DISPLAY_TEXT))
        val summaries = RoomMemoryRepository(database.memoryDao(), database.companionDao())
        val summary = ConversationSummary(
            context.conversationId, context.characterId, "安静陪伴", 2, clock++, sources,
            PromptCompletionStatus.COMPLETED, context,
        )
        assertTrue(summaries.saveSummaryIfSourcesCurrent(summary, null))
        val read = summaries.getSummaryForContext(context)!!
        assertEquals(
            2, ConversationSummaryCoverageSupport.verifiedSummaryCoverage(
                read, store.listMessages(context.conversationId), context
            )
        )
        assertEquals(
            ConversationSummaryCoverageSupport.CONTROL_NOOP,
            read.sourceSnapshot.first().kind
        )
    }

    @Test
    fun newerTurn_rejectsExpiredRerollOfOlderAnswer() = runBlocking {
        val first = prepared()
        received(first, "已提交的A")
        val original = repository.commitAttempt(first.attempt.id) as CompanionCommitResult.Committed
        val reroll = repository.newAttempt(first.turn.id)!!
        repository.claimAttempt(reroll.id, "old", 1000)
        clock += 10_000
        repository.recoverEncounter(first.turn.encounterId)
        repository.beginTurn(
            first.turn.encounterId, CompanionTurnKind.INPUT,
            ChatMessage(
                "newer-input",
                first.attempt.context.conversationId,
                MessageRole.USER,
                "下一轮B",
                createdAt = clock++
            )
        )
        assertFalse(
            repository.saveGeneratedArtifact(
                reroll.id, "old",
                CompanionGeneratedArtifact("迟到改写A", PromptCompletionStatus.COMPLETED)
            )
        )
        assertTrue(repository.commitAttempt(reroll.id) is CompanionCommitResult.Conflict)
        assertEquals(
            original.replyMessage.content,
            store.listMessages(first.attempt.context.conversationId)
                .single { it.id == original.replyMessage.id }.content
        )
        assertEquals(3, repository.messages(first.turn.encounterId).size)
    }

    @Test
    fun reroll_rebasesOwnInputAfterQualificationPromotion() = runBlocking {
        val context = runtime()
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val input = ChatMessage(
            "source-input",
            context.conversationId,
            MessageRole.USER,
            "原输入",
            createdAt = clock++
        )
        val prepared = repository.beginTurn(
            encounter.id, CompanionTurnKind.INPUT, input,
            CompanionRequestDescriptor(
                sourceMessages = listOf(
                    CompanionMessageRevision(input.id, 1, context.conversationId)
                )
            )
        )
        received(prepared)
        val first = repository.commitAttempt(prepared.attempt.id) as CompanionCommitResult.Committed
        val next = repository.newAttempt(prepared.turn.id)!!
        assertEquals(
            first.inputMessage!!.memoryRevision,
            next.descriptor.sourceMessages.single().revision
        )
        assertNotNull(repository.claimAttempt(next.id, "reroll"))
    }

    @Test
    fun snapshotFromAnotherCharacter_cannotBeRequested() = runBlocking {
        val foreign = runtime("bob")
        store.upsertMessages(
            listOf(
                ChatMessage(
                    "foreign", foreign.conversationId, MessageRole.USER,
                    "另一个角色的原文", createdAt = clock++
                )
            )
        )
        val source = store.listMessages(foreign.conversationId).single()
        val own = runtime()
        val encounter = repository.createEncounter(own, CompanionOpeningMode.DIRECT)
        val pending = repository.beginTurn(
            encounter.id, CompanionTurnKind.INPUT,
            ChatMessage(
                "own",
                own.conversationId,
                MessageRole.USER,
                "本人输入",
                createdAt = clock++
            ),
            CompanionRequestDescriptor(
                sourceMessages = listOf(
                    CompanionMessageRevision(
                        source.id,
                        source.memoryRevision,
                        source.conversationId
                    )
                )
            )
        )
        assertNull(repository.claimAttempt(pending.attempt.id, "owner"))
        assertEquals(
            CompanionAttemptState.NOT_STARTED,
            repository.getAttempt(pending.attempt.id)!!.state
        )
    }

    @Test
    fun corruptDescriptor_cannotStartNetwork() = runBlocking {
        val turn = prepared()
        sql("UPDATE companion_attempts SET requestFingerprint = 'corrupt'")
        assertNull(repository.claimAttempt(turn.attempt.id, "owner"))
        assertEquals(
            CompanionAttemptState.NOT_STARTED,
            repository.getAttempt(turn.attempt.id)!!.state
        )
    }

    @Test
    fun incompleteResult_requiresExplicitNewRequest() = runBlocking {
        val turn = prepared()
        received(turn, "未确认完整", PromptCompletionStatus.UNKNOWN)
        assertNull(repository.newAttempt(turn.turn.id))
        assertNotNull(repository.newAttempt(turn.turn.id, allowUncertainRetry = true))
    }

    @Test
    fun proof_isNotReusableAfterCopyOrImport() = runBlocking {
        val turn = prepared()
        received(turn)
        val reply =
            (repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed).replyMessage
        assertEquals(CompanionSourceEligibility.TEXT_ALLOWED, reply.companionSourceEligibility())
        for (changed in listOf(
            reply.copy(content = "更改"),
            reply.copy(parts = listOf(textMessagePart("变化"))),
            reply.copy(companionTurnId = "other"),
            reply.copy(companionTurnId = "", companionMemoryState = CompanionMemoryState.LEGACY),
            reply.copy(createdAt = reply.createdAt + 1),
            reply.copy(status = MessageStatus.ERROR)
        )) {
            assertEquals(
                CompanionSourceEligibility.UNAVAILABLE,
                changed.companionSourceEligibility()
            )
        }
        val json = AppJson.gson.toJson(reply)
        assertFalse(json.contains("companionSourceProof"))
        val imported = AppJson.gson.fromJson(json, ChatMessage::class.java)
        assertNull(imported.companionSourceProof)
        assertEquals(CompanionSourceEligibility.UNAVAILABLE, imported.companionSourceEligibility())
    }

    @Test
    fun brokenSelectedAttempt_deniesOriginalsManualSourcesAndSummary() = runBlocking<Unit> {
        val turn = prepared()
        received(turn)
        val committed = repository.commitAttempt(turn.attempt.id) as CompanionCommitResult.Committed
        val context = turn.attempt.context
        val originals = repository.messages(turn.turn.encounterId)
        val sourceIds = archive.listPendingIngress(context).map { it.source.id }
        val summaries = RoomMemoryRepository(database.memoryDao(), database.companionDao())
        val summary = ConversationSummary(
            context.conversationId,
            context.characterId,
            "合成摘要",
            2,
            clock++,
            ConversationSummaryCoverageSupport.sourceSnapshot(originals),
            PromptCompletionStatus.COMPLETED,
            context
        )
        val dao = database.companionDao()
        val selected = dao.getAttempt(turn.attempt.id)!!
        val storedTurn = dao.getTurn(turn.turn.id)!!
        val another = repository.beginTurn(turn.turn.encounterId, CompanionTurnKind.CONTINUE)
        received(another)
        repository.commitAttempt(another.attempt.id)
        for (damage in listOf("missing", "foreign", "uncommitted", "runtime")) {
            dao.upsertTurn(storedTurn)
            dao.upsertAttempt(selected)
            when (damage) {
                "missing" -> dao.upsertTurn(storedTurn.copy(selectedAttemptId = "missing"))
                "foreign" -> dao.upsertTurn(storedTurn.copy(selectedAttemptId = another.attempt.id))
                "uncommitted" -> dao.upsertAttempt(selected.copy(state = "REQUESTED"))
                "runtime" -> dao.upsertAttempt(
                    selected.copy(
                        frozenRuntimeJson = AppJson.gson.toJson(
                            context.copy(runId = "foreign-run")
                        )
                    )
                )
            }
            val fresh = store.listMessages(context.conversationId)
                .filter { it.id in originals.map(ChatMessage::id) }
            assertTrue(
                damage,
                fresh.all { it.companionSourceEligibility() == CompanionSourceEligibility.UNAVAILABLE })
            assertTrue(
                damage,
                fresh.all { RoleplayTranscriptFormatter.memorySemanticSegments(it).isEmpty() })
            val manual = archive.captureSelectedMessages(
                context, MemoryMessageSelection(
                    context.conversationId,
                    listOf(committed.inputMessage!!.id, committed.replyMessage.id)
                )
            )
            assertTrue(damage, manual.sources.isEmpty())
            for (sourceId in sourceIds) assertTrue(
                damage,
                archive.getSourceSegments(context, sourceId).isEmpty()
            )
            assertFalse(damage, summaries.saveSummaryIfSourcesCurrent(summary, null))
        }
    }

    @Test
    fun jsonDistinguishesLegacyMissingFromInvalidFields() {
        val original = ChatMessage("old", "old-conv", MessageRole.USER, "旧原文")
        val old = JsonParser.parseString(AppJson.gson.toJson(original)).asJsonObject
        old.remove("companionTurnId"); old.remove("companionMemoryState")
        val restored = AppJson.gson.fromJson(old, ChatMessage::class.java)
        assertEquals("", restored.companionTurnId)
        assertEquals(CompanionMemoryState.LEGACY, restored.companionMemoryState)
        assertEquals(CompanionSourceEligibility.TEXT_ALLOWED, restored.companionSourceEligibility())
        old.add("companionTurnId", com.google.gson.JsonNull.INSTANCE)
        old.add("companionMemoryState", com.google.gson.JsonNull.INSTANCE)
        val invalid = AppJson.gson.fromJson(old, ChatMessage::class.java)
        assertEquals(CompanionMemoryState.UNKNOWN, invalid.companionMemoryState)
        assertEquals(CompanionSourceEligibility.UNAVAILABLE, invalid.companionSourceEligibility())
        old.addProperty("companionTurnId", "linked"); old.addProperty(
            "companionMemoryState",
            "future-state"
        )
        assertEquals(
            CompanionMemoryState.UNKNOWN,
            AppJson.gson.fromJson(old, ChatMessage::class.java).companionMemoryState
        )
    }
}
