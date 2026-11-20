package com.example.myapplication.conversation

import com.example.myapplication.model.*
import com.example.myapplication.testutil.knowledgeResponse
import com.example.myapplication.testutil.residentCandidate
import com.example.myapplication.testutil.stateUpdate
import org.junit.Assert.*
import org.junit.Test

class MemoryKnowledgeParserTest {
    private val source = MemorySourceRef("source", 3, 0, 12)
    private val resident = MemoryResidentTarget(
        "resident",
        7,
        MemoryResidentCategory.USER,
        "星河路128号",
        false,
        false
    )
    private val state =
        MemoryStateTarget("state", 4, MemoryStateKey.TASK, "修好罗盘", "ACTIVE", false)
    private val snapshot = MemoryExtractionSnapshot(
        listOf(
            MemoryExtractionSpan(
                source,
                "message",
                "user",
                "USER",
                "persona",
                listOf("a"),
                1_800_000_000_000L,
                MemorySourceKind.MESSAGE,
                MemorySemanticKind.SPEECH,
                "改成星河路182号"
            )
        ),
        emptyList(), "合成角色", "合成身份",
        knowledgeTargets = MemoryKnowledgeTargets(
            residents = listOf(resident),
            states = listOf(state)
        ),
    )

    @Test
    fun replacementAndEnd_mapFrozenIdsRevisionsAndSources() {
        val batch = parse(
            residents = listOf(
                residentCandidate(
                    "星河路182号",
                    operation = "replace",
                    targetRef = "R0",
                    expectedRevision = 7
                )
            ),
            states = listOf(stateUpdate(MemoryStateKey.TASK, "罗盘已修好", "end", 4)),
        )
        assertTrue(batch.records.isEmpty())
        assertEquals(MemoryResidentOperation.REPLACE, batch.residents.single().operation)
        assertEquals("resident", batch.residents.single().targetId)
        assertEquals(7L, batch.residents.single().expectedRevision)
        assertEquals(listOf(source), batch.residents.single().sources)
        assertEquals(MemoryStateOperation.END, batch.states.single().operation)
        assertEquals(4L, batch.states.single().expectedRevision)
    }

    @Test
    fun legacySavedCandidates_keepAddAndStateWithoutInventingTargets() {
        val batch = MemoryExtractionParser.parse(
            knowledgeResponse(
                residents = listOf(
                    residentCandidate(
                        "喜欢栀子花",
                        durability = null
                    ) - "operation"
                ),
                states = listOf(
                    stateUpdate(
                        MemoryStateKey.MOOD,
                        "今天紧张",
                        expectedRevision = null
                    )
                ),
            ), snapshot.copy(knowledgeTargets = null)
        )
        assertEquals(MemoryResidentOperation.ADD, batch.residents.single().operation)
        assertNull(batch.states.single().expectedRevision)
        assertEquals(listOf(source), batch.states.single().sources)
    }

    @Test
    fun wrongTargetRevisionCategoryAndForeignReferences_rejectWholeResponse() {
        val replacement = residentCandidate(
            "星河路182号",
            operation = "replace",
            targetRef = "R0",
            expectedRevision = 7
        )
        listOf(
            replacement + ("target_ref" to "R9"),
            replacement + ("expected_revision" to 6),
            replacement + ("category" to "shared"),
            replacement + ("source_refs" to listOf("M9")),
            replacement + ("characterId" to "b"),
            replacement + ("subjective" to "false"),
        ).forEach { assertInvalid { parse(residents = listOf(it)) } }
        assertInvalid { parse(residents = listOf(replacement, replacement)) }
        assertInvalid {
            parse(
                residents = listOf(
                    residentCandidate(
                        "越权新增",
                        targetRef = "R0",
                        expectedRevision = 7
                    )
                )
            )
        }
        assertInvalid {
            MemoryExtractionParser.parse(
                knowledgeResponse(
                    residents = listOf(
                        replacement
                    )
                ), snapshot.copy(knowledgeTargets = null)
            )
        }
    }

    @Test
    fun stateCasAndKeyValidation_rejectsMissingOrDuplicateTargets() {
        assertInvalid {
            parse(
                states = listOf(
                    stateUpdate(
                        MemoryStateKey.TASK,
                        "迟到",
                        expectedRevision = 3
                    )
                )
            )
        }
        assertInvalid {
            parse(
                states = listOf(
                    stateUpdate(
                        MemoryStateKey.TASK,
                        "无版本",
                        expectedRevision = null
                    )
                )
            )
        }
        assertInvalid {
            parse(
                states = listOf(
                    stateUpdate(
                        MemoryStateKey.LOCATION,
                        "不存在",
                        "end"
                    )
                )
            )
        }
        val update = stateUpdate(MemoryStateKey.TASK, "继续", expectedRevision = 4)
        assertInvalid { parse(states = listOf(update, update)) }
        assertInvalid { parse(states = listOf(update + ("state_key" to "personality"))) }
        assertInvalid { parse(states = listOf(update + ("expires_at" to 0))) }
    }

    @Test
    fun reinforceCannotRewriteContentOrTurnOpinionIntoFact() {
        val reinforcement = residentCandidate(
            resident.content,
            operation = "reinforce",
            targetRef = "R0",
            expectedRevision = 7
        )
        assertEquals(
            MemoryResidentOperation.REINFORCE,
            parse(residents = listOf(reinforcement)).residents.single().operation
        )
        assertInvalid { parse(residents = listOf(reinforcement + ("content" to "星河路182号"))) }
        assertInvalid { parse(residents = listOf(reinforcement + ("subjective" to true))) }
    }

    @Test
    fun thoughtAndDescription_remainSubjectiveEvenWhenModelClaimsFact() {
        listOf(MemorySemanticKind.THOUGHT, MemorySemanticKind.DESCRIPTION).forEach { kind ->
            val parsed = MemoryExtractionParser.parse(
                knowledgeResponse(
                residents = listOf(residentCandidate("我觉得芮珩更信任我")),
            ), snapshot.copy(sources = snapshot.sources.map { it.copy(semanticKind = kind) })
            )
            assertTrue(parsed.residents.single().subjective)
        }
    }

    @Test
    fun temporaryAndStateDuplicates_areNotStableResidents() {
        val batch = parse(
            residents = listOf(
                residentCandidate("今天紧张", durability = "temporary"),
                residentCandidate("在北站"),
                residentCandidate("喜欢栀子花"),
            ), states = listOf(stateUpdate(MemoryStateKey.LOCATION, "在北站"))
        )
        assertEquals(listOf(false, false, true), batch.residents.map { it.stable })
        assertInvalid {
            parse(
                residents = listOf(
                    residentCandidate(
                        "伪造标记",
                        durability = "eternal"
                    )
                )
            )
        }
    }

    @Test
    fun omittedActions_leaveExistingTargetsUnchanged() {
        val empty = parse()
        assertTrue(empty.residents.isEmpty())
        assertTrue(empty.states.isEmpty())
        assertEquals(listOf(resident), snapshot.knowledgeTargets?.residents)
        assertEquals(listOf(state), snapshot.knowledgeTargets?.states)
    }

    private fun parse(
        residents: List<Map<String, Any>> = emptyList(),
        states: List<Map<String, Any>> = emptyList()
    ) =
        MemoryExtractionParser.parse(knowledgeResponse(residents, states), snapshot)

    private fun assertInvalid(block: () -> Unit) {
        try {
            block(); fail("应拒绝无效认知动作")
        } catch (_: IllegalArgumentException) {
        } catch (_: IllegalStateException) {
        }
    }
}
