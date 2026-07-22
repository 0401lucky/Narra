package com.example.myapplication.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ComplianceConsentStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun accept_persistsVersionAndTimestamp() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "compliance.preferences_pb") },
        )
        val store = ComplianceConsentStore(dataStore)

        assertFalse(store.isAccepted("2026-07-15-v1"))

        store.accept(
            policyVersion = "2026-07-15-v1",
            acceptedAtEpochMillis = 2_000L,
        )

        val consent = store.consentFlow.first()
        assertEquals("2026-07-15-v1", consent.acceptedPolicyVersion)
        assertEquals(2_000L, consent.acceptedAtEpochMillis)
        assertTrue(store.isAccepted("2026-07-15-v1"))
        assertFalse(store.isAccepted("2026-07-15-v2"))
    }
}
