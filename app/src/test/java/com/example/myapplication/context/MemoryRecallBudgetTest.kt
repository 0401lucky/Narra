package com.example.myapplication.context

import com.example.myapplication.model.*
import org.junit.Assert.*
import org.junit.Test

class MemoryRecallBudgetTest {
    private val context =
        MemoryRuntimeContext("a", "space", "conversation", runId = "run", personaKey = "p")

    @Test
    fun reservationsAndBorrowing_applyToRenderedTokensAndEpisodeCountOnly() {
        val candidates = listOf(
            candidate("resident", "喜欢淡茶。", MemoryRecallLayer.RESIDENT),
            candidate("pin", "别忘记带钥匙。", MemoryRecallLayer.PINNED),
            candidate("episode", "经历".repeat(500), MemoryRecallLayer.EXPERIENCE),
            candidate("extra", "另一经历。", MemoryRecallLayer.EXPERIENCE),
            candidate("state", "目前正在等车。", MemoryRecallLayer.STATE),
        )
        val plan = MemoryRecallBudget().allocate(context, candidates, 1, 2000)
        assertEquals(
            setOf("resident", "pin", "episode", "state"),
            plan.materials.map { it.ref.id }.toSet()
        )
        assertFalse(plan.materials.single { it.ref.id == "episode" }.fragment)
        assertTrue(plan.estimatedTokens <= 2000)
        assertEquals(MemoryRecallText.estimateTokens(plan.section), plan.estimatedTokens)
        assertTrue(plan.omitted.any { it.ref.id == "extra" && it.reason == MemoryRecallOmissionReason.ITEM_LIMIT })
    }

    @Test
    fun longRecord_preservesOriginalAndEmitsExplicitSpanWithoutSplittingSurrogates() {
        val body = "😀旧事".repeat(800) + "芮珩在731号室。"
        val original = candidate("long", body, MemoryRecallLayer.EXPERIENCE)
        val plan = MemoryRecallBudget().allocate(
            context,
            listOf(original.copy(hitStart = body.length - 45, hitEnd = body.length)),
            6,
            300
        )
        val sent = plan.materials.single()
        assertTrue(sent.fragment)
        assertEquals(body.substring(sent.ref.spanStart, sent.ref.spanEnd), sent.content)
        assertFalse(sent.content.first().isLowSurrogate())
        assertTrue(sent.content.contains("731"))
        assertEquals(body, original.material.content)
        assertTrue(plan.section.contains("fragment=\"true\""))
    }

    @Test
    fun omittedAndDuplicatedPresetSlots_areReconciledAgainstActualOutput() {
        val plan = MemoryRecallBudget().allocate(
            context,
            listOf(candidate("one", "明确约定。", MemoryRecallLayer.EXPERIENCE)),
            1,
            1000
        )
        val dropped =
            MemoryRecallAssemblySupport.reconcile("只保留角色设定", PromptEnvelope(), plan)
        assertTrue(dropped.plan.materials.isEmpty())
        assertTrue(dropped.plan.omitted.any { it.reason == MemoryRecallOmissionReason.NOT_RENDERED })
        val duplicated = MemoryRecallAssemblySupport.reconcile(
            plan.section,
            PromptEnvelope(preHistoryMessages = listOf(PromptEnvelopeMessage(content = plan.section))),
            plan
        )
        assertEquals(1, duplicated.plan.materials.size)
        assertFalse(duplicated.envelope.preHistoryMessages.single().content.contains("<narra_memory "))
        assertFalse(
            duplicated.envelope.preHistoryMessages.single().content.contains(
                MemoryRecallRendering.HEADER
            )
        )
        assertTrue(duplicated.plan.omitted.any { it.reason == MemoryRecallOmissionReason.DUPLICATE_RENDERING })
    }

    @Test
    fun impossibleBudget_hasOmissionAndNoTruncatedResidentClaim() {
        val candidate = candidate("resident", "稳定认知".repeat(200), MemoryRecallLayer.RESIDENT)
        val plan = MemoryRecallBudget().allocate(context, listOf(candidate), 0, 256)
        assertTrue(plan.materials.isEmpty())
        assertEquals("", plan.section)
        assertEquals(MemoryRecallOmissionReason.TOKEN_BUDGET, plan.omitted.single().reason)
    }

    @Test
    fun modifiedCurrentPresetBlock_cannotRemainAfterItsMaterialIsDropped() {
        val plan = MemoryRecallBudget().allocate(
            context,
            listOf(candidate("one", "芮珩约定731号室。", MemoryRecallLayer.EXPERIENCE)),
            1,
            1000
        )
        val changed = plan.section.replace("731", "926")
        assertTrue(runCatching {
            MemoryRecallAssemblySupport.reconcile(
                changed,
                PromptEnvelope(),
                plan
            )
        }.isFailure)
        assertTrue(runCatching {
            MemoryRecallAssemblySupport.reconcile(
                "角色设定", PromptEnvelope(
                    historyInjections = listOf(PromptHistoryInjection(content = changed)),
                ), plan
            )
        }.isFailure)
        val foreign = changed.replace(plan.materials.single().renderKey, "previous-round")
        val result = MemoryRecallAssemblySupport.reconcile(foreign, PromptEnvelope(), plan)
        assertEquals(foreign, result.systemPrompt)
        assertTrue(result.plan.materials.isEmpty())
    }

    @Test
    fun tokenizer_keepsShortNamesEnglishNumbersAndDatesWithoutCrossPunctuationBigrams() {
        val terms = MemoryRecallText.terms("芮珩去了海棠。船坞 Alpha7 731")
        assertTrue("芮珩" in terms)
        assertTrue("alpha7" in terms)
        assertTrue("731" in terms)
        assertFalse("棠船" in terms)
        assertTrue("2026-11-19" in MemoryRecallText.datesAndNumbers("2026年11月19日"))
        assertTrue(MemoryRecallText.estimateTokens("中文中文") > MemoryRecallText.estimateTokens("abcd"))
    }

    @Test
    fun narrowBudget_neverCutsAwayRequiredHitWhileClaimingAFragment() {
        val body = "前言".repeat(1000) + "旁白".repeat(12) + "芮珩731" + "后文".repeat(500)
        val termStart = body.indexOf("芮珩731")
        val original = candidate("tight", body, MemoryRecallLayer.EXPERIENCE).copy(
            hitStart = termStart - 24,
            hitEnd = termStart + 200,
            requiredStart = termStart,
            requiredEnd = termStart + "芮珩731".length
        )
        val plan = MemoryRecallBudget().allocate(context, listOf(original), 1, 256)
        if (plan.materials.isEmpty()) assertTrue(plan.omitted.any { it.reason == MemoryRecallOmissionReason.TOKEN_BUDGET })
        else {
            assertTrue(plan.materials.single().content.contains("芮珩731"))
            assertTrue(plan.materials.single().ref.spanEnd >= termStart + "芮珩731".length)
        }
    }

    @Test
    fun experienceLimit_appliesAfterPriorityOrder() {
        val low = candidate("related", "补充前情", MemoryRecallLayer.EXPERIENCE)
        val high =
            candidate("current", "当前明确话题", MemoryRecallLayer.EXPERIENCE).copy(priority = 10.0)
        val plan = MemoryRecallBudget().allocate(context, listOf(low, high), 1, 1000)
        assertEquals(listOf("current"), plan.materials.map { it.ref.id })
        assertTrue(plan.omitted.any { it.ref.id == "related" && it.reason == MemoryRecallOmissionReason.ITEM_LIMIT })
    }

    @Test
    fun shortTailHit_expandsRealContextWithoutSplittingSurrogates() {
        val tail = "尾部芮珩731"
        val body = "😀背景".repeat(500) + tail
        val start = body.length - tail.length
        val original = candidate("short-tail", body, MemoryRecallLayer.EXPERIENCE).copy(
            hitStart = start, hitEnd = body.length, requiredStart = start, requiredEnd = body.length
        )

        val plan = MemoryRecallBudget().allocate(context, listOf(original), 1, 500)

        val sent = plan.materials.single()
        assertTrue(sent.fragment)
        assertTrue(sent.ref.spanStart < start)
        assertEquals(body.length, sent.ref.spanEnd)
        assertTrue(sent.content.endsWith(tail))
        assertTrue(sent.content.length >= 24)
        assertEquals(body.substring(sent.ref.spanStart, sent.ref.spanEnd), sent.content)
        assertEquals(MemoryRecallText.safeBoundary(body, sent.ref.spanStart), sent.ref.spanStart)
        assertEquals(MemoryRecallText.safeBoundary(body, sent.ref.spanEnd), sent.ref.spanEnd)
        assertEquals(MemoryArchiveIdentity.fingerprint(sent.content), sent.ref.contentFingerprint)
        assertEquals(body, original.material.content)
        assertTrue(plan.estimatedTokens <= 500)
    }

    @Test
    fun completeShortHit_thatFitsTheActualBudget_isNotDroppedByFixedMinimums() {
        val tail = "编号733173已交付"
        val body = "不相关背景".repeat(1000) + tail
        val start = body.length - tail.length
        val original = candidate("short", body, MemoryRecallLayer.EXPERIENCE).copy(
            hitStart = start, hitEnd = body.length, requiredStart = start, requiredEnd = body.length
        )
        val exact = original.material.copy(
            content = tail,
            fragment = true,
            ref = original.material.ref.copy(
                spanStart = start, spanEnd = body.length,
                contentFingerprint = MemoryArchiveIdentity.fingerprint(tail)
            )
        )
        val budget = MemoryRecallText.estimateTokens(MemoryRecallRendering.HEADER + "\n") +
                MemoryRecallText.estimateTokens(MemoryRecallRendering.block(exact) + "\n")

        val plan = MemoryRecallBudget().allocate(context, listOf(original), 1, budget)

        assertEquals(listOf(exact), plan.materials)
        assertTrue(plan.estimatedTokens <= budget)
        assertTrue(plan.omitted.any { it.reason == MemoryRecallOmissionReason.PARTIAL_RECORD })
        assertEquals(body, original.material.content)
    }

    private fun candidate(
        id: String,
        content: String,
        layer: MemoryRecallLayer
    ): MemoryRecallCandidate {
        val kind = when (layer) {
            MemoryRecallLayer.RESIDENT -> MemoryMaterialKind.RESIDENT
            MemoryRecallLayer.STATE -> MemoryMaterialKind.STATE
            else -> MemoryMaterialKind.RECORD
        }
        return MemoryRecallCandidate(
            MemoryRecallMaterial(
                MemoryMaterialRef(
                    kind, id, 1, 0, content.length,
                    MemoryArchiveIdentity.fingerprint(content)
                ), layer, content, layer.name
            ), 1.0
        )
    }
}
