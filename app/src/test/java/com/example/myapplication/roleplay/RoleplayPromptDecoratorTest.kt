package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayPromptDecoratorTest {
    @Test
    fun decorate_longformIncludesRoleLockAndParagraphRules() {
        val prompt = RoleplayPromptDecorator.decorate(
            baseSystemPrompt = "你要始终维持角色设定。",
            scenario = RoleplayScenario(
                id = "scene-1",
                title = "雨夜对峙",
                description = "旧案真相即将被揭开。",
                userDisplayNameOverride = "林晚",
                characterDisplayNameOverride = "余罪",
                longformModeEnabled = true,
            ),
            assistant = Assistant(
                id = "assistant-1",
                name = "余罪",
            ),
            settings = AppSettings(),
            directorNote = "优先接住她刚刚的逼问。",
        )

        assertTrue(prompt.contains("【角色锁定与互动边界】"))
        assertTrue(prompt.contains("不替 林晚 做决定"))
        assertTrue(prompt.contains("每轮先接住对方刚刚的动作、情绪、问题或态度"))
        assertTrue(prompt.contains("自然段落感优先于机械凑字数"))
        assertTrue(prompt.contains("每一段只承载一个主要动作、情绪重心或信息推进"))
        assertTrue(prompt.contains("【本轮导演提示】"))
    }

    @Test
    fun decorate_protocolIncludesRespondThenAdvanceRule() {
        val prompt = RoleplayPromptDecorator.decorate(
            baseSystemPrompt = "",
            scenario = RoleplayScenario(
                id = "scene-1",
                userDisplayNameOverride = "林晚",
                characterDisplayNameOverride = "余罪",
                enableRoleplayProtocol = true,
                enableNarration = true,
                longformModeEnabled = false,
            ),
            assistant = Assistant(id = "assistant-1", name = "余罪"),
            settings = AppSettings(),
        )

        assertTrue(prompt.contains("【输出协议】"))
        assertTrue(prompt.contains("先回应对方当前动作或问题，再推进关系、信息或局势中的一项"))
        assertTrue(prompt.contains("保持角色口吻稳定，不要跳出设定解释规则"))
    }

    @Test
    fun decorate_replacesScenarioPlaceholders() {
        val prompt = RoleplayPromptDecorator.decorate(
            baseSystemPrompt = "{{char}} 没有看向{{user}}。",
            scenario = RoleplayScenario(
                id = "scene-1",
                title = "{{char}}的夜班",
                description = "{{User}} 走进值班室时，{{char}} 正低头扣上颈间的金属扣。",
                openingNarration = "{{user}} 听见那声轻响时，心口跟着一缩。",
                userDisplayNameOverride = "lucky",
                characterDisplayNameOverride = "金乘",
                enableRoleplayProtocol = false,
            ),
            assistant = Assistant(id = "assistant-1", name = "金乘"),
            settings = AppSettings(),
        )

        assertTrue(prompt.contains("金乘 没有看向lucky"))
        assertTrue(prompt.contains("场景标题：金乘的夜班"))
        assertTrue(prompt.contains("lucky 走进值班室时，金乘 正低头扣上颈间的金属扣"))
        assertTrue(prompt.contains("lucky 听见那声轻响时"))
    }

    @Test
    fun decorate_onlineModeRequiresJsonArrayProtocol() {
        val prompt = RoleplayPromptDecorator.decorate(
            baseSystemPrompt = "",
            scenario = RoleplayScenario(
                id = "scene-1",
                userDisplayNameOverride = "林晚",
                characterDisplayNameOverride = "余罪",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
                enableNarration = true,
            ),
            assistant = Assistant(id = "assistant-1", name = "余罪"),
            settings = AppSettings(showOnlineRoleplayNarration = true),
        )

        assertTrue(prompt.contains("合法 JSON 数组"))
        assertTrue(prompt.contains("reply_to、recall、emoji、voice_message、ai_photo、location、transfer、poke、video_call"))
        assertTrue(prompt.contains("不是面对面现场互动"))
    }

    @Test
    fun decorate_onlineModeWithUserPersonaOverrideIncludesSceneSpecificUserPrompt() {
        val prompt = RoleplayPromptDecorator.decorate(
            baseSystemPrompt = "",
            scenario = RoleplayScenario(
                id = "scene-1",
                userDisplayNameOverride = "lucky",
                userPersonaOverride = "lucky是个嘴硬但会主动试探的人。",
                characterDisplayNameOverride = "余罪",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
                enableNarration = true,
            ),
            assistant = Assistant(id = "assistant-1", name = "余罪"),
            settings = AppSettings(showOnlineRoleplayNarration = true),
        )

        assertTrue(prompt.contains("【本场景对话者覆写】"))
        assertTrue(prompt.contains("lucky是个嘴硬但会主动试探的人"))
    }

    @Test
    fun decorate_onlineModeWithoutNarrationKeepsPureChatReminder() {
        val prompt = RoleplayPromptDecorator.decorate(
            baseSystemPrompt = "",
            scenario = RoleplayScenario(
                id = "scene-1",
                userDisplayNameOverride = "林晚",
                characterDisplayNameOverride = "余罪",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
                enableNarration = true,
            ),
            assistant = Assistant(id = "assistant-1", name = "余罪"),
            settings = AppSettings(showOnlineRoleplayNarration = false),
        )

        assertTrue(prompt.contains("只通过真实聊天消息表达情绪与推进"))
        assertTrue(prompt.contains("不写心声、旁白或小说段落"))
        assertTrue(prompt.contains("不要机械每轮都发动作对象"))
    }

    @Test
    fun decorate_videoCallModeAddsRealtimeConstraints() {
        val prompt = RoleplayPromptDecorator.decorate(
            baseSystemPrompt = "",
            scenario = RoleplayScenario(
                id = "scene-1",
                userDisplayNameOverride = "林晚",
                characterDisplayNameOverride = "余罪",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
                enableNarration = true,
            ),
            assistant = Assistant(id = "assistant-1", name = "余罪"),
            settings = AppSettings(showOnlineRoleplayNarration = true),
            isVideoCallActive = true,
        )

        assertTrue(prompt.contains("【线上视频通话模式】"))
        assertTrue(prompt.contains("实时视频通话"))
        assertTrue(prompt.contains("不要再输出 video_call 动作"))
        assertTrue(prompt.contains("更短、更即时"))
    }
}
