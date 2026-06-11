package com.example.myapplication.roleplay.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoleplayScriptPermissionGateTest {
    @Test
    fun visibleInputForScript_hidesVariablesWithoutReadPermission() {
        val script = script(grantedPermissions = emptySet())
        val input = RoleplayScriptInput(variables = mapOf("mood" to "quiet"))

        val visible = RoleplayScriptPermissionGate.visibleInputForScript(
            script = script,
            input = input,
            currentVariables = input.variables,
        )

        assertEquals(emptyMap<String, String>(), visible.variables)
    }

    @Test
    fun visibleInputForScript_exposesVariablesWithReadPermission() {
        val script = script(grantedPermissions = setOf(RoleplayScriptPermission.READ_VARIABLES))
        val input = RoleplayScriptInput(variables = mapOf("mood" to "quiet"))

        val visible = RoleplayScriptPermissionGate.visibleInputForScript(
            script = script,
            input = input,
            currentVariables = input.variables,
        )

        assertEquals(mapOf("mood" to "quiet"), visible.variables)
    }

    @Test
    fun filterOutput_removesEffectsWithoutMatchingPermissions() {
        val output = RoleplayScriptHostOutput(
            variables = mapOf("mood" to "warm"),
            promptAdditions = listOf("追加提示"),
            outgoingMessage = "改写文本",
            uiDirectives = listOf(RoleplayScriptUiDirective("toast", "hi")),
            logs = listOf("日志"),
        )

        val filtered = RoleplayScriptPermissionGate.filterOutput(
            script = script(grantedPermissions = setOf(RoleplayScriptPermission.WRITE_VARIABLES)),
            output = output,
        )

        assertEquals(mapOf("mood" to "warm"), filtered.variables)
        assertEquals(emptyList<String>(), filtered.promptAdditions)
        assertNull(filtered.outgoingMessage)
        assertEquals(emptyList<RoleplayScriptUiDirective>(), filtered.uiDirectives)
        assertEquals(emptyList<String>(), filtered.logs)
    }

    @Test
    fun filterOutput_keepsOnlyGrantedEffects() {
        val output = RoleplayScriptHostOutput(
            variables = mapOf("mood" to "warm"),
            promptAdditions = listOf("追加提示"),
            outgoingMessage = "改写文本",
            uiDirectives = listOf(RoleplayScriptUiDirective("toast", "hi")),
            logs = listOf("日志"),
        )

        val filtered = RoleplayScriptPermissionGate.filterOutput(
            script = script(
                grantedPermissions = setOf(
                    RoleplayScriptPermission.WRITE_VARIABLES,
                    RoleplayScriptPermission.MODIFY_PROMPT,
                    RoleplayScriptPermission.MODIFY_OUTGOING_MESSAGE,
                    RoleplayScriptPermission.RENDER_STATE,
                    RoleplayScriptPermission.WRITE_LOG,
                ),
            ),
            output = output,
        )

        assertEquals(output, filtered)
    }

    @Test
    fun filterOutput_limitsGrantedEffectsByQuota() {
        val output = RoleplayScriptHostOutput(
            variables = (0 until 48).associate { index -> "key-$index" to "value-$index" },
            promptAdditions = (0 until 8).map { index -> "prompt-$index" },
            outgoingMessage = "m".repeat(RoleplayScriptOutputQuota.MAX_OUTGOING_MESSAGE_CHARS + 50),
            uiDirectives = (0 until 8).map { index -> RoleplayScriptUiDirective("toast-$index", "payload-$index") },
            logs = (0 until 16).map { index -> "log-$index" },
        )

        val filtered = RoleplayScriptPermissionGate.filterOutput(
            script = script(
                grantedPermissions = setOf(
                    RoleplayScriptPermission.WRITE_VARIABLES,
                    RoleplayScriptPermission.MODIFY_PROMPT,
                    RoleplayScriptPermission.MODIFY_OUTGOING_MESSAGE,
                    RoleplayScriptPermission.RENDER_STATE,
                    RoleplayScriptPermission.WRITE_LOG,
                ),
            ),
            output = output,
        )

        assertEquals(RoleplayScriptOutputQuota.MAX_VARIABLE_UPDATES_PER_SCRIPT, filtered.variables.size)
        assertEquals(RoleplayScriptOutputQuota.MAX_PROMPT_ADDITIONS_PER_SCRIPT, filtered.promptAdditions.size)
        assertEquals(RoleplayScriptOutputQuota.MAX_OUTGOING_MESSAGE_CHARS, filtered.outgoingMessage?.length)
        assertEquals(RoleplayScriptOutputQuota.MAX_UI_DIRECTIVES_PER_SCRIPT, filtered.uiDirectives.size)
        assertEquals(RoleplayScriptOutputQuota.MAX_LOGS_PER_SCRIPT, filtered.logs.size)
    }

    private fun script(
        grantedPermissions: Set<RoleplayScriptPermission>,
    ): RoleplayScriptDefinition {
        return RoleplayScriptDefinition(
            id = "script",
            name = "脚本",
            scope = RoleplayScriptScope.SESSION,
            source = "function beforePrompt() {}",
            grantedPermissions = grantedPermissions,
        )
    }
}
