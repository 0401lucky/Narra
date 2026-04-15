package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayScenario
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayConversationSupportTest {
    @Test
    fun buildDynamicDirectorNote_includesTensionGoalObstacleAndAnchor() {
        val note = RoleplayConversationSupport.buildDynamicDirectorNote(
            messages = listOf(
                ChatMessage(
                    id = "u1",
                    role = MessageRole.USER,
                    content = "你到底还瞒了我多少事？",
                    createdAt = 1L,
                ),
                ChatMessage(
                    id = "a1",
                    role = MessageRole.ASSISTANT,
                    content = "余罪没有立刻回答，只是盯着她的眼睛。",
                    createdAt = 2L,
                ),
            ),
            scenario = RoleplayScenario(
                id = "scene-1",
                userDisplayNameOverride = "林晚",
                characterDisplayNameOverride = "余罪",
                enableNarration = true,
            ),
            assistant = Assistant(id = "assistant-1", name = "余罪"),
            settings = AppSettings(),
            outputParser = RoleplayOutputParser(),
        )

        assertTrue(note.contains("当前关系张力："))
        assertTrue(note.contains("当前目标或优先推进点："))
        assertTrue(note.contains("当前阻碍："))
        assertTrue(note.contains("优先接住上一轮已经抛出的线索或态度"))
        assertTrue(note.contains("不要把上一轮已经表达过的核心态度换个说法再重复"))
        assertTrue(note.contains("本轮必须新增一个有效推进点"))
        assertTrue(note.contains("推进时先回应，再顺势往前推一小步"))
    }
}
