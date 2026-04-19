package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayInteractionSpecTest {

    private val offlineDialogueDefault = RoleplayInteractionSpec(
        interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
        longformModeEnabled = false,
        enableRoleplayProtocol = true,
    )

    // --- normalized() ---

    @Test
    fun normalized_promotesLongformOfflineDialogueToOfflineLongform() {
        val broken = RoleplayInteractionSpec(
            interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
            longformModeEnabled = true,
            enableRoleplayProtocol = true,
        )

        val normalized = broken.normalized()

        assertEquals(RoleplayInteractionMode.OFFLINE_LONGFORM, normalized.interactionMode)
        assertTrue(normalized.longformModeEnabled)
        assertTrue(normalized.enableRoleplayProtocol)
    }

    @Test
    fun normalized_isIdempotentWhenAlreadyConsistent() {
        val spec = offlineDialogueDefault.copy(
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
            longformModeEnabled = true,
        )

        assertEquals(spec, spec.normalized())
    }

    @Test
    fun normalized_doesNotTouchOnlinePhoneEvenIfLongformFlagOn() {
        val spec = offlineDialogueDefault.copy(
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            longformModeEnabled = true,
        )

        val normalized = spec.normalized()

        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, normalized.interactionMode)
        assertTrue(normalized.longformModeEnabled)
    }

    // --- withInteractionMode(...) ---

    @Test
    fun withInteractionMode_offlineLongformForcesLongformOnAndProtocolOff() {
        val next = offlineDialogueDefault.withInteractionMode(RoleplayInteractionMode.OFFLINE_LONGFORM)

        assertEquals(RoleplayInteractionMode.OFFLINE_LONGFORM, next.interactionMode)
        assertTrue(next.longformModeEnabled)
        assertFalse(next.enableRoleplayProtocol)
    }

    @Test
    fun withInteractionMode_offlineDialogueClearsLongformButKeepsProtocol() {
        val before = RoleplayInteractionSpec(
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
            longformModeEnabled = true,
            enableRoleplayProtocol = true,
        )

        val next = before.withInteractionMode(RoleplayInteractionMode.OFFLINE_DIALOGUE)

        assertEquals(RoleplayInteractionMode.OFFLINE_DIALOGUE, next.interactionMode)
        assertFalse(next.longformModeEnabled)
        assertTrue(next.enableRoleplayProtocol)
    }

    @Test
    fun withInteractionMode_onlinePhoneForcesLongformOffAndProtocolOn() {
        val before = RoleplayInteractionSpec(
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
            longformModeEnabled = true,
            enableRoleplayProtocol = false,
        )

        val next = before.withInteractionMode(RoleplayInteractionMode.ONLINE_PHONE)

        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, next.interactionMode)
        assertFalse(next.longformModeEnabled)
        assertTrue(next.enableRoleplayProtocol)
    }

    // --- withLongform(...) ---

    @Test
    fun withLongform_trueForcesOfflineLongformAndProtocolOff() {
        val next = offlineDialogueDefault.withLongform(true)

        assertEquals(RoleplayInteractionMode.OFFLINE_LONGFORM, next.interactionMode)
        assertTrue(next.longformModeEnabled)
        assertFalse(next.enableRoleplayProtocol)
    }

    @Test
    fun withLongform_falseDemotesLongformToOfflineDialogue() {
        val before = RoleplayInteractionSpec(
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
            longformModeEnabled = true,
            enableRoleplayProtocol = false,
        )

        val next = before.withLongform(false)

        assertEquals(RoleplayInteractionMode.OFFLINE_DIALOGUE, next.interactionMode)
        assertFalse(next.longformModeEnabled)
        assertFalse(next.enableRoleplayProtocol)
    }

    @Test
    fun withLongform_falseFromOnlinePhoneKeepsOnlinePhoneMode() {
        val before = RoleplayInteractionSpec(
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            longformModeEnabled = false,
            enableRoleplayProtocol = true,
        )

        val next = before.withLongform(false)

        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, next.interactionMode)
        assertFalse(next.longformModeEnabled)
        assertTrue(next.enableRoleplayProtocol)
    }

    // --- withRoleplayProtocol(...) ---

    @Test
    fun withRoleplayProtocol_offlineBranchNormalizesModeToOfflineDialogue() {
        val before = RoleplayInteractionSpec(
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
            longformModeEnabled = false,
            enableRoleplayProtocol = false,
        )

        val next = before.withRoleplayProtocol(true)

        assertEquals(RoleplayInteractionMode.OFFLINE_DIALOGUE, next.interactionMode)
        assertFalse(next.longformModeEnabled)
        assertTrue(next.enableRoleplayProtocol)
    }

    @Test
    fun withRoleplayProtocol_underLongformKeepsMode() {
        val before = RoleplayInteractionSpec(
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
            longformModeEnabled = true,
            enableRoleplayProtocol = false,
        )

        val next = before.withRoleplayProtocol(true)

        assertEquals(RoleplayInteractionMode.OFFLINE_LONGFORM, next.interactionMode)
        assertTrue(next.longformModeEnabled)
        assertTrue(next.enableRoleplayProtocol)
    }

    @Test
    fun withRoleplayProtocol_underOnlinePhoneKeepsMode() {
        val before = RoleplayInteractionSpec(
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            longformModeEnabled = false,
            enableRoleplayProtocol = true,
        )

        val next = before.withRoleplayProtocol(false)

        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, next.interactionMode)
        assertFalse(next.longformModeEnabled)
        assertFalse(next.enableRoleplayProtocol)
    }

    // --- RoleplayScenario 扩展 ---

    @Test
    fun scenarioWithInteractionMode_delegatesToSpec() {
        val scenario = RoleplayScenario(
            title = "示例",
            interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
            longformModeEnabled = false,
            enableRoleplayProtocol = true,
        )

        val next = scenario.withInteractionMode(RoleplayInteractionMode.OFFLINE_LONGFORM)

        assertEquals(RoleplayInteractionMode.OFFLINE_LONGFORM, next.interactionMode)
        assertTrue(next.longformModeEnabled)
        assertFalse(next.enableRoleplayProtocol)
        assertEquals(scenario.title, next.title)
    }

    @Test
    fun scenarioWithLongform_delegatesToSpec() {
        val scenario = RoleplayScenario(
            title = "示例",
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
            longformModeEnabled = true,
            enableRoleplayProtocol = false,
        )

        val next = scenario.withLongform(false)

        assertEquals(RoleplayInteractionMode.OFFLINE_DIALOGUE, next.interactionMode)
        assertFalse(next.longformModeEnabled)
        assertFalse(next.enableRoleplayProtocol)
    }

    @Test
    fun scenarioWithRoleplayProtocol_delegatesToSpec() {
        val scenario = RoleplayScenario(
            title = "示例",
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
            longformModeEnabled = false,
            enableRoleplayProtocol = false,
        )

        val next = scenario.withRoleplayProtocol(true)

        assertEquals(RoleplayInteractionMode.OFFLINE_DIALOGUE, next.interactionMode)
        assertFalse(next.longformModeEnabled)
        assertTrue(next.enableRoleplayProtocol)
    }

    @Test
    fun scenarioToInteractionSpec_extractsOnlyThreeFields() {
        val scenario = RoleplayScenario(
            title = "保留标题",
            description = "保留描述",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            longformModeEnabled = false,
            enableRoleplayProtocol = true,
        )

        val spec = scenario.toInteractionSpec()

        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, spec.interactionMode)
        assertFalse(spec.longformModeEnabled)
        assertTrue(spec.enableRoleplayProtocol)
    }

    @Test
    fun scenarioWithInteractionSpec_keepsUnrelatedFieldsUntouched() {
        val scenario = RoleplayScenario(
            title = "保留标题",
            description = "保留描述",
            assistantId = "assistant-42",
            interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
            longformModeEnabled = false,
            enableRoleplayProtocol = false,
        )
        val newSpec = RoleplayInteractionSpec(
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            longformModeEnabled = false,
            enableRoleplayProtocol = true,
        )

        val next = scenario.withInteractionSpec(newSpec)

        assertEquals("保留标题", next.title)
        assertEquals("保留描述", next.description)
        assertEquals("assistant-42", next.assistantId)
        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, next.interactionMode)
        assertTrue(next.enableRoleplayProtocol)
    }

    @Test
    fun withInteractionMode_returnsSameInstanceShapeForNoOp() {
        val spec = RoleplayInteractionSpec(
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            longformModeEnabled = false,
            enableRoleplayProtocol = true,
        )

        // 重复切换同一 mode 不应破坏字段一致性
        val next = spec.withInteractionMode(RoleplayInteractionMode.ONLINE_PHONE)

        assertEquals(spec, next)
        // data class copy 不保证 === 但语义相等即通过；防止回归。
        assertSame(spec.interactionMode, next.interactionMode)
    }
}
