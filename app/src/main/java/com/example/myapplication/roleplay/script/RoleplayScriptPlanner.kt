package com.example.myapplication.roleplay.script

object RoleplayScriptPlanner {
    fun orderedExecutableScripts(scripts: List<RoleplayScriptDefinition>): List<RoleplayScriptDefinition> {
        return scripts
            .filter { script -> script.enabled && script.source.isNotBlank() && script.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<RoleplayScriptDefinition> { it.scope.executionOrder }
                    .thenBy { it.updatedAt }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id },
            )
    }
}

object RoleplayScriptPermissionGate {
    fun visibleInputForScript(
        script: RoleplayScriptDefinition,
        input: RoleplayScriptInput,
        currentVariables: Map<String, String>,
    ): RoleplayScriptInput {
        val visibleVariables = if (RoleplayScriptPermission.READ_VARIABLES in script.grantedPermissions) {
            RoleplayScriptOutputQuota.sanitizeVariables(currentVariables)
        } else {
            emptyMap()
        }
        return input.copy(variables = visibleVariables)
    }

    fun filterOutput(
        script: RoleplayScriptDefinition,
        output: RoleplayScriptHostOutput,
    ): RoleplayScriptHostOutput {
        val permissions = script.grantedPermissions
        return RoleplayScriptOutputQuota.sanitizeScriptOutput(
            RoleplayScriptHostOutput(
                variables = if (RoleplayScriptPermission.WRITE_VARIABLES in permissions) {
                    output.variables
                } else {
                    emptyMap()
                },
                promptAdditions = if (RoleplayScriptPermission.MODIFY_PROMPT in permissions) {
                    output.promptAdditions
                } else {
                    emptyList()
                },
                outgoingMessage = if (RoleplayScriptPermission.MODIFY_OUTGOING_MESSAGE in permissions) {
                    output.outgoingMessage
                } else {
                    null
                },
                uiDirectives = if (RoleplayScriptPermission.RENDER_STATE in permissions) {
                    output.uiDirectives
                } else {
                    emptyList()
                },
                logs = if (RoleplayScriptPermission.WRITE_LOG in permissions) {
                    output.logs
                } else {
                    emptyList()
                },
            ),
        )
    }
}
