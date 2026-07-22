package com.example.myapplication.model

data class ComplianceConsent(
    val acceptedPolicyVersion: String = "",
    val acceptedAtEpochMillis: Long = 0L,
) {
    fun isAcceptedFor(policyVersion: String): Boolean {
        return policyVersion.isNotBlank() && acceptedPolicyVersion == policyVersion
    }
}
