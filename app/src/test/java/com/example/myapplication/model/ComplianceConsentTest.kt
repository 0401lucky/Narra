package com.example.myapplication.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplianceConsentTest {
    @Test
    fun isAcceptedFor_emptyVersion_returnsFalse() {
        val consent = ComplianceConsent(
            acceptedPolicyVersion = "2026-07-15-v1",
            acceptedAtEpochMillis = 1_000L,
        )

        assertFalse(consent.isAcceptedFor(""))
    }

    @Test
    fun isAcceptedFor_sameVersion_returnsTrue() {
        val consent = ComplianceConsent(
            acceptedPolicyVersion = "2026-07-15-v1",
            acceptedAtEpochMillis = 1_000L,
        )

        assertTrue(consent.isAcceptedFor("2026-07-15-v1"))
    }

    @Test
    fun isAcceptedFor_differentVersion_returnsFalse() {
        val consent = ComplianceConsent(
            acceptedPolicyVersion = "2026-07-15-v1",
            acceptedAtEpochMillis = 1_000L,
        )

        assertFalse(consent.isAcceptedFor("2026-07-15-v2"))
    }
}
