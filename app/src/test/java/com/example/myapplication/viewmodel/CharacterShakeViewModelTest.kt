package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.AssistantMessageDto
import com.example.myapplication.model.ChatChoiceDto
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.CharacterShakeFilters
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.testutil.TestOpenAiCompatibleApi
import com.example.myapplication.testutil.createTestAiServices
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterShakeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun generateAssistant_savesGeneratedAssistantAndSelectsIt() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val provider = ProviderSettings(
                id = "provider-a",
                name = "Provider A",
                baseUrl = "https://api.example.com/v1/",
                apiKey = "test-key",
                selectedModel = "deepseek-chat",
                availableModels = listOf("deepseek-chat"),
            )
            val services = createTestAiServices(
                settings = AppSettings(
                    providers = listOf(provider),
                    selectedProviderId = provider.id,
                    defaultPresetId = "roleplay-preset",
                ),
                dispatcher = mainDispatcherRule.dispatcher,
                apiServiceProvider = { _, _ -> fakeCharacterShakeApi() },
            )
            val viewModel = CharacterShakeViewModel(
                settingsRepository = services.settingsRepository,
                settingsEditor = services.settingsEditor,
                aiPromptExtrasService = services.aiPromptExtrasService,
            )

            val filters = CharacterShakeFilters(
                ageRange = "28-32",
                personality = "理性",
                identity = "自由职业",
                zodiacSign = "天蝎座",
                mbti = "INTJ",
            )
            viewModel.generateAssistant(filters)
            advanceUntilIdle()

            val stored = services.settingsRepository.settingsFlow.first()
            val assistant = stored.assistants.single()
            assertEquals("沈宴清", assistant.name)
            assertEquals(assistant.id, stored.selectedAssistantId)
            assertEquals("roleplay-preset", assistant.defaultPresetId)
            assertTrue(assistant.memoryEnabled)
            assertTrue(assistant.tags.contains("摇一摇"))
            assertEquals("已创建角色：沈宴清", viewModel.uiState.value.message)
        }

    private fun fakeCharacterShakeApi(): TestOpenAiCompatibleApi {
        return object : TestOpenAiCompatibleApi() {
            override suspend fun createChatCompletionAt(
                url: String,
                request: ChatCompletionRequest,
            ): Response<ChatCompletionResponse> {
                val prompt = request.messages.single().content.toString()
                assertTrue(prompt.contains("天蝎座"))
                assertTrue(prompt.contains("INTJ"))
                return Response.success(
                    ChatCompletionResponse(
                        choices = listOf(
                            ChatChoiceDto(
                                index = 0,
                                message = AssistantMessageDto(
                                    role = "assistant",
                                    content = """
                                    {
                                      "name": "沈宴清",
                                      "icon_name": "auto_stories",
                                      "description": "理性克制的同城建筑师。",
                                      "system_prompt": "沈宴清是 28 岁建筑师，表达克制，重视承诺和边界。",
                                      "scenario": "用户和沈宴清因为一次旧楼改造项目相识。",
                                      "greeting": "我刚从工地回来。你说想看的那张图，我带来了。",
                                      "example_dialogues": ["用户：你为什么总是这么冷静？\\n角色：因为有些话要想清楚再说。"],
                                      "creator_notes": "适合慢热现实向关系。",
                                      "tags": ["理性", "同城", "慢热"]
                                    }
                                    """.trimIndent(),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
    }
}
