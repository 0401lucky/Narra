package com.example.myapplication.roleplay.script

import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayScriptPlannerTest {
    @Test
    fun orderedExecutableScripts_sortsByScopeAndDropsDisabledOrBlankScripts() {
        val scripts = listOf(
            script("session", RoleplayScriptScope.SESSION),
            script("disabled", RoleplayScriptScope.GLOBAL, enabled = false),
            script("scenario", RoleplayScriptScope.SCENARIO),
            script("blank", RoleplayScriptScope.GLOBAL, source = " "),
            script("global", RoleplayScriptScope.GLOBAL),
            script("character", RoleplayScriptScope.CHARACTER),
        )

        val orderedIds = RoleplayScriptPlanner.orderedExecutableScripts(scripts).map { it.id }

        assertEquals(listOf("global", "character", "scenario", "session"), orderedIds)
    }

    @Test
    fun orderedExecutableScripts_keepsFirstScriptWhenIdsDuplicate() {
        val scripts = listOf(
            script("same", RoleplayScriptScope.SESSION, source = "first"),
            script("same", RoleplayScriptScope.GLOBAL, source = "second"),
        )

        val ordered = RoleplayScriptPlanner.orderedExecutableScripts(scripts)

        assertEquals(1, ordered.size)
        assertEquals("first", ordered.single().source)
    }

    private fun script(
        id: String,
        scope: RoleplayScriptScope,
        source: String = "function beforePrompt() {}",
        enabled: Boolean = true,
    ): RoleplayScriptDefinition {
        return RoleplayScriptDefinition(
            id = id,
            name = id,
            scope = scope,
            source = source,
            enabled = enabled,
        )
    }
}
