package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayConversationSupportTest {
    @Test
    fun buildTranscriptInput_keepsLatestLinesWithinLimit() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "沈砚清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )
        val expected = listOf(
            "林晚：关键推进：周六聚会已经结束了",
            "沈砚清：关键推进：我们现在已经在一起了",
        ).joinToString(separator = "\n")

        val transcript = RoleplayConversationSupport.buildTranscriptInput(
            messages = listOf(
                ChatMessage(id = "m1", role = MessageRole.USER, content = "最早铺垫：刚刚重逢", createdAt = 1L),
                ChatMessage(id = "m2", role = MessageRole.ASSISTANT, content = "最早铺垫：还在试探", createdAt = 2L),
                ChatMessage(id = "m3", role = MessageRole.USER, content = "关键推进：周六聚会已经结束了", createdAt = 3L),
                ChatMessage(id = "m4", role = MessageRole.ASSISTANT, content = "关键推进：我们现在已经在一起了", createdAt = 4L),
            ),
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "沈砚清"),
            settings = AppSettings(userDisplayName = "林晚"),
            maxLength = expected.length,
        )

        assertEquals(expected, transcript)
        assertFalse(transcript.contains("最早铺垫"))
    }

    @Test
    fun buildTranscriptInput_keepsLatestTailWhenSingleLineTooLong() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "沈砚清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        val transcript = RoleplayConversationSupport.buildTranscriptInput(
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    role = MessageRole.ASSISTANT,
                    content = "前情铺垫很长很长，但最后一句才重要：我已经处理完聚会后的所有事，现在可以认真和你在一起了",
                    createdAt = 1L,
                ),
            ),
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "沈砚清"),
            settings = AppSettings(userDisplayName = "林晚"),
            maxLength = 24,
        )

        assertTrue(transcript.startsWith("沈砚清：…"))
        assertTrue(transcript.contains("现在可以认真和你在一起了"))
        assertFalse(transcript.contains("前情铺垫很长"))
    }

    @Test
    fun buildTranscriptInput_keepsSpeakerPrefixForMultilineUserMessage() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "沈砚清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        val transcript = RoleplayConversationSupport.buildTranscriptInput(
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    role = MessageRole.USER,
                    content = "第一句铺垫\n第二句重点",
                    createdAt = 1L,
                ),
            ),
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "沈砚清"),
            settings = AppSettings(userDisplayName = "林晚"),
            maxLength = 10,
        )

        assertTrue(transcript.startsWith("林晚："))
        assertTrue(transcript.contains("第二句重点"))
        assertFalse(transcript == "第二句重点")
    }

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

    @Test
    fun buildDynamicDirectorNote_timeAwarenessDisabled_skipsTimeGapNarration() {
        val note = RoleplayConversationSupport.buildDynamicDirectorNote(
            messages = listOf(
                ChatMessage(
                    id = "u1",
                    role = MessageRole.USER,
                    content = "你在干嘛",
                    createdAt = 1_000L,
                ),
                ChatMessage(
                    id = "a1",
                    role = MessageRole.ASSISTANT,
                    content = "刚忙完",
                    createdAt = 2_000L,
                ),
            ),
            scenario = RoleplayScenario(
                id = "scene-1",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
                enableTimeAwareness = false,
            ),
            assistant = Assistant(id = "assistant-1", name = "余罪"),
            settings = AppSettings(),
            outputParser = RoleplayOutputParser(),
            nowProvider = { 10 * 60 * 60 * 1000L },
        )

        assertFalse(note.contains("【时间旁白】"))
        assertFalse(note.contains("时间差提示："))
    }

    @Test
    fun decorate_offlineDialogueWithTimeAwareness_includesTimeSection() {
        val prompt = RoleplayPromptDecorator.decorate(
            baseSystemPrompt = "你是一个角色",
            scenario = RoleplayScenario(
                id = "scene-1",
                interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
                enableTimeAwareness = true,
            ),
            assistant = Assistant(id = "assistant-1", name = "余罪"),
            settings = AppSettings(userDisplayName = "林晚"),
        )

        assertTrue(prompt.contains("【时间感知】"))
        assertTrue(prompt.contains("当前绝对时间"))
    }
}
