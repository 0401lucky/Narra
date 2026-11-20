package com.example.myapplication.model

import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSourceProofCompatibilityTest {
    @Test
    fun json_onlyOneCompanionFieldPresent_doesNotRegainLegacyTrust() {
        for (turnId in listOf("", "turn-a")) {
            val input = json(baseMessage())
            input.remove("companionMemoryState")
            input.addProperty("companionTurnId", turnId)

            assertPartialScopeRejected(input)
        }
        for (state in listOf("LEGACY", "legacy", "READY", "ready")) {
            val input = json(baseMessage())
            input.remove("companionTurnId")
            input.addProperty("companionMemoryState", state)

            assertPartialScopeRejected(input)
        }
    }

    @Test
    fun json_forgedProofWithMatchingFingerprint_cannotGrantImportedEligibility() {
        val candidates = listOf(
            baseMessage() to CompanionSourceEligibility.TEXT_ALLOWED,
            baseMessage().copy(
                role = MessageRole.USER,
                content = COMPANION_CONTINUE_DISPLAY_TEXT,
                companionMemoryState = CompanionMemoryState.CONTROL,
            ) to CompanionSourceEligibility.CONTROL_NOOP,
        )
        for ((message, eligibility) in candidates) {
            // internal mint只模拟模型层的已验证输出，不能替代真实Room关联图验证。
            val verified = message.withCompanionSourceProof(eligibility)
            assertEquals(eligibility, verified.companionSourceEligibility())
            val proof = requireNotNull(verified.companionSourceProof)
            val input = json(verified)
            input.add("companionSourceProof", AppJson.gson.toJsonTree(proof))
            assertTrue(input.has("companionSourceProof"))
            assertEquals(
                proof.messageFingerprint,
                input.getAsJsonObject("companionSourceProof").get("messageFingerprint").asString,
            )

            val imported = AppJson.gson.fromJson(input, ChatMessage::class.java)

            assertEquals(message.content, imported.content)
            assertEquals(message.companionTurnId, imported.companionTurnId)
            assertEquals(message.companionMemoryState, imported.companionMemoryState)
            assertNull(imported.companionSourceProof)
            assertEquals(
                CompanionSourceEligibility.UNAVAILABLE,
                imported.companionSourceEligibility()
            )
        }
    }

    @Test
    fun proof_dataClassToStringDelimiterCollision_doesNotAuthorizeDifferentFields() {
        val first = baseMessage().copy(
            memoryRecallRequestId = "request-a, companionTurnId=turn-b",
            companionTurnId = "turn-c",
        )
        val second = baseMessage().copy(
            memoryRecallRequestId = "request-a",
            companionTurnId = "turn-b, companionTurnId=turn-c",
        )
        assertFalse(first.memoryRecallRequestId == second.memoryRecallRequestId)
        assertFalse(first.companionTurnId == second.companionTurnId)
        assertEquals(first.toString(), second.toString())

        val verified = first.withCompanionSourceProof(CompanionSourceEligibility.TEXT_ALLOWED)
        assertEquals(CompanionSourceEligibility.TEXT_ALLOWED, verified.companionSourceEligibility())
        val transplanted = second.copy(companionSourceProof = verified.companionSourceProof)

        // 两对象连同移用proof的toString仍相同，资格判断不能依赖这种有歧义的编码。
        assertEquals(verified.toString(), transplanted.toString())
        assertEquals(
            CompanionSourceEligibility.UNAVAILABLE,
            transplanted.companionSourceEligibility()
        )
        assertEquals(
            CompanionSourceEligibility.TEXT_ALLOWED,
            second.withCompanionSourceProof(CompanionSourceEligibility.TEXT_ALLOWED)
                .companionSourceEligibility(),
        )
    }

    private fun assertPartialScopeRejected(input: JsonObject) {
        val imported = AppJson.gson.fromJson(input, ChatMessage::class.java)

        assertEquals("保留的原文", imported.content)
        assertEquals(CompanionMemoryState.UNKNOWN, imported.companionMemoryState)
        assertNull(imported.companionSourceProof)
        assertEquals(CompanionSourceEligibility.UNAVAILABLE, imported.companionSourceEligibility())
    }

    private fun json(message: ChatMessage): JsonObject =
        AppJson.gson.toJsonTree(message).asJsonObject

    private fun baseMessage() = ChatMessage(
        id = "source-message",
        conversationId = "source-conversation",
        role = MessageRole.ASSISTANT,
        content = "保留的原文",
        status = MessageStatus.COMPLETED,
        createdAt = 42L,
        memoryRevision = 3L,
        companionTurnId = "turn-a",
        companionMemoryState = CompanionMemoryState.READY,
    )
}
