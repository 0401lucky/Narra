package com.example.myapplication.viewmodel

import com.example.myapplication.data.local.ComplianceConsentRepository
import com.example.myapplication.model.ComplianceConsent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComplianceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialState_withoutConsent_requiresAcceptance() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = ComplianceViewModel(
            consentRepository = FakeComplianceConsentRepository(),
            currentPolicyVersion = "2026-07-15-v1",
        )

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isAccepted)
    }

    @Test
    fun acceptCurrentPolicy_success_marksAcceptedAndPersistsVersion() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = FakeComplianceConsentRepository()
        val viewModel = ComplianceViewModel(
            consentRepository = repository,
            currentPolicyVersion = "2026-07-15-v1",
            clock = { 2_000L },
        )

        advanceUntilIdle()
        viewModel.acceptCurrentPolicy()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAccepted)
        assertEquals("2026-07-15-v1", repository.currentConsent.acceptedPolicyVersion)
        assertEquals(2_000L, repository.currentConsent.acceptedAtEpochMillis)
    }

    @Test
    fun existingConsent_withOlderVersion_requiresReconfirmation() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = ComplianceViewModel(
            consentRepository = FakeComplianceConsentRepository(
                initialConsent = ComplianceConsent(
                    acceptedPolicyVersion = "2026-07-15-v1",
                    acceptedAtEpochMillis = 1_000L,
                ),
            ),
            currentPolicyVersion = "2026-07-15-v2",
        )

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAccepted)
        assertEquals("2026-07-15-v1", viewModel.uiState.value.acceptedPolicyVersion)
    }

    @Test
    fun acceptCurrentPolicy_failure_keepsGateAndShowsSafeMessage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = ComplianceViewModel(
            consentRepository = FakeComplianceConsentRepository(
                failure = IllegalStateException("internal path D:\\secret"),
            ),
            currentPolicyVersion = "2026-07-15-v1",
            clock = { 2_000L },
        )

        advanceUntilIdle()
        viewModel.acceptCurrentPolicy()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAccepted)
        assertEquals("确认状态保存失败，请重试", viewModel.uiState.value.errorMessage)
    }
}

private class FakeComplianceConsentRepository(
    initialConsent: ComplianceConsent = ComplianceConsent(),
    private val failure: Throwable? = null,
) : ComplianceConsentRepository {
    private val state = MutableStateFlow(initialConsent)

    override val consentFlow: Flow<ComplianceConsent> = state

    val currentConsent: ComplianceConsent
        get() = state.value

    override suspend fun accept(policyVersion: String, acceptedAtEpochMillis: Long) {
        failure?.let { throw it }
        state.value = ComplianceConsent(
            acceptedPolicyVersion = policyVersion,
            acceptedAtEpochMillis = acceptedAtEpochMillis,
        )
    }

    override suspend fun isAccepted(policyVersion: String): Boolean {
        return state.value.isAcceptedFor(policyVersion)
    }
}
