@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.myapplication.model.EconomyImageStyle
import com.example.myapplication.model.GiftImageStatus
import com.example.myapplication.model.InventoryItem
import com.example.myapplication.model.InventoryItemStatus
import com.example.myapplication.model.RoleplayEconomyState
import com.example.myapplication.model.ShopItem
import com.example.myapplication.model.ShopItemStatus
import com.example.myapplication.model.WalletAccount
import com.example.myapplication.model.WalletLedgerEntry
import com.example.myapplication.model.formatMoneyLabel
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraButton
import com.example.myapplication.ui.component.NarraOutlinedButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.viewmodel.RoleplayWalletUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RoleplayWalletCallbacks(
    val onAddPocketMoney: () -> Unit,
    val onGenerateShop: (EconomyImageStyle) -> Unit,
    val onRetryFailedImage: (String) -> Unit,
    val onPurchaseItem: (String) -> Unit,
    val onGiftInventoryItem: (String) -> Unit,
    val onUseInventoryItem: (String) -> Unit,
    val onClearNoticeMessage: () -> Unit,
    val onClearErrorMessage: () -> Unit,
)

@Composable
fun RoleplayWalletScreen(
    uiState: RoleplayWalletUiState,
    callbacks: RoleplayWalletCallbacks,
    onNavigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showStyleDialog by rememberSaveable { mutableStateOf(false) }
    var detailItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var rememberedStyle by rememberSaveable { mutableStateOf<EconomyImageStyle?>(null) }
    val effectiveStyle = rememberedStyle
        ?: uiState.economyState.shopItems.firstOrNull()?.imageStyle
        ?: EconomyImageStyle.ILLUSTRATED

    LaunchedEffect(uiState.noticeMessage) {
        uiState.noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            callbacks.onClearNoticeMessage()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            callbacks.onClearErrorMessage()
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            RoleplayWalletTopBar(
                title = uiState.scenario?.title?.ifBlank { "钱包" } ?: "钱包",
                onNavigateBack = onNavigateBack,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .navigationBarsPadding(),
        ) {
            if (uiState.isBootstrapping) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            TabRow(selectedTabIndex = selectedTab) {
                listOf("钱包", "商店", "库存", "流水").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }
            when (selectedTab) {
                0 -> WalletTab(
                    state = uiState.economyState,
                    onAddPocketMoney = callbacks.onAddPocketMoney,
                )

                1 -> ShopTab(
                    items = uiState.economyState.shopItems,
                    userAvailableCents = uiState.economyState.userAccount?.availableCents ?: 0L,
                    isGeneratingShop = uiState.isGeneratingShop,
                    generatingImageItemIds = uiState.generatingImageItemIds,
                    effectiveStyle = effectiveStyle,
                    onGenerateOneTap = { callbacks.onGenerateShop(effectiveStyle) },
                    onOpenStyleDialog = { showStyleDialog = true },
                    onOpenDetail = { detailItemId = it },
                    onPurchaseItem = callbacks.onPurchaseItem,
                    onRetryFailedImage = callbacks.onRetryFailedImage,
                )

                2 -> InventoryTab(
                    items = uiState.userInventory,
                    onGift = callbacks.onGiftInventoryItem,
                    onUse = callbacks.onUseInventoryItem,
                )

                3 -> LedgerTab(
                    state = uiState.economyState,
                )
            }
        }
    }

    if (showStyleDialog) {
        ShopStyleDialog(
            isGenerating = uiState.isGeneratingShop,
            onDismiss = { showStyleDialog = false },
            onSelect = { style ->
                showStyleDialog = false
                rememberedStyle = style
                callbacks.onGenerateShop(style)
            },
        )
    }

    detailItemId?.let { id ->
        uiState.economyState.shopItems.firstOrNull { it.id == id }?.let { item ->
            ShopItemDetailSheet(
                item = item,
                affordable = (uiState.economyState.userAccount?.availableCents ?: 0L) >= item.priceCents,
                onPurchase = {
                    callbacks.onPurchaseItem(item.id)
                    detailItemId = null
                },
                onDismiss = { detailItemId = null },
            )
        }
    }
}

@Composable
private fun RoleplayWalletTopBar(
    title: String,
    onNavigateBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.statusBarsPadding(),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "钱包与商店",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WalletTab(
    state: RoleplayEconomyState,
    onAddPocketMoney: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountCard(
                    account = state.userAccount,
                    fallbackName = "我",
                    modifier = Modifier.weight(1f),
                )
                AccountCard(
                    account = state.characterAccount,
                    fallbackName = "角色",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            NarraButton(onClick = onAddPocketMoney, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("补一点零花")
            }
        }
        item {
            Text(
                text = "最近可用道具会进入角色上下文，购买、赠送和使用都会影响后续剧情反应。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: WalletAccount?,
    fallbackName: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = account?.displayName?.ifBlank { fallbackName } ?: fallbackName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = account?.availableCents?.formatMoneyLabel() ?: "¥0.00",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if ((account?.frozenCents ?: 0L) > 0L) {
                Text(
                    text = "待确认 ${account!!.frozenCents.formatMoneyLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ShopTab(
    items: List<ShopItem>,
    userAvailableCents: Long,
    isGeneratingShop: Boolean,
    generatingImageItemIds: Set<String>,
    effectiveStyle: EconomyImageStyle,
    onGenerateOneTap: () -> Unit,
    onOpenStyleDialog: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onPurchaseItem: (String) -> Unit,
    onRetryFailedImage: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "我的余额 ${userAvailableCents.formatMoneyLabel()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "共 ${items.count { it.status == ShopItemStatus.AVAILABLE }} 件在售",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NarraButton(
                    onClick = onGenerateOneTap,
                    enabled = !isGeneratingShop,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isGeneratingShop) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(if (isGeneratingShop) "正在生成商品" else "生成今日商店（${effectiveStyle.displayName}）")
                }
                IconButton(onClick = onOpenStyleDialog, enabled = !isGeneratingShop) {
                    Icon(Icons.Default.Image, contentDescription = "选择图片风格")
                }
            }
        }
        if (items.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "货架还空着",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "点上方「生成今日商店」，让商品长在你和角色的故事里。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(items, key = { it.id }) { item ->
                ShopItemCard(
                    item = item,
                    affordable = userAvailableCents >= item.priceCents,
                    isGeneratingImage = item.id in generatingImageItemIds,
                    onClick = { onOpenDetail(item.id) },
                    onPurchase = { onPurchaseItem(item.id) },
                    onRetryImage = { onRetryFailedImage(item.id) },
                )
            }
        }
    }
}

@Composable
private fun ShopItemCard(
    item: ShopItem,
    affordable: Boolean,
    isGeneratingImage: Boolean,
    onClick: () -> Unit,
    onPurchase: () -> Unit,
    onRetryImage: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ItemImageBox(
                    item = item,
                    isGeneratingImage = isGeneratingImage,
                    onRetry = onRetryImage,
                    modifier = Modifier.size(108.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.category.isNotBlank()) LabelChip(item.category)
                        if (item.rarity.isNotBlank()) RarityBadge(item.rarity)
                    }
                    Text(
                        text = item.description.ifBlank { "暂时没有描述。" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = item.priceCents.formatMoneyLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (affordable) LocalContentColor.current else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                NarraOutlinedButton(
                    onClick = onPurchase,
                    enabled = item.status == ShopItemStatus.AVAILABLE && affordable,
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        when {
                            item.status == ShopItemStatus.PURCHASED -> "已购买"
                            !affordable -> "余额不足"
                            else -> "购买"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemImageBox(
    item: ShopItem,
    isGeneratingImage: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (item.imageStatus == GiftImageStatus.SUCCEEDED && item.imageUri.isNotBlank()) {
            AsyncImage(
                model = item.imageUri,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (item.imageStatus == GiftImageStatus.GENERATING || isGeneratingImage) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else if (item.imageStatus == GiftImageStatus.FAILED) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(8.dp),
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                NarraTextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("重试")
                }
            }
        } else {
            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InventoryTab(
    items: List<InventoryItem>,
    onGift: (String) -> Unit,
    onUse: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (items.isEmpty()) {
            item { EmptyHint("库存还是空的。买下商品后会出现在这里。") }
        } else {
            items(items, key = { it.id }) { item ->
                InventoryItemCard(
                    item = item,
                    onGift = { onGift(item.id) },
                    onUse = { onUse(item.id) },
                )
            }
        }
    }
}

@Composable
private fun InventoryItemCard(
    item: InventoryItem,
    onGift: () -> Unit,
    onUse: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                if (item.imageUri.isNotBlank()) {
                    AsyncImage(
                        model = item.imageUri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        item.description.ifBlank { "没有额外描述。" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.effectPrompt.isNotBlank()) {
                        Text(
                            item.effectPrompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelChip(item.status.displayName)
                Spacer(modifier = Modifier.weight(1f))
                NarraTextButton(
                    onClick = onGift,
                    enabled = item.status == InventoryItemStatus.AVAILABLE,
                ) {
                    Text("赠送")
                }
                NarraOutlinedButton(
                    onClick = onUse,
                    enabled = item.status == InventoryItemStatus.AVAILABLE,
                ) {
                    Text("使用")
                }
            }
        }
    }
}

@Composable
private fun LedgerTab(state: RoleplayEconomyState) {
    val accountsById = remember(state.accounts) { state.accounts.associateBy { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.ledgerEntries.isEmpty()) {
            item { EmptyHint("还没有流水。") }
        } else {
            items(state.ledgerEntries, key = { it.id }) { entry ->
                LedgerEntryRow(
                    entry = entry,
                    accountName = accountsById[entry.accountId]?.displayName.orEmpty(),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun LedgerEntryRow(
    entry: WalletLedgerEntry,
    accountName: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.note.ifBlank { entry.type.displayName },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(accountName, formatLedgerTime(entry.createdAt))
                    .filter(String::isNotBlank)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = entry.amountCents.formatMoneyLabel(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (entry.amountCents >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ShopStyleDialog(
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onSelect: (EconomyImageStyle) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择商品图片风格") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StyleOption(
                    title = "插画质感",
                    description = "更适合礼物和道具，画面会更干净精致。",
                    enabled = !isGenerating,
                    onClick = { onSelect(EconomyImageStyle.ILLUSTRATED) },
                )
                StyleOption(
                    title = "真实质感",
                    description = "保留真实摄影风格，适合偏写实的剧情。",
                    enabled = !isGenerating,
                    onClick = { onSelect(EconomyImageStyle.REALISTIC) },
                )
                StyleOption(
                    title = "不配图",
                    description = "只生成商品文字，速度最快。",
                    enabled = !isGenerating,
                    onClick = { onSelect(EconomyImageStyle.NONE) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            NarraTextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun StyleOption(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    NarraOutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabelChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun EmptyHint(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp),
        )
    }
}

private fun formatLedgerTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

@Composable
private fun ShopItemDetailSheet(
    item: ShopItem,
    affordable: Boolean,
    onPurchase: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (item.imageStatus == GiftImageStatus.SUCCEEDED && item.imageUri.isNotBlank()) {
                AsyncImage(
                    model = item.imageUri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.category.isNotBlank()) LabelChip(item.category)
                if (item.rarity.isNotBlank()) RarityBadge(item.rarity)
            }
            Text(
                text = item.description.ifBlank { "暂时没有描述。" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = item.priceCents.formatMoneyLabel(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (affordable) LocalContentColor.current else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                NarraButton(
                    onClick = onPurchase,
                    enabled = item.status == ShopItemStatus.AVAILABLE && affordable,
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        when {
                            item.status == ShopItemStatus.PURCHASED -> "已购买"
                            !affordable -> "余额不足"
                            else -> "购买"
                        },
                    )
                }
            }
        }
    }
}

private data class RarityVisual(
    val container: Color,
    val content: Color,
)

@Composable
private fun rarityVisual(rarity: String): RarityVisual {
    val scheme = MaterialTheme.colorScheme
    val normalized = rarity.trim()
    return when {
        normalized.contains("珍") || normalized.contains("史诗") || normalized.contains("传说") ->
            RarityVisual(container = Color(0xFFF6E3B4), content = Color(0xFF6B4E00))
        normalized.contains("稀") || normalized.contains("罕") ->
            RarityVisual(container = Color(0xFFCDE3F5), content = Color(0xFF0B4A6F))
        else -> RarityVisual(container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
    }
}

@Composable
private fun RarityBadge(rarity: String) {
    val visual = rarityVisual(rarity)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = visual.container,
        contentColor = visual.content,
    ) {
        Text(
            text = rarity,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
