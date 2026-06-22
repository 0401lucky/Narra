package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.EconomyImageStyle
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayShopPromptServiceTest {
    @Test
    fun buildRoleplayShopItemsPrompt_anchorsItemsToRecentContextAndMemory() {
        val prompt = buildRoleplayShopItemsPrompt(
            characterName = "沈烬",
            userName = "lucky",
            characterPersona = "沈烬沉默克制，习惯把关心藏进具体行动。",
            userPersona = "lucky 对旧物和约定很敏感。",
            scenarioContext = "标题：旧车站重逢\n场景：雨夜旧车站，两个人还没说清那次失约。",
            conversationExcerpt = "用户：你还留着那枚铜纽扣吗？\n沈烬：那晚之后，我一直放在外套内袋。",
            memoryContext = "- 沈烬记得 lucky 曾经说过，真正重要的东西要能被带在身上。",
            economyContext = "钱包余额：¥1100.00",
            imageStyle = EconomyImageStyle.NONE,
        )

        assertTrue(prompt.contains("不是生成通用商城货架"))
        assertTrue(prompt.contains("至少 4 个必须能从【近期互动】或【记忆线索】找到直接来源"))
        assertTrue(prompt.contains("铜纽扣"))
        assertTrue(prompt.contains("旧车站"))
        assertTrue(prompt.contains("除非上下文明示出现，否则不要使用钥匙扣"))
        assertTrue(prompt.contains("\"items\""))
    }
}
