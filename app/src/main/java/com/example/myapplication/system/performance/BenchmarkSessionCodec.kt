package com.example.myapplication.system.performance

object BenchmarkSessionCodec {
    private const val KEY_SCHEMA_VERSION = "schema_version"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_SCENARIO_ID = "scenario_id"
    private const val KEY_FIXTURE_VERSION = "fixture_version"
    private const val KEY_PHASE = "phase"
    private const val KEY_CREATED_AT = "created_at_epoch_millis"
    private const val KEY_TTL = "ttl_millis"

    fun encode(descriptor: BenchmarkSessionDescriptor): String {
        return buildString {
            appendLine("$KEY_SCHEMA_VERSION=${descriptor.schemaVersion}")
            appendLine("$KEY_SESSION_ID=${descriptor.sessionId}")
            appendLine("$KEY_SCENARIO_ID=${descriptor.scenarioId.wireId}")
            appendLine("$KEY_FIXTURE_VERSION=${descriptor.fixtureVersion}")
            appendLine("$KEY_PHASE=${descriptor.phase.wireId}")
            appendLine("$KEY_CREATED_AT=${descriptor.createdAtEpochMillis}")
            appendLine("$KEY_TTL=${descriptor.ttlMillis}")
        }
    }

    fun decodeOrNull(encoded: String): BenchmarkSessionDescriptor? {
        val values = linkedMapOf<String, String>()
        encoded.lineSequence()
            .filter(String::isNotBlank)
            .forEach { line ->
                val parts = line.split('=', limit = 2)
                if (parts.size != 2 || parts[0] in values) {
                    return null
                }
                values[parts[0]] = parts[1]
            }
        if (values.keys != REQUIRED_KEYS) {
            return null
        }
        return BenchmarkSessionDescriptor(
            schemaVersion = values[KEY_SCHEMA_VERSION]?.toIntOrNull() ?: return null,
            sessionId = values[KEY_SESSION_ID].orEmpty(),
            scenarioId = BenchmarkScenarioId.fromWireId(values[KEY_SCENARIO_ID].orEmpty()) ?: return null,
            fixtureVersion = values[KEY_FIXTURE_VERSION].orEmpty(),
            phase = BenchmarkPhase.fromWireId(values[KEY_PHASE].orEmpty()) ?: return null,
            createdAtEpochMillis = values[KEY_CREATED_AT]?.toLongOrNull() ?: return null,
            ttlMillis = values[KEY_TTL]?.toLongOrNull() ?: return null,
        )
    }

    private val REQUIRED_KEYS = setOf(
        KEY_SCHEMA_VERSION,
        KEY_SESSION_ID,
        KEY_SCENARIO_ID,
        KEY_FIXTURE_VERSION,
        KEY_PHASE,
        KEY_CREATED_AT,
        KEY_TTL,
    )
}
