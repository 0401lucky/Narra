package com.example.myapplication.ui.component.roleplay

import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayGlassBubblePolicyTest {
    @Test
    fun onlineBubble_usesGlassOnlyWhenBackgroundIsLoadedAndSwitchIsEnabled() {
        assertPolicy(RoleplayMessageBubbleMode.ONLINE_PHONE, hasImage = false, enabled = false, expected = false)
        assertPolicy(RoleplayMessageBubbleMode.ONLINE_PHONE, hasImage = false, enabled = true, expected = false)
        assertPolicy(RoleplayMessageBubbleMode.ONLINE_PHONE, hasImage = true, enabled = false, expected = false)
        assertPolicy(RoleplayMessageBubbleMode.ONLINE_PHONE, hasImage = true, enabled = true, expected = true)
    }

    @Test
    fun offlineBubble_followsLoadedBackgroundRegardlessOfOnlineSwitch() {
        assertPolicy(RoleplayMessageBubbleMode.DEFAULT, hasImage = false, enabled = false, expected = false)
        assertPolicy(RoleplayMessageBubbleMode.DEFAULT, hasImage = false, enabled = true, expected = false)
        assertPolicy(RoleplayMessageBubbleMode.DEFAULT, hasImage = true, enabled = false, expected = true)
        assertPolicy(RoleplayMessageBubbleMode.DEFAULT, hasImage = true, enabled = true, expected = true)
    }

    private fun assertPolicy(
        mode: RoleplayMessageBubbleMode,
        hasImage: Boolean,
        enabled: Boolean,
        expected: Boolean,
    ) {
        assertEquals(
            "mode=$mode, hasImage=$hasImage, enabled=$enabled",
            expected,
            shouldUseRoleplayGlassBubble(
                hasBackgroundImage = hasImage,
                bubbleMode = mode,
                onlineBackgroundGlassEnabled = enabled,
            ),
        )
    }
}
