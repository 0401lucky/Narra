package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.EconomyImageStyle
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ShopItemDraft
import com.example.myapplication.model.parseMoneyToCents
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

private const val ROLEPLAY_SHOP_OPERATION = "角色商店商品生成"

internal fun buildRoleplayShopItemsPrompt(
    characterName: String,
    userName: String,
    characterPersona: String,
    userPersona: String,
    scenarioContext: String,
    conversationExcerpt: String,
    memoryContext: String,
    economyContext: String,
    imageStyle: EconomyImageStyle,
): String = buildString {
    appendLine("你是 Narra 的角色扮演商店策划。")
    appendLine("你的任务不是生成通用商城货架，而是把当前角色关系、最近互动和记忆转成能进入剧情的 6 个商品/道具。")
    appendLine("这些商品必须能真实作用到后续剧情里，像可被赠送、使用、触发事件或推进关系的道具。")
    appendLine()
    appendLine("【角色】${characterName.trim().ifBlank { "角色" }}")
    appendLine("【用户】${userName.trim().ifBlank { "用户" }}")
    if (characterPersona.isNotBlank()) {
        appendLine("【角色人设】")
        appendLine(characterPersona.take(2200))
    }
    if (userPersona.isNotBlank()) {
        appendLine("【用户人设】")
        appendLine(userPersona.take(1200))
    }
    if (scenarioContext.isNotBlank()) {
        appendLine("【当前剧情/场景】")
        appendLine(scenarioContext.take(1600))
    }
    if (conversationExcerpt.isNotBlank()) {
        appendLine("【近期互动】")
        appendLine(conversationExcerpt.take(2400))
    }
    if (memoryContext.isNotBlank()) {
        appendLine("【记忆线索】")
        appendLine(memoryContext.take(1600))
    }
    if (economyContext.isNotBlank()) {
        appendLine("【钱包与库存状态】")
        appendLine(economyContext.take(1000))
    }
    appendLine("【图片风格】${imageStyle.displayName}；风格提示：${imageStyle.promptHint}")
    appendLine()
    appendLine("【取材优先级】")
    appendLine("1. 优先使用近期互动里的具体物件、地点、未完成约定、冲突、暧昧点、伤口、秘密和情绪变化。")
    appendLine("2. 其次使用记忆线索里的长期偏好、旧事件、关系承诺、禁忌、创伤或习惯。")
    appendLine("3. 再用角色人设和用户人设补足商品的语气、审美、价格和边界。")
    appendLine("4. 如果上下文信息存在张力，以近期互动和记忆优先，不要只按角色标签生成。")
    appendLine()
    appendLine("【生成要求】")
    appendLine("1. 6 个商品里至少 4 个必须能从【近期互动】或【记忆线索】找到直接来源；其余也必须贴合角色人设。")
    appendLine("2. 每个商品都要围绕一个具体剧情钩子生成，description 要让用户看出它和当前故事有关。")
    appendLine("3. effect_prompt 写给后续 AI 使用，要说明这个道具会自然触发哪段关系、记忆、事件或角色反应，不要写成系统命令。")
    appendLine("4. 除非上下文明示出现，否则不要使用钥匙扣、围巾、明信片、薄荷糖、票根这类默认泛用道具。")
    appendLine("5. 价格必须符合沉浸感，单位为人民币元，可用小数；不要全都很贵，也不要全都很便宜。")
    appendLine("6. 商品应贴合当前角色和用户关系，不要泛泛生成现代商城常见商品。")
    appendLine("7. image_prompt 用英文，描述单个商品主体、材质、构图、光影和氛围；不要人物正脸、文字、水印、logo、UI。")
    appendLine("8. 严格输出 JSON 对象，不要 Markdown，不要解释。格式：")
    appendLine(
        """
        {
          "items": [
            {
              "name": "商品名",
              "description": "给用户看的描述",
              "price": "12.80",
              "category": "纪念/日常/线索/服饰/补给等",
              "rarity": "普通/稀有/珍贵",
              "effect_prompt": "这个道具在剧情里的自然作用",
              "image_prompt": "English item image prompt"
            }
          ]
        }
        """.trimIndent(),
    )
}

internal class RoleplayShopPromptService(
    private val core: PromptExtrasCore,
) {
    suspend fun generateRoleplayShopItems(
        characterName: String,
        userName: String,
        characterPersona: String,
        userPersona: String,
        scenarioContext: String,
        conversationExcerpt: String,
        memoryContext: String,
        economyContext: String,
        imageStyle: EconomyImageStyle,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<ShopItemDraft> {
        val prompt = buildRoleplayShopItemsPrompt(
            characterName = characterName,
            userName = userName,
            characterPersona = characterPersona,
            userPersona = userPersona,
            scenarioContext = scenarioContext,
            conversationExcerpt = conversationExcerpt,
            memoryContext = memoryContext,
            economyContext = economyContext,
            imageStyle = imageStyle,
        )
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = ROLEPLAY_SHOP_OPERATION,
            request = core.buildRequestWithRoleplaySampling(
                model = modelId,
                messages = listOf(ChatMessageDto(role = "user", content = prompt)),
                baseUrl = baseUrl,
                apiProtocol = apiProtocol,
                promptMode = PromptMode.ROLEPLAY,
            ),
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        )
        val items = parseShopItems(content)
        if (items.isEmpty()) {
            error("$ROLEPLAY_SHOP_OPERATION：模型返回格式不符合要求")
        }
        return items
    }

    private fun parseShopItems(rawContent: String): List<ShopItemDraft> {
        val cleaned = core.stripMarkdownCodeFence(rawContent)
        val root = core.extractStructuredJsonObject(cleaned)
        val array = root?.getAsJsonArrayOrNull("items")
            ?: parseArray(cleaned)
            ?: return emptyList()
        return array.mapNotNull(::parseShopItem)
            .distinctBy { it.name.trim() }
            .take(6)
    }

    private fun parseArray(content: String): JsonArray? {
        val startIndex = content.indexOf('[')
        if (startIndex < 0) return null
        var inString = false
        var escaped = false
        var depth = 0
        for (index in startIndex until content.length) {
            val char = content[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '[' -> depth += 1
                ']' -> {
                    depth -= 1
                    if (depth == 0) {
                        return runCatching {
                            JsonParser.parseString(content.substring(startIndex, index + 1)).asJsonArray
                        }.getOrNull()
                    }
                }
            }
        }
        return null
    }

    private fun parseShopItem(element: JsonElement): ShopItemDraft? {
        val json = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return null
        val name = json.stringValueAny("name", "title").take(40)
        if (name.isBlank()) return null
        val priceCents = json.priceCents()
        if (priceCents <= 0L) return null
        return ShopItemDraft(
            name = name,
            description = json.stringValueAny("description", "summary", "detail").take(240),
            priceCents = priceCents,
            category = json.stringValueAny("category", "type").take(24),
            rarity = json.stringValueAny("rarity", "level").take(24),
            effectPrompt = json.stringValueAny("effect_prompt", "effectPrompt", "effect").take(500),
            imagePrompt = json.stringValueAny("image_prompt", "imagePrompt", "prompt").take(1200),
        )
    }

    private fun JsonObject.priceCents(): Long {
        val raw = stringValueAny("price", "price_label", "priceLabel", "amount")
        parseMoneyToCents(raw)?.let { return it.coerceAtLeast(1L) }
        val cents = get("price_cents")
            ?.takeIf { it.isJsonPrimitive }
            ?.asLongOrNull()
            ?: get("priceCents")
                ?.takeIf { it.isJsonPrimitive }
                ?.asLongOrNull()
        return cents?.coerceAtLeast(1L) ?: 0L
    }
}

private fun JsonObject.stringValueAny(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key ->
        get(key)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }.orEmpty()
}

private fun JsonElement.asLongOrNull(): Long? {
    return runCatching { asLong }.getOrNull()
}
