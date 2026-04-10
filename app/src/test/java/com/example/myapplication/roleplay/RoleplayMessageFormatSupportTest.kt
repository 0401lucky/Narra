package com.example.myapplication.roleplay

import com.example.myapplication.model.RoleplayOutputFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayMessageFormatSupportTest {
    @Test
    fun resolveContentOutputFormat_protocolThoughtDoesNotMisclassifyAsLongform() {
        val format = RoleplayMessageFormatSupport.resolveContentOutputFormat(
            preferredFormat = RoleplayOutputFormat.PROTOCOL,
            rawContent = """
                <narrative>他指节轻敲了两下杯壁。</narrative>
                <thought>不能再让她绕开这个问题。</thought>
                <dialogue speaker="character">这次别再含糊过去。</dialogue>
            """.trimIndent(),
        )

        assertEquals(RoleplayOutputFormat.PROTOCOL, format)
    }

    @Test
    fun resolveContentOutputFormat_unspecifiedThoughtOnlyStillFallsBackToLongform() {
        val format = RoleplayMessageFormatSupport.resolveContentOutputFormat(
            preferredFormat = RoleplayOutputFormat.UNSPECIFIED,
            rawContent = "<thought>（不能再退了。）</thought>",
        )

        assertEquals(RoleplayOutputFormat.LONGFORM, format)
    }
}
