package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.*
import org.junit.Assert.*
import org.junit.Test

class CompanionResourceResolverTest {
    private val scene = RoleplayScenario(
        id = "scene",
        assistantId = "alice",
        characterPortraitUri = "/old.png",
        characterPortraitUrl = "https://example.invalid/old.png",
        backgroundUri = "/old-background.png"
    )
    private val assistant = Assistant(id = "alice", avatarUri = "/avatar.png")
    private val images = listOf(
        CompanionImageResource("happy", "表情", "/happy.png", CompanionImageKind.PORTRAIT),
        CompanionImageResource("default", "默认", "/default.png", CompanionImageKind.PORTRAIT),
        CompanionImageResource(
            "background",
            "背景",
            "/background.png",
            CompanionImageKind.BACKGROUND
        ),
        CompanionImageResource("decor", "装饰", "/decor.png", CompanionImageKind.DECOR),
    )
    private val visuals = CompanionVisualSettings(
        resources = images,
        outfits = listOf(CompanionOutfit("outfit", "套装", "default", mapOf("happy" to "happy"))),
        selectedOutfitId = "outfit",
        selectedBackgroundId = "background"
    )

    @Test
    fun explicitOutfit_isAheadOfLegacyAndTargetsDoNotCarryLastExpression() {
        val resolved = CompanionResourceResolver.resolve(visuals, scene, assistant) { true }
        assertEquals(
            listOf("happy", "default", null, null, null),
            resolved.portraits("happy").map { it.resourceId })
        assertEquals("default", resolved.portraits("unknown").first().resourceId)
        assertEquals("background", resolved.backgrounds.first().resourceId)
    }

    @Test
    fun missingFiles_fallBackInOrderAndDecorCannotBecomeSubject() {
        val resolved = CompanionResourceResolver.resolve(
            visuals.copy(decorationIds = listOf("decor")),
            scene,
            assistant
        ) { it.uri != "/happy.png" && it.uri != "/default.png" && it.uri != "/old.png" }
        assertEquals("https://example.invalid/old.png", resolved.portraits("happy").first().url)
        assertFalse(resolved.portraits("happy").any { it.resourceId == "decor" })
        val bad = visuals.copy(
            outfits = listOf(
                CompanionOutfit(
                    "outfit",
                    "坏套装",
                    "decor",
                    mapOf("happy" to "decor")
                )
            )
        )
        assertNotNull(CompanionVisualValidation.error(bad))
        assertNull(
            CompanionResourceResolver.resolve(bad, scene, null) { true }.portraits("happy")
                .first().resourceId
        )
    }

    @Test
    fun sceneChoice_overridesCharacterAndCanExplicitlyReturnToOldImages() {
        val overridden = visuals.copy(
            sceneOverrides = mapOf(
                "scene" to CompanionSceneVisualOverride(
                    layout = CompanionStageLayout(
                        CompanionPortraitPosition.LEFT,
                        0.5f,
                        0.25f
                    )
                )
            )
        )
        assertEquals(
            "/old.png",
            CompanionResourceResolver.resolve(overridden, scene, assistant) { true }
                .portraits("happy").first().uri
        )
        assertEquals(
            "/old-background.png",
            CompanionResourceResolver.resolve(
                overridden,
                scene,
                assistant
            ) { true }.backgrounds.first().uri
        )
        assertEquals("outfit", overridden.selectedOutfitId)
        assertEquals(
            CompanionPortraitPosition.LEFT,
            overridden.forScene("scene").layout.portraitPosition
        )
        assertEquals(
            CompanionPortraitPosition.CENTER,
            overridden.forScene("other").layout.portraitPosition
        )
    }

    @Test
    fun noNewSelection_keepsOldPortraitThenAvatarAndNameFallback() {
        assertEquals(
            "/old.png",
            CompanionResourceResolver.resolve(CompanionVisualSettings(), scene, assistant) { true }
                .portraits("happy").first().uri
        )
        val avatar = CompanionResourceResolver.resolve(
            CompanionVisualSettings(),
            scene,
            assistant
        ) { it.uri == "/avatar.png" }
        assertEquals("/avatar.png", avatar.defaultPortraits.single().uri)
        assertTrue(
            CompanionResourceResolver.resolve(
                visuals,
                scene,
                assistant
            ) { false }.defaultPortraits.isEmpty()
        )
        assertEquals(CompanionDisplayMode.LIST, CompanionVisualSettings().displayMode)
    }
}
