package com.example.myapplication.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

enum class EconomyOwnerType(val storageValue: String, val displayName: String) {
    USER("user", "我"),
    CHARACTER("character", "角色");

    companion object {
        fun fromStorageValue(value: String): EconomyOwnerType {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: CHARACTER
        }
    }
}

enum class WalletLedgerType(val storageValue: String, val displayName: String) {
    ADJUSTMENT("adjustment", "钱包调整"),
    PURCHASE("purchase", "买下道具"),
    TRANSFER_HOLD("transfer_hold", "等待对方确认"),
    TRANSFER_SENT("transfer_sent", "转给对方"),
    TRANSFER_RECEIVED("transfer_received", "收到转账"),
    TRANSFER_RELEASE("transfer_release", "对方没有收下"),
    REFUND("refund", "退回"),
    FAILED("failed", "先缓一缓");

    companion object {
        fun fromStorageValue(value: String): WalletLedgerType {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: ADJUSTMENT
        }
    }
}

enum class ShopItemStatus(val storageValue: String, val displayName: String) {
    AVAILABLE("available", "可购买"),
    PURCHASED("purchased", "已购买"),
    ARCHIVED("archived", "已归档");

    companion object {
        fun fromStorageValue(value: String): ShopItemStatus {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: AVAILABLE
        }
    }
}

enum class InventoryItemStatus(val storageValue: String, val displayName: String) {
    AVAILABLE("available", "可使用"),
    GIFTED("gifted", "已赠送"),
    USED("used", "已使用");

    companion object {
        fun fromStorageValue(value: String): InventoryItemStatus {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: AVAILABLE
        }
    }
}

enum class EconomyImageStyle(
    val storageValue: String,
    val displayName: String,
    val promptHint: String,
) {
    ILLUSTRATED(
        storageValue = "illustrated",
        displayName = "插画质感",
        promptHint = "polished 2.5D semi-realistic anime illustration, premium object rendering, clean aesthetic",
    ),
    REALISTIC(
        storageValue = "realistic",
        displayName = "真实质感",
        promptHint = "photorealistic product photography, natural materials, believable lighting, clean composition",
    ),
    NONE(
        storageValue = "none",
        displayName = "不配图",
        promptHint = "",
    );

    companion object {
        fun fromStorageValue(value: String): EconomyImageStyle {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: ILLUSTRATED
        }
    }
}

data class WalletAccount(
    val id: String = UUID.randomUUID().toString(),
    val scenarioId: String,
    val conversationId: String = "",
    val ownerType: EconomyOwnerType,
    val ownerId: String,
    val displayName: String,
    val balanceCents: Long,
    val frozenCents: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val availableCents: Long
        get() = (balanceCents - frozenCents).coerceAtLeast(0L)
}

data class WalletLedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val scenarioId: String,
    val accountId: String,
    val type: WalletLedgerType,
    val amountCents: Long,
    val balanceAfterCents: Long,
    val frozenAfterCents: Long = 0L,
    val relatedAccountId: String = "",
    val relatedShopItemId: String = "",
    val relatedInventoryItemId: String = "",
    val referenceId: String = "",
    val note: String = "",
    val failureReason: String = "",
    val createdAt: Long = 0L,
)

data class ShopBatch(
    val id: String = UUID.randomUUID().toString(),
    val scenarioId: String,
    val conversationId: String = "",
    val style: EconomyImageStyle = EconomyImageStyle.ILLUSTRATED,
    val promptContext: String = "",
    val createdAt: Long = 0L,
)

data class ShopItem(
    val id: String = UUID.randomUUID().toString(),
    val batchId: String,
    val scenarioId: String,
    val name: String,
    val description: String,
    val priceCents: Long,
    val category: String = "",
    val rarity: String = "",
    val effectPrompt: String = "",
    val imageStyle: EconomyImageStyle = EconomyImageStyle.ILLUSTRATED,
    val imagePrompt: String = "",
    val imageNegativePrompt: String = "",
    val imageStatus: GiftImageStatus? = null,
    val imageUri: String = "",
    val imageMimeType: String = "",
    val imageFileName: String = "",
    val imageError: String = "",
    val status: ShopItemStatus = ShopItemStatus.AVAILABLE,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class InventoryItem(
    val id: String = UUID.randomUUID().toString(),
    val scenarioId: String,
    val ownerType: EconomyOwnerType,
    val ownerId: String,
    val sourceShopItemId: String = "",
    val name: String,
    val description: String,
    val effectPrompt: String = "",
    val imageUri: String = "",
    val imageMimeType: String = "",
    val imageFileName: String = "",
    val status: InventoryItemStatus = InventoryItemStatus.AVAILABLE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class RoleplayEconomyState(
    val scenarioId: String = "",
    val accounts: List<WalletAccount> = emptyList(),
    val ledgerEntries: List<WalletLedgerEntry> = emptyList(),
    val shopItems: List<ShopItem> = emptyList(),
    val inventoryItems: List<InventoryItem> = emptyList(),
) {
    val userAccount: WalletAccount?
        get() = accounts.firstOrNull { it.ownerType == EconomyOwnerType.USER }

    val characterAccount: WalletAccount?
        get() = accounts.firstOrNull { it.ownerType == EconomyOwnerType.CHARACTER }
}

data class ShopItemDraft(
    val name: String,
    val description: String,
    val priceCents: Long,
    val category: String = "",
    val rarity: String = "",
    val effectPrompt: String = "",
    val imagePrompt: String = "",
)

sealed interface EconomyOperationResult<out T> {
    data class Success<T>(val value: T) : EconomyOperationResult<T>
    data class Failure(val reason: EconomyFailureReason, val message: String = "") : EconomyOperationResult<Nothing>
}

enum class EconomyFailureReason {
    NOT_FOUND,
    INSUFFICIENT_FUNDS,
    INVALID_AMOUNT,
    UNAVAILABLE,
}

fun Long.formatMoneyLabel(): String {
    val sign = if (this < 0) "-" else ""
    val absolute = kotlin.math.abs(this)
    val yuan = absolute / 100
    val cents = absolute % 100
    return "$sign¥$yuan.${cents.toString().padStart(2, '0')}"
}

fun parseMoneyToCents(raw: String): Long? {
    val normalized = raw.trim()
        .replace("人民币", "")
        .replace("元", "")
        .replace("块", "")
        .replace("RMB", "", ignoreCase = true)
        .replace("CNY", "", ignoreCase = true)
        .replace("¥", "")
        .replace("￥", "")
        .replace(",", "")
    if (normalized.isBlank()) {
        return null
    }
    return runCatching {
        BigDecimal(normalized)
            .setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .longValueExact()
    }.getOrNull()
}
