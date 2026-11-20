package com.example.myapplication.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryArchiveViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun savedStateKeysWithSeparators_doNotShareDrafts() {
        assertNotEquals(
            memoryArchiveSavedStateKey("a:b", "c"),
            memoryArchiveSavedStateKey("a", "b:c")
        )
    }

    @Test
    fun newestMessageWithoutReceipt_doesNotFallBackAndLoadsTail() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            repeat(120) { index ->
                fixture.messages += MemoryArchiveMessage(
                    ChatMessage(
                        "m$index",
                        "conv",
                        MessageRole.ASSISTANT,
                        "合成回合 $index",
                        createdAt = index.toLong(),
                        memoryRecallRequestId = if (index == 118) "older" else ""
                    ), "p", null
                )
            }
            val model =
                MemoryArchiveViewModel(fixture.repository, "a", "conv", rejectingArchiveTasks())
            advanceUntilIdle()
            assertEquals("m119", model.uiState.value.selectedMessageId)
            assertNull(model.uiState.value.receipt)
            assertTrue(fixture.receiptCalls.isEmpty())
            assertTrue(fixture.messageOffsets.contains(70))
            model.selectMessage("m118")
            advanceUntilIdle()
            assertEquals(listOf("conv", "m118", "older"), fixture.receiptCalls.single())
            assertTrue(fixture.writeCalls.isEmpty())
        }

    @Test
    fun editUsesSeenRevision_keepsMultilineDraftOnConflictAndFailure() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            val model =
                MemoryArchiveViewModel(fixture.repository, "a", null, rejectingArchiveTasks())
            advanceUntilIdle()
            model.openItem(MemoryArchiveItem.Record(fixture.records.single()))
            advanceUntilIdle()
            model.beginEdit()
            model.updateEditor(model.uiState.value.editor!!.copy(content = "修改第一段\n\n保留第二段"))
            fixture.editResult = MemoryWriteResult.Conflict(MemoryConflict.RECORD_CHANGED)
            model.saveEditor()
            model.saveEditor()
            advanceUntilIdle()
            assertEquals(Triple("r", 3L, "修改第一段\n\n保留第二段"), fixture.lastEdit)
            assertEquals(1, fixture.writeCalls.size)
            assertEquals("修改第一段\n\n保留第二段", model.uiState.value.editor?.content)
            assertTrue(model.uiState.value.editor!!.error!!.contains("新版本"))
            fixture.editFailure = IllegalStateException("disk write failed")
            model.saveEditor()
            advanceUntilIdle()
            assertFalse(model.uiState.value.isSaving)
            assertEquals("修改第一段\n\n保留第二段", model.uiState.value.editor?.content)
        }

    @Test
    fun selectedRevisionUsesItsOwnSources() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val fixture = MemoryArchiveUiTestFixture()
        fun source(id: String, status: MemorySourceStatus) = MemorySourceEvidence(
            id,
            "daily",
            "a",
            MemorySourceKind.LEGACY_HINT,
            "conv",
            "m",
            1,
            "a",
            "ASSISTANT",
            "p",
            listOf("a"),
            "原文",
            2,
            status,
            1,
            1,
            "fingerprint"
        )
        fixture.revisionSources = mapOf(
            1L to listOf(source("old", MemorySourceStatus.UNKNOWN)),
            3L to listOf(source("new", MemorySourceStatus.CHANGED))
        )
        val model = MemoryArchiveViewModel(fixture.repository, "a", null, rejectingArchiveTasks())
        advanceUntilIdle()
        model.openItem(MemoryArchiveItem.Record(fixture.records.single()))
        advanceUntilIdle()
        assertEquals("new", model.uiState.value.detail!!.sources.single().ref.sourceId)
        model.selectRevision(1)
        advanceUntilIdle()
        assertEquals("old", model.uiState.value.detail!!.sources.single().ref.sourceId)
        assertEquals(
            MemorySourceStatus.UNKNOWN,
            model.uiState.value.detail!!.sources.single().evidence!!.status
        )
        model.beginEdit()
        assertNull(model.uiState.value.editor)
    }

    @Test
    fun changedPersonaAndQuery_ignoreLatePagesAndPreserveSourceOnlyIdentity() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            val slow = CompletableDeferred<Unit>()
            fixture.query = { context, request ->
                if (request.search == "slow") withContext(NonCancellable) { slow.await() }
                MemoryArchivePage(
                    listOf(MemoryArchiveItem.Record(archiveUiRecord(id = "${context.personaKey}:${request.search}"))),
                    1,
                    0,
                    request.limit
                )
            }
            val model =
                MemoryArchiveViewModel(fixture.repository, "a", null, rejectingArchiveTasks())
            advanceUntilIdle()
            assertTrue(model.uiState.value.personas.any { it.key == "knowledge-only" })
            model.setQuery(model.uiState.value.query.copy(search = "slow"))
            runCurrent()
            model.selectPersona("knowledge-only")
            runCurrent()
            model.setQuery(
                model.uiState.value.query.copy(
                    search = "new",
                    category = MemoryCategory.FUTURE
                )
            )
            runCurrent()
            slow.complete(Unit)
            advanceUntilIdle()
            assertEquals("knowledge-only:new", model.uiState.value.items.single().id)
            assertEquals(MemoryCategory.FUTURE, fixture.queries.last().second.category)
        }

    @Test
    fun paginationPassesScopeAndOffsetWithoutDroppingEarlierItems() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            fixture.records.clear()
            repeat(65) { fixture.records += archiveUiRecord(id = "record-$it") }
            val model =
                MemoryArchiveViewModel(fixture.repository, "a", null, rejectingArchiveTasks())
            advanceUntilIdle()
            assertEquals(50, model.uiState.value.items.size)
            model.loadMore(); advanceUntilIdle()
            assertEquals(65, model.uiState.value.items.size)
            assertFalse(model.uiState.value.hasMore)
            assertEquals(50, fixture.queries.last().second.offset)
            assertTrue(fixture.queries.all { it.first.personaKey == "p" })
        }

    @Test
    fun originalReadIsPagedAndNeverStartsScenarioOrWrites() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            repeat(103) {
                fixture.messages += MemoryArchiveMessage(
                    ChatMessage(
                        "old-$it",
                        "conv",
                        MessageRole.USER,
                        "旧原文 $it",
                        createdAt = it.toLong(),
                        speakerName = "历史署名"
                    ), "p", null
                )
            }
            val model = MemoryOriginalViewModel(fixture.repository, "a", "daily", "p", "conv")
            advanceUntilIdle()
            assertEquals(100, model.uiState.value.messages.size)
            model.loadMore(); advanceUntilIdle()
            assertEquals(103, model.uiState.value.messages.size)
            assertEquals("历史署名", model.uiState.value.messages.last().message.speakerName)
            assertTrue(fixture.writeCalls.isEmpty())
            val missing = MemoryOriginalViewModel(
                fixture.repository,
                "a",
                "daily",
                "p",
                "missing-conversation"
            )
            advanceUntilIdle()
            assertTrue(missing.uiState.value.missing)
            assertTrue(missing.uiState.value.messages.isEmpty())
        }

    @Test
    fun resumeUsesExactSavedJobId() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val fixture = MemoryArchiveUiTestFixture()
        val context = MemoryRuntimeContext("a", "daily", "", personaKey = "p")
        fixture.jobs = listOf(
            MemoryProcessingJob(
                "original-job", context, "key", emptyList(), emptyMap(), MemoryJobStatus.PAUSED,
                "saved", "", 0, "", 1, 1, MemoryJobPurpose.USER_SELECTION
            )
        )
        val calls = mutableListOf<String>()
        val actions = rejectingArchiveTasks().copy(resumeExtraction = { _, jobId ->
            calls += jobId; MemoryExtractionProgress(committedJobs = 1)
        })
        val model = MemoryArchiveViewModel(fixture.repository, "a", null, actions)
        advanceUntilIdle()
        model.resumeJob("original-job"); advanceUntilIdle()
        assertEquals(listOf("original-job"), calls)
    }

    @Test
    fun reenterRefreshesLatestRound_butKeepsExplicitHistoricalSelection() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            fun message(id: String, time: Long) = MemoryArchiveMessage(
                ChatMessage(
                    id,
                    "conv",
                    MessageRole.ASSISTANT,
                    "合成回复",
                    createdAt = time
                ), "p", null
            )
            fixture.messages += message("first", 1)
            val model =
                MemoryArchiveViewModel(fixture.repository, "a", "conv", rejectingArchiveTasks())
            advanceUntilIdle()
            fixture.messages += message("second", 2)
            model.onRouteEntered(); advanceUntilIdle()
            assertEquals("second", model.uiState.value.selectedMessageId)
            model.selectMessage("first"); advanceUntilIdle()
            fixture.messages += message("third", 3)
            model.onRouteEntered(); advanceUntilIdle()
            assertEquals("first", model.uiState.value.selectedMessageId)
            assertTrue(model.uiState.value.selectedMessageExplicit)
            model.selectPersona("knowledge-only"); advanceUntilIdle()
            assertEquals("third", model.uiState.value.selectedMessageId)
            assertFalse(model.uiState.value.selectedMessageExplicit)
        }

    @Test
    fun newestMessageFromAnotherPersona_doesNotFallBackToFilteredOlderRound() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            fixture.messages += MemoryArchiveMessage(
                ChatMessage(
                    "older",
                    "conv",
                    MessageRole.ASSISTANT,
                    "旧身份回复",
                    createdAt = 1,
                    memoryRecallRequestId = "older-request"
                ), "p", null
            )
            fixture.messages += MemoryArchiveMessage(
                ChatMessage(
                    "latest-other",
                    "conv",
                    MessageRole.ASSISTANT,
                    "新身份回复",
                    createdAt = 2
                ), "different-persona", null
            )
            val model =
                MemoryArchiveViewModel(fixture.repository, "a", "conv", rejectingArchiveTasks())
            advanceUntilIdle()
            assertEquals("latest-other", model.uiState.value.selectedMessageId)
            assertEquals(listOf("older"), model.uiState.value.messages.map { it.message.id })
            assertNull(model.uiState.value.receipt)
            assertTrue(fixture.receiptCalls.isEmpty())
            model.toggleSelection("latest-other")
            assertTrue(model.uiState.value.selectedMessageIds.isEmpty())
        }

    @Test
    fun refreshDuringJobRead_keepsLatestCompletedTaskAndPauseControl() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            val waiting = CompletableDeferred<Unit>()
            val context = MemoryRuntimeContext("a", "daily", "", personaKey = "p")
            val original = MemoryProcessingJob(
                "job", context, "key", emptyList(), emptyMap(), MemoryJobStatus.PAUSED,
                "saved", "", 0, "", 1, 1, MemoryJobPurpose.USER_SELECTION
            )
            var calls = 0
            val repository = object : MemoryArchiveRepository by fixture.repository {
                override suspend fun listJobHistory(
                    context: MemoryRuntimeContext,
                    limit: Int,
                    offset: Int
                ): List<MemoryProcessingJob> {
                    val call = ++calls
                    if (call == 2) withContext(NonCancellable) { waiting.await() }
                    return listOf(if (call >= 3) original.copy(status = MemoryJobStatus.COMMITTED) else original)
                }

                override suspend fun getProcessingControl(
                    context: MemoryRuntimeContext,
                    jobId: String
                ) =
                    MemoryProcessingControl(
                        context.spaceId,
                        jobId,
                        userPaused = calls >= 3,
                        version = if (calls >= 3) 1 else 0
                    )
            }
            val model = MemoryArchiveViewModel(repository, "a", null, rejectingArchiveTasks())
            advanceUntilIdle()
            model.reload()
            runCurrent()
            model.reload()
            runCurrent()
            waiting.complete(Unit)
            advanceUntilIdle()
            assertEquals(3, calls)
            assertEquals(MemoryJobStatus.COMMITTED, model.uiState.value.jobs.single().status)
            assertEquals(true, model.uiState.value.processingControl?.userPaused)
            assertFalse(model.uiState.value.jobsLoading)
        }

    @Test
    fun repeatedLinkSearch_ignoresEarlierSameQueryResultAndOldDetail() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            val model =
                MemoryArchiveViewModel(fixture.repository, "a", null, rejectingArchiveTasks())
            advanceUntilIdle()
            model.openItem(MemoryArchiveItem.Record(fixture.records.single()))
            advanceUntilIdle()
            val waiting = CompletableDeferred<Unit>()
            var calls = 0
            fixture.query = { _, query ->
                val call = ++calls
                if (call == 1) withContext(NonCancellable) { waiting.await() }
                MemoryArchivePage(
                    listOf(MemoryArchiveItem.Record(archiveUiRecord(id = "candidate-$call"))),
                    1,
                    0,
                    query.limit
                )
            }
            model.loadLinkCandidates("相同查询")
            runCurrent()
            model.loadLinkCandidates("相同查询")
            runCurrent()
            waiting.complete(Unit)
            advanceUntilIdle()
            assertEquals("candidate-2", model.uiState.value.linkCandidates.single().id)

            val afterClose = CompletableDeferred<Unit>()
            fixture.query =
                { _, _ -> withContext(NonCancellable) { afterClose.await(); error("旧详情查询失败") } }
            model.loadLinkCandidates("关闭前查询")
            runCurrent()
            model.closeDetail()
            afterClose.complete(Unit)
            advanceUntilIdle()
            assertNull(model.uiState.value.message)
        }

    @Test
    fun lateLinkFailure_doesNotReplaceNewSearchOrClearItsLoadingState() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            val model =
                MemoryArchiveViewModel(fixture.repository, "a", null, rejectingArchiveTasks())
            advanceUntilIdle()
            val old = CompletableDeferred<Unit>()
            val current = CompletableDeferred<Unit>()
            fixture.query = { _, query ->
                if (query.search == "旧") withContext(NonCancellable) { old.await(); error("旧查询失败") }
                current.await()
                MemoryArchivePage(
                    listOf(MemoryArchiveItem.Record(archiveUiRecord(id = "new"))),
                    1,
                    0,
                    query.limit
                )
            }
            model.loadLinkCandidates("旧")
            runCurrent()
            model.loadLinkCandidates("新")
            runCurrent()
            old.complete(Unit)
            runCurrent()
            assertTrue(model.uiState.value.linkLoading)
            assertNull(model.uiState.value.message)
            current.complete(Unit)
            advanceUntilIdle()
            assertEquals("new", model.uiState.value.linkCandidates.single().id)
        }

    @Test
    fun delayedIndexProgress_doesNotReplaceCompletedRefresh() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            val repository = object : MemoryArchiveRepository by fixture.repository {
                override suspend fun listActiveRecallContexts(characterId: String) =
                    emptyList<MemoryRuntimeContext>()
            }
            val waiting = CompletableDeferred<Unit>()
            var calls = 0
            val extras = MemoryArchiveReadExtras(
                indexProgress = {
                    val call = ++calls
                    if (call == 2) withContext(NonCancellable) { waiting.await() }
                    listOf(
                        MemoryVectorIndexProgress(
                            call.toLong(), if (call >= 3) "READY" else "BUILDING", call >= 3,
                            10, if (call >= 3) 10 else 1, false, ""
                        )
                    )
                },
                origins = { _, _, _ -> emptyList() },
                rebuildIndex = { _, _ -> error("状态读取不得调用模型") },
            )
            val model = MemoryArchiveViewModel(
                repository,
                "a",
                null,
                rejectingArchiveTasks(),
                extras = extras
            )
            advanceUntilIdle()
            model.reload()
            runCurrent()
            model.reload()
            runCurrent()
            waiting.complete(Unit)
            advanceUntilIdle()
            assertEquals("READY", model.uiState.value.indexProgress!!.single().status)
            assertEquals(3L, model.uiState.value.indexProgress!!.single().generation)
            assertNull(model.uiState.value.indexError)
        }

    @Test
    fun restoredStateFilter_isSentToRepositoryBeforePaging() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            val savedState = SavedStateHandle()
            val model = MemoryArchiveViewModel(
                fixture.repository,
                "a",
                null,
                rejectingArchiveTasks(),
                savedState
            )
            advanceUntilIdle()
            model.setQuery(
                model.uiState.value.query.copy(
                    tab = MemoryArchiveTab.STATES,
                    stateKey = MemoryStateKey.APPOINTMENT
                )
            )
            advanceUntilIdle()
            val restored = MemoryArchiveViewModel(
                fixture.repository,
                "a",
                null,
                rejectingArchiveTasks(),
                savedState
            )
            advanceUntilIdle()
            assertEquals(MemoryStateKey.APPOINTMENT, fixture.queries.last().second.stateKey)
            assertEquals(MemoryArchiveTab.STATES, restored.uiState.value.query.tab)
        }

    @Test
    fun historicalState_usesSelectedRevisionStatusAndExpiry() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            val current = MemoryCurrentState(
                "state", "daily", "a", "p", MemoryStateKey.APPOINTMENT, "新的约定", 2,
                "ENDED", 2000, 3000, 1500, true, 1, 3000
            )
            val old = MemoryKnowledgeRevision(
                MemoryMaterialKind.STATE,
                "state",
                1,
                "最初的约定",
                "ACTIVE",
                MemoryChangeOrigin.MANUAL,
                false,
                1000,
                null,
                emptyList(),
                emptyList(),
                1
            )
            val latest = old.copy(
                revision = 2,
                content = current.content,
                status = current.status,
                expiresAt = current.expiresAt,
                endedAt = current.endedAt
            )
            val repository = object : MemoryArchiveRepository by fixture.repository {
                override suspend fun getCurrentStateDetails(
                    context: MemoryRuntimeContext,
                    id: String
                ) =
                    MemoryCurrentStateDetails(current, listOf(old, latest), emptyList())
            }
            val model = MemoryArchiveViewModel(repository, "a", null, rejectingArchiveTasks())
            advanceUntilIdle()
            model.openItem(MemoryArchiveItem.State(current))
            advanceUntilIdle()
            model.selectRevision(1)
            advanceUntilIdle()
            val detail = model.uiState.value.detail!!
            assertEquals("最初的约定", detail.content)
            assertEquals("ACTIVE", detail.knowledgeRevision?.status)
            assertEquals(1000L, detail.knowledgeRevision?.expiresAt)
            assertNull(detail.knowledgeRevision?.endedAt)
            model.beginEdit()
            assertNull(model.uiState.value.editor)
        }

    @Test
    fun legacyKnowledgeWithoutRevisionHistory_remainsReadableAndEditableWithoutInventingSources() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            val state = MemoryCurrentState(
                "state", "daily", "a", "p", MemoryStateKey.MOOD, "旧状态正文", 2,
                "ACTIVE", null, null, 1, false, 1, 1
            )
            val resident = MemoryResidentEntry(
                "resident", "daily", "a", "p", MemoryResidentCategory.USER, "旧认知正文", 4,
                "READY", false, false, null, 1, 1
            )
            val repository = object : MemoryArchiveRepository by fixture.repository {
                override suspend fun getCurrentStateDetails(
                    context: MemoryRuntimeContext,
                    id: String
                ) = MemoryCurrentStateDetails(state, emptyList(), emptyList())

                override suspend fun getResidentDetails(context: MemoryRuntimeContext, id: String) =
                    MemoryResidentDetails(resident, emptyList(), emptyList())
            }
            val model = MemoryArchiveViewModel(repository, "a", null, rejectingArchiveTasks())
            advanceUntilIdle()
            for (item in listOf(
                MemoryArchiveItem.State(state),
                MemoryArchiveItem.Resident(resident)
            )) {
                model.openItem(item)
                advanceUntilIdle()
                val detail = model.uiState.value.detail!!
                assertNull(detail.error)
                assertEquals(item.content(), detail.content)
                assertTrue(detail.historyLabel.contains("尚未记录"))
                assertTrue(detail.sources.isEmpty())
                assertNull(detail.knowledgeRevision)
                model.beginEdit()
                assertEquals(item.revision(), model.uiState.value.editor?.expectedRevision)
                model.closeEditor()
            }
            assertTrue(fixture.writeCalls.isEmpty())
        }

    @Test
    fun knowledgeSupportingRecord_usesExactHistoricalSources() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            val oldSource = MemorySourceEvidence(
                "old-source",
                "daily",
                "a",
                MemorySourceKind.LEGACY_HINT,
                "conv",
                "m",
                1,
                "a",
                "ASSISTANT",
                "p",
                listOf("a"),
                "旧出处",
                3,
                MemorySourceStatus.CHANGED,
                1,
                1,
                "fingerprint"
            )
            fixture.revisionSources =
                mapOf(1L to listOf(oldSource), 3L to listOf(oldSource.copy(id = "new-source")))
            val resident = MemoryResidentEntry(
                "resident", "daily", "a", "p", MemoryResidentCategory.USER, "认知正文", 1,
                "READY", false, false, null, 1, 1
            )
            val revision = MemoryKnowledgeRevision(
                MemoryMaterialKind.RESIDENT,
                resident.id,
                1,
                resident.content,
                "READY",
                MemoryChangeOrigin.EXTRACTION,
                false,
                null,
                null,
                emptyList(),
                listOf(MemoryRecordVersion("r", 1)),
                1
            )
            val repository = object : MemoryArchiveRepository by fixture.repository {
                override suspend fun getResidentDetails(context: MemoryRuntimeContext, id: String) =
                    MemoryResidentDetails(resident, listOf(revision), emptyList())
            }
            val model = MemoryArchiveViewModel(repository, "a", null, rejectingArchiveTasks())
            advanceUntilIdle()
            model.openItem(MemoryArchiveItem.Resident(resident))
            advanceUntilIdle()
            assertEquals("old-source", model.uiState.value.detail!!.sources.single().ref.sourceId)
            assertEquals(
                MemorySourceStatus.CHANGED,
                model.uiState.value.detail!!.sources.single().evidence?.status
            )
        }

    @Test
    fun eventUpdatedSinceListing_usesLoadedSummaryRevisionForOrigins() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val fixture = MemoryArchiveUiTestFixture()
            val event = MemoryEventBox(
                "event", "daily", "a", "p", "事件", "新摘要", 2, MemoryEventSummaryStatus.READY,
                MemoryEventStatus.ACTIVE, "已结束", 2, 1, 2
            )
            val repository = object : MemoryArchiveRepository by fixture.repository {
                override suspend fun getEventDetails(
                    context: MemoryRuntimeContext,
                    eventId: String
                ) = MemoryEventDetails(event, emptyList())

                override suspend fun listActiveRecallContexts(characterId: String) =
                    emptyList<MemoryRuntimeContext>()
            }
            val origins = (1L..2L).map { revision ->
                MemoryTransferOriginLink(
                    MemoryTransferObjectKind.EVENT, event.id, revision,
                    MemoryTransferOrigin(
                        "batch",
                        2,
                        1,
                        1,
                        "origin-$revision",
                        "original-space",
                        "a",
                        "p",
                        revision
                    )
                )
            }
            val extras = MemoryArchiveReadExtras(
                indexProgress = { emptyList() },
                origins = { _, _, _ -> origins },
                rebuildIndex = { _, _ -> error("读取不得请求模型") })
            val model = MemoryArchiveViewModel(
                repository,
                "a",
                null,
                rejectingArchiveTasks(),
                extras = extras
            )
            advanceUntilIdle()
            model.openItem(
                MemoryArchiveItem.Event(
                    event.copy(
                        summaryRevision = 1,
                        summary = "旧列表摘要"
                    )
                )
            )
            advanceUntilIdle()
            assertEquals(2L, model.uiState.value.detail!!.selectedRevision)
            assertEquals(
                "origin-2",
                model.uiState.value.detail!!.importOrigins.single().origin.originalId
            )
        }
}
