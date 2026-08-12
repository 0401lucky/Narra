package com.example.myapplication.system.performance

import android.os.Bundle

internal object BenchmarkFixtureProtocol {
    const val AUTHORITY_SUFFIX = ".benchmark.fixture"
    const val METHOD_PREPARE = "prepare"
    const val METHOD_CLEAR_SESSION = "clear_session"
    const val METHOD_READ_SESSION = "read_session"

    const val KEY_SESSION_DESCRIPTOR = "session_descriptor"
    const val KEY_SUCCESS = "success"
    const val KEY_ERROR_CODE = "error_code"
    const val KEY_ERROR_MESSAGE = "error_message"
    const val KEY_FIXTURE_ID = "fixture_id"
    const val KEY_CHAT_MESSAGE_COUNT = "chat_message_count"
    const val KEY_ROLEPLAY_MESSAGE_COUNT = "roleplay_message_count"
    const val KEY_SAFE_SHA256 = "safe_sha256"

    fun success(
        descriptor: BenchmarkSessionDescriptor,
        summary: FixtureSafeSummary,
    ): Bundle {
        return Bundle().apply {
            putBoolean(KEY_SUCCESS, true)
            putString(KEY_SESSION_DESCRIPTOR, BenchmarkSessionCodec.encode(descriptor))
            putString(KEY_FIXTURE_ID, summary.fixtureId.wireId)
            putInt(KEY_CHAT_MESSAGE_COUNT, summary.chatMessageCount)
            putInt(KEY_ROLEPLAY_MESSAGE_COUNT, summary.roleplayMessageCount)
            putString(KEY_SAFE_SHA256, summary.safeSha256)
        }
    }

    fun successWithSession(descriptor: BenchmarkSessionDescriptor?): Bundle {
        return Bundle().apply {
            putBoolean(KEY_SUCCESS, true)
            descriptor?.let { putString(KEY_SESSION_DESCRIPTOR, BenchmarkSessionCodec.encode(it)) }
        }
    }

    fun failure(
        code: BenchmarkFixtureErrorCode,
        message: String,
    ): Bundle {
        return Bundle().apply {
            putBoolean(KEY_SUCCESS, false)
            putString(KEY_ERROR_CODE, code.wireId)
            putString(KEY_ERROR_MESSAGE, message)
        }
    }
}

internal enum class BenchmarkFixtureErrorCode(val wireId: String) {
    WRONG_CHANNEL("wrong_channel"),
    MISSING_DESCRIPTOR("missing_descriptor"),
    INVALID_DESCRIPTOR("invalid_descriptor"),
    INVALID_PHASE("invalid_phase"),
    PREPARE_FAILED("prepare_failed"),
    SESSION_WRITE_FAILED("session_write_failed"),
    UNKNOWN_METHOD("unknown_method"),
}
