package com.example.myapplication.roleplay.script

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayScriptJsonCodecTest {
    private val codec = RoleplayScriptJsonCodec()
    private val gson = Gson()

    @Test
    fun decodeOutput_ignoresOversizedRawJson() {
        val output = codec.decodeOutput("x".repeat(RoleplayScriptOutputQuota.MAX_RAW_OUTPUT_CHARS + 1))

        assertEquals(RoleplayScriptHostOutput(), output)
    }

    @Test
    fun decodeOutput_trimsOutputToPerScriptQuota() {
        val raw = gson.toJson(
            mapOf(
                "variables" to (0 until 40).associate { index ->
                    " key-$index-${"k".repeat(80)} " to " value-$index-${"v".repeat(600)} "
                },
                "promptAdditions" to (0 until 6).map { index -> " prompt-$index-${"p".repeat(700)} " },
                "outgoingMessage" to " ${"m".repeat(4_500)} ",
                "uiDirectives" to (0 until 6).map { index ->
                    mapOf(
                        "type" to " type-$index-${"t".repeat(48)} ",
                        "payload" to " payload-$index-${"u".repeat(700)} ",
                    )
                },
                "logs" to (0 until 12).map { index -> " log-$index-${"l".repeat(400)} " },
            ),
        )

        val output = codec.decodeOutput(raw)

        assertEquals(RoleplayScriptOutputQuota.MAX_VARIABLE_UPDATES_PER_SCRIPT, output.variables.size)
        assertTrue(output.variables.keys.all { it.length <= RoleplayScriptOutputQuota.MAX_VARIABLE_KEY_CHARS })
        assertTrue(output.variables.values.all { it.length <= RoleplayScriptOutputQuota.MAX_VARIABLE_VALUE_CHARS })
        assertEquals(RoleplayScriptOutputQuota.MAX_PROMPT_ADDITIONS_PER_SCRIPT, output.promptAdditions.size)
        assertTrue(output.promptAdditions.all { it.length <= RoleplayScriptOutputQuota.MAX_PROMPT_ADDITION_CHARS })
        assertEquals(RoleplayScriptOutputQuota.MAX_OUTGOING_MESSAGE_CHARS, output.outgoingMessage?.length)
        assertEquals(RoleplayScriptOutputQuota.MAX_UI_DIRECTIVES_PER_SCRIPT, output.uiDirectives.size)
        assertTrue(output.uiDirectives.all { it.type.length <= RoleplayScriptOutputQuota.MAX_UI_TYPE_CHARS })
        assertTrue(output.uiDirectives.all { it.payload.length <= RoleplayScriptOutputQuota.MAX_UI_PAYLOAD_CHARS })
        assertEquals(RoleplayScriptOutputQuota.MAX_LOGS_PER_SCRIPT, output.logs.size)
        assertTrue(output.logs.all { it.length <= RoleplayScriptOutputQuota.MAX_LOG_CHARS })
    }

    @Test
    fun runtimeStateApply_limitsRoundLevelAccumulation() {
        val state = RoleplayScriptRuntimeState(variables = emptyMap())
        val output = RoleplayScriptHostOutput(
            variables = (0 until 160).associate { index -> "key-$index" to "value-$index" },
            promptAdditions = (0 until 20).map { index -> "prompt-$index" },
            uiDirectives = (0 until 20).map { index ->
                RoleplayScriptUiDirective(type = "toast-$index", payload = "payload-$index")
            },
        )

        val applied = state.apply(output)

        assertEquals(RoleplayScriptOutputQuota.MAX_RUNTIME_VARIABLES, applied.variables.size)
        assertEquals(RoleplayScriptOutputQuota.MAX_PROMPT_ADDITIONS_PER_ROUND, applied.promptAdditions.size)
        assertEquals(RoleplayScriptOutputQuota.MAX_UI_DIRECTIVES_PER_ROUND, applied.uiDirectives.size)
        assertNull(applied.outgoingMessage)
    }
}
