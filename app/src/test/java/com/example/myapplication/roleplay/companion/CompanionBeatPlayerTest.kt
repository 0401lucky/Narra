package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.CompanionBeat
import com.example.myapplication.model.CompanionRawSpan
import com.example.myapplication.model.CompanionReplySource
import com.example.myapplication.model.ParsedCompanionReply
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.companionRawHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionBeatPlayerTest {
    @Test
    fun idle_ignoresPlaybackActions() = runTest {
        CompanionBeatPlayer(backgroundScope).use { player ->
            assertIdle(player)
            player.advanceOrReveal()
            player.replay()
            player.setSource(null)
            tick(10)
            assertIdle(player)
        }
    }

    @Test
    fun typing_finishesCurrentBeatAndWaitsForInput() = runTest {
        CompanionBeatPlayer(backgroundScope).use { player ->
            val reply = reply("你好", "下一拍")
            player.setSource(reply)
            assertEquals(CompanionBeatPlayerPhase.TYPING, player.state.value.phase)
            assertEquals("", player.state.value.visibleText)
            assertEquals(0, player.state.value.beatIndex)
            assertEquals(2, player.state.value.beatCount)
            assertSame(reply.source, player.state.value.source)
            runCurrent()

            tick()
            assertEquals("你", player.state.value.visibleText)
            assertEquals(1, player.state.value.revealedGraphemes)
            assertEquals(CompanionBeatPlayerPhase.TYPING, player.state.value.phase)
            tick()
            assertEquals("你好", player.state.value.visibleText)
            assertEquals(CompanionBeatPlayerPhase.COMPLETE, player.state.value.phase)
            tick(10)
            assertEquals(0, player.state.value.beatIndex)
            assertEquals("你好", player.state.value.visibleText)

            player.advanceOrReveal()
            assertEquals(1, player.state.value.beatIndex)
            assertEquals("", player.state.value.visibleText)
            assertSame(reply.beats[1], player.state.value.beat)
        }
    }

    @Test
    fun advanceWhileTyping_revealsBeforeNextClickAndCancelsScheduledTick() = runTest {
        CompanionBeatPlayer(backgroundScope).use { player ->
            player.setSource(reply("中文正文", "第二拍"))
            runCurrent()
            tick()
            assertEquals("中", player.state.value.visibleText)
            advanceTimeBy(DELAY_MILLIS)

            player.advanceOrReveal()
            val revealed = player.state.value
            assertEquals(CompanionBeatPlayerPhase.COMPLETE, revealed.phase)
            assertEquals("中文正文", revealed.visibleText)
            assertEquals(4, revealed.revealedGraphemes)
            assertEquals(0, revealed.beatIndex)
            runCurrent()
            tick(10)
            assertEquals(revealed, player.state.value)

            player.advanceOrReveal()
            assertEquals(1, player.state.value.beatIndex)
            assertEquals(CompanionBeatPlayerPhase.TYPING, player.state.value.phase)
        }
    }

    @Test
    fun reveal_nonCancellableOldTickCannotOverwriteNextBeat() = runTest {
        val oldTick = CompletableDeferred<Unit>()
        var delays = 0
        CompanionBeatPlayer(backgroundScope, delayCharacter = { millis ->
            if (delays++ == 0) {
                withContext(NonCancellable) { oldTick.await() }
            } else {
                delay(millis)
            }
        }).use { player ->
            try {
                player.setSource(reply("旧拍", "新拍"))
                runCurrent()
                player.advanceOrReveal()
                assertEquals("旧拍", player.state.value.visibleText)
                assertEquals(0, player.state.value.beatIndex)
                player.advanceOrReveal()
                runCurrent()

                oldTick.complete(Unit)
                runCurrent()
                assertEquals(1, player.state.value.beatIndex)
                assertEquals("", player.state.value.visibleText)
                assertEquals(CompanionBeatPlayerPhase.TYPING, player.state.value.phase)
                tick()
                assertEquals("新", player.state.value.visibleText)
                tick()
                assertEquals("新拍", player.state.value.visibleText)
                assertEquals(CompanionBeatPlayerPhase.COMPLETE, player.state.value.phase)
            } finally {
                oldTick.complete(Unit)
            }
        }
    }

    @Test
    fun setSource_sameFixedProjectionKeepsProgressAndOneClock() = runTest {
        var delays = 0
        CompanionBeatPlayer(backgroundScope, delayCharacter = { millis ->
            delays++
            delay(millis)
        }).use { player ->
            val reply = reply("同源内容")
            player.setSource(reply)
            runCurrent()
            tick()
            val before = player.state.value
            repeat(10) { player.setSource(reply.copy(beats = reply.beats.toList())) }
            runCurrent()
            assertEquals(before, player.state.value)
            assertEquals(2, delays)
            tick()
            assertEquals("同源", player.state.value.visibleText)
            assertEquals(3, delays)
            assertEquals(before.generation, player.state.value.generation)
        }
    }

    @Test
    fun setSource_anySourceIdentityChangeRestartsAndInvalidatesOldTick() = runTest {
        val changes: List<(CompanionReplySource) -> CompanionReplySource> = listOf(
            { it.copy(messageId = "other-message") },
            { it.copy(conversationId = "other-conversation") },
            { it.copy(turnId = "other-turn") },
            { it.copy(revision = it.revision + 1) },
            { it.copy(protocolVersion = it.protocolVersion + 1) },
            { it.copy(parserVersion = it.parserVersion + 1) },
            { it.copy(rawHash = "different-raw-hash") },
            { it.copy(attemptId = "other-attempt") },
            { it.copy(configurationRevision = 99) },
            { it.copy(characterId = "other-character") },
        )
        for (change in changes) {
            CompanionBeatPlayer(backgroundScope).use { player ->
                val original = reply("重置文字")
                player.setSource(original)
                runCurrent()
                tick()
                val previousGeneration = player.state.value.generation
                val replacement = original.copy(source = change(original.source))

                player.setSource(replacement)
                assertEquals(replacement.source, player.state.value.source)
                assertEquals("", player.state.value.visibleText)
                assertEquals(0, player.state.value.beatIndex)
                assertTrue(player.state.value.generation > previousGeneration)
                runCurrent()
                tick()
                assertEquals("重", player.state.value.visibleText)
                assertEquals(1, player.state.value.revealedGraphemes)
            }
            runCurrent()
        }
    }

    @Test
    fun setSource_changedRawOrBeatsWithoutNewIdentityRestarts() = runTest {
        CompanionBeatPlayer(backgroundScope).use { player ->
            val original = reply("初始内容")
            player.setSource(original)
            runCurrent()
            tick()
            player.setSource(original.copy(raw = "原文编码改变"))
            assertEquals("", player.state.value.visibleText)
            runCurrent()
            tick()
            assertEquals("初", player.state.value.visibleText)

            val changedBeat = original.beats.single().copy(content = "新正文", spriteKey = "happy")
            player.setSource(original.copy(beats = listOf(changedBeat)))
            assertEquals("", player.state.value.visibleText)
            assertSame(changedBeat, player.state.value.beat)
            runCurrent()
            tick()
            assertEquals("新", player.state.value.visibleText)
        }
    }

    @Test
    fun setSource_freezesCallerBeatCollection() = runTest {
        CompanionBeatPlayer(backgroundScope).use { player ->
            val original = reply("第一拍", "第二拍")
            val mutableBeats = original.beats.toMutableList()
            player.setSource(original.copy(beats = mutableBeats))
            mutableBeats.clear()
            player.advanceOrReveal()
            player.advanceOrReveal()
            assertEquals(2, player.state.value.beatCount)
            assertSame(original.beats[1], player.state.value.beat)
            player.advanceOrReveal()
            assertEquals("第二拍", player.state.value.visibleText)
        }
    }

    @Test
    fun setSource_nullOrEmptyStopsOldTyping() = runTest {
        var delays = 0
        CompanionBeatPlayer(backgroundScope, delayCharacter = { millis ->
            delays++
            delay(millis)
        }).use { player ->
            player.setSource(reply("待取消正文"))
            runCurrent()
            player.setSource(null)
            tick(10)
            assertIdle(player)

            val empty = reply()
            player.setSource(empty)
            val before = player.state.value
            player.advanceOrReveal()
            player.replay()
            tick(10)
            assertEquals(before, player.state.value)
            assertEquals(CompanionBeatPlayerPhase.IDLE, before.phase)
            assertEquals(empty.source, before.source)
            assertNull(before.beat)
            assertEquals(1, delays)
        }
    }

    @Test
    fun emptyBeat_isCompleteWithoutTimerAndDoesNotSkip() = runTest {
        var delays = 0
        CompanionBeatPlayer(backgroundScope, delayCharacter = { millis ->
            delays++
            delay(millis)
        }).use { player ->
            player.setSource(reply("", "正文"))
            runCurrent()
            assertEquals(CompanionBeatPlayerPhase.COMPLETE, player.state.value.phase)
            assertEquals(0, player.state.value.beatIndex)
            assertEquals(0, player.state.value.graphemeCount)
            assertEquals(0, delays)
            player.advanceOrReveal()
            runCurrent()
            tick()
            assertEquals(1, player.state.value.beatIndex)
            assertEquals("正", player.state.value.visibleText)
        }
    }

    @Test
    fun end_replaysLocallyOnNextClickWithoutChangingProjection() = runTest {
        val raw = " \r\n<narration sprite=\"normal\">窗边。</narration>" +
                "<dialogue sprite=\"happy\" voice_emotion=\"calm\">你好。</dialogue>\r\n "
        val source = reply("unused").source.copy(rawHash = companionRawHash(raw))
        val projection = CompanionReplyParser().parse(raw, source, null, "角色")
        val originalBeats = projection.beats.toList()
        CompanionBeatPlayer(backgroundScope).use { player ->
            player.setSource(projection)
            player.advanceOrReveal()
            player.advanceOrReveal()
            player.advanceOrReveal()
            assertSame(projection.beats[1], player.state.value.beat)
            assertEquals("happy", player.state.value.beat?.spriteKey)
            assertEquals(CompanionBeatPlayerPhase.COMPLETE, player.state.value.phase)
            player.advanceOrReveal()
            assertEquals(CompanionBeatPlayerPhase.END, player.state.value.phase)
            assertEquals("你好。", player.state.value.visibleText)
            val ended = player.state.value
            tick(10)
            assertEquals(ended, player.state.value)

            player.advanceOrReveal()
            assertEquals(CompanionBeatPlayerPhase.TYPING, player.state.value.phase)
            assertEquals(0, player.state.value.beatIndex)
            assertEquals("", player.state.value.visibleText)
            assertSame(projection.beats[0], player.state.value.beat)
            assertSame(source, player.state.value.source)
            assertNotEquals(ended.generation, player.state.value.generation)
            assertEquals(raw, projection.raw)
            assertEquals(originalBeats, projection.beats)
            assertEquals(companionRawHash(raw), source.rawHash)
            assertEquals(7L, source.revision)
        }
    }

    @Test
    fun replay_duringLaterBeatReturnsToFirstWithFreshClock() = runTest {
        CompanionBeatPlayer(backgroundScope).use { player ->
            val reply = reply("第一拍", "第二拍")
            player.setSource(reply)
            player.advanceOrReveal()
            player.advanceOrReveal()
            runCurrent()
            tick()
            assertEquals("第", player.state.value.visibleText)
            val previousGeneration = player.state.value.generation
            player.replay()
            assertEquals(0, player.state.value.beatIndex)
            assertEquals("", player.state.value.visibleText)
            assertTrue(player.state.value.generation > previousGeneration)
            runCurrent()
            tick()
            assertEquals("第", player.state.value.visibleText)
            assertSame(reply.beats[0], player.state.value.beat)
        }
    }

    @Test
    fun stop_cancelsAndClearsButAllowsReenteringSameSource() = runTest {
        CompanionBeatPlayer(backgroundScope).use { player ->
            val reply = reply("离场正文")
            player.setSource(reply)
            runCurrent()
            tick()
            player.stop()
            val stopped = player.state.value
            assertIdle(player)
            player.advanceOrReveal()
            player.replay()
            tick(10)
            assertEquals(stopped, player.state.value)
            player.setSource(reply)
            runCurrent()
            tick()
            assertEquals("离", player.state.value.visibleText)
            assertEquals(reply.source, player.state.value.source)
        }
    }

    @Test
    fun close_cancelsAndRefusesLateActionsWithoutCancellingHost() = runTest {
        val player = CompanionBeatPlayer(backgroundScope)
        val reply = reply("永久离场")
        player.setSource(reply)
        runCurrent()
        player.close()
        val closed = player.state.value
        player.close()
        player.setSource(reply)
        player.replay()
        player.advanceOrReveal()
        player.stop()
        tick(10)
        assertIdle(player)
        assertEquals(closed, player.state.value)
        assertTrue(backgroundScope.coroutineContext[Job]!!.isActive)
    }

    @Test
    fun parentCancellation_clearsTypingAndRefusesReuse() = runTest {
        val parent = Job(backgroundScope.coroutineContext[Job])
        val host = CoroutineScope(backgroundScope.coroutineContext + parent)
        val oldTick = CompletableDeferred<Unit>()
        CompanionBeatPlayer(host, delayCharacter = {
            withContext(NonCancellable) { oldTick.await() }
        }).use { player ->
            try {
                val reply = reply("宿主离场")
                player.setSource(reply)
                runCurrent()
                assertEquals(CompanionBeatPlayerPhase.TYPING, player.state.value.phase)
                parent.cancel()
                assertIdle(player)
                val cancelled = player.state.value
                player.setSource(reply)
                player.advanceOrReveal()
                player.replay()
                oldTick.complete(Unit)
                tick(10)
                assertEquals(cancelled, player.state.value)
                assertFalse(parent.isActive)
            } finally {
                oldTick.complete(Unit)
                parent.cancel()
            }
        }
    }

    @Test
    fun parentCancellation_afterCompleteOrEndStillClearsSource() = runTest {
        for (end in listOf(false, true)) {
            val parent = Job(backgroundScope.coroutineContext[Job])
            val host = CoroutineScope(backgroundScope.coroutineContext + parent)
            CompanionBeatPlayer(host).use { player ->
                player.setSource(reply("已完整正文"))
                player.advanceOrReveal()
                if (end) player.advanceOrReveal()
                assertEquals(
                    if (end) CompanionBeatPlayerPhase.END else CompanionBeatPlayerPhase.COMPLETE,
                    player.state.value.phase,
                )
                parent.cancel()
                assertIdle(player)
                tick(10)
                assertIdle(player)
            }
        }
    }

    @Test
    fun cancelledClock_clearsStateWithoutCompletingOrAdvancing() = runTest {
        CompanionBeatPlayer(backgroundScope, delayCharacter = {
            throw CancellationException("local clock cancelled")
        }).use { player ->
            val reply = reply("正文", "下一拍")
            player.setSource(reply)
            runCurrent()
            assertIdle(player)
            assertTrue(backgroundScope.coroutineContext[Job]!!.isActive)
            player.setSource(reply)
            player.advanceOrReveal()
            assertEquals(CompanionBeatPlayerPhase.COMPLETE, player.state.value.phase)
            assertEquals(0, player.state.value.beatIndex)
            assertEquals("正文", player.state.value.visibleText)
        }
    }

    @Test
    fun failedClock_revealsCurrentTextAndKeepsHostActive() = runTest {
        CompanionBeatPlayer(backgroundScope, delayCharacter = {
            error("local animation clock failed")
        }).use { player ->
            player.setSource(reply("完整正文", "仍需下一点"))
            runCurrent()
            assertEquals(CompanionBeatPlayerPhase.COMPLETE, player.state.value.phase)
            assertEquals("完整正文", player.state.value.visibleText)
            assertEquals(0, player.state.value.beatIndex)
            assertEquals(4, player.state.value.revealedGraphemes)
            assertTrue(backgroundScope.coroutineContext[Job]!!.isActive)
            tick(10)
            assertEquals(0, player.state.value.beatIndex)
        }
    }

    @Test
    fun oldFailedClock_doesNotRevealOrClearReplacementSource() = runTest {
        val oldTick = CompletableDeferred<Unit>()
        var delays = 0
        CompanionBeatPlayer(backgroundScope, delayCharacter = { millis ->
            if (delays++ == 0) {
                withContext(NonCancellable) {
                    oldTick.await()
                    error("stale animation clock failed")
                }
            } else {
                delay(millis)
            }
        }).use { player ->
            player.setSource(reply("旧正文"))
            runCurrent()
            val replacement = reply("新正文")
            player.setSource(replacement)
            runCurrent()
            oldTick.complete(Unit)
            runCurrent()
            assertSame(replacement.source, player.state.value.source)
            assertEquals(CompanionBeatPlayerPhase.TYPING, player.state.value.phase)
            assertEquals("", player.state.value.visibleText)
            tick()
            assertEquals("新", player.state.value.visibleText)
        }
    }

    @Test
    fun immediateClock_andUnconfinedDispatcherKeepsCorrectOwner() = runTest {
        val host = CoroutineScope(
            backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
        )
        var delays = 0
        CompanionBeatPlayer(host, delayCharacter = { delays++ }).use { player ->
            player.setSource(reply("甲乙", "丙丁"))
            assertEquals(CompanionBeatPlayerPhase.COMPLETE, player.state.value.phase)
            assertEquals("甲乙", player.state.value.visibleText)
            assertEquals(2, delays)
            player.advanceOrReveal()
            assertEquals("丙丁", player.state.value.visibleText)
            assertEquals(4, delays)
            player.replay()
            assertEquals("甲乙", player.state.value.visibleText)
            assertEquals(6, delays)
            runCurrent()
            assertEquals(0, player.state.value.beatIndex)
        }
    }

    @Test
    fun synchronousStateObserver_replacingSourceCannotInstallOldClock() = runTest {
        var delays = 0
        CompanionBeatPlayer(backgroundScope, delayCharacter = { millis ->
            delays++
            delay(millis)
        }).use { player ->
            val original = reply("旧正文")
            val replacement = reply("替换正文")
            var replaced = false
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                player.state.collect { state ->
                    if (!replaced && state.source == original.source &&
                        state.phase == CompanionBeatPlayerPhase.TYPING
                    ) {
                        replaced = true
                        player.setSource(replacement)
                    }
                }
            }
            try {
                player.setSource(original)
                runCurrent()
                assertTrue(replaced)
                assertSame(replacement.source, player.state.value.source)
                assertEquals(1, delays)
                tick()
                assertEquals("替", player.state.value.visibleText)
                player.advanceOrReveal()
                tick(10)
                assertEquals("替换正文", player.state.value.visibleText)
                assertEquals(CompanionBeatPlayerPhase.COMPLETE, player.state.value.phase)
            } finally {
                observer.cancel()
            }
        }
    }

    @Test
    fun typing_preservesChineseCombinedAndEmojiPrefixes() = runTest {
        assertTypedClusters(
            listOf("中", "文", "e\u0301", " ", "👩🏽‍🚀", "🇨🇳", "🇺🇸", "1️⃣", "。")
        )
    }

    @Test
    fun typing_keepsComplexEmojiAndTagFlagWhole() = runTest {
        assertTypedClusters(
            listOf(
                "👨‍👩‍👧‍👦", "👩‍❤️‍💋‍👨", "🧑🏿‍🦽", "🫱🏻‍🫲🏽",
                "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F",
                "©️‍🔥",
            )
        )
    }

    @Test
    fun typing_keepsRegionalIndicatorsInPairsWithTrailingSingleton() = runTest {
        assertTypedClusters(listOf("🇨🇳", "🇺🇸", "🇫"))
        assertTypedClusters(listOf("🇨🇳\u0301", "🇺🇸"))
    }

    @Test
    fun typing_preservesCombiningScriptsAndControlBoundaries() = runTest {
        assertTypedClusters(
            listOf(
                "a\u0308\u0301",
                "\r\n",
                "\u1100\u1161\u11A8",
                "क्ष",
                "का",
                "\t",
                "尾"
            )
        )
        assertTypedClusters(listOf("\u0301\u0308", "A", "\u0600B"))
    }

    @Test
    fun typing_keepsIndicConjunctsWholeWithoutJoiningUnrelatedText() = runTest {
        assertTypedClusters(
            listOf(
                "\u0915\u094D\u0937", "\u0995\u09CD\u09B7", "\u0A95\u0ACD\u0AB7",
                "\u0B15\u0B4D\u0B37", "\u0C15\u0C4D\u0C37", "\u0D15\u0D4D\u0D37",
                "क\u093C\u0301्\u200D\u093Cष", "क्ष्त", "क््\u200Dष",
            )
        )
        assertTypedClusters(
            listOf(
                "्", "क", "क", "ष", "अ्", "क", "a्", "क", "क्\u200C", "ष", "क्\u034F", "ष",
                "क्\uFE0F", "ष", "कु्", "ष", "क\u200D", "ष", "क्", "\t", "ष",
            )
        )
    }

    @Test
    fun constructor_rejectsNonPositiveDelays() = runTest {
        for (invalid in listOf(0L, -1L)) {
            try {
                CompanionBeatPlayer(backgroundScope, characterDelayMillis = invalid).close()
                fail("非正间隔应被拒绝")
            } catch (_: IllegalArgumentException) {
                assertTrue(backgroundScope.coroutineContext[Job]!!.isActive)
            }
        }
    }

    private fun TestScope.assertTypedClusters(clusters: List<String>) {
        CompanionBeatPlayer(backgroundScope).use { player ->
            player.setSource(reply(clusters.joinToString("")))
            assertEquals(clusters.size, player.state.value.graphemeCount)
            runCurrent()
            var expected = ""
            clusters.forEachIndexed { index, cluster ->
                tick()
                expected += cluster
                assertEquals("第 ${index + 1} 个字符边界", expected, player.state.value.visibleText)
                assertEquals(index + 1, player.state.value.revealedGraphemes)
                assertEquals(
                    if (index == clusters.lastIndex) {
                        CompanionBeatPlayerPhase.COMPLETE
                    } else {
                        CompanionBeatPlayerPhase.TYPING
                    },
                    player.state.value.phase,
                )
            }
        }
        runCurrent()
    }

    private fun TestScope.tick(count: Int = 1) {
        repeat(count) {
            advanceTimeBy(DELAY_MILLIS)
            runCurrent()
        }
    }

    private fun assertIdle(player: CompanionBeatPlayer) {
        val state = player.state.value
        assertEquals(CompanionBeatPlayerPhase.IDLE, state.phase)
        assertNull(state.source)
        assertNull(state.beat)
        assertEquals(-1, state.beatIndex)
        assertEquals(0, state.beatCount)
        assertEquals("", state.visibleText)
        assertEquals(0, state.graphemeCount)
        assertEquals(0, state.revealedGraphemes)
    }

    private fun reply(vararg contents: String): ParsedCompanionReply {
        val raw = contents.joinToString("\n")
        var offset = 0
        val beats = contents.mapIndexed { index, content ->
            CompanionBeat(
                id = "beat:$index",
                contentType = RoleplayContentType.DIALOGUE,
                speaker = RoleplaySpeaker.CHARACTER,
                speakerName = "角色",
                content = content,
                rawSpan = CompanionRawSpan(offset, offset + content.length),
            ).also { offset += content.length + 1 }
        }
        return ParsedCompanionReply(
            source = CompanionReplySource(
                messageId = "message",
                conversationId = "conversation",
                turnId = "turn",
                revision = 7,
                protocolVersion = 1,
                parserVersion = CompanionReplyParser.PARSER_VERSION,
                rawHash = companionRawHash(raw),
                attemptId = "attempt",
                configurationRevision = 8,
                characterId = "character",
            ),
            raw = raw,
            beats = beats,
            observation = null,
        )
    }

    private companion object {
        const val DELAY_MILLIS = 35L
    }
}
