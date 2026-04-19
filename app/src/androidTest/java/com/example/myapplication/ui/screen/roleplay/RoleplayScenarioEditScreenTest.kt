package com.example.myapplication.ui.screen.roleplay

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.repository.LocalImageStore
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.LocalImagePersister
import com.example.myapplication.ui.theme.ChatAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleplayScenarioEditScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchingInteractionModes_keepsDraftInputsAndSavesExpectedSpec() {
        val savedScenarios = mutableListOf<RoleplayScenario>()

        composeRule.setContent {
            ChatAppTheme {
                CompositionLocalProvider(
                    LocalImagePersister provides LocalImageStore(composeRule.activity),
                ) {
                    RoleplayScenarioEditScreen(
                        scenario = null,
                        settings = AppSettings(),
                        assistants = emptyList(),
                        onSave = { scenario -> savedScenarios += scenario },
                        onDelete = null,
                        noticeMessage = null,
                        errorMessage = null,
                        onClearNoticeMessage = {},
                        onClearErrorMessage = {},
                        onNavigateBack = {},
                    )
                }
            }
        }

        val title = "雨夜站台"
        val description = "久别重逢后的第一句对白要带点试探。"
        val openingNarration = "雨点砸在站台顶棚上，你隔着人群看见她停下脚步。"
        val userPersona = "表面冷静，实际上很在意这次见面。"

        composeRule.onNodeWithTag(TAG_SCENARIO_TITLE_INPUT)
            .performTextInput(title)
        composeRule.onNodeWithTag(TAG_SCENARIO_DESCRIPTION_INPUT)
            .performTextInput(description)

        composeRule.onNodeWithTag(TAG_SCENARIO_EDIT_LIST)
            .performScrollToNode(hasTestTag(TAG_SCENARIO_OPENING_NARRATION_INPUT))
        composeRule.onNodeWithTag(TAG_SCENARIO_OPENING_NARRATION_INPUT)
            .performTextInput(openingNarration)

        composeRule.onNodeWithTag(TAG_SCENARIO_EDIT_LIST)
            .performScrollToNode(hasTestTag(TAG_SCENARIO_USER_PERSONA_INPUT))
        composeRule.onNodeWithTag(TAG_SCENARIO_USER_PERSONA_INPUT)
            .performTextInput(userPersona)

        val onlineModeTag = roleplayInteractionModeTag(RoleplayInteractionMode.ONLINE_PHONE)
        composeRule.onNodeWithTag(TAG_SCENARIO_EDIT_LIST)
            .performScrollToNode(hasTestTag(onlineModeTag))
        composeRule.onNodeWithTag(onlineModeTag)
            .performClick()
        composeRule.onNodeWithTag(onlineModeTag)
            .assertIsSelected()

        assertDraftFields(
            title = title,
            description = description,
            openingNarration = openingNarration,
            userPersona = userPersona,
        )

        clickSaveButton()
        composeRule.runOnIdle {
            val saved = savedScenarios.lastOrNull()
            assertNotNull(saved)
            assertEquals(RoleplayInteractionMode.ONLINE_PHONE, saved?.interactionMode)
            assertEquals(false, saved?.longformModeEnabled)
            assertEquals(true, saved?.enableRoleplayProtocol)
            assertEquals(title, saved?.title)
            assertEquals(description, saved?.description)
            assertEquals(openingNarration, saved?.openingNarration)
            assertEquals(userPersona, saved?.userPersonaOverride)
        }

        val longformModeTag = roleplayInteractionModeTag(RoleplayInteractionMode.OFFLINE_LONGFORM)
        composeRule.onNodeWithTag(TAG_SCENARIO_EDIT_LIST)
            .performScrollToNode(hasTestTag(longformModeTag))
        composeRule.onNodeWithTag(longformModeTag)
            .performClick()
        composeRule.onNodeWithTag(longformModeTag)
            .assertIsSelected()

        assertDraftFields(
            title = title,
            description = description,
            openingNarration = openingNarration,
            userPersona = userPersona,
        )

        clickSaveButton()
        composeRule.runOnIdle {
            val saved = savedScenarios.lastOrNull()
            assertNotNull(saved)
            assertEquals(RoleplayInteractionMode.OFFLINE_LONGFORM, saved?.interactionMode)
            assertEquals(true, saved?.longformModeEnabled)
            assertEquals(false, saved?.enableRoleplayProtocol)
            assertEquals(title, saved?.title)
            assertEquals(description, saved?.description)
            assertEquals(openingNarration, saved?.openingNarration)
            assertEquals(userPersona, saved?.userPersonaOverride)
        }
    }

    private fun assertDraftFields(
        title: String,
        description: String,
        openingNarration: String,
        userPersona: String,
    ) {
        composeRule.onNodeWithTag(TAG_SCENARIO_EDIT_LIST)
            .performScrollToNode(hasTestTag(TAG_SCENARIO_TITLE_INPUT))
        composeRule.onNodeWithTag(TAG_SCENARIO_TITLE_INPUT)
            .assertTextContains(title)

        composeRule.onNodeWithTag(TAG_SCENARIO_EDIT_LIST)
            .performScrollToNode(hasTestTag(TAG_SCENARIO_DESCRIPTION_INPUT))
        composeRule.onNodeWithTag(TAG_SCENARIO_DESCRIPTION_INPUT)
            .assertTextContains(description)

        composeRule.onNodeWithTag(TAG_SCENARIO_EDIT_LIST)
            .performScrollToNode(hasTestTag(TAG_SCENARIO_OPENING_NARRATION_INPUT))
        composeRule.onNodeWithTag(TAG_SCENARIO_OPENING_NARRATION_INPUT)
            .assertTextContains(openingNarration)

        composeRule.onNodeWithTag(TAG_SCENARIO_EDIT_LIST)
            .performScrollToNode(hasTestTag(TAG_SCENARIO_USER_PERSONA_INPUT))
        composeRule.onNodeWithTag(TAG_SCENARIO_USER_PERSONA_INPUT)
            .assertTextContains(userPersona)
    }

    private fun clickSaveButton() {
        composeRule.onNodeWithTag(TAG_SCENARIO_EDIT_LIST)
            .performScrollToNode(hasText("创建场景"))
        composeRule.onNodeWithText("创建场景")
            .performClick()
    }
}
