package com.example.myapplication.data.repository.roleplay

import android.app.Application
import com.example.myapplication.model.*
import com.example.myapplication.testutil.CompanionEntryTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionHistoryScopeTest : CompanionEntryTestFixture() {
    @Test
    fun continuous_freshUnextractedOriginalsCrossConversationWithFirstPersona() =
        runBlocking<Unit> {
            val old = scene()
            add(old, "刚刚在旧会话发现的海边花店", "fresh")
            val current = scene()
            val otherRole = scene(character = "bob")
            add(otherRole, "乙私有信息", "bob")
            val otherPersona = scene(persona = "身份乙")
            add(otherPersona, "另一个身份的内容", "other-persona")
            val scope = history.freeze(
                current,
                CompanionHistoryCursor(clock++, "\uffff"),
                CompanionHistorySelection(20)
            )
            assertEquals(listOf("fresh"), history.read(scope).map { it.id })
            assertTrue(archive.listRecords(current).isEmpty())
            assertTrue(archive.listPendingIngress(old).isNotEmpty())
        }

    @Test
    fun emptyWindowBreakpointAndExclusions_areAuthoritativeAndNeverFallBack() = runBlocking<Unit> {
        val current = scene()
        val before = add(current, "断点前", "before")
        add(current, "排除内容", "excluded")
        val after = add(current, "断点后", "after")
        val cutoff = CompanionHistoryCursor(clock++, "\uffff")
        val empty = history.freeze(
            current,
            cutoff,
            CompanionHistorySelection(20, allowedConversationIds = emptySet())
        )
        assertTrue(history.read(empty).isEmpty())
        val window = history.freeze(
            current, cutoff, CompanionHistorySelection(
                20,
                startsAfter = mapOf(
                    current.conversationId to CompanionHistoryCursor(
                        before.createdAt,
                        before.id
                    )
                ),
                excludedMessageIds = setOf("excluded")
            )
        )
        assertEquals(listOf(after.id), history.read(window).map { it.id })
        assertTrue(
            history.read(history.freeze(current, cutoff, CompanionHistorySelection(0))).isEmpty()
        )
    }

    @Test
    fun independentRun_andGroupPrivateHistoryNeverAggregate() = runBlocking<Unit> {
        val daily = scene()
        add(daily, "日常内容", "daily")
        val independent = scene(purpose = MemoryPurpose.INDEPENDENT)
        add(independent, "独立当前", "independent")
        val group = RoleplayScenario(
            id = "group",
            assistantId = "alice",
            memoryPurpose = MemoryPurpose.CONTINUOUS,
            userPersonaOverride = "身份甲",
            chatType = RoleplayChatType.GROUP,
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE
        )
        roleplay.upsertScenarioWithParticipants(
            group, listOf(
                RoleplayGroupParticipant(scenarioId = group.id, assistantId = "alice"),
                RoleplayGroupParticipant(scenarioId = group.id, assistantId = "bob")
            )
        )
        val groupSession = roleplay.startScenario(group.id).session
        val groupContext = archive.getRuntimeContext(groupSession.conversationId, "alice")!!
        add(
            groupContext,
            "群私有",
            "group-private",
            MessageRole.ASSISTANT,
            listOf(thoughtMessagePart("群私有"))
        )
        val scope = history.freeze(
            independent,
            CompanionHistoryCursor(clock++, "\uffff"),
            CompanionHistorySelection(100)
        )
        assertEquals(listOf("independent"), history.read(scope).map { it.id })
        val dailyScope = history.freeze(
            daily,
            CompanionHistoryCursor(clock++, "\uffff"),
            CompanionHistorySelection(100)
        )
        assertEquals(listOf("daily"), history.read(dailyScope).map { it.id })
    }

    @Test
    fun frozenSources_rejectEditDeletionAndPersonaSwitch() = runBlocking<Unit> {
        val current = scene()
        val message = add(current, "原文", "source")
        val scope = history.freeze(
            current,
            CompanionHistoryCursor(clock++, "\uffff"),
            CompanionHistorySelection(8)
        )
        assertTrue(history.validate(scope))
        store.upsertMessages(listOf(message.copy(content = "已修订")))
        assertFalse(history.validate(scope))
        val refreshed = history.freeze(current, scope.cutoff, CompanionHistorySelection(8), scope)
        assertEquals("已修订", history.read(refreshed).single().content)
        conversations.deleteMessage(current.conversationId, message.id)
        assertFalse(history.validate(refreshed))
        assertTrue(runCatching { history.read(refreshed) }.isFailure)
        roleplay.upsertScenario(
            roleplay.getScenario(current.scenarioId)!!.copy(userPersonaOverride = "新身份")
        )
        assertFalse(history.validate(scope.copy(messages = emptyList())))
    }

    @Test
    fun rerollScope_doesNotAcquireLaterMessagesOrOtherTurnsPendingInput() = runBlocking<Unit> {
        val current = scene()
        add(current, "原触发背景", "before")
        val scope = history.freeze(
            current,
            CompanionHistoryCursor(clock++, "\uffff"),
            CompanionHistorySelection(20)
        )
        add(current, "后来线上对话", "later")
        val encounter = repository.createEncounter(current, CompanionOpeningMode.DIRECT)
        repository.beginTurn(
            encounter.id,
            CompanionTurnKind.INPUT,
            ChatMessage("pending", current.conversationId, MessageRole.USER, "未完成输入")
        )
        val next = history.freeze(
            current,
            CompanionHistoryCursor(clock++, "\uffff"),
            CompanionHistorySelection(20),
            scope
        )
        assertEquals(listOf("before"), history.read(next).map { it.id })
        assertFalse(
            history.freeze(
                current,
                CompanionHistoryCursor(clock++, "\uffff"),
                CompanionHistorySelection(20)
            ).messages.any { it.messageId == "pending" })
    }
}
