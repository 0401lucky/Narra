package com.example.myapplication.data.local.economy

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wallet_accounts",
    indices = [
        Index("scenarioId"),
        Index(value = ["scenarioId", "ownerType", "ownerId"], unique = true),
    ],
)
data class WalletAccountEntity(
    @PrimaryKey val id: String,
    val scenarioId: String,
    val conversationId: String,
    val ownerType: String,
    val ownerId: String,
    val displayName: String,
    val balanceCents: Long,
    val frozenCents: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "wallet_ledger_entries",
    indices = [
        Index("scenarioId"),
        Index("accountId"),
        Index("createdAt"),
        Index("referenceId"),
    ],
)
data class WalletLedgerEntryEntity(
    @PrimaryKey val id: String,
    val scenarioId: String,
    val accountId: String,
    val type: String,
    val amountCents: Long,
    val balanceAfterCents: Long,
    val frozenAfterCents: Long,
    val relatedAccountId: String,
    val relatedShopItemId: String,
    val relatedInventoryItemId: String,
    val referenceId: String,
    val note: String,
    val failureReason: String,
    val createdAt: Long,
)

@Entity(
    tableName = "shop_batches",
    indices = [
        Index("scenarioId"),
        Index("createdAt"),
    ],
)
data class ShopBatchEntity(
    @PrimaryKey val id: String,
    val scenarioId: String,
    val conversationId: String,
    val style: String,
    val promptContext: String,
    val createdAt: Long,
)

@Entity(
    tableName = "shop_items",
    indices = [
        Index("batchId"),
        Index("scenarioId"),
        Index("status"),
        Index("imageStatus"),
    ],
)
data class ShopItemEntity(
    @PrimaryKey val id: String,
    val batchId: String,
    val scenarioId: String,
    val name: String,
    val description: String,
    val priceCents: Long,
    val category: String,
    val rarity: String,
    val effectPrompt: String,
    val imageStyle: String,
    val imagePrompt: String,
    val imageNegativePrompt: String,
    val imageStatus: String,
    val imageUri: String,
    val imageMimeType: String,
    val imageFileName: String,
    val imageError: String,
    val status: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "inventory_items",
    indices = [
        Index("scenarioId"),
        Index(value = ["scenarioId", "ownerType", "ownerId"]),
        Index("sourceShopItemId"),
        Index("status"),
    ],
)
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val scenarioId: String,
    val ownerType: String,
    val ownerId: String,
    val sourceShopItemId: String,
    val name: String,
    val description: String,
    val effectPrompt: String,
    val imageUri: String,
    val imageMimeType: String,
    val imageFileName: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)
