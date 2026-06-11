package com.example.myapplication.roleplay.script

enum class RoleplayScriptScope(
    val storageValue: String,
    val executionOrder: Int,
) {
    GLOBAL("global", 0),
    CHARACTER("character", 1),
    SCENARIO("scenario", 2),
    SESSION("session", 3);

    companion object {
        fun fromStorageValue(value: String): RoleplayScriptScope {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.storageValue == normalized } ?: SESSION
        }
    }
}

enum class RoleplayScriptEvent(val functionName: String) {
    ON_SESSION_START("onSessionStart"),
    BEFORE_PROMPT("beforePrompt"),
    BEFORE_SEND("beforeSend"),
    AFTER_ASSISTANT("afterAssistant"),
    RENDER_STATE("renderState"),
}

enum class RoleplayScriptPermission(
    val storageValue: String,
    val dangerous: Boolean = false,
) {
    READ_VARIABLES("read_variables"),
    WRITE_VARIABLES("write_variables"),
    MODIFY_PROMPT("modify_prompt"),
    MODIFY_OUTGOING_MESSAGE("modify_outgoing_message"),
    RENDER_STATE("render_state"),
    WRITE_LOG("write_log"),
    READ_FILE("read_file", dangerous = true),
    WRITE_FILE("write_file", dangerous = true),
    SAVE_IMAGE("save_image", dangerous = true),
    EXTERNAL_IMPORT("external_import", dangerous = true);

    companion object {
        fun fromStorageValue(value: String): RoleplayScriptPermission? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.storageValue == normalized }
        }
    }
}

data class RoleplayScriptDefinition(
    val id: String,
    val name: String,
    val scope: RoleplayScriptScope,
    val ownerId: String = "",
    val source: String,
    val enabled: Boolean = true,
    val grantedPermissions: Set<RoleplayScriptPermission> = emptySet(),
    val updatedAt: Long = 0L,
)

data class RoleplayScriptInput(
    val sessionId: String = "",
    val characterId: String = "",
    val scenarioId: String = "",
    val userText: String = "",
    val promptText: String = "",
    val assistantText: String = "",
    val variables: Map<String, String> = emptyMap(),
)

data class RoleplayScriptExecutionRequest(
    val event: RoleplayScriptEvent,
    val input: RoleplayScriptInput,
    val scripts: List<RoleplayScriptDefinition>,
)

data class RoleplayScriptUiDirective(
    val type: String,
    val payload: String = "",
)

data class RoleplayScriptLogEntry(
    val scriptId: String,
    val message: String,
    val level: String = "info",
)

data class RoleplayScriptHostOutput(
    val variables: Map<String, String> = emptyMap(),
    val promptAdditions: List<String> = emptyList(),
    val outgoingMessage: String? = null,
    val uiDirectives: List<RoleplayScriptUiDirective> = emptyList(),
    val logs: List<String> = emptyList(),
)

data class RoleplayScriptExecutionResult(
    val variables: Map<String, String> = emptyMap(),
    val variableUpdatesByScriptId: Map<String, Map<String, String>> = emptyMap(),
    val promptAdditions: List<String> = emptyList(),
    val outgoingMessage: String? = null,
    val uiDirectives: List<RoleplayScriptUiDirective> = emptyList(),
    val logs: List<RoleplayScriptLogEntry> = emptyList(),
    val disabledScriptIds: Set<String> = emptySet(),
    val available: Boolean = true,
    val unavailableReason: String = "",
)

internal data class RoleplayScriptRuntimeState(
    val variables: Map<String, String>,
    val promptAdditions: List<String> = emptyList(),
    val outgoingMessage: String? = null,
    val uiDirectives: List<RoleplayScriptUiDirective> = emptyList(),
) {
    fun apply(output: RoleplayScriptHostOutput): RoleplayScriptRuntimeState {
        return copy(
            variables = RoleplayScriptOutputQuota.sanitizeVariables(variables + output.variables),
            promptAdditions = RoleplayScriptOutputQuota.mergePromptAdditions(
                current = promptAdditions,
                incoming = output.promptAdditions,
            ),
            outgoingMessage = output.outgoingMessage?.takeIf { it.isNotBlank() } ?: outgoingMessage,
            uiDirectives = RoleplayScriptOutputQuota.mergeUiDirectives(
                current = uiDirectives,
                incoming = output.uiDirectives,
            ),
        )
    }
}
