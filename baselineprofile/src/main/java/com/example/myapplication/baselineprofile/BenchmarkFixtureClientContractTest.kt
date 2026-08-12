package com.example.myapplication.baselineprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class BenchmarkFixtureClientContractTest {

    @Test
    fun encodeDescriptor_measureScenario_usesExactFiveMinuteTtl() {
        assertEncodedTtl(
            scenario = BenchmarkScenario.S2_POPULATED_STARTUP,
            expectedPhase = BenchmarkPhase.MEASURE,
            expectedTtlMillis = 300_000L,
        )
    }

    @Test
    fun encodeDescriptor_profileScenario_usesExactThirtyMinuteTtl() {
        assertEncodedTtl(
            scenario = BenchmarkScenario.P1_BASELINE_PROFILE,
            expectedPhase = BenchmarkPhase.PROFILE,
            expectedTtlMillis = 1_800_000L,
        )
    }

    private fun assertEncodedTtl(
        scenario: BenchmarkScenario,
        expectedPhase: BenchmarkPhase,
        expectedTtlMillis: Long,
    ) {
        assertEquals(expectedTtlMillis, BenchmarkFixtureClient.ttlMillisForScenario(scenario))

        val descriptor = BenchmarkFixtureClient.encodeDescriptor(
            sessionId = SESSION_ID,
            scenario = scenario,
            createdAtEpochMillis = CREATED_AT_EPOCH_MILLIS,
        ).lineSequence()
            .filter(String::isNotBlank)
            .associate { line ->
                val separatorIndex = line.indexOf('=')
                check(separatorIndex > 0) { "无效 benchmark session 行：$line" }
                line.substring(0, separatorIndex) to line.substring(separatorIndex + 1)
            }

        assertEquals(expectedPhase.wireId, descriptor["phase"])
        assertEquals(expectedTtlMillis.toString(), descriptor["ttl_millis"])
    }

    private companion object {
        const val SESSION_ID = "01234567-89ab-cdef-0123-456789abcdef"
        const val CREATED_AT_EPOCH_MILLIS = 1_800_000_000_000L
    }
}
