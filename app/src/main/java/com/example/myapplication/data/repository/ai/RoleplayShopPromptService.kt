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
        val prompt = buildPrompt(
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

    fun fallbackShopItems(
        characterName: String,
        scenarioContext: String,
        imageStyle: EconomyImageStyle,
    ): List<ShopItemDraft> {
        val scene = scenarioContext
            .replace("\r\n", "\n")
            .lineSequence()
            .map(String::trim)
            .firstOrNull(String::isNotBlank)
            .orEmpty()
            .take(40)
        val owner = characterName.trim().ifBlank { "角色" }
        return listOf(
            ShopItemDraft(
                name = "旧票根夹",
                description = "一本能收纳约定、车票和便签的小夹子，边角有轻微磨痕。",
                priceCents = 1_200L,
                category = "纪念",
                rarity = "普通",
                effectPrompt = "可作为回忆触发物，引出一次关于过去约定或错过瞬间的剧情。",
                imagePrompt = buildImagePrompt("a worn ticket holder with folded notes", imageStyle, scene, owner),
            ),
            ShopItemDraft(
                name = "静音钥匙扣",
                description = "一枚不会发出响声的金属钥匙扣，表面刻着只有两个人懂的记号。",
                priceCents = 2_800L,
                category = "随身",
                rarity = "稀有",
                effectPrompt = "可用来开启一次私下见面、借宿、保管钥匙或秘密通行的剧情。",
                imagePrompt = buildImagePrompt("a quiet metal keychain with a small secret mark", imageStyle, scene, owner),
            ),
            ShopItemDraft(
                name = "薄荷糖铁盒",
                description = "小巧的铁盒里装着薄荷糖，也适合藏一张折起来的字条。",
                priceCents = 900L,
                category = "日常",
                rarity = "普通",
                effectPrompt = "可用于缓和尴尬、递东西、藏纸条或制造短暂靠近的机会。",
                imagePrompt = buildImagePrompt("a small mint candy tin with folded paper inside", imageStyle, scene, owner),
            ),
            ShopItemDraft(
                name = "深夜便利店袋",
                description = "装着热饮、创可贴和临时买来的小零食，像一次不太坦率的照顾。",
                priceCents = 3_600L,
                category = "补给",
                rarity = "普通",
                effectPrompt = "可触发照顾、生病、夜归、道歉或别扭关心相关剧情。",
                imagePrompt = buildImagePrompt("a late-night convenience store bag with warm drink and bandages", imageStyle, scene, owner),
            ),
            ShopItemDraft(
                name = "未寄出的明信片",
                description = "背面写了一半又划掉的明信片，照片角落被手指捏得发白。",
                priceCents = 1_800L,
                category = "线索",
                rarity = "稀有",
                effectPrompt = "可揭开一次没说出口的话、旧地点、旅行计划或隐藏心事。",
                imagePrompt = buildImagePrompt("an unsent postcard with crossed-out handwriting", imageStyle, scene, owner),
            ),
            ShopItemDraft(
                name = "备用围巾",
                description = "柔软但不新的围巾，带着淡淡洗衣液气味，像被临时塞进包里的备用物。",
                priceCents = 4_200L,
                category = "服饰",
                rarity = "珍贵",
                effectPrompt = "可用于降温、送还、借用衣物、靠近或留下气味记忆的剧情。",
                imagePrompt = buildImagePrompt("a soft spare scarf with subtle used texture", imageStyle, scene, owner),
            ),
        )
    }

    private fun buildPrompt(
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
        appendLine("请根据角色人设、用户人设、近期剧情、记忆和钱包状态，生成 6 个可购买商品/道具。")
        appendLine("这些商品必须能真实作用到后续剧情里，像可被赠送、使用、触发事件或推进关系的道具。")
        appendLine()
        appendLine("【角色】${characterName.trim().ifBlank { "角色" }}")
        appendLine("【用户】${userName.trim().ifBlank { "用户" }}")
        if (characterPersona.isNotBlank()) {
            appendLine("【角色人设】")
            appendLine(characterPersona.take(1800))
        }
        if (userPersona.isNotBlank()) {
            appendLine("【用户人设】")
            appendLine(userPersona.take(900))
        }
        if (scenarioContext.isNotBlank()) {
            appendLine("【当前剧情/场景】")
            appendLine(scenarioContext.take(1200))
        }
        if (conversationExcerpt.isNotBlank()) {
            appendLine("【近期互动】")
            appendLine(conversationExcerpt.take(1400))
        }
        if (memoryContext.isNotBlank()) {
            appendLine("【记忆线索】")
            appendLine(memoryContext.take(1000))
        }
        if (economyContext.isNotBlank()) {
            appendLine("【钱包与库存状态】")
            appendLine(economyContext.take(900))
        }
        appendLine("【图片风格】${imageStyle.displayName}；风格提示：${imageStyle.promptHint}")
        appendLine()
        appendLine("【生成要求】")
        appendLine("1. 价格必须符合沉浸感，单位为人民币元，可用小数；不要全都很贵，也不要全都很便宜。")
        appendLine("2. 每个商品要有明确剧情用途，effect_prompt 写给后续 AI 使用，不要写成系统命令。")
        appendLine("3. 商品应贴合当前角色和用户关系，不要泛泛生成现代商城常见商品。")
        appendLine("4. image_prompt 用英文，描述单个商品主体、材质、构图、光影和氛围；不要人物正脸、文字、水印、logo、UI。")
        appendLine("5. 严格输出 JSON 对象，不要 Markdown，不要解释。格式：")
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

    private fun buildImagePrompt(
        subject: String,
        imageStyle: EconomyImageStyle,
        scene: String,
        owner: String,
    ): String {
        return listOf(
            imageStyle.promptHint,
            subject,
            "single prop object, refined materials, clean background, soft diffuse lighting, depth of field",
            scene.takeIf(String::isNotBlank)?.let { "story context: $it" }.orEmpty(),
            "inspired by $owner's personal belongings",
        ).filter(String::isNotBlank).joinToString(separator = ", ")
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
