package com.example.myapplication.data.repository.ai

import com.example.myapplication.conversation.PhoneGenerationContext
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.PhoneGalleryEntry
import com.example.myapplication.model.PhoneMessageItem
import com.example.myapplication.model.PhoneMessageThread
import com.example.myapplication.model.PhoneNoteEntry
import com.example.myapplication.model.PhoneRelationshipHighlight
import com.example.myapplication.model.PhoneSearchDetail
import com.example.myapplication.model.PhoneSearchEntry
import com.example.myapplication.model.PhoneShoppingEntry
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PhoneSnapshotSection
import com.example.myapplication.model.PhoneSnapshotSections
import com.example.myapplication.model.PhoneSocialComment
import com.example.myapplication.model.PhoneSocialPost
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 负责"查手机玩法"相关的三种内容生成：
 * - [generatePhoneSnapshotSections]：按指定板块重建手机快照（联系人 / 消息 / 备忘录 / 相册 / 购物 / 搜索 / 动态）。
 * - [generatePhoneSearchDetail]：生成搜索结果点开后的详情页。
 * - [generateSocialCommentReplies]：动态评论区的 NPC 自然回复。
 *
 * T6.7 从 DefaultAiPromptExtrasService 抽离。全部使用 roleplay 采样 + 回退；
 * 所有 Phone 专属 prompt 组装 helper 与 [parsePhoneSnapshotSections] 响应解析都内聚于此。
 */
internal class PhoneContentPromptService(
    private val core: PromptExtrasCore,
) {
    suspend fun generatePhoneSnapshotSections(
        context: PhoneGenerationContext,
        requestedSections: Set<PhoneSnapshotSection>,
        existingSnapshot: PhoneSnapshot?,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): PhoneSnapshotSections {
        val normalizedSections = requestedSections.ifEmpty { PhoneSnapshotSection.entries.toSet() }
        val prompt = buildString {
            append("你是“查手机玩法”的内容生成器。")
            append(phoneSnapshotOwnerInstruction(context))
            append("所有内容必须基于给定的人设、记忆、关系、剧情和最近对话自然推导，可以适度扩展，但不能脱离上下文乱编。")
            append(phoneSnapshotAuthenticityInstruction(context))
            append("手机主人：").append(context.ownerName).append("。")
            append("查看者：").append(context.viewerName).append("。")
            append("关系方向：").append(context.relationshipDirection).append("。")
            if (context.timeGapContext.isNotBlank()) {
                append("\n时间间隔：").append(context.timeGapContext)
            }
            append("\n本次只重建这些板块：")
            append(normalizedSections.joinToString("、") { it.displayName })
            append("。")
            append("\n如果重建“消息”板块，必须同时输出 relationship_highlights 和 message_threads。")
            append("\n严格输出 JSON 对象，不要 Markdown，不要解释。")
            append("""JSON 键固定为：relationship_highlights、message_threads、notes、gallery、shopping_records、search_history、social_posts。""")
            append("\n每个数组项都必须包含 id 字段。")
            append("\n字段要求：")
            append("\n1. relationship_highlights: [{id,name,relation_label,stance,note}]")
            append("\n2. message_threads: [{id,contact_name,relation_label,preview,time_label,avatar_label,messages:[{id,sender_name,text,time_label,is_owner}]}]")
            append("\n3. notes: [{id,title,summary,content,time_label,icon}]")
            append("\n4. gallery: [{id,title,summary,description,time_label}]")
            append("\n5. shopping_records: [{id,title,status,price_label,note,detail,time_label}]")
            append("\n6. search_history: [{id,query,time_label}]")
            append("\n7. social_posts: [{id,author_name,author_label,content,time_label,like_count,liked_by_names:[...],comments:[{id,author_name,text}]}]")
            append("\n其中相册需要额外遵守这些规则：")
            append("\n- title 要像手机相册里真正会起的照片名，短、具体、带对象或场景。")
            append("\n- summary 是一句简短的画面摘要，突出镜头主体、场景或拍摄瞬间，控制在 12 到 30 字。")
            append("\n- description 不是重复 summary，而是要写成“照片里具体拍到了什么 + 为什么拍下这张照片/这张照片背后的故事或情绪”。")
            append("\n- description 默认使用第一人称，更像手机主人回看照片时对这张图的私人解释。")
            append("\n- description 要有明确画面细节，例如姿势、构图、光线、衣着、地点、视角、当时的小动作。")
            append("\n- description 允许带轻微私密感和暧昧感，但必须仍然像相册说明，不要写成露骨对白或纯欲望宣泄。")
            append("\n- 优先生成值得收藏、带明显关系痕迹或个人执念的照片，不要生成泛泛的风景照。")
            append("\n- messages[].is_owner 表示该消息是否由手机主人本人发出。")
            append("\n其中动态（social_posts）需要额外遵守这些规则：")
            append("\n- 动态像微信朋友圈或 Instagram 帖文，包含发布者、正文、点赞和评论。")
            append("\n- author_name 是发布者昵称，author_label 是身份标签（闺蜜、同事、手机主人等），手机主人自己也可以发动态。")
            append("\n- content 是动态正文，应像朋友圈真实文案：生活碎片、情感感悟、日常记录、暧昧暗示等，避免过于书面或模板化。")
            append("\n- like_count 是点赞总数，liked_by_names 是具体点赞人名列表，评论人可以和点赞人部分重叠。")
            append("\n- comments 是评论列表，每条包含评论人和正文，评论要自然口语化，像真实社交互动。")
            append("\n- 优先生成带关系痕迹、情绪暗示或戏剧性的动态，不要生成完全无关的鸡汤或广告。")
            append("\n- 动态数量建议 3-5 条。")
            append(phoneSnapshotOwnerRules(context))
            append("\n除非本次重建该板块，否则对应键返回 []。")
            if (context.systemContext.isNotBlank()) {
                append("\n\n【基础设定】\n")
                append(context.systemContext)
            }
            if (context.scenarioContext.isNotBlank()) {
                append("\n\n【场景补充】\n")
                append(context.scenarioContext)
            }
            if (context.conversationExcerpt.isNotBlank()) {
                append("\n\n【最近上下文】\n")
                append(context.conversationExcerpt)
            }
            existingSnapshot?.takeIf { it.hasContent() }?.let { snapshot ->
                append("\n\n【当前已存在的手机内容，仅供保持一致】\n")
                append(buildPhoneSnapshotReference(snapshot, excludeSections = normalizedSections))
            }
        }
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "手机内容生成失败",
            request = core.buildRequestWithRoleplaySampling(
                model = modelId,
                messages = listOf(ChatMessageDto(role = "user", content = prompt)),
                baseUrl = baseUrl,
                apiProtocol = apiProtocol,
                promptMode = context.promptMode,
            ),
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        ).trim()
        val parsedJson = core.parseRequiredStructuredJsonObject(
            content = content,
            operation = "手机内容生成失败",
        )
        return parsePhoneSnapshotSections(parsedJson)
    }

    suspend fun generatePhoneSearchDetail(
        context: PhoneGenerationContext,
        query: String,
        relatedContext: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): PhoneSearchDetail {
        val prompt = buildString {
            append("你是“查手机玩法”的搜索详情生成器。")
            append(phoneSearchDetailOwnerInstruction(context))
            append("详情必须像搜索结果页、百科摘要、帖子整理或经验总结，不要写成第一人称自白。")
            append("手机主人：").append(context.ownerName).append("。")
            append("查看者：").append(context.viewerName).append("。")
            append("\n搜索词：").append(query.trim())
            append("\n严格输出 JSON 对象：{")
            append("\"title\":\"...\",\"summary\":\"...\",\"content\":\"...\"}")
            append("。不要输出额外解释。")
            if (context.systemContext.isNotBlank()) {
                append("\n\n【基础设定】\n")
                append(context.systemContext)
            }
            if (context.scenarioContext.isNotBlank()) {
                append("\n\n【场景补充】\n")
                append(context.scenarioContext)
            }
            if (context.conversationExcerpt.isNotBlank()) {
                append("\n\n【最近上下文】\n")
                append(context.conversationExcerpt)
            }
            if (relatedContext.isNotBlank()) {
                append("\n\n【与该搜索词直接相关的手机线索】\n")
                append(relatedContext)
            }
        }
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "搜索详情生成失败",
            request = core.buildRequestWithRoleplaySampling(
                model = modelId,
                messages = listOf(ChatMessageDto(role = "user", content = prompt)),
                baseUrl = baseUrl,
                apiProtocol = apiProtocol,
                promptMode = context.promptMode,
            ),
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        ).trim()
        val parsedJson = core.parseRequiredStructuredJsonObject(
            content = content,
            operation = "搜索详情生成失败",
        )
        return PhoneSearchDetail(
            title = parsedJson.stringValue("title")
                .ifBlank { query.trim() },
            summary = parsedJson.stringValue("summary"),
            content = parsedJson.stringValue("content"),
        )
    }

    suspend fun generateSocialCommentReplies(
        context: PhoneGenerationContext,
        postAuthorName: String,
        postAuthorLabel: String,
        postContent: String,
        existingComments: String,
        userComment: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<Pair<String, String>> {
        val prompt = buildString {
            appendLine("你正在模拟一个社交动态的评论区互动。")
            appendLine("以下是本次扮演的完整人设和场景背景，所有角色的评论回复必须严格遵守各自人设，不得 OOC（即不得偏离角色性格、口吻和关系定位）。")
            appendLine()
            // 注入人设和场景
            if (context.systemContext.isNotBlank()) {
                appendLine("【角色人设与基础设定】")
                appendLine(context.systemContext)
                appendLine()
            }
            if (context.scenarioContext.isNotBlank()) {
                appendLine("【当前场景】")
                appendLine(context.scenarioContext)
                appendLine()
            }
            if (context.conversationExcerpt.isNotBlank()) {
                appendLine("【最近剧情上下文】")
                appendLine(context.conversationExcerpt)
                appendLine()
            }
            appendLine("【动态信息】")
            appendLine("发布者：$postAuthorName（$postAuthorLabel）")
            appendLine("正文：$postContent")
            if (existingComments.isNotBlank()) {
                appendLine()
                appendLine("【已有评论】")
                appendLine(existingComments)
            }
            appendLine()
            appendLine("【${context.viewerName} 刚发表的新评论】")
            appendLine("${context.viewerName}：$userComment")
            appendLine()
            appendLine("现在请生成 1-3 条其他人对这条评论的自然回复，候选包括：")
            appendLine("- 动态发布者 $postAuthorName 本人的回应")
            appendLine("- 手机主人 ${context.ownerName} 的反应")
            appendLine("- 其他 NPC/朋友的互动（吃醋、帮腔、打趣、隐晦暗示等）")
            appendLine()
            appendLine("【强制约束 - 必须严格遵守】")
            appendLine("1. 每个角色的评论必须符合其在人设中的性格、说话习惯和对 ${context.viewerName} 的关系定位")
            appendLine("2. 禁止 OOC：不得让角色说出与其人设矛盾的话，不得让性格冷淡的角色突然热情，不得让不认识的角色冒泡")
            appendLine("3. 评论要口语化、简短、有个性，像真实朋友圈互动，而非书面语或旁白")
            appendLine("4. 允许角色之间争风吃醋、暗中较劲、含蓄表达情绪，但须符合人设")
            appendLine("5. 严格仅输出 JSON 数组，不要输出任何额外说明、标题或 Markdown")
            appendLine("格式：[{\"author_name\":\"xxx\",\"text\":\"xxx\"}]")
        }
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "评论回复生成失败",
            request = core.buildRequestWithRoleplaySampling(
                model = modelId,
                messages = listOf(
                    ChatMessageDto(role = "user", content = prompt),
                ),
                baseUrl = baseUrl,
                apiProtocol = apiProtocol,
                promptMode = context.promptMode,
            ),
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        )
        return runCatching {
            val cleaned = content.trim()
                .removePrefix("```json").removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```").trim()
            val parsed = JsonParser.parseString(cleaned)
            // 兼容直接数组 [...] 和包裹对象 {"replies": [...]} 两种格式
            val array = if (parsed.isJsonArray) {
                parsed.asJsonArray
            } else if (parsed.isJsonObject) {
                val obj = parsed.asJsonObject
                obj.get("replies")
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?: obj.entrySet()
                        .map { it.value }
                        .singleOrNull { it.isJsonArray }
                        ?.asJsonArray
                    ?: return@runCatching emptyList()
            } else {
                return@runCatching emptyList()
            }
            array.mapNotNull { element ->
                val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val authorName = obj.stringValue("author_name")
                val text = obj.stringValue("text")
                if (authorName.isNotBlank() && text.isNotBlank()) authorName to text else null
            }
        }.getOrDefault(emptyList())
    }

    private fun buildPhoneSnapshotReference(
        snapshot: PhoneSnapshot,
        excludeSections: Set<PhoneSnapshotSection>,
    ): String {
        return buildString {
            if (PhoneSnapshotSection.MESSAGES !in excludeSections) {
                if (snapshot.relationshipHighlights.isNotEmpty()) {
                    appendLine("关系速览：")
                    snapshot.relationshipHighlights.take(4).forEach { item ->
                        append("- ")
                        append(item.name)
                        if (item.relationLabel.isNotBlank()) {
                            append("（")
                            append(item.relationLabel)
                            append("）")
                        }
                        if (item.note.isNotBlank()) {
                            append("：")
                            append(item.note)
                        }
                        appendLine()
                    }
                }
                if (snapshot.messageThreads.isNotEmpty()) {
                    appendLine("消息概览：")
                    snapshot.messageThreads.take(4).forEach { thread ->
                        append("- ")
                        append(thread.contactName)
                        append("：")
                        append(thread.preview)
                        appendLine()
                    }
                }
            }
            if (PhoneSnapshotSection.NOTES !in excludeSections && snapshot.notes.isNotEmpty()) {
                appendLine("备忘录概览：")
                snapshot.notes.take(4).forEach { note ->
                    append("- ")
                    append(note.title)
                    append("：")
                    append(note.summary)
                    appendLine()
                }
            }
            if (PhoneSnapshotSection.GALLERY !in excludeSections && snapshot.gallery.isNotEmpty()) {
                appendLine("相册概览：")
                snapshot.gallery.take(4).forEach { item ->
                    append("- ")
                    append(item.title)
                    append("：")
                    append(item.summary)
                    appendLine()
                }
            }
            if (PhoneSnapshotSection.SHOPPING !in excludeSections && snapshot.shoppingRecords.isNotEmpty()) {
                appendLine("购物概览：")
                snapshot.shoppingRecords.take(4).forEach { item ->
                    append("- ")
                    append(item.title)
                    append("：")
                    append(item.note)
                    appendLine()
                }
            }
            if (PhoneSnapshotSection.SEARCH !in excludeSections && snapshot.searchHistory.isNotEmpty()) {
                appendLine("搜索概览：")
                snapshot.searchHistory.take(6).forEach { item ->
                    append("- ")
                    append(item.query)
                    appendLine()
                }
            }
            if (PhoneSnapshotSection.SOCIAL_POSTS !in excludeSections && snapshot.socialPosts.isNotEmpty()) {
                appendLine("动态概览：")
                snapshot.socialPosts.take(4).forEach { post ->
                    append("- ")
                    append(post.authorName)
                    append("：")
                    append(post.content.take(40))
                    appendLine()
                }
            }
        }.trim()
    }

    private fun phoneSnapshotOwnerInstruction(
        context: PhoneGenerationContext,
    ): String {
        return when (context.ownerType) {
            PhoneSnapshotOwnerType.CHARACTER ->
                "现在要为一个虚构角色生成他的手机内容快照。"
            PhoneSnapshotOwnerType.USER ->
                "现在要为当前用户生成他的手机内容快照，这些内容会被角色翻看到，用来触发后续反应。"
        }
    }

    private fun phoneSnapshotAuthenticityInstruction(
        context: PhoneGenerationContext,
    ): String {
        return when (context.ownerType) {
            PhoneSnapshotOwnerType.CHARACTER ->
                "输出必须像已经真实存在于这个角色手机里的内容，而不是总结报告。"
            PhoneSnapshotOwnerType.USER ->
                "输出必须像已经真实存在于用户手机里的内容，而不是总结报告；可以优先挑选最能触发角色反应的线索，但不能写成角色自己的手机内容。"
        }
    }

    private fun phoneSnapshotOwnerRules(
        context: PhoneGenerationContext,
    ): String {
        return when (context.ownerType) {
            PhoneSnapshotOwnerType.CHARACTER -> ""
            PhoneSnapshotOwnerType.USER -> buildString {
                append("\n- 当手机主人是用户时，关系速览、消息、备忘录、相册、购物和搜索都必须从用户侧出发。")
                append("\n- 可以让角色本人、朋友、家人、同事成为用户的联系人，但不要把主体写成角色自己的社交圈和日常手机。")
                append("\n- 如果出现角色本人，也只能作为“用户手机里与角色有关的线索”出现。")
                append("\n- 可以增强戏剧触发点，但必须先保证这些内容像用户真的会留下的痕迹。")
            }
        }
    }

    private fun phoneSearchDetailOwnerInstruction(
        context: PhoneGenerationContext,
    ): String {
        return when (context.ownerType) {
            PhoneSnapshotOwnerType.CHARACTER ->
                "请为角色手机里的一个搜索词生成点开后的详情内容。"
            PhoneSnapshotOwnerType.USER ->
                "请为用户手机里的一个搜索词生成点开后的详情内容；搜索方向可以优先保留最能触发角色反应的线索，但主体必须仍是用户自己会搜的内容。"
        }
    }

    private fun parsePhoneSnapshotSections(
        jsonObject: JsonObject,
    ): PhoneSnapshotSections {
        return PhoneSnapshotSections(
            relationshipHighlights = jsonObject.getAsJsonArrayOrNull("relationship_highlights")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneRelationshipHighlight(
                        id = item.stringValue("id").ifBlank { "relationship-${index + 1}" },
                        name = item.stringValue("name"),
                        relationLabel = item.stringValue("relation_label"),
                        stance = item.stringValue("stance"),
                        note = item.stringValue("note"),
                    )
                }
                ?.filter { it.name.isNotBlank() },
            messageThreads = jsonObject.getAsJsonArrayOrNull("message_threads")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneMessageThread(
                        id = item.stringValue("id").ifBlank { "thread-${index + 1}" },
                        contactName = item.stringValue("contact_name"),
                        relationLabel = item.stringValue("relation_label"),
                        preview = item.stringValue("preview"),
                        timeLabel = item.stringValue("time_label"),
                        avatarLabel = item.stringValue("avatar_label"),
                        messages = item?.getAsJsonArrayOrNull("messages")
                            ?.mapIndexed { msgIndex, msgElement ->
                                val messageItem = msgElement.asJsonObjectOrNull()
                                PhoneMessageItem(
                                    id = messageItem.stringValue("id").ifBlank { "message-${index + 1}-${msgIndex + 1}" },
                                    senderName = messageItem.stringValue("sender_name"),
                                    text = messageItem.stringValue("text"),
                                    timeLabel = messageItem.stringValue("time_label"),
                                    isOwner = messageItem.booleanValue("is_owner"),
                                )
                            }
                            ?.filter { it.text.isNotBlank() }
                            .orEmpty(),
                    )
                }
                ?.filter { it.contactName.isNotBlank() },
            notes = jsonObject.getAsJsonArrayOrNull("notes")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneNoteEntry(
                        id = item.stringValue("id").ifBlank { "note-${index + 1}" },
                        title = item.stringValue("title"),
                        summary = item.stringValue("summary"),
                        content = item.stringValue("content"),
                        timeLabel = item.stringValue("time_label"),
                        icon = item.stringValue("icon"),
                    )
                }
                ?.filter { it.title.isNotBlank() },
            gallery = jsonObject.getAsJsonArrayOrNull("gallery")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneGalleryEntry(
                        id = item.stringValue("id").ifBlank { "gallery-${index + 1}" },
                        title = item.stringValue("title"),
                        summary = item.stringValue("summary"),
                        description = item.stringValue("description"),
                        timeLabel = item.stringValue("time_label"),
                    )
                }
                ?.filter { it.title.isNotBlank() },
            shoppingRecords = jsonObject.getAsJsonArrayOrNull("shopping_records")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneShoppingEntry(
                        id = item.stringValue("id").ifBlank { "shopping-${index + 1}" },
                        title = item.stringValue("title"),
                        status = item.stringValue("status"),
                        priceLabel = item.stringValue("price_label"),
                        note = item.stringValue("note"),
                        detail = item.stringValue("detail"),
                        timeLabel = item.stringValue("time_label"),
                    )
                }
                ?.filter { it.title.isNotBlank() },
            searchHistory = jsonObject.getAsJsonArrayOrNull("search_history")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneSearchEntry(
                        id = item.stringValue("id").ifBlank { "search-${index + 1}" },
                        query = item.stringValue("query"),
                        timeLabel = item.stringValue("time_label"),
                    )
                }
                ?.filter { it.query.isNotBlank() },
            socialPosts = jsonObject.getAsJsonArrayOrNull("social_posts")
                ?.mapIndexed { index, element ->
                    val item = element.asJsonObjectOrNull()
                    PhoneSocialPost(
                        id = item.stringValue("id").ifBlank { "social-${index + 1}" },
                        authorName = item.stringValue("author_name"),
                        authorLabel = item.stringValue("author_label"),
                        content = item.stringValue("content"),
                        timeLabel = item.stringValue("time_label"),
                        likeCount = item?.get("like_count")
                            ?.takeIf { !it.isJsonNull }
                            ?.runCatching { asInt }?.getOrNull() ?: 0,
                        likedByNames = item?.getAsJsonArrayOrNull("liked_by_names")
                            ?.mapNotNull { nameElement ->
                                runCatching { nameElement.asString.trim() }.getOrNull()
                                    ?.takeIf { it.isNotEmpty() }
                            }
                            .orEmpty(),
                        comments = item?.getAsJsonArrayOrNull("comments")
                            ?.mapIndexed { commentIndex, commentElement ->
                                val commentItem = commentElement.asJsonObjectOrNull()
                                PhoneSocialComment(
                                    id = commentItem.stringValue("id").ifBlank { "comment-${index + 1}-${commentIndex + 1}" },
                                    authorName = commentItem.stringValue("author_name"),
                                    text = commentItem.stringValue("text"),
                                )
                            }
                            ?.filter { it.text.isNotBlank() }
                            .orEmpty(),
                    )
                }
                ?.filter { it.authorName.isNotBlank() && it.content.isNotBlank() },
        )
    }
}
