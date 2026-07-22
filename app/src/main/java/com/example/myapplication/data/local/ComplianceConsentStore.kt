package com.example.myapplication.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.compliance.CompliancePolicy
import com.example.myapplication.model.ComplianceConsent
import com.example.myapplication.system.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.complianceDataStore by preferencesDataStore(name = "compliance_consent")

interface ComplianceConsentRepository {
    val consentFlow: Flow<ComplianceConsent>

    suspend fun accept(policyVersion: String, acceptedAtEpochMillis: Long)

    suspend fun isAccepted(policyVersion: String): Boolean
}

class ComplianceConsentStore(
    private val dataStore: DataStore<Preferences>,
) : ComplianceConsentRepository {
    constructor(context: Context) : this(context.complianceDataStore)

    override val consentFlow: Flow<ComplianceConsent> = dataStore.data
        .catch { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            if (throwable is IOException) {
                AppLogger.w(TAG, "读取合规确认状态失败，将按未确认处理", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            ComplianceConsent(
                acceptedPolicyVersion = preferences[PreferencesKeys.acceptedPolicyVersion].orEmpty(),
                acceptedAtEpochMillis = preferences[PreferencesKeys.acceptedAtEpochMillis] ?: 0L,
            )
        }

    override suspend fun accept(policyVersion: String, acceptedAtEpochMillis: Long) {
        require(policyVersion.isNotBlank()) { "条款版本不能为空" }
        require(acceptedAtEpochMillis > 0L) { "确认时间必须为正数" }
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.acceptedPolicyVersion] = policyVersion
            preferences[PreferencesKeys.acceptedAtEpochMillis] = acceptedAtEpochMillis
        }
    }

    override suspend fun isAccepted(policyVersion: String): Boolean {
        if (policyVersion.isBlank()) {
            return false
        }
        return consentFlow.first().isAcceptedFor(policyVersion)
    }

    private object PreferencesKeys {
        val acceptedPolicyVersion = stringPreferencesKey("accepted_policy_version")
        val acceptedAtEpochMillis = longPreferencesKey("accepted_at_epoch_millis")
    }

    private companion object {
        const val TAG = "ComplianceConsent"
    }
}
