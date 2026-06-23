package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.context.MemoryScopeSupport
import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.economy.DEFAULT_USER_OWNER_ID
import com.example.myapplication.data.repository.economy.RoleplayEconomyRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.EconomyFailureReason
import com.example.myapplication.model.EconomyImageStyle
import com.example.myapplication.model.EconomyOperationResult
import com.example.myapplication.model.EconomyOwnerType
import com.example.myapplication.model.GiftImageStatus
import com.example.myapplication.model.ImagePromptPolishRequest
import com.example.myapplication.model.ImagePromptPolishResult
import com.example.myapplication.model.ImagePromptPurpose
import com.example.myapplication.model.InventoryItem
import com.example.myapplication.model.InventoryItemStatus
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayEconomyState
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.ShopItem
import com.example.myapplication.model.ShopItemDraft
import com.example.myapplication.model.ShopItemStatus
import com.example.myapplication.model.fallbackPolishResult
import com.example.myapplication.model.formatMoneyLabel
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.roleplay.RoleplayConversationSupport
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class RoleplayWalletUiState(
    val scenarioId: String = "",
    val scenario: RoleplayScenario? = null,
    val session: RoleplaySession? = null,
    val economyState: RoleplayEconomyState = RoleplayEconomyState(),
    val isBootstrapping: Boolean = true,
    val isGeneratingShop: Boolean = false,
    val generatingImageItemIds: Set<String> = emptySet(),
    val noticeMessage: String? = null,
    val errorMessage: String? = null,
) {
    val userInventory: List<InventoryItem>
        get() = economyState.inventoryItems.filter { item ->
            item.ownerType == EconomyOwnerType.USER &&
                item.ownerId == DEFAULT_USER_OWNER_ID
        }
}

class RoleplayWalletViewModel(
    private val scenarioId: String,
    private val roleplayRepository: RoleplayRepository,
    private val economyRepository: RoleplayEconomyRepository,
    private val settingsRepository: AiSettingsRepository,
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val aiGateway: AiGateway,
    private val memoryRepository: MemoryRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val imageSaver: suspend (String) -> SavedImageFile,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoleplayWalletUiState(scenarioId = scenarioId))
    val uiState: StateFlow<RoleplayWalletUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            roleplayRepository.observeScenario(scenarioId).collect { scenario ->
                _uiState.update { it.copy(scenario = scenario) }
            }
        }
        viewModelScope.launch {
            roleplayRepository.observeSessionByScenario(scenarioId).collect { session ->
                _uiState.update { it.copy(session = session) }
            }
        }
        viewModelScope.launch {
            economyRepository.observeState(scenarioId).collect { economyState ->
                _uiState.update { it.copy(economyState = economyState) }
            }
        }
        viewModelScope.launch {
            bootstrapAccounts()
        }
    }

    fun clearNoticeMessage() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun addPocketMoney(amountCents: Long = 10_000L) {
        val account = _uiState.value.economyState.userAccount ?: return fail("钱包还在初始化")
        viewModelScope.launch {
            when (
                val result = economyRepository.adjustBalance(
                    accountId = account.id,
                    newBalanceCents = account.balanceCents + amountCents.coerceAtLeast(1L),
                    note = "补一点零花",
                )
            ) {
                is EconomyOperationResult.Success -> notice("已经放进钱包：${amountCents.formatMoneyLabel()}")
                is EconomyOperationResult.Failure -> fail(result.message.ifBlank { "这次没补上，稍后再试" })
            }
        }
    }

    fun generateShop(style: EconomyImageStyle) {
        if (_uiState.value.isGeneratingShop) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingShop = true,
                    noticeMessage = null,
                    errorMessage = null,
                )
            }
            runCatching {
                val context = buildShopGenerationContext(style)
                val drafts = generateShopDrafts(context)
                val items = economyRepository.replaceShopBatch(
                    scenarioId = scenarioId,
                    conversationId = context.session.conversationId,
                    style = style,
                    promptContext = context.promptContext,
                    drafts = drafts,
                )
                if (style == EconomyImageStyle.NONE) {
                    notice("商店已经换新")
                } else {
                    generateImagesForItems(
                        items = items,
                        context = context,
                    )
                    notice("商店已经换新，失败图片可单独重试")
                }
            }.onFailure { throwable ->
                fail(buildFriendlyError(throwable, "商店生成失败"))
            }
            _uiState.update { it.copy(isGeneratingShop = false) }
        }
    }

    fun retryFailedImage(itemId: String) {
        val item = _uiState.value.economyState.shopItems.firstOrNull { it.id == itemId }
            ?: return fail("商品不见了")
        if (item.imageStatus != GiftImageStatus.FAILED) {
            notice("这张图现在不需要重试")
            return
        }
        viewModelScope.launch {
            runCatching {
                val context = buildShopGenerationContext(item.imageStyle)
                val generating = economyRepository.updateShopItemImage(
                    itemId = item.id,
                    status = GiftImageStatus.GENERATING,
                    errorMessage = "",
                ) ?: item.copy(imageStatus = GiftImageStatus.GENERATING)
                generateImagesForItems(
                    items = listOf(generating),
                    context = context,
                )
                notice("已重试这张商品图")
            }.onFailure { throwable ->
                fail(buildFriendlyError(throwable, "图片重试失败"))
            }
        }
    }

    fun purchaseItem(itemId: String) {
        viewModelScope.launch {
            when (val result = economyRepository.purchaseItem(scenarioId = scenarioId, itemId = itemId)) {
                is EconomyOperationResult.Success -> notice("${result.value.name} 已放入库存")
                is EconomyOperationResult.Failure -> fail(purchaseFailureMessage(result.reason))
            }
        }
    }

    fun markGifted(inventoryItemId: String) {
        updateInventory(
            inventoryItemId = inventoryItemId,
            successMessage = { "${it.name} 已送出" },
            update = economyRepository::markInventoryGifted,
        )
    }

    fun markUsed(inventoryItemId: String) {
        updateInventory(
            inventoryItemId = inventoryItemId,
            successMessage = { "${it.name} 已使用" },
            update = economyRepository::markInventoryUsed,
        )
    }

    private fun updateInventory(
        inventoryItemId: String,
        successMessage: (InventoryItem) -> String,
        update: suspend (String) -> EconomyOperationResult<InventoryItem>,
    ) {
        viewModelScope.launch {
            when (val result = update(inventoryItemId)) {
                is EconomyOperationResult.Success -> notice(successMessage(result.value))
                is EconomyOperationResult.Failure -> fail(result.message.ifBlank { "道具状态暂时没改成" })
            }
        }
    }

    private suspend fun bootstrapAccounts() {
        runCatching {
            val scenario = roleplayRepository.getScenario(scenarioId) ?: return@runCatching
            val session = roleplayRepository.getSessionByScenario(scenarioId)
                ?: roleplayRepository.startScenario(scenarioId).session
            val settings = settingsRepository.settingsFlow.first()
            val assistant = resolveAssistant(settings, scenario)
            val userPersona = RoleplayConversationSupport.resolveUserPersona(scenario, settings)
            economyRepository.ensureDefaultAccounts(
                scenarioId = scenarioId,
                conversationId = session.conversationId,
                userName = userPersona.displayName.ifBlank { "我" },
                characterName = resolveCharacterName(scenario, assistant),
                characterInitialBalanceCents = assistant?.initialWalletBalanceCents ?: 0L,
            )
        }.onFailure { throwable ->
            fail(buildFriendlyError(throwable, "钱包初始化失败"))
        }
        _uiState.update { it.copy(isBootstrapping = false) }
    }

    private suspend fun buildShopGenerationContext(style: EconomyImageStyle): ShopGenerationContext {
        val scenario = roleplayRepository.getScenario(scenarioId)
            ?: error("当前场景不存在")
        val session = roleplayRepository.getSessionByScenario(scenarioId)
            ?: roleplayRepository.startScenario(scenarioId).session
        val settings = settingsRepository.settingsFlow.first()
        val assistant = resolveAssistant(settings, scenario)
        val userPersona = RoleplayConversationSupport.resolveUserPersona(scenario, settings)
        val messages = roleplayRepository.observeConversationMessages(scenarioId).first()
        val conversationExcerpt = buildConversationExcerpt(messages)
        val summary = conversationSummaryRepository.getSummary(session.conversationId)?.summary.orEmpty()
        val memoryContext = buildMemoryContext(
            assistant = assistant,
            conversationId = session.conversationId,
        )
        val economyContext = economyRepository.buildPromptContext(scenarioId)
        val scenarioContext = buildScenarioContext(scenario, assistant, summary)
        val characterName = resolveCharacterName(scenario, assistant)
        val userName = userPersona.displayName.ifBlank { "用户" }
        return ShopGenerationContext(
            scenario = scenario,
            session = session,
            settings = settings,
            assistant = assistant,
            characterName = characterName,
            userName = userName,
            userPersona = userPersona.personaPrompt,
            characterPersona = buildCharacterPersona(assistant),
            scenarioContext = scenarioContext,
            conversationExcerpt = conversationExcerpt,
            memoryContext = memoryContext,
            economyContext = economyContext,
            imageStyle = style,
            promptContext = listOf(
                "角色：$characterName",
                "用户：$userName",
                scenarioContext.takeIf(String::isNotBlank)?.let { "当前剧情/场景：\n$it" }.orEmpty(),
                conversationExcerpt.takeIf(String::isNotBlank)?.let { "近期互动：\n$it" }.orEmpty(),
                memoryContext.takeIf(String::isNotBlank)?.let { "记忆线索：\n$it" }.orEmpty(),
                economyContext.takeIf(String::isNotBlank)?.let { "钱包与库存状态：\n$it" }.orEmpty(),
            ).filter(String::isNotBlank).joinToString(separator = "\n").take(4000),
        )
    }

    private suspend fun generateShopDrafts(context: ShopGenerationContext): List<ShopItemDraft> {
        val provider = context.settings.resolveFunctionProvider(ProviderFunction.CHAT)
            ?: context.settings.activeProvider()
        val modelId = provider?.resolveFunctionModel(ProviderFunction.CHAT)
            ?.ifBlank { provider.selectedModel }
            ?.trim()
            .orEmpty()
        val aiDrafts = if (provider != null && modelId.isNotBlank()) {
            withTimeout(ShopPromptTimeoutMs) {
                aiPromptExtrasService.generateRoleplayShopItems(
                    characterName = context.characterName,
                    userName = context.userName,
                    characterPersona = context.characterPersona,
                    userPersona = context.userPersona,
                    scenarioContext = context.scenarioContext,
                    conversationExcerpt = context.conversationExcerpt,
                    memoryContext = context.memoryContext,
                    economyContext = context.economyContext,
                    imageStyle = context.imageStyle,
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    modelId = modelId,
                    apiProtocol = provider.resolvedApiProtocol(),
                    provider = provider,
                )
            }
        } else {
            emptyList()
        }
        return aiDrafts.ifEmpty { fallbackShopDrafts(context) }
    }

    private suspend fun generateImagesForItems(
        items: List<ShopItem>,
        context: ShopGenerationContext,
    ) {
        val candidates = items.filter { item ->
            item.imageStyle != EconomyImageStyle.NONE &&
                item.status == ShopItemStatus.AVAILABLE &&
                item.imageStatus != GiftImageStatus.SUCCEEDED
        }
        if (candidates.isEmpty()) return
        val imageProvider = context.settings.resolveFunctionProvider(ProviderFunction.GIFT_IMAGE)
        val imageModelId = context.settings.resolveFunctionModel(ProviderFunction.GIFT_IMAGE).trim()
        if (imageProvider == null || imageModelId.isBlank()) {
            candidates.forEach { item ->
                economyRepository.updateShopItemImage(
                    itemId = item.id,
                    status = GiftImageStatus.FAILED,
                    errorMessage = "默认生图模型未配置",
                )
            }
            return
        }
        candidates.forEach { item ->
            _uiState.update { it.copy(generatingImageItemIds = it.generatingImageItemIds + item.id) }
            economyRepository.updateShopItemImage(
                itemId = item.id,
                status = GiftImageStatus.GENERATING,
                errorMessage = "",
            )
            runCatching {
                val polished = polishShopImagePrompt(
                    item = item,
                    context = context,
                    imageProvider = imageProvider,
                )
                val image = withTimeout(ImageTimeoutMs) {
                    aiGateway.generateImageWithProvider(
                        prompt = polished.finalPrompt(),
                        provider = imageProvider,
                        modelId = imageModelId,
                    ).firstOrNull() ?: error("生图接口未返回图片")
                }
                val saved = persistImage(image, item.id)
                economyRepository.updateShopItemImage(
                    itemId = item.id,
                    status = GiftImageStatus.SUCCEEDED,
                    imageUri = saved.path,
                    mimeType = saved.mimeType,
                    fileName = saved.fileName,
                    prompt = polished.visualPrompt,
                    negativePrompt = polished.negativePrompt,
                )
            }.onFailure { throwable ->
                economyRepository.updateShopItemImage(
                    itemId = item.id,
                    status = GiftImageStatus.FAILED,
                    errorMessage = buildFriendlyError(throwable, "商品图生成失败"),
                )
            }
            _uiState.update { it.copy(generatingImageItemIds = it.generatingImageItemIds - item.id) }
        }
    }

    private suspend fun polishShopImagePrompt(
        item: ShopItem,
        context: ShopGenerationContext,
        imageProvider: ProviderSettings,
    ): ImagePromptPolishResult {
        val promptProvider = context.settings.activeProvider() ?: imageProvider
        val promptModelId = promptProvider.resolveFunctionModel(ProviderFunction.CHAT)
            .ifBlank { promptProvider.selectedModel }
            .trim()
        val request = ImagePromptPolishRequest(
            purpose = ImagePromptPurpose.SHOP_ITEM,
            basePrompt = item.imagePrompt.ifBlank { item.description },
            subject = item.name,
            styleHint = item.imageStyle.promptHint,
            roleContext = context.characterPersona,
            sceneContext = listOf(
                context.scenarioContext,
                context.conversationExcerpt,
                item.effectPrompt,
            ).filter(String::isNotBlank).joinToString(separator = "\n").take(1800),
        )
        if (promptModelId.isBlank()) {
            return request.fallbackPolishResult()
        }
        return runCatching {
            withTimeout(ImagePromptPolishTimeoutMs) {
                aiPromptExtrasService.polishImagePrompt(
                    request = request,
                    baseUrl = promptProvider.baseUrl,
                    apiKey = promptProvider.apiKey,
                    modelId = promptModelId,
                    apiProtocol = promptProvider.resolvedApiProtocol(),
                    provider = promptProvider,
                )
            }
        }.getOrElse {
            request.fallbackPolishResult()
        }
    }

    private suspend fun persistImage(
        imageResult: ImageGenerationResult,
        itemId: String,
    ): SavedImageFile {
        if (imageResult.b64Data.isNotBlank()) {
            return imageSaver(imageResult.b64Data)
        }
        val remoteUrl = imageResult.url.trim()
        if (remoteUrl.isBlank()) {
            error("生图结果为空")
        }
        return SavedImageFile(
            path = remoteUrl,
            mimeType = "image/*",
            fileName = "shop-item-$itemId",
        )
    }

    private suspend fun buildMemoryContext(
        assistant: Assistant?,
        conversationId: String,
    ): String {
        val conversation = conversationId
            .takeIf(String::isNotBlank)
            ?.let { Conversation(id = it, createdAt = 0L, updatedAt = 0L) }
        val accessibleEntries = MemoryScopeSupport.filterAccessibleEntries(
            entries = memoryRepository.listEntries(),
            assistant = assistant,
            conversation = conversation,
        )
        return MemoryScopeSupport.sortByPriority(accessibleEntries)
            .take(12)
            .joinToString(separator = "\n") { "- ${it.content.take(180)}" }
    }

    private fun buildConversationExcerpt(messages: List<ChatMessage>): String {
        return messages.takeLast(16).mapNotNull { message ->
            val content = message.parts.toContentMirror(
                imageFallback = "图片",
                specialFallback = "特殊玩法",
            ).ifBlank { message.content }.trim()
            if (content.isBlank()) {
                null
            } else {
                val role = when (message.role) {
                    MessageRole.USER -> "用户"
                    MessageRole.ASSISTANT -> message.speakerName.ifBlank { "角色" }
                }
                "$role：${content.take(240)}"
            }
        }.joinToString(separator = "\n").take(2800)
    }

    private fun buildScenarioContext(
        scenario: RoleplayScenario,
        assistant: Assistant?,
        summary: String,
    ): String {
        return buildString {
            scenario.title.trim().takeIf(String::isNotBlank)?.let { appendLine("标题：$it") }
            scenario.description.trim().takeIf(String::isNotBlank)?.let { appendLine("场景：$it") }
            assistant?.scenario?.trim()?.takeIf(String::isNotBlank)?.let { appendLine("角色场景：${it.take(800)}") }
            summary.trim().takeIf(String::isNotBlank)?.let { appendLine("剧情摘要：${it.take(800)}") }
        }.trim()
    }

    private fun buildCharacterPersona(assistant: Assistant?): String {
        if (assistant == null) return ""
        return listOf(
            assistant.description,
            assistant.systemPrompt,
            assistant.scenario,
            assistant.creatorNotes,
            assistant.tags.joinToString(separator = "、"),
        )
            .map { it.replace("\r\n", "\n").trim() }
            .filter(String::isNotBlank)
            .joinToString(separator = "\n")
            .take(2200)
    }

    private fun fallbackShopDrafts(context: ShopGenerationContext): List<ShopItemDraft> {
        val seed = context.scenario.title.ifBlank { context.characterName }
        val suffix = UUID.nameUUIDFromBytes(seed.toByteArray()).toString().take(4)
        return listOf(
            ShopItemDraft(
                name = "口袋便签 $suffix",
                description = "几张折得很小的便签，适合写下只给对方看的话。",
                priceCents = 800L,
                category = "日常",
                rarity = "普通",
                effectPrompt = "可以触发一次私下留言、道歉或临时约定。",
                imagePrompt = "small folded pocket notes, warm paper texture, intimate everyday prop",
            ),
            ShopItemDraft(
                name = "备用创可贴",
                description = "小盒创可贴和消毒棉片，像一次提前准备好的关心。",
                priceCents = 1_500L,
                category = "补给",
                rarity = "普通",
                effectPrompt = "适合受伤、照顾、夜归或笨拙关心的剧情。",
                imagePrompt = "small bandage box and cotton pads, clean practical daily-life item",
            ),
            ShopItemDraft(
                name = "旧照片夹",
                description = "透明相片夹里还能塞下一张新的拍立得。",
                priceCents = 2_600L,
                category = "纪念",
                rarity = "稀有",
                effectPrompt = "可以引出合照、回忆、保留证据或关系确认。",
                imagePrompt = "transparent photo holder with one empty slot, nostalgic keepsake",
            ),
            ShopItemDraft(
                name = "热饮兑换券",
                description = "一张可以换热饮的小券，边缘被捏得有些皱。",
                priceCents = 1_200L,
                category = "约定",
                rarity = "普通",
                effectPrompt = "可以自然推进一次见面、等人或雨天同行。",
                imagePrompt = "warm drink voucher, slightly creased paper, cozy cafe mood",
            ),
            ShopItemDraft(
                name = "没有署名的钥匙",
                description = "一把没有标签的钥匙，像是迟早会被问起来源。",
                priceCents = 5_800L,
                category = "线索",
                rarity = "珍贵",
                effectPrompt = "可以引出秘密房间、保管、同居或信任试探。",
                imagePrompt = "unlabeled metal key, mysterious personal prop, dramatic soft lighting",
            ),
            ShopItemDraft(
                name = "干净围巾",
                description = "一条柔软围巾，带着淡淡洗衣液味道。",
                priceCents = 4_200L,
                category = "服饰",
                rarity = "稀有",
                effectPrompt = "适合降温、借用、送还、靠近和留下气味记忆。",
                imagePrompt = "soft clean scarf, folded fabric texture, intimate winter prop",
            ),
        )
    }

    private fun resolveAssistant(settings: AppSettings, scenario: RoleplayScenario): Assistant? {
        return settings.resolvedAssistants().firstOrNull { it.id == scenario.assistantId }
            ?: settings.activeAssistant()
    }

    private fun resolveCharacterName(scenario: RoleplayScenario, assistant: Assistant?): String {
        return scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
    }

    private fun purchaseFailureMessage(reason: EconomyFailureReason): String {
        return when (reason) {
            EconomyFailureReason.INSUFFICIENT_FUNDS -> "最近手头有点紧，先把它放进愿望清单也不错。"
            EconomyFailureReason.UNAVAILABLE -> "这个东西刚刚被拿走了，看看别的吧。"
            EconomyFailureReason.INVALID_AMOUNT -> "价格有点不对，先别急着下单。"
            EconomyFailureReason.NOT_FOUND -> "这件东西暂时找不到了。"
        }
    }

    private fun buildFriendlyError(throwable: Throwable, fallback: String): String {
        return when (throwable) {
            is TimeoutCancellationException -> "$fallback：等太久了"
            else -> throwable.message
                ?.takeIf(String::isNotBlank)
                ?.take(80)
                ?: fallback
        }
    }

    private fun notice(message: String) {
        _uiState.update { it.copy(noticeMessage = message, errorMessage = null) }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(errorMessage = message, noticeMessage = null) }
    }

    private data class ShopGenerationContext(
        val scenario: RoleplayScenario,
        val session: RoleplaySession,
        val settings: AppSettings,
        val assistant: Assistant?,
        val characterName: String,
        val userName: String,
        val userPersona: String,
        val characterPersona: String,
        val scenarioContext: String,
        val conversationExcerpt: String,
        val memoryContext: String,
        val economyContext: String,
        val imageStyle: EconomyImageStyle,
        val promptContext: String,
    )

    companion object {
        private const val ShopPromptTimeoutMs = 45_000L
        private const val ImagePromptPolishTimeoutMs = 15_000L
        private const val ImageTimeoutMs = 240_000L

        fun factory(
            scenarioId: String,
            roleplayRepository: RoleplayRepository,
            economyRepository: RoleplayEconomyRepository,
            settingsRepository: AiSettingsRepository,
            aiPromptExtrasService: AiPromptExtrasService,
            aiGateway: AiGateway,
            memoryRepository: MemoryRepository,
            conversationSummaryRepository: ConversationSummaryRepository,
            imageSaver: suspend (String) -> SavedImageFile,
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                RoleplayWalletViewModel(
                    scenarioId = scenarioId,
                    roleplayRepository = roleplayRepository,
                    economyRepository = economyRepository,
                    settingsRepository = settingsRepository,
                    aiPromptExtrasService = aiPromptExtrasService,
                    aiGateway = aiGateway,
                    memoryRepository = memoryRepository,
                    conversationSummaryRepository = conversationSummaryRepository,
                    imageSaver = imageSaver,
                )
            }
        }
    }
}
