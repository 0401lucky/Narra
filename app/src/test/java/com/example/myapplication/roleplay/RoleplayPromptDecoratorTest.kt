package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
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
}

