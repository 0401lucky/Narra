package com.example.myapplication.conversation

import android.app.Application
import com.example.myapplication.context.DefaultPromptContextAssembler
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayPromptDecorator
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.CompanionEntryTestFixture
import com.example.myapplication.testutil.FakePresetRepository
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionBuiltinPresetTest : CompanionEntryTestFixture() {
    @Test
    fun defaultInput_doesNotRequirePlotChange() =
        verifyCompanion(DEFAULT_PRESET_ID, CompanionTurnKind.INPUT)

    @Test
    fun defaultContinue_doesNotRequirePlotChange() =
        verifyCompanion(DEFAULT_PRESET_ID, CompanionTurnKind.CONTINUE)

    @Test
    fun qwenInput_doesNotRequirePlotChange() =
        verifyCompanion(QWEN_COMPAT_PRESET_ID, CompanionTurnKind.INPUT)

    @Test
    fun qwenContinue_doesNotRequirePlotChange() =
        verifyCompanion(QWEN_COMPAT_PRESET_ID, CompanionTurnKind.CONTINUE)

    private fun verifyCompanion(presetId: String, kind: CompanionTurnKind) = runBlocking<Unit> {
        val original = AppJson.gson.toJson(BUILTIN_PRESETS)
        val preset = BUILTIN_PRESETS.single { it.id == presetId }
        worldBook.upsertEntry(
            WorldBookEntry(
                id = "book",
                title = "背景",
                content = "必须保留的世界书背景",
                alwaysActive = true
            )
        )
        val body = sendCompanion(preset, kind)
        for (command in FORCED_CHANGES) assertFalse(command, body.contains(command))
        assertTrue(body.contains("甲角色事实哨兵"))
        assertTrue(body.contains("身份甲"))
        assertEquals(1, Regex("身份甲").findAll(body).count())
        assertTrue(body.contains("必须保留的世界书背景"))
        assertTrue(body.contains("共享沉默"))
        assertTrue(body.contains(if (kind == CompanionTurnKind.INPUT) "【本轮互动】" else "【场景留白】"))
        val root = JsonParser.parseString(body).asJsonObject
        assertEquals(
            requireNotNull(preset.sampler.temperature),
            root["temperature"].asFloat,
            0.0001f
        )
        assertEquals(requireNotNull(preset.sampler.topP), root["top_p"].asFloat, 0.0001f)
        assertEquals(original, AppJson.gson.toJson(BUILTIN_PRESETS))
    }

    @Test
    fun legacyBuiltinModes_keepTheirOriginalRulesAndSampler() = runBlocking<Unit> {
        for (presetId in listOf(DEFAULT_PRESET_ID, QWEN_COMPAT_PRESET_ID)) {
            val preset = BUILTIN_PRESETS.single { it.id == presetId }
            for (mode in RoleplayInteractionMode.entries) {
                val context = scene(mode = mode)
                roleplay.upsertScenario(
                    roleplay.getScenario(context.scenarioId)!!.copy(presetId = presetId)
                )
                val scenario = requireNotNull(roleplay.getScenario(context.scenarioId))
                val current = settings.settingsFlow.first()
                val assistant = current.assistants.first()
                val input = ChatMessage(
                    "legacy-$presetId-$mode",
                    context.conversationId,
                    MessageRole.USER,
                    "旧路径输入",
                    createdAt = clock++
                )
                val prompt = assembler(preset).assemble(
                    RoleplayConversationSupport.resolvePromptSettings(scenario, current),
                    RoleplayConversationSupport.resolvePromptAssistant(scenario, assistant),
                    requireNotNull(conversations.getConversation(context.conversationId)),
                    input.content,
                    listOf(input),
                    PromptMode.ROLEPLAY,
                    includePhoneSnapshot = false,
                    scenarioPresetId = preset.id,
                )
                reply()
                gateway().sendMessageStream(
                    listOf(input), RoleplayPromptDecorator.decorate(
                        prompt.systemPrompt,
                        scenario, assistant, current
                    ), PromptMode.ROLEPLAY, prompt.promptEnvelope
                ).toList()
                val body = payload()
                assertTrue(body.contains(if (presetId == DEFAULT_PRESET_ID) "每次回应都要让" else "每轮至少带来一个真实变化"))
                assertEquals(preset.sampler, prompt.promptEnvelope.sampler)
            }
        }
        assertEquals(6, server.requestCount)
    }

    @Test
    fun customPresetAndCharacterText_areNotFilteredByPhrase() = runBlocking<Unit> {
        val custom =
            BUILTIN_PRESETS.first().copy(id = "custom", builtIn = false, userModified = true)
        val current = settings.settingsFlow.first()
        settings.saveAssistants(current.assistants.map {
            if (it.id == "alice")
                it.copy(systemPrompt = "角色记得一句话：每次回应都要让每个人有选择。") else it
        }, "alice")
        val body = sendCompanion(custom, CompanionTurnKind.CONTINUE)
        assertTrue(body.contains("每次回应都要让关系"))
        assertTrue(body.contains("角色记得一句话：每次回应都要让每个人有选择。"))
    }

    @Test
    fun userEditedBuiltinBlockAndSampler_arePreserved() = runBlocking<Unit> {
        val original = BUILTIN_PRESETS.first()
        val edited = original.copy(
            userModified = true, sampler = original.sampler.copy(temperature = 0.61f),
            entries = original.entries.map {
                if (it.id == "narra-core-roleplay")
                    it.copy(content = "这是用户自定义核心：每次回应都要让自定义哨兵留在请求。") else it
            })
        val body = sendCompanion(edited, CompanionTurnKind.INPUT)
        assertTrue(body.contains("这是用户自定义核心：每次回应都要让自定义哨兵留在请求。"))
        assertFalse(body.contains("每次回应至少带来一个小变化"))
        assertEquals(
            0.61f,
            JsonParser.parseString(body).asJsonObject["temperature"].asFloat,
            0.0001f
        )
        assertEquals(
            "这是用户自定义核心：每次回应都要让自定义哨兵留在请求。",
            edited.entries.first { it.id == "narra-core-roleplay" }.content
        )
    }

    private suspend fun sendCompanion(preset: Preset, kind: CompanionTurnKind): String {
        val context = scene()
        roleplay.upsertScenario(
            roleplay.getScenario(context.scenarioId)!!.copy(presetId = preset.id)
        )
        val coordinator = requests(assembler = assembler(preset))
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val turn = coordinator.beginTurn(
            encounter.id,
            kind,
            if (kind == CompanionTurnKind.INPUT) "只想坐一会儿" else ""
        )
        reply()
        assertTrue(coordinator.generate(turn.attempt.id) is CompanionGenerationResult.Saved)
        return payload()
    }

    private fun assembler(preset: Preset) = DefaultPromptContextAssembler(
        memoryArchiveRepository = archive,
        worldBookRepository = worldBook, presetRepository = FakePresetRepository(listOf(preset))
    )

    companion object {
        private val FORCED_CHANGES = listOf(
            "每次回应都要让", "每次回应至少带来一个小变化", "每轮至少带来一个真实变化",
            "推进可以很小，但必须真实发生", "打破僵局", "用一句有角色味的话推进"
        )
    }
}
