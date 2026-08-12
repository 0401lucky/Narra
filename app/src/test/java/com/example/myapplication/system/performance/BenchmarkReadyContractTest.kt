package com.example.myapplication.system.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkReadyContractTest {

    @Test
    fun readyAndActionIds_areUniqueStableAndResourceSafe() {
        val allIds = BenchmarkReadyIds.ALL + BenchmarkActionIds.ALL

        assertEquals(allIds.size, allIds.toSet().size)
        assertTrue(allIds.all { id -> id.length <= 64 })
        assertTrue(allIds.all { id -> id.matches(Regex("[a-z0-9_]+")) })
        assertTrue(BenchmarkReadyIds.ALL.all { it.startsWith("benchmark_ready_") })
        assertTrue(BenchmarkActionIds.ALL.all { it.startsWith("benchmark_action_") })
    }

    @Test
    fun everyScenario_declaresKnownInitialReadyId() {
        BenchmarkScenarioId.entries.forEach { scenario ->
            assertTrue(
                "${scenario.wireId} 缺少合法 ready",
                scenario.initialReadyId in BenchmarkReadyIds.ALL,
            )
        }
    }

    @Test
    fun navigationContract_keepsNormalGateAndUsesDeterministicFixtureTargets() {
        assertEquals(
            BenchmarkInitialUiTarget.NONE,
            BenchmarkUiNavigationContract.initialTarget(BenchmarkScenarioId.S0_FIRST_RUN_STARTUP),
        )
        assertEquals(
            BenchmarkInitialUiTarget.CHAT,
            BenchmarkUiNavigationContract.initialTarget(BenchmarkScenarioId.F1_CHAT_SCROLL),
        )
        assertEquals(
            BenchmarkInitialUiTarget.ROLEPLAY_SESSION,
            BenchmarkUiNavigationContract.initialTarget(BenchmarkScenarioId.F2_ROLEPLAY_SCROLL),
        )
        assertTrue(
            BenchmarkUiNavigationContract.shouldSelectFixedChatConversation(
                BenchmarkScenarioId.F1_CHAT_SCROLL,
            ),
        )
        assertTrue(
            BenchmarkUiNavigationContract.shouldSelectFixedChatConversation(
                BenchmarkScenarioId.P1_BASELINE_PROFILE,
            ),
        )
    }
}
