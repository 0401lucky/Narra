package com.example.myapplication.roleplay.script

internal object RoleplayScriptOutputQuota {
    const val MAX_RAW_OUTPUT_CHARS: Int = 64 * 1024
    const val MAX_VARIABLE_UPDATES_PER_SCRIPT: Int = 32
    const val MAX_RUNTIME_VARIABLES: Int = 128
    const val MAX_VARIABLE_KEY_CHARS: Int = 64
    const val MAX_VARIABLE_VALUE_CHARS: Int = 512
    const val MAX_PROMPT_ADDITIONS_PER_SCRIPT: Int = 4
    const val MAX_PROMPT_ADDITIONS_PER_ROUND: Int = 8
    const val MAX_PROMPT_ADDITION_CHARS: Int = 600
    const val MAX_PROMPT_ADDITIONS_TOTAL_CHARS: Int = 3_000
    const val MAX_OUTGOING_MESSAGE_CHARS: Int = 4_000
    const val MAX_UI_DIRECTIVES_PER_SCRIPT: Int = 4
    const val MAX_UI_DIRECTIVES_PER_ROUND: Int = 8
    const val MAX_UI_TYPE_CHARS: Int = 32
    const val MAX_UI_PAYLOAD_CHARS: Int = 512
    const val MAX_LOGS_PER_SCRIPT: Int = 10
    const val MAX_LOGS_PER_ROUND: Int = 40
    const val MAX_LOG_CHARS: Int = 300

    fun sanitizeScriptOutput(output: RoleplayScriptHostOutput): RoleplayScriptHostOutput {
        return RoleplayScriptHostOutput(
            variables = sanitizeVariables(
                values = output.variables,
                maxItems = MAX_VARIABLE_UPDATES_PER_SCRIPT,
            ),
            promptAdditions = sanitizeTextList(
                values = output.promptAdditions,
                maxItems = MAX_PROMPT_ADDITIONS_PER_SCRIPT,
                maxChars = MAX_PROMPT_ADDITION_CHARS,
            ),
            outgoingMessage = output.outgoingMessage?.trimToLimit(MAX_OUTGOING_MESSAGE_CHARS),
            uiDirectives = sanitizeUiDirectives(
                values = output.uiDirectives,
                maxItems = MAX_UI_DIRECTIVES_PER_SCRIPT,
            ),
            logs = sanitizeTextList(
                values = output.logs,
                maxItems = MAX_LOGS_PER_SCRIPT,
                maxChars = MAX_LOG_CHARS,
            ),
        )
    }

    fun sanitizeVariables(
        values: Map<String, String>,
        maxItems: Int = MAX_RUNTIME_VARIABLES,
    ): Map<String, String> {
        val sanitized = linkedMapOf<String, String>()
        for ((rawKey, rawValue) in values) {
            if (sanitized.size >= maxItems) break
            val key = rawKey.trimToLimit(MAX_VARIABLE_KEY_CHARS) ?: continue
            val value = rawValue.trimToLimit(MAX_VARIABLE_VALUE_CHARS) ?: continue
            sanitized[key] = value
        }
        return sanitized
    }

    fun mergePromptAdditions(
        current: List<String>,
        incoming: List<String>,
    ): List<String> {
        val merged = mutableListOf<String>()
        var totalChars = 0
        for (value in current + incoming) {
            if (merged.size >= MAX_PROMPT_ADDITIONS_PER_ROUND) break
            val text = value.trimToLimit(MAX_PROMPT_ADDITION_CHARS) ?: continue
            if (totalChars + text.length > MAX_PROMPT_ADDITIONS_TOTAL_CHARS) {
                val remaining = MAX_PROMPT_ADDITIONS_TOTAL_CHARS - totalChars
                if (remaining > 0) {
                    merged += text.take(remaining)
                }
                break
            }
            merged += text
            totalChars += text.length
        }
        return merged
    }

    fun mergeUiDirectives(
        current: List<RoleplayScriptUiDirective>,
        incoming: List<RoleplayScriptUiDirective>,
    ): List<RoleplayScriptUiDirective> {
        return sanitizeUiDirectives(
            values = current + incoming,
            maxItems = MAX_UI_DIRECTIVES_PER_ROUND,
        )
    }

    fun canAcceptMoreLogs(currentSize: Int): Boolean {
        return currentSize < MAX_LOGS_PER_ROUND
    }

    fun remainingLogSlots(currentSize: Int): Int {
        return (MAX_LOGS_PER_ROUND - currentSize).coerceAtLeast(0)
    }

    private fun sanitizeTextList(
        values: List<String>,
        maxItems: Int,
        maxChars: Int,
    ): List<String> {
        val sanitized = mutableListOf<String>()
        for (value in values) {
            if (sanitized.size >= maxItems) break
            val text = value.trimToLimit(maxChars) ?: continue
            sanitized += text
        }
        return sanitized
    }

    private fun sanitizeUiDirectives(
        values: List<RoleplayScriptUiDirective>,
        maxItems: Int,
    ): List<RoleplayScriptUiDirective> {
        val sanitized = mutableListOf<RoleplayScriptUiDirective>()
        for (value in values) {
            if (sanitized.size >= maxItems) break
            val type = value.type.trimToLimit(MAX_UI_TYPE_CHARS) ?: continue
            val payload = value.payload.trimToLimit(MAX_UI_PAYLOAD_CHARS).orEmpty()
            sanitized += RoleplayScriptUiDirective(
                type = type,
                payload = payload,
            )
        }
        return sanitized
    }

    private fun String.trimToLimit(maxChars: Int): String? {
        return trim()
            .take(maxChars)
            .takeIf { it.isNotBlank() }
    }
}
