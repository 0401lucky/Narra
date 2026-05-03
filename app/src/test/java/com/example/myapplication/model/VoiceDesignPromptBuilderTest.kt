package com.example.myapplication.model

import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDesignPromptBuilderTest {
    @Test
    fun buildVoiceDesignPromptFromAssistant_infersGentleFemaleVoice() {
        val prompt = buildVoiceDesignPromptFromAssistant(
            Assistant(
                name = "白月",
                description = "温柔治愈的年轻女性，像姐姐一样照顾用户。",
                systemPrompt = "说话柔软，偶尔带一点笑意。",
            ),
        )

        assertTrue(prompt.contains("白月"))
        assertTrue(prompt.contains("年轻女性"))
        assertTrue(prompt.contains("温柔"))
        assertTrue(prompt.contains("清亮柔和"))
        assertTrue(prompt.contains("不播音腔"))
    }

    @Test
    fun buildVoiceDesignPromptFromAssistant_infersCalmMaleVoice() {
        val prompt = buildVoiceDesignPromptFromAssistant(
            Assistant(
                name = "陆沉",
                description = "成熟男性，冷静克制，职业是侦探，重视证据。",
                scenario = "雨夜调查。",
            ),
        )

        assertTrue(prompt.contains("成熟男性"))
        assertTrue(prompt.contains("低沉稳定"))
        assertTrue(prompt.contains("冷静克制"))
        assertTrue(prompt.contains("专业沉稳"))
        assertTrue(prompt.contains("语速中等偏慢"))
    }

    @Test
    fun buildVoiceDesignPromptFromAssistant_handlesSparsePersona() {
        val prompt = buildVoiceDesignPromptFromAssistant(
            Assistant(name = "未央"),
        )

        assertTrue(prompt.contains("未央"))
        assertTrue(prompt.contains("自然中性声音"))
        assertTrue(prompt.contains("自然贴近角色"))
        assertTrue(prompt.contains("普通话清晰"))
    }
}
