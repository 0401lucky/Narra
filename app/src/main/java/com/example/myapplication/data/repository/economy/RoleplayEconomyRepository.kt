package com.example.myapplication.data.repository.economy

import androidx.room.withTransaction
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.economy.InventoryItemEntity
import com.example.myapplication.data.local.economy.RoleplayEconomyDao
import com.example.myapplication.data.local.economy.ShopBatchEntity
import com.example.myapplication.data.local.economy.ShopItemEntity
import com.example.myapplication.data.local.economy.WalletAccountEntity
import com.example.myapplication.data.local.economy.WalletLedgerEntryEntity
import com.example.myapplication.model.EconomyFailureReason
import com.example.myapplication.model.EconomyImageStyle
import com.example.myapplication.model.EconomyOperationResult
import com.example.myapplication.model.EconomyOwnerType
import com.example.myapplication.model.GiftImageStatus
import com.example.myapplication.model.InventoryItem
import com.example.myapplication.model.InventoryItemStatus
import com.example.myapplication.model.RoleplayEconomyState
import com.example.myapplication.model.ShopBatch
import com.example.myapplication.model.ShopItem
import com.example.myapplication.model.ShopItemDraft
import com.example.myapplication.model.ShopItemStatus
import com.example.myapplication.model.WalletAccount
import com.example.myapplication.model.WalletLedgerEntry
import com.example.myapplication.model.WalletLedgerType
import com.example.myapplication.model.formatMoneyLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

interface RoleplayEconomyRepository {
    fun observeState(scenarioId: String): Flow<RoleplayEconomyState>

    suspend fun getState(scenarioId: String): RoleplayEconomyState

    suspend fun ensureDefaultAccounts(
        scenarioId: String,
        conversationId: String,
        userName: String,
        characterName: String,
        characterInitialBalanceCents: Long = 0L,
    ): List<WalletAccount>

    suspend fun adjustBalance(
        accountId: String,
        newBalanceCents: Long,
        note: String = "手动调整余额",
    ): EconomyOperationResult<WalletAccount>

    suspend fun replaceShopBatch(
        scenarioId: String,
        conversationId: String,
        style: EconomyImageStyle,
        promptContext: String,
        drafts: List<ShopItemDraft>,
    ): List<ShopItem>

    suspend fun updateShopItemImage(
        itemId: String,
        status: GiftImageStatus,
        imageUri: String = "",
        mimeType: String = "",
        fileName: String = "",
        errorMessage: String = "",
        prompt: String = "",
        negativePrompt: String = "",
    ): ShopItem?

    suspend fun purchaseItem(
        scenarioId: String,
        itemId: String,
        buyerOwnerType: EconomyOwnerType = EconomyOwnerType.USER,
        buyerOwnerId: String = DEFAULT_USER_OWNER_ID,
    ): EconomyOperationResult<InventoryItem>

    suspend fun markInventoryGifted(inventoryItemId: String): EconomyOperationResult<InventoryItem>

    suspend fun markInventoryUsed(inventoryItemId: String): EconomyOperationResult<InventoryItem>

    suspend fun startTransferHold(
        scenarioId: String,
        fromOwnerType: EconomyOwnerType,
        fromOwnerId: String,
        toOwnerType: EconomyOwnerType,
        toOwnerId: String,
        amountCents: Long,
        referenceId: String,
        note: String,
    ): EconomyOperationResult<WalletAccount>

    suspend fun settleTransfer(referenceId: String): EconomyOperationResult<Unit>

    suspend fun releaseTransfer(referenceId: String, reason: String = "对方没有收下"): EconomyOperationResult<Unit>

    suspend fun buildPromptContext(scenarioId: String): String

    suspend fun deleteScenarioData(scenarioId: String)
}

class RoomRoleplayEconomyRepository(
    private val database: ChatDatabase,
    private val dao: RoleplayEconomyDao = database.roleplayEconomyDao(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : RoleplayEconomyRepository {
    override fun observeState(scenarioId: String): Flow<RoleplayEconomyState> {
        return combine(
            dao.observeAccounts(scenarioId),
            dao.observeLedgerEntries(scenarioId),
            dao.observeLatestShopItems(scenarioId),
            dao.observeInventoryItems(scenarioId),
        ) { accounts, ledger, shopItems, inventory ->
            RoleplayEconomyState(
                scenarioId = scenarioId,
                accounts = accounts.map { it.toDomain() },
                ledgerEntries = ledger.map { it.toDomain() },
                shopItems = shopItems.map { it.toDomain() },
                inventoryItems = inventory.map { it.toDomain() },
            )
        }
    }

    override suspend fun getState(scenarioId: String): RoleplayEconomyState {
        return RoleplayEconomyState(
            scenarioId = scenarioId,
            accounts = dao.listAccounts(scenarioId).map { it.toDomain() },
            ledgerEntries = dao.listLedgerEntries(scenarioId).map { it.toDomain() },
            shopItems = dao.listLatestShopItems(scenarioId).map { it.toDomain() },
            inventoryItems = dao.listInventoryItems(scenarioId).map { it.toDomain() },
        )
    }

    override suspend fun ensureDefaultAccounts(
        scenarioId: String,
        conversationId: String,
        userName: String,
        characterName: String,
        characterInitialBalanceCents: Long,
    ): List<WalletAccount> {
        if (scenarioId.isBlank()) return emptyList()
        val now = nowProvider()
        val resolvedCharacterBalance = characterInitialBalanceCents
            .takeIf { it > 0L }
            ?: DEFAULT_CHARACTER_BALANCE_CENTS
        val accounts = database.withTransaction {
            val existing = dao.listAccounts(scenarioId)
            val next = mutableListOf<WalletAccountEntity>()
            val user = existing.firstOrNull {
                it.ownerType == EconomyOwnerType.USER.storageValue && it.ownerId == DEFAULT_USER_OWNER_ID
            } ?: WalletAccountEntity(
                id = UUID.randomUUID().toString(),
                scenarioId = scenarioId,
                conversationId = conversationId,
                ownerType = EconomyOwnerType.USER.storageValue,
                ownerId = DEFAULT_USER_OWNER_ID,
                displayName = userName.trim().ifBlank { "我" },
                balanceCents = DEFAULT_USER_BALANCE_CENTS,
                frozenCents = 0L,
                createdAt = now,
                updatedAt = now,
            ).also(next::add)
            val existingCharacter = existing.firstOrNull {
                it.ownerType == EconomyOwnerType.CHARACTER.storageValue && it.ownerId == DEFAULT_CHARACTER_OWNER_ID
            }
            val character = when {
                existingCharacter == null -> WalletAccountEntity(
                    id = UUID.randomUUID().toString(),
                    scenarioId = scenarioId,
                    conversationId = conversationId,
                    ownerType = EconomyOwnerType.CHARACTER.storageValue,
                    ownerId = DEFAULT_CHARACTER_OWNER_ID,
                    displayName = characterName.trim().ifBlank { "角色" },
                    balanceCents = resolvedCharacterBalance,
                    frozenCents = 0L,
                    createdAt = now,
                    updatedAt = now,
                ).also(next::add)

                existingCharacter.canApplyInitialCharacterBalance(
                    scenarioLedger = dao.listLedgerEntries(scenarioId),
                    resolvedCharacterBalance = resolvedCharacterBalance,
                ) -> existingCharacter.copy(
                    balanceCents = resolvedCharacterBalance,
                    updatedAt = now,
                ).also(next::add)

                else -> existingCharacter
            }
            if (next.isNotEmpty()) {
                dao.upsertAccounts(next)
            }
            buildList {
                add(user)
                add(character)
                addAll(existing.filterNot { it.id == user.id || it.id == character.id })
            }
        }
        return accounts.map { it.toDomain() }
    }

    override suspend fun adjustBalance(
        accountId: String,
        newBalanceCents: Long,
        note: String,
    ): EconomyOperationResult<WalletAccount> {
        if (newBalanceCents < 0L) {
            return EconomyOperationResult.Failure(EconomyFailureReason.INVALID_AMOUNT)
        }
        val updated = database.withTransaction {
            val account = dao.getAccount(accountId) ?: return@withTransaction null
            val now = nowProvider()
            val next = account.copy(
                balanceCents = newBalanceCents,
                frozenCents = account.frozenCents.coerceAtMost(newBalanceCents),
                updatedAt = now,
            )
            dao.upsertAccount(next)
            dao.upsertLedgerEntry(
                WalletLedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    scenarioId = account.scenarioId,
                    accountId = account.id,
                    type = WalletLedgerType.ADJUSTMENT.storageValue,
                    amountCents = newBalanceCents - account.balanceCents,
                    balanceAfterCents = next.balanceCents,
                    frozenAfterCents = next.frozenCents,
                    relatedAccountId = "",
                    relatedShopItemId = "",
                    relatedInventoryItemId = "",
                    referenceId = "",
                    note = note,
                    failureReason = "",
                    createdAt = now,
                ),
            )
            next
        } ?: return EconomyOperationResult.Failure(EconomyFailureReason.NOT_FOUND)
        return EconomyOperationResult.Success(updated.toDomain())
    }

    override suspend fun replaceShopBatch(
        scenarioId: String,
        conversationId: String,
        style: EconomyImageStyle,
        promptContext: String,
        drafts: List<ShopItemDraft>,
    ): List<ShopItem> {
        if (scenarioId.isBlank() || drafts.isEmpty()) return emptyList()
        val now = nowProvider()
        val batch = ShopBatch(
            scenarioId = scenarioId,
            conversationId = conversationId,
            style = style,
            promptContext = promptContext,
            createdAt = now,
        )
        val items = drafts.take(MAX_SHOP_ITEMS).mapIndexed { index, draft ->
            ShopItem(
                batchId = batch.id,
                scenarioId = scenarioId,
                name = draft.name.trim().take(40).ifBlank { "未命名道具" },
                description = draft.description.trim().take(240),
                priceCents = draft.priceCents.coerceAtLeast(1L),
                category = draft.category.trim().take(24),
                rarity = draft.rarity.trim().take(24),
                effectPrompt = draft.effectPrompt.trim().take(500),
                imageStyle = style,
                imagePrompt = draft.imagePrompt.trim().take(1200),
                imageStatus = if (style == EconomyImageStyle.NONE) null else GiftImageStatus.GENERATING,
                sortOrder = index,
                createdAt = now + index,
                updatedAt = now + index,
            )
        }
        database.withTransaction {
            dao.upsertShopBatch(batch.toEntity())
            dao.upsertShopItems(items.map(ShopItem::toEntity))
        }
        return items
    }

    override suspend fun updateShopItemImage(
        itemId: String,
        status: GiftImageStatus,
        imageUri: String,
        mimeType: String,
        fileName: String,
        errorMessage: String,
        prompt: String,
        negativePrompt: String,
    ): ShopItem? {
        if (itemId.isBlank()) return null
        val updated = database.withTransaction {
            val item = dao.getShopItem(itemId) ?: return@withTransaction null
            val next = item.copy(
                imageStatus = status.storageValue,
                imageUri = imageUri.trim(),
                imageMimeType = mimeType.trim(),
                imageFileName = fileName.trim(),
                imageError = errorMessage.trim().take(80),
                imagePrompt = prompt.trim().ifBlank { item.imagePrompt },
                imageNegativePrompt = negativePrompt.trim().ifBlank { item.imageNegativePrompt },
                updatedAt = nowProvider(),
            )
            dao.upsertShopItem(next)
            next
        }
        return updated?.let { it.toDomain() }
    }

    override suspend fun purchaseItem(
        scenarioId: String,
        itemId: String,
        buyerOwnerType: EconomyOwnerType,
        buyerOwnerId: String,
    ): EconomyOperationResult<InventoryItem> {
        val purchased = database.withTransaction {
            val item = dao.getShopItem(itemId) ?: return@withTransaction PurchaseOutcome.NotFound
            if (item.status != ShopItemStatus.AVAILABLE.storageValue) {
                return@withTransaction PurchaseOutcome.Unavailable
            }
            val account = dao.getAccount(
                scenarioId = scenarioId,
                ownerType = buyerOwnerType.storageValue,
                ownerId = buyerOwnerId,
            ) ?: return@withTransaction PurchaseOutcome.NotFound
            if (account.balanceCents - account.frozenCents < item.priceCents) {
                recordFailedLedger(
                    account = account,
                    type = WalletLedgerType.PURCHASE,
                    amountCents = -item.priceCents,
                    relatedShopItemId = item.id,
                    failureReason = "insufficient_funds",
                    note = "想买 ${item.name}，但这次先缓了缓",
                )
                return@withTransaction PurchaseOutcome.InsufficientFunds
            }
            val now = nowProvider()
            val nextAccount = account.copy(
                balanceCents = account.balanceCents - item.priceCents,
                updatedAt = now,
            )
            val inventory = InventoryItem(
                scenarioId = scenarioId,
                ownerType = buyerOwnerType,
                ownerId = buyerOwnerId,
                sourceShopItemId = item.id,
                name = item.name,
                description = item.description,
                effectPrompt = item.effectPrompt,
                imageUri = item.imageUri,
                imageMimeType = item.imageMimeType,
                imageFileName = item.imageFileName,
                createdAt = now,
                updatedAt = now,
            )
            dao.upsertAccount(nextAccount)
            dao.upsertShopItem(
                item.copy(
                    status = ShopItemStatus.PURCHASED.storageValue,
                    updatedAt = now,
                ),
            )
            dao.upsertInventoryItem(inventory.toEntity())
            dao.upsertLedgerEntry(
                WalletLedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    scenarioId = scenarioId,
                    accountId = account.id,
                    type = WalletLedgerType.PURCHASE.storageValue,
                    amountCents = -item.priceCents,
                    balanceAfterCents = nextAccount.balanceCents,
                    frozenAfterCents = nextAccount.frozenCents,
                    relatedAccountId = "",
                    relatedShopItemId = item.id,
                    relatedInventoryItemId = inventory.id,
                    referenceId = "",
                    note = "购买 ${item.name}",
                    failureReason = "",
                    createdAt = now,
                ),
            )
            PurchaseOutcome.Purchased(inventory)
        }
        return when (purchased) {
            PurchaseOutcome.NotFound -> EconomyOperationResult.Failure(EconomyFailureReason.NOT_FOUND)
            PurchaseOutcome.InsufficientFunds -> EconomyOperationResult.Failure(EconomyFailureReason.INSUFFICIENT_FUNDS)
            PurchaseOutcome.Unavailable -> EconomyOperationResult.Failure(EconomyFailureReason.UNAVAILABLE)
            is PurchaseOutcome.Purchased -> EconomyOperationResult.Success(purchased.item)
        }
    }

    override suspend fun markInventoryGifted(inventoryItemId: String): EconomyOperationResult<InventoryItem> {
        return updateInventoryStatus(inventoryItemId, InventoryItemStatus.GIFTED)
    }

    override suspend fun markInventoryUsed(inventoryItemId: String): EconomyOperationResult<InventoryItem> {
        return updateInventoryStatus(inventoryItemId, InventoryItemStatus.USED)
    }

    override suspend fun startTransferHold(
        scenarioId: String,
        fromOwnerType: EconomyOwnerType,
        fromOwnerId: String,
        toOwnerType: EconomyOwnerType,
        toOwnerId: String,
        amountCents: Long,
        referenceId: String,
        note: String,
    ): EconomyOperationResult<WalletAccount> {
        if (amountCents <= 0L || referenceId.isBlank()) {
            return EconomyOperationResult.Failure(EconomyFailureReason.INVALID_AMOUNT)
        }
        val held = database.withTransaction {
            val from = dao.getAccount(scenarioId, fromOwnerType.storageValue, fromOwnerId)
                ?: return@withTransaction TransferHoldOutcome.NotFound
            val to = dao.getAccount(scenarioId, toOwnerType.storageValue, toOwnerId)
            if (from.balanceCents - from.frozenCents < amountCents) {
                recordFailedLedger(
                    account = from,
                    type = WalletLedgerType.TRANSFER_HOLD,
                    amountCents = -amountCents,
                    referenceId = referenceId,
                    failureReason = "insufficient_funds",
                    note = note,
                )
                return@withTransaction TransferHoldOutcome.InsufficientFunds
            }
            val now = nowProvider()
            val next = from.copy(
                frozenCents = from.frozenCents + amountCents,
                updatedAt = now,
            )
            dao.upsertAccount(next)
            dao.upsertLedgerEntry(
                WalletLedgerEntryEntity(
                    id = UUID.randomUUID().toString(),
                    scenarioId = scenarioId,
                    accountId = from.id,
                    type = WalletLedgerType.TRANSFER_HOLD.storageValue,
                    amountCents = -amountCents,
                    balanceAfterCents = next.balanceCents,
                    frozenAfterCents = next.frozenCents,
                    relatedAccountId = to?.id.orEmpty(),
                    relatedShopItemId = "",
                    relatedInventoryItemId = "",
                    referenceId = referenceId,
                    note = note,
                    failureReason = "",
                    createdAt = now,
                ),
            )
            TransferHoldOutcome.Held(next)
        }
        return when (held) {
            TransferHoldOutcome.NotFound -> EconomyOperationResult.Failure(EconomyFailureReason.NOT_FOUND)
            TransferHoldOutcome.InsufficientFunds -> EconomyOperationResult.Failure(EconomyFailureReason.INSUFFICIENT_FUNDS)
            is TransferHoldOutcome.Held -> EconomyOperationResult.Success(held.account.toDomain())
        }
    }

    override suspend fun settleTransfer(referenceId: String): EconomyOperationResult<Unit> {
        if (referenceId.isBlank()) {
            return EconomyOperationResult.Failure(EconomyFailureReason.INVALID_AMOUNT)
        }
        val result = database.withTransaction {
            val hold = dao.listLedgerEntriesByReference(referenceId)
                .firstOrNull { it.type == WalletLedgerType.TRANSFER_HOLD.storageValue && it.failureReason.isBlank() }
                ?: return@withTransaction false
            if (dao.listLedgerEntriesByReference(referenceId).any { it.type == WalletLedgerType.TRANSFER_SENT.storageValue }) {
                return@withTransaction true
            }
            val from = dao.getAccount(hold.accountId) ?: return@withTransaction false
            val to = hold.relatedAccountId.takeIf(String::isNotBlank)?.let { dao.getAccount(it) }
                ?: return@withTransaction false
            val amount = kotlin.math.abs(hold.amountCents)
            val now = nowProvider()
            val nextFrom = from.copy(
                balanceCents = from.balanceCents - amount,
                frozenCents = (from.frozenCents - amount).coerceAtLeast(0L),
                updatedAt = now,
            )
            val nextTo = to.copy(
                balanceCents = to.balanceCents + amount,
                updatedAt = now,
            )
            dao.upsertAccounts(listOf(nextFrom, nextTo))
            dao.upsertLedgerEntries(
                listOf(
                    transferLedger(
                        account = nextFrom,
                        type = WalletLedgerType.TRANSFER_SENT,
                        amountCents = -amount,
                        relatedAccountId = nextTo.id,
                        referenceId = referenceId,
                        note = hold.note,
                        createdAt = now,
                    ),
                    transferLedger(
                        account = nextTo,
                        type = WalletLedgerType.TRANSFER_RECEIVED,
                        amountCents = amount,
                        relatedAccountId = nextFrom.id,
                        referenceId = referenceId,
                        note = hold.note,
                        createdAt = now + 1,
                    ),
                ),
            )
            true
        }
        return if (result) {
            EconomyOperationResult.Success(Unit)
        } else {
            EconomyOperationResult.Failure(EconomyFailureReason.NOT_FOUND)
        }
    }

    override suspend fun releaseTransfer(referenceId: String, reason: String): EconomyOperationResult<Unit> {
        if (referenceId.isBlank()) {
            return EconomyOperationResult.Failure(EconomyFailureReason.INVALID_AMOUNT)
        }
        val result = database.withTransaction {
            val hold = dao.listLedgerEntriesByReference(referenceId)
                .firstOrNull { it.type == WalletLedgerType.TRANSFER_HOLD.storageValue && it.failureReason.isBlank() }
                ?: return@withTransaction false
            if (dao.listLedgerEntriesByReference(referenceId).any { it.type == WalletLedgerType.TRANSFER_RELEASE.storageValue }) {
                return@withTransaction true
            }
            val from = dao.getAccount(hold.accountId) ?: return@withTransaction false
            val amount = kotlin.math.abs(hold.amountCents)
            val now = nowProvider()
            val next = from.copy(
                frozenCents = (from.frozenCents - amount).coerceAtLeast(0L),
                updatedAt = now,
            )
            dao.upsertAccount(next)
            dao.upsertLedgerEntry(
                transferLedger(
                    account = next,
                    type = WalletLedgerType.TRANSFER_RELEASE,
                    amountCents = amount,
                    relatedAccountId = hold.relatedAccountId,
                    referenceId = referenceId,
                    note = reason,
                    createdAt = now,
                ),
            )
            true
        }
        return if (result) {
            EconomyOperationResult.Success(Unit)
        } else {
            EconomyOperationResult.Failure(EconomyFailureReason.NOT_FOUND)
        }
    }

    override suspend fun buildPromptContext(scenarioId: String): String {
        val state = getState(scenarioId)
        if (state.accounts.isEmpty() && state.inventoryItems.isEmpty() && state.ledgerEntries.isEmpty()) {
            return ""
        }
        return buildString {
            appendLine("【钱包与道具状态】")
            state.accounts.forEach { account ->
                append("- ")
                append(account.displayName.ifBlank { account.ownerType.displayName })
                append("：可用 ")
                append(account.availableCents.formatMoneyLabel())
                if (account.frozenCents > 0) {
                    append("，待确认 ")
                    append(account.frozenCents.formatMoneyLabel())
                }
                appendLine()
            }
            val inventory = state.inventoryItems
                .filter { it.status == InventoryItemStatus.AVAILABLE }
                .take(5)
            if (inventory.isNotEmpty()) {
                appendLine("可用道具（在合适时机主动把它们织进叙事，而不是当摆设）：")
                inventory.forEach { item ->
                    append("- ")
                    append(item.name)
                    if (item.effectPrompt.isNotBlank()) {
                        append("：")
                        append(item.effectPrompt.take(120))
                    }
                    appendLine()
                }
            }
            val recentlyAppliedProps = state.inventoryItems
                .filter { it.status != InventoryItemStatus.AVAILABLE }
                .sortedByDescending { it.updatedAt }
                .take(4)
            if (recentlyAppliedProps.isNotEmpty()) {
                appendLine("最近道具动作：")
                recentlyAppliedProps.forEach { item ->
                    append("- ")
                    append(
                        when (item.status) {
                            InventoryItemStatus.GIFTED -> "已赠送"
                            InventoryItemStatus.USED -> "已使用"
                            InventoryItemStatus.AVAILABLE -> "可使用"
                        },
                    )
                    append("：")
                    append(item.name)
                    if (item.effectPrompt.isNotBlank()) {
                        append("，")
                        append(item.effectPrompt.take(80))
                    }
                    appendLine()
                }
            }
            val recent = state.ledgerEntries.take(4)
            if (recent.isNotEmpty()) {
                appendLine("最近经济事件：")
                recent.forEach { entry ->
                    append("- ")
                    append(entry.promptDisplayName())
                    if (entry.shouldExposeAmountInPrompt()) {
                        append(" ")
                        append(entry.amountCents.formatMoneyLabel())
                    }
                    val note = entry.promptNote()
                    if (note.isNotBlank()) {
                        append("，")
                        append(note.take(40))
                    }
                    appendLine()
                }
            }
            appendLine("写角色反应时只能把这些当作生活状态：手头紧就自然表达为先缓缓，没收下就自然表达为对方暂时没接。")
        }.trim()
    }

    override suspend fun deleteScenarioData(scenarioId: String) {
        if (scenarioId.isBlank()) return
        dao.deleteScenarioData(scenarioId)
    }

    private fun WalletAccountEntity.canApplyInitialCharacterBalance(
        scenarioLedger: List<WalletLedgerEntryEntity>,
        resolvedCharacterBalance: Long,
    ): Boolean {
        if (resolvedCharacterBalance <= 0L || balanceCents == resolvedCharacterBalance || frozenCents != 0L) {
            return false
        }
        if (balanceCents != DEFAULT_CHARACTER_BALANCE_CENTS) {
            return false
        }
        return scenarioLedger.none { it.accountId == id }
    }

    private suspend fun updateInventoryStatus(
        inventoryItemId: String,
        status: InventoryItemStatus,
    ): EconomyOperationResult<InventoryItem> {
        val updated = database.withTransaction {
            val item = dao.getInventoryItem(inventoryItemId) ?: return@withTransaction null
            val next = item.copy(
                status = status.storageValue,
                updatedAt = nowProvider(),
            )
            dao.upsertInventoryItem(next)
            next
        } ?: return EconomyOperationResult.Failure(EconomyFailureReason.NOT_FOUND)
        return EconomyOperationResult.Success(updated.toDomain())
    }

    private suspend fun recordFailedLedger(
        account: WalletAccountEntity,
        type: WalletLedgerType,
        amountCents: Long,
        relatedShopItemId: String = "",
        referenceId: String = "",
        failureReason: String,
        note: String,
    ) {
        dao.upsertLedgerEntry(
            WalletLedgerEntryEntity(
                id = UUID.randomUUID().toString(),
                scenarioId = account.scenarioId,
                accountId = account.id,
                type = WalletLedgerType.FAILED.storageValue,
                amountCents = amountCents,
                balanceAfterCents = account.balanceCents,
                frozenAfterCents = account.frozenCents,
                relatedAccountId = "",
                relatedShopItemId = relatedShopItemId,
                relatedInventoryItemId = "",
                referenceId = referenceId,
                note = note.ifBlank { "这件事先缓了缓" },
                failureReason = failureReason,
                createdAt = nowProvider(),
            ),
        )
    }

    private fun transferLedger(
        account: WalletAccountEntity,
        type: WalletLedgerType,
        amountCents: Long,
        relatedAccountId: String,
        referenceId: String,
        note: String,
        createdAt: Long,
    ): WalletLedgerEntryEntity {
        return WalletLedgerEntryEntity(
            id = UUID.randomUUID().toString(),
            scenarioId = account.scenarioId,
            accountId = account.id,
            type = type.storageValue,
            amountCents = amountCents,
            balanceAfterCents = account.balanceCents,
            frozenAfterCents = account.frozenCents,
            relatedAccountId = relatedAccountId,
            relatedShopItemId = "",
            relatedInventoryItemId = "",
            referenceId = referenceId,
            note = note,
            failureReason = "",
            createdAt = createdAt,
        )
    }

    private sealed interface PurchaseOutcome {
        data object NotFound : PurchaseOutcome
        data object InsufficientFunds : PurchaseOutcome
        data object Unavailable : PurchaseOutcome
        data class Purchased(val item: InventoryItem) : PurchaseOutcome
    }

    private sealed interface TransferHoldOutcome {
        data object NotFound : TransferHoldOutcome
        data object InsufficientFunds : TransferHoldOutcome
        data class Held(val account: WalletAccountEntity) : TransferHoldOutcome
    }
}

private fun WalletAccountEntity.toDomain(): WalletAccount {
    return WalletAccount(
        id = id,
        scenarioId = scenarioId,
        conversationId = conversationId,
        ownerType = EconomyOwnerType.fromStorageValue(ownerType),
        ownerId = ownerId,
        displayName = displayName,
        balanceCents = balanceCents,
        frozenCents = frozenCents,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun WalletAccount.toEntity(): WalletAccountEntity {
    return WalletAccountEntity(
        id = id,
        scenarioId = scenarioId,
        conversationId = conversationId,
        ownerType = ownerType.storageValue,
        ownerId = ownerId,
        displayName = displayName,
        balanceCents = balanceCents,
        frozenCents = frozenCents,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun WalletLedgerEntryEntity.toDomain(): WalletLedgerEntry {
    return WalletLedgerEntry(
        id = id,
        scenarioId = scenarioId,
        accountId = accountId,
        type = WalletLedgerType.fromStorageValue(type),
        amountCents = amountCents,
        balanceAfterCents = balanceAfterCents,
        frozenAfterCents = frozenAfterCents,
        relatedAccountId = relatedAccountId,
        relatedShopItemId = relatedShopItemId,
        relatedInventoryItemId = relatedInventoryItemId,
        referenceId = referenceId,
        note = note,
        failureReason = failureReason,
        createdAt = createdAt,
    )
}

private fun WalletLedgerEntry.promptDisplayName(): String {
    if (failureReason == "insufficient_funds") {
        return "手头有点紧"
    }
    return when (type) {
        WalletLedgerType.ADJUSTMENT -> "钱包里多了一点余裕"
        WalletLedgerType.PURCHASE -> "买下道具"
        WalletLedgerType.TRANSFER_HOLD -> "有一笔心意等对方确认"
        WalletLedgerType.TRANSFER_SENT -> "转给对方"
        WalletLedgerType.TRANSFER_RECEIVED -> "收到转账"
        WalletLedgerType.TRANSFER_RELEASE -> "对方没有收下"
        WalletLedgerType.REFUND -> "退回"
        WalletLedgerType.FAILED -> "这次先缓了缓"
    }
}

private fun WalletLedgerEntry.promptNote(): String {
    val trimmed = note.trim()
    if (failureReason == "insufficient_funds") {
        return trimmed
            .replace("失败", "先缓了缓")
            .ifBlank { "最近手头有点紧，先缓了缓" }
    }
    return trimmed
        .replace("失败", "没成")
        .replace("扣款", "花出去")
        .replace("余额不足", "手头有点紧")
}

private fun WalletLedgerEntry.shouldExposeAmountInPrompt(): Boolean {
    return type != WalletLedgerType.FAILED && failureReason != "insufficient_funds"
}

private fun ShopBatch.toEntity(): ShopBatchEntity {
    return ShopBatchEntity(
        id = id,
        scenarioId = scenarioId,
        conversationId = conversationId,
        style = style.storageValue,
        promptContext = promptContext,
        createdAt = createdAt,
    )
}

private fun ShopItemEntity.toDomain(): ShopItem {
    return ShopItem(
        id = id,
        batchId = batchId,
        scenarioId = scenarioId,
        name = name,
        description = description,
        priceCents = priceCents,
        category = category,
        rarity = rarity,
        effectPrompt = effectPrompt,
        imageStyle = EconomyImageStyle.fromStorageValue(imageStyle),
        imagePrompt = imagePrompt,
        imageNegativePrompt = imageNegativePrompt,
        imageStatus = GiftImageStatus.fromStorageValue(imageStatus),
        imageUri = imageUri,
        imageMimeType = imageMimeType,
        imageFileName = imageFileName,
        imageError = imageError,
        status = ShopItemStatus.fromStorageValue(status),
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun ShopItem.toEntity(): ShopItemEntity {
    return ShopItemEntity(
        id = id,
        batchId = batchId,
        scenarioId = scenarioId,
        name = name,
        description = description,
        priceCents = priceCents,
        category = category,
        rarity = rarity,
        effectPrompt = effectPrompt,
        imageStyle = imageStyle.storageValue,
        imagePrompt = imagePrompt,
        imageNegativePrompt = imageNegativePrompt,
        imageStatus = imageStatus?.storageValue.orEmpty(),
        imageUri = imageUri,
        imageMimeType = imageMimeType,
        imageFileName = imageFileName,
        imageError = imageError,
        status = status.storageValue,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun InventoryItemEntity.toDomain(): InventoryItem {
    return InventoryItem(
        id = id,
        scenarioId = scenarioId,
        ownerType = EconomyOwnerType.fromStorageValue(ownerType),
        ownerId = ownerId,
        sourceShopItemId = sourceShopItemId,
        name = name,
        description = description,
        effectPrompt = effectPrompt,
        imageUri = imageUri,
        imageMimeType = imageMimeType,
        imageFileName = imageFileName,
        status = InventoryItemStatus.fromStorageValue(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun InventoryItem.toEntity(): InventoryItemEntity {
    return InventoryItemEntity(
        id = id,
        scenarioId = scenarioId,
        ownerType = ownerType.storageValue,
        ownerId = ownerId,
        sourceShopItemId = sourceShopItemId,
        name = name,
        description = description,
        effectPrompt = effectPrompt,
        imageUri = imageUri,
        imageMimeType = imageMimeType,
        imageFileName = imageFileName,
        status = status.storageValue,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

const val DEFAULT_USER_OWNER_ID = "user"
const val DEFAULT_CHARACTER_OWNER_ID = "character"

private const val DEFAULT_USER_BALANCE_CENTS = 100_000L
private const val DEFAULT_CHARACTER_BALANCE_CENTS = 30_000L
private const val MAX_SHOP_ITEMS = 6
