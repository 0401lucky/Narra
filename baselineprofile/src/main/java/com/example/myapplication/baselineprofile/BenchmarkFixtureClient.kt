package com.example.myapplication.baselineprofile

import android.content.Context
import android.net.Uri
import android.os.Bundle
import java.util.UUID

internal data class BenchmarkSession(
    val sessionId: String,
    val scenario: BenchmarkScenario,
    val encodedDescriptor: String,
)

internal class BenchmarkFixtureClient(
    private val context: Context,
    private val targetPackage: String,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val providerUri = Uri.parse("content://$targetPackage$AUTHORITY_SUFFIX")

    fun prepareFixtureOrThrow(scenario: BenchmarkScenario): BenchmarkSession {
        val sessionId = UUID.randomUUID().toString()
        val encoded = encodeDescriptor(
            sessionId = sessionId,
            scenario = scenario,
            createdAtEpochMillis = nowEpochMillis(),
        )
        val response = callProviderOrThrow(
            method = METHOD_PREPARE,
            extras = Bundle().apply { putString(KEY_SESSION_DESCRIPTOR, encoded) },
        )
        if (!response.getBoolean(KEY_SUCCESS, false)) {
            val errorCode = response.getString(KEY_ERROR_CODE).orEmpty().ifBlank { "unknown" }
            val safeMessage = response.getString(KEY_ERROR_MESSAGE).orEmpty().ifBlank { "无安全错误说明" }
            throw AssertionError("fixture 准备失败 [$errorCode]：$safeMessage")
        }
        assertResponseEquals(KEY_SESSION_DESCRIPTOR, encoded, response)
        assertResponseEquals(KEY_FIXTURE_ID, scenario.fixtureId, response)
        val chatCount = response.getInt(KEY_CHAT_MESSAGE_COUNT, -1)
        val roleplayCount = response.getInt(KEY_ROLEPLAY_MESSAGE_COUNT, -1)
        if (chatCount != scenario.expectedChatMessageCount) {
            throw AssertionError("fixture 普通聊天数量不符：expected=${scenario.expectedChatMessageCount}, actual=$chatCount")
        }
        if (roleplayCount != scenario.expectedRoleplayMessageCount) {
            throw AssertionError(
                "fixture RP 消息数量不符：expected=${scenario.expectedRoleplayMessageCount}, actual=$roleplayCount",
            )
        }
        val safeSha256 = response.getString(KEY_SAFE_SHA256).orEmpty()
        if (!safeSha256.matches(Regex("[a-f0-9]{64}"))) {
            throw AssertionError("fixture 安全摘要缺失或格式无效")
        }
        return BenchmarkSession(
            sessionId = sessionId,
            scenario = scenario,
            encodedDescriptor = encoded,
        )
    }

    fun clearSessionOrThrow() {
        val response = callProviderOrThrow(METHOD_CLEAR_SESSION, null)
        if (!response.getBoolean(KEY_SUCCESS, false)) {
            throw AssertionError("benchmark session 清理失败")
        }
    }

    private fun callProviderOrThrow(
        method: String,
        extras: Bundle?,
    ): Bundle {
        return try {
            context.contentResolver.call(providerUri, method, null, extras)
                ?: throw AssertionError("benchmark 控制 Provider 返回空结果")
        } catch (throwable: Throwable) {
            if (throwable is AssertionError) {
                throw throwable
            }
            throw AssertionError("无法调用 benchmark 控制 Provider").also { error ->
                error.initCause(throwable)
            }
        }
    }

    private fun assertResponseEquals(
        key: String,
        expected: String,
        response: Bundle,
    ) {
        val actual = response.getString(key)
        if (actual != expected) {
            throw AssertionError("fixture 响应字段不符：key=$key")
        }
    }

    companion object {
        internal fun ttlMillisForScenario(scenario: BenchmarkScenario): Long {
            return if (scenario.phase == BenchmarkPhase.PROFILE) {
                MAX_TTL_MILLIS
            } else {
                DEFAULT_TTL_MILLIS
            }
        }

        internal fun encodeDescriptor(
            sessionId: String,
            scenario: BenchmarkScenario,
            createdAtEpochMillis: Long,
        ): String {
            return buildString {
                appendLine("$DESCRIPTOR_KEY_SCHEMA_VERSION=$SCHEMA_VERSION")
                appendLine("$DESCRIPTOR_KEY_SESSION_ID=$sessionId")
                appendLine("$DESCRIPTOR_KEY_SCENARIO_ID=${scenario.wireId}")
                appendLine("$DESCRIPTOR_KEY_FIXTURE_VERSION=${BenchmarkWireContract.FIXTURE_VERSION}")
                appendLine("$DESCRIPTOR_KEY_PHASE=${scenario.phase.wireId}")
                appendLine("$DESCRIPTOR_KEY_CREATED_AT=$createdAtEpochMillis")
                appendLine("$DESCRIPTOR_KEY_TTL=${ttlMillisForScenario(scenario)}")
            }
        }

        private const val AUTHORITY_SUFFIX = ".benchmark.fixture"
        private const val SCHEMA_VERSION = 1
        private const val DEFAULT_TTL_MILLIS = 5 * 60 * 1_000L
        private const val MAX_TTL_MILLIS = 30 * 60 * 1_000L

        private const val DESCRIPTOR_KEY_SCHEMA_VERSION = "schema_version"
        private const val DESCRIPTOR_KEY_SESSION_ID = "session_id"
        private const val DESCRIPTOR_KEY_SCENARIO_ID = "scenario_id"
        private const val DESCRIPTOR_KEY_FIXTURE_VERSION = "fixture_version"
        private const val DESCRIPTOR_KEY_PHASE = "phase"
        private const val DESCRIPTOR_KEY_CREATED_AT = "created_at_epoch_millis"
        private const val DESCRIPTOR_KEY_TTL = "ttl_millis"

        private const val METHOD_PREPARE = "prepare"
        private const val METHOD_CLEAR_SESSION = "clear_session"
        private const val KEY_SESSION_DESCRIPTOR = "session_descriptor"
        private const val KEY_SUCCESS = "success"
        private const val KEY_ERROR_CODE = "error_code"
        private const val KEY_ERROR_MESSAGE = "error_message"
        private const val KEY_FIXTURE_ID = "fixture_id"
        private const val KEY_CHAT_MESSAGE_COUNT = "chat_message_count"
        private const val KEY_ROLEPLAY_MESSAGE_COUNT = "roleplay_message_count"
        private const val KEY_SAFE_SHA256 = "safe_sha256"
    }
}
