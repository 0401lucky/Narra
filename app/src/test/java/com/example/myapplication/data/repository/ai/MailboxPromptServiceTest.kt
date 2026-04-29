package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.ApiServiceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MailboxPromptServiceTest {
    private val service = MailboxPromptService(
        PromptExtrasCore(apiServiceFactory = ApiServiceFactory()),
    )

    @Test
    fun parseMailboxReply_readsStructuredJson() {
        val reply = service.parseMailboxReply(
            """
            {
              "subject": "关于旧车站",
              "content": "我后来又想起那天的雨。",
              "mood": "克制认真",
              "tags": ["旧车站", "情绪波动"],
              "memoryCandidate": "用户和角色都记得旧车站那次雨。"
            }
            """.trimIndent(),
        )

        assertEquals("关于旧车站", reply.subject)
        assertEquals("克制认真", reply.mood)
        assertEquals(listOf("旧车站", "情绪波动"), reply.tags)
        assertEquals("用户和角色都记得旧车站那次雨。", reply.memoryCandidate)
    }

    @Test
    fun parseMailboxReply_fallsBackToPlainTextContent() {
        val reply = service.parseMailboxReply("我把你的信读完了。")

        assertEquals("关于你那封信", reply.subject)
        assertEquals("我把你的信读完了。", reply.content)
        assertTrue(reply.tags.isEmpty())
    }

    @Test
    fun buildLocalProactiveLetter_marksActiveLetter() {
        val reply = service.buildLocalProactiveLetter(
            MailboxProactiveRequest(
                scenario = null,
                assistant = null,
                userName = "我",
                characterName = "林屿",
                assembledContextText = "【最近聊天】\n我：旧车站那晚的雨还在。",
            ),
        )

        assertTrue(reply.subject.isNotBlank())
        assertTrue(reply.content.contains("我没有等你先开口"))
        assertTrue(reply.tags.contains("主动来信"))
    }

    @Test(expected = IllegalStateException::class)
    fun parseMailboxReply_rejectsJsonWithoutContent() {
        service.parseMailboxReply("""{"subject":"空回信","content":""}""")
    }
}
