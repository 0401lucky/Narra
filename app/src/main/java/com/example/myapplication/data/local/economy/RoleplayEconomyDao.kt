package com.example.myapplication.data.local.economy

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RoleplayEconomyDao {
    @Query("SELECT * FROM wallet_accounts WHERE scenarioId = :scenarioId ORDER BY ownerType DESC, createdAt ASC")
    fun observeAccounts(scenarioId: String): Flow<List<WalletAccountEntity>>

    @Query("SELECT * FROM wallet_accounts WHERE scenarioId = :scenarioId ORDER BY ownerType DESC, createdAt ASC")
    suspend fun listAccounts(scenarioId: String): List<WalletAccountEntity>

    @Query("SELECT * FROM wallet_accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccount(accountId: String): WalletAccountEntity?

    @Query(
        """
        SELECT * FROM wallet_accounts
        WHERE scenarioId = :scenarioId AND ownerType = :ownerType AND ownerId = :ownerId
        LIMIT 1
        """,
    )
    suspend fun getAccount(
        scenarioId: String,
        ownerType: String,
        ownerId: String,
    ): WalletAccountEntity?

    @Upsert
    suspend fun upsertAccounts(accounts: List<WalletAccountEntity>)

    @Upsert
    suspend fun upsertAccount(account: WalletAccountEntity)

    @Query("SELECT * FROM wallet_ledger_entries WHERE scenarioId = :scenarioId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeLedgerEntries(scenarioId: String, limit: Int = 80): Flow<List<WalletLedgerEntryEntity>>

    @Query("SELECT * FROM wallet_ledger_entries WHERE scenarioId = :scenarioId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun listLedgerEntries(scenarioId: String, limit: Int = 80): List<WalletLedgerEntryEntity>

    @Query("SELECT * FROM wallet_ledger_entries WHERE referenceId = :referenceId ORDER BY createdAt DESC, id DESC")
    suspend fun listLedgerEntriesByReference(referenceId: String): List<WalletLedgerEntryEntity>

    @Upsert
    suspend fun upsertLedgerEntry(entry: WalletLedgerEntryEntity)

    @Upsert
    suspend fun upsertLedgerEntries(entries: List<WalletLedgerEntryEntity>)

    @Query("SELECT * FROM shop_items WHERE scenarioId = :scenarioId AND batchId = (SELECT id FROM shop_batches WHERE scenarioId = :scenarioId ORDER BY createdAt DESC, id DESC LIMIT 1) ORDER BY sortOrder ASC, createdAt ASC")
    fun observeLatestShopItems(scenarioId: String): Flow<List<ShopItemEntity>>

    @Query("SELECT * FROM shop_items WHERE scenarioId = :scenarioId AND batchId = (SELECT id FROM shop_batches WHERE scenarioId = :scenarioId ORDER BY createdAt DESC, id DESC LIMIT 1) ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun listLatestShopItems(scenarioId: String): List<ShopItemEntity>

    @Query("SELECT * FROM shop_items WHERE id = :itemId LIMIT 1")
    suspend fun getShopItem(itemId: String): ShopItemEntity?

    @Upsert
    suspend fun upsertShopBatch(batch: ShopBatchEntity)

    @Upsert
    suspend fun upsertShopItems(items: List<ShopItemEntity>)

    @Upsert
    suspend fun upsertShopItem(item: ShopItemEntity)

    @Query("SELECT * FROM inventory_items WHERE scenarioId = :scenarioId ORDER BY updatedAt DESC, createdAt DESC")
    fun observeInventoryItems(scenarioId: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE scenarioId = :scenarioId ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun listInventoryItems(scenarioId: String): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items WHERE id = :inventoryItemId LIMIT 1")
    suspend fun getInventoryItem(inventoryItemId: String): InventoryItemEntity?

    @Upsert
    suspend fun upsertInventoryItem(item: InventoryItemEntity)

    @Query("DELETE FROM wallet_accounts WHERE scenarioId = :scenarioId")
    suspend fun deleteAccountsForScenario(scenarioId: String)

    @Query("DELETE FROM wallet_ledger_entries WHERE scenarioId = :scenarioId")
    suspend fun deleteLedgerForScenario(scenarioId: String)

    @Query("DELETE FROM shop_batches WHERE scenarioId = :scenarioId")
    suspend fun deleteShopBatchesForScenario(scenarioId: String)

    @Query("DELETE FROM shop_items WHERE scenarioId = :scenarioId")
    suspend fun deleteShopItemsForScenario(scenarioId: String)

    @Query("DELETE FROM inventory_items WHERE scenarioId = :scenarioId")
    suspend fun deleteInventoryForScenario(scenarioId: String)

    @Transaction
    suspend fun deleteScenarioData(scenarioId: String) {
        deleteAccountsForScenario(scenarioId)
        deleteLedgerForScenario(scenarioId)
        deleteShopBatchesForScenario(scenarioId)
        deleteShopItemsForScenario(scenarioId)
        deleteInventoryForScenario(scenarioId)
    }
}
