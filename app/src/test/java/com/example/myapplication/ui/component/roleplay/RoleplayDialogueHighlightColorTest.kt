package com.example.myapplication.ui.component.roleplay

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoleplayDialogueHighlightColorTest {

    @Test
    fun resolveRoleplayDialogueHighlightColor_withoutImageUsesThemeCharacterText() {
        val colors = testRoleplayColors(textPrimary = Color(0xFF3A2130))

        val resolved = resolveRoleplayDialogueHighlightColor(
            hasImage = false,
            colors = colors,
        )

        assertEquals(Color(0xFF3A2130), resolved)
        assertNotEquals(RoleplayQuotedDialogueHighlightColor, resolved)
    }

    @Test
    fun resolveRoleplayDialogueHighlightColor_withImageKeepsGlassHighlight() {
        val colors = testRoleplayColors(textPrimary = Color(0xFF3A2130))

        val resolved = resolveRoleplayDialogueHighlightColor(
            hasImage = true,
            colors = colors,
        )

        assertEquals(RoleplayQuotedDialogueHighlightColor, resolved)
    }

    @Test
    fun buildCharacterDialogueAnnotatedString_withoutImageColorsWholeSpeechWithThemeText() {
        val themeTextColor = Color(0xFF3A2130)
        val rendered = buildCharacterDialogueAnnotatedString(
            text = "I will stay here.",
            narrationColor = themeTextColor.copy(alpha = 0.78f),
            dialogueColor = resolveRoleplayDialogueHighlightColor(
                hasImage = false,
                colors = testRoleplayColors(textPrimary = themeTextColor),
            ),
        )

        assertEquals(themeTextColor, rendered.spanStyles.single().item.color)
    }
}

private fun testRoleplayColors(
    textPrimary: Color,
): ImmersiveRoleplayColors {
    return ImmersiveRoleplayColors(
        textPrimary = textPrimary,
        userText = Color.White,
        textMuted = Color(0xFF9A667A),
        characterAccent = Color(0xFFE58BAD),
        userAccent = Color(0xFFE58BAD),
        thoughtText = Color(0xFF9A667A),
        panelBackground = Color.White,
        panelBackgroundStrong = Color.White,
        panelBorder = Color(0xFFF2C8D7),
        errorText = Color(0xFFB3261E),
        errorBackground = Color(0xFFFFEDEA),
        errorBackgroundStrong = Color(0xFFFFDAD6),
        userBubbleBackground = Color(0xFFF5DDE6),
        characterBubbleBackground = Color.White,
        narrationBubbleBackground = Color.White,
    )
}
