# 角色扮演商店优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让角色扮演商店的道具有「参与感」（买/送/用后角色当轮主动反应）并真正长在人物/剧情/记忆上，同时修复 AI 失败静默降级问题，并打磨界面交互。

**Architecture:** 用一个 AppGraph 内存事件总线在「钱包页」与「对话执行器」之间传递道具事件，对话下一轮消费一次即清，转成强导演指令；grounding 侧移除静默降级、统一兜底、去重凑齐；UI 侧在 `RoleplayWalletScreen.kt` 内打磨。

**Tech Stack:** Kotlin、Jetpack Compose、Material 3、手写 DI（AppGraph）、Room（仅读，不改 schema）、JUnit4、kotlinx-coroutines-test。

## Global Constraints

- 所有用户可见文案、提示、注释必须用简体中文。
- 订阅一律 `collectAsStateWithLifecycle()`，禁止裸 `collectAsState()`。
- Gson 一律 `AppJson.gson`，禁止 `Gson()`。
- 不改 Room schema、不改 DataStore、不改 ViewModel 公开接口语义。
- 依赖装配只走 `AppGraph`（`by lazy`），不在 Activity/ViewModel 里 new Repository。
- 构建命令 Windows 用 `./gradlew.bat`。
- kotlin.code.style=official（避免遗留未使用 import）。
- 编译校验最快用 `./gradlew.bat app:compileDebugKotlin`；单测用 `./gradlew.bat app:testDebugUnitTest`。

---

## File Structure

| 文件 | 责任 | 任务 |
| --- | --- | --- |
| `data/repository/economy/RoleplayEconomyEventBus.kt`（新增） | 内存事件总线 + 事件模型 + `formatEconomyEventNote` | 1 |
| `di/AppGraph.kt` | 装配 `roleplayEconomyEventBus` 单例 | 2 |
| `ui/navigation/NavigationViewModelOwners.kt` | `buildEconomyPromptContext` 改组合 lambda（消费事件） | 2 |
| `viewmodel/RoleplayWalletViewModel.kt` | 注入 bus、投递事件；兜底统一 + 失败提示；去重凑数；重写 `fallbackShopDrafts` | 2、4 |
| `ui/navigation/RoleplayNavGraph.kt` | 钱包 VM 工厂调用点补 `economyEventBus` 实参 | 2 |
| `data/repository/economy/RoleplayEconomyRepository.kt` | `buildPromptContext` 可用道具段加主动指令、放宽截断 | 3 |
| `data/repository/ai/AiPromptExtrasService.kt` | 移除静默降级 | 4 |
| `data/repository/ai/RoleplayShopPromptService.kt` | 删除 `fallbackShopItems` 与孤儿 `buildImagePrompt`；prompt 微调 | 4、5 |
| `ui/screen/roleplay/RoleplayWalletScreen.kt` | 隐藏 effectPrompt、稀有度配色、顶部余额条、详情弹层、一键生成、空态 | 6-10 |

---

## Task 1: 道具事件总线与事件文案

**Files:**
- Create: `app/src/main/java/com/example/myapplication/data/repository/economy/RoleplayEconomyEventBus.kt`
- Test: `app/src/test/java/com/example/myapplication/data/repository/economy/RoleplayEconomyEventBusTest.kt`

**Interfaces:**
- Produces:
  - `enum class RoleplayEconomyEventType { PURCHASED, GIFTED, USED }`
  - `data class RoleplayEconomyEvent(type: RoleplayEconomyEventType, itemName: String, effectPrompt: String = "")`
  - `class RoleplayEconomyEventBus`，方法 `fun post(scenarioId: String, event: RoleplayEconomyEvent)`、`fun consume(scenarioId: String): List<RoleplayEconomyEvent>`（取出并清空）
  - `fun formatEconomyEventNote(events: List<RoleplayEconomyEvent>): String`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/com/example/myapplication/data/repository/economy/RoleplayEconomyEventBusTest.kt`：

```kotlin
package com.example.myapplication.data.repository.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayEconomyEventBusTest {
    @Test
    fun consume_returnsPostedEventsThenClears() {
        val bus = RoleplayEconomyEventBus()
        bus.post("s1", RoleplayEconomyEvent(RoleplayEconomyEventType.GIFTED, "铜纽扣", "引出旧约定"))
        bus.post("s1", RoleplayEconomyEvent(RoleplayEconomyEventType.USED, "旧照片"))

        val first = bus.consume("s1")
        assertEquals(2, first.size)
        assertEquals("铜纽扣", first[0].itemName)

        val second = bus.consume("s1")
        assertTrue(second.isEmpty())
    }

    @Test
    fun consume_isScopedByScenario() {
        val bus = RoleplayEconomyEventBus()
        bus.post("s1", RoleplayEconomyEvent(RoleplayEconomyEventType.PURCHASED, "A"))
        assertTrue(bus.consume("s2").isEmpty())
        assertEquals(1, bus.consume("s1").size)
    }

    @Test
    fun formatEconomyEventNote_buildsDirectorInstructionPerType() {
        val note = formatEconomyEventNote(
            listOf(
                RoleplayEconomyEvent(RoleplayEconomyEventType.GIFTED, "铜纽扣", "引出旧约定"),
                RoleplayEconomyEvent(RoleplayEconomyEventType.USED, "旧照片"),
                RoleplayEconomyEvent(RoleplayEconomyEventType.PURCHASED, "热可可"),
            ),
        )
        assertTrue(note.contains("【道具刚刚发生的事】"))
        assertTrue(note.contains("送给你《铜纽扣》"))
        assertTrue(note.contains("引出旧约定"))
        assertTrue(note.contains("使用了《旧照片》"))
        assertTrue(note.contains("买下了《热可可》"))
        assertTrue(note.contains("作出符合人设的即时反应"))
    }

    @Test
    fun formatEconomyEventNote_emptyReturnsBlank() {
        assertEquals("", formatEconomyEventNote(emptyList()))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.data.repository.economy.RoleplayEconomyEventBusTest"`
Expected: 编译失败 / 未解析引用 `RoleplayEconomyEventBus`。

- [ ] **Step 3: 写实现**

创建 `app/src/main/java/com/example/myapplication/data/repository/economy/RoleplayEconomyEventBus.kt`：

```kotlin
package com.example.myapplication.data.repository.economy

enum class RoleplayEconomyEventType {
    PURCHASED,
    GIFTED,
    USED,
}

data class RoleplayEconomyEvent(
    val type: RoleplayEconomyEventType,
    val itemName: String,
    val effectPrompt: String = "",
)

/**
 * 钱包操作与对话回路之间的一次性事件通道。纯内存，按 scenarioId 维护待处理事件，
 * 对话下一轮 consume 后清空，不做持久化（进程被杀后丢失一次性反应可接受）。
 */
class RoleplayEconomyEventBus {
    private val lock = Any()
    private val pending = mutableMapOf<String, MutableList<RoleplayEconomyEvent>>()

    fun post(scenarioId: String, event: RoleplayEconomyEvent) {
        if (scenarioId.isBlank()) return
        synchronized(lock) {
            pending.getOrPut(scenarioId) { mutableListOf() }.add(event)
        }
    }

    fun consume(scenarioId: String): List<RoleplayEconomyEvent> {
        if (scenarioId.isBlank()) return emptyList()
        return synchronized(lock) {
            pending.remove(scenarioId).orEmpty()
        }
    }
}

fun formatEconomyEventNote(events: List<RoleplayEconomyEvent>): String {
    if (events.isEmpty()) return ""
    return buildString {
        appendLine("【道具刚刚发生的事】")
        events.forEach { event ->
            val action = when (event.type) {
                RoleplayEconomyEventType.PURCHASED -> "买下了"
                RoleplayEconomyEventType.GIFTED -> "送给你"
                RoleplayEconomyEventType.USED -> "使用了"
            }
            append("- 用户刚刚")
            append(action)
            append("《")
            append(event.itemName)
            append("》")
            if (event.effectPrompt.isNotBlank()) {
                append("（它在剧情里的作用：")
                append(event.effectPrompt.take(120))
                append("）")
            }
            appendLine()
        }
        append("请在本次回复中自然地承接这一动作，作出符合人设的即时反应，不要无视。")
    }.trim()
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.data.repository.economy.RoleplayEconomyEventBusTest"`
Expected: PASS（4 个用例）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/myapplication/data/repository/economy/RoleplayEconomyEventBus.kt app/src/test/java/com/example/myapplication/data/repository/economy/RoleplayEconomyEventBusTest.kt
git commit -m "feat(roleplay): 新增道具事件总线与导演指令文案"
```

---

## Task 2: 接通事件回路（装配 + 投递 + 消费）

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/di/AppGraph.kt:136-138`
- Modify: `app/src/main/java/com/example/myapplication/ui/navigation/NavigationViewModelOwners.kt:49`
- Modify: `app/src/main/java/com/example/myapplication/viewmodel/RoleplayWalletViewModel.kt`（构造参数、purchase/gift/use 投递、factory 签名）
- Modify: `app/src/main/java/com/example/myapplication/ui/navigation/RoleplayNavGraph.kt:880-895`

**Interfaces:**
- Consumes（来自 Task 1）：`RoleplayEconomyEventBus`、`RoleplayEconomyEvent`、`RoleplayEconomyEventType`、`formatEconomyEventNote`。
- Produces：`AppGraph.roleplayEconomyEventBus`；`RoleplayWalletViewModel.factory(... , economyEventBus: RoleplayEconomyEventBus)`。

- [ ] **Step 1: AppGraph 装配事件总线**

在 `AppGraph.kt` 的 `roleplayEconomyRepository`（136-138 行）之后插入：

```kotlin
    val roleplayEconomyEventBus: RoleplayEconomyEventBus by lazy {
        RoleplayEconomyEventBus()
    }
```

并在 `AppGraph.kt` 顶部 import 区补：

```kotlin
import com.example.myapplication.data.repository.economy.RoleplayEconomyEventBus
```

- [ ] **Step 2: 钱包 VM 注入并投递事件**

在 `RoleplayWalletViewModel.kt` import 区补：

```kotlin
import com.example.myapplication.data.repository.economy.RoleplayEconomyEventBus
import com.example.myapplication.data.repository.economy.RoleplayEconomyEvent
import com.example.myapplication.data.repository.economy.RoleplayEconomyEventType
```

构造函数加参数（放在 `imageSaver` 之前，保持与 factory 一致）：

```kotlin
    private val economyEventBus: RoleplayEconomyEventBus,
    private val imageSaver: suspend (String) -> SavedImageFile,
```

`purchaseItem` 成功分支投递（替换现有 `purchaseItem`）：

```kotlin
    fun purchaseItem(itemId: String) {
        viewModelScope.launch {
            when (val result = economyRepository.purchaseItem(scenarioId = scenarioId, itemId = itemId)) {
                is EconomyOperationResult.Success -> {
                    economyEventBus.post(
                        scenarioId = scenarioId,
                        event = RoleplayEconomyEvent(
                            type = RoleplayEconomyEventType.PURCHASED,
                            itemName = result.value.name,
                            effectPrompt = result.value.effectPrompt,
                        ),
                    )
                    notice("${result.value.name} 已放入库存")
                }
                is EconomyOperationResult.Failure -> fail(purchaseFailureMessage(result.reason))
            }
        }
    }
```

`markGifted` / `markUsed` 通过 `updateInventory` 投递。先给 `updateInventory` 加事件类型参数（替换现有 `markGifted`、`markUsed`、`updateInventory`）：

```kotlin
    fun markGifted(inventoryItemId: String) {
        updateInventory(
            inventoryItemId = inventoryItemId,
            eventType = RoleplayEconomyEventType.GIFTED,
            successMessage = { "${it.name} 已送出" },
            update = economyRepository::markInventoryGifted,
        )
    }

    fun markUsed(inventoryItemId: String) {
        updateInventory(
            inventoryItemId = inventoryItemId,
            eventType = RoleplayEconomyEventType.USED,
            successMessage = { "${it.name} 已使用" },
            update = economyRepository::markInventoryUsed,
        )
    }

    private fun updateInventory(
        inventoryItemId: String,
        eventType: RoleplayEconomyEventType,
        successMessage: (InventoryItem) -> String,
        update: suspend (String) -> EconomyOperationResult<InventoryItem>,
    ) {
        viewModelScope.launch {
            when (val result = update(inventoryItemId)) {
                is EconomyOperationResult.Success -> {
                    economyEventBus.post(
                        scenarioId = scenarioId,
                        event = RoleplayEconomyEvent(
                            type = eventType,
                            itemName = result.value.name,
                            effectPrompt = result.value.effectPrompt,
                        ),
                    )
                    notice(successMessage(result.value))
                }
                is EconomyOperationResult.Failure -> fail(result.message.ifBlank { "道具状态暂时没改成" })
            }
        }
    }
```

`factory(...)` 增加参数与传参（在 `conversationSummaryRepository` 之后、`imageSaver` 之前）：

```kotlin
        fun factory(
            scenarioId: String,
            roleplayRepository: RoleplayRepository,
            economyRepository: RoleplayEconomyRepository,
            settingsRepository: AiSettingsRepository,
            aiPromptExtrasService: AiPromptExtrasService,
            aiGateway: AiGateway,
            memoryRepository: MemoryRepository,
            conversationSummaryRepository: ConversationSummaryRepository,
            economyEventBus: RoleplayEconomyEventBus,
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
                    economyEventBus = economyEventBus,
                    imageSaver = imageSaver,
                )
            }
        }
```

- [ ] **Step 3: NavGraph 工厂调用点补实参**

在 `RoleplayNavGraph.kt:888` 的 `conversationSummaryRepository = appGraph.conversationSummaryRepository,` 之后插入：

```kotlin
                    economyEventBus = appGraph.roleplayEconomyEventBus,
```

- [ ] **Step 4: wiring 层消费事件**

在 `NavigationViewModelOwners.kt` import 区补：

```kotlin
import com.example.myapplication.data.repository.economy.formatEconomyEventNote
```

把第 49 行：

```kotlin
            buildEconomyPromptContext = appGraph.roleplayEconomyRepository::buildPromptContext,
```

替换为：

```kotlin
            buildEconomyPromptContext = { scenarioId ->
                val eventNote = formatEconomyEventNote(appGraph.roleplayEconomyEventBus.consume(scenarioId))
                val baseContext = appGraph.roleplayEconomyRepository.buildPromptContext(scenarioId)
                listOf(eventNote, baseContext).filter(String::isNotBlank).joinToString("\n\n")
            },
```

- [ ] **Step 5: 编译校验**

Run: `./gradlew.bat app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/example/myapplication/di/AppGraph.kt app/src/main/java/com/example/myapplication/ui/navigation/NavigationViewModelOwners.kt app/src/main/java/com/example/myapplication/viewmodel/RoleplayWalletViewModel.kt app/src/main/java/com/example/myapplication/ui/navigation/RoleplayNavGraph.kt
git commit -m "feat(roleplay): 买/送/用道具后下一轮对话当场反应"
```

---

## Task 3: 强化可用道具的导演指令

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/data/repository/economy/RoleplayEconomyRepository.kt:575-589`

**Interfaces:**
- 仅修改 `buildPromptContext` 内部文案，签名不变。

- [ ] **Step 1: 改写「可用道具」段**

将 `RoleplayEconomyRepository.kt` 的这段（575-589 行）：

```kotlin
            val inventory = state.inventoryItems
                .filter { it.status == InventoryItemStatus.AVAILABLE }
                .take(5)
            if (inventory.isNotEmpty()) {
                appendLine("可用道具：")
                inventory.forEach { item ->
                    append("- ")
                    append(item.name)
                    if (item.effectPrompt.isNotBlank()) {
                        append("：")
                        append(item.effectPrompt.take(80))
                    }
                    appendLine()
                }
            }
```

替换为：

```kotlin
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
```

- [ ] **Step 2: 编译校验**

Run: `./gradlew.bat app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/myapplication/data/repository/economy/RoleplayEconomyRepository.kt
git commit -m "feat(roleplay): 鼓励角色主动把可用道具织进叙事"
```

---

## Task 4: 移除 AI 失败的静默降级

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/data/repository/ai/AiPromptExtrasService.kt:598-636`
- Modify: `app/src/main/java/com/example/myapplication/data/repository/ai/RoleplayShopPromptService.kt:146-215,289-302`

**Interfaces:**
- `generateRoleplayShopItems` 失败时改为抛异常（不再返回兜底）。
- 删除 `RoleplayShopPromptService.fallbackShopItems` 与其专属私有 `buildImagePrompt`。

- [ ] **Step 1: AiPromptExtrasService 去掉 getOrElse**

将 `AiPromptExtrasService.kt:598-636` 的整段 `generateRoleplayShopItems`（`= runCatching { ... }.getOrElse { roleplayShopPromptService.fallbackShopItems(...) }`）改为直接委托：

```kotlin
    override suspend fun generateRoleplayShopItems(
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
    ): List<ShopItemDraft> = roleplayShopPromptService.generateRoleplayShopItems(
        characterName = characterName,
        userName = userName,
        characterPersona = characterPersona,
        userPersona = userPersona,
        scenarioContext = scenarioContext,
        conversationExcerpt = conversationExcerpt,
        memoryContext = memoryContext,
        economyContext = economyContext,
        imageStyle = imageStyle,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )
```

- [ ] **Step 2: 删除 service 兜底与孤儿 helper**

在 `RoleplayShopPromptService.kt` 中删除整个 `fallbackShopItems(...)` 方法（146-215 行）以及只被它使用的私有 `buildImagePrompt(...)` 方法（289-302 行）。删除后确认 `parseShopItems` / `parseShopItem` / `priceCents` 等其余方法保持不变。

- [ ] **Step 3: 编译校验（确认无孤儿引用）**

Run: `./gradlew.bat app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（若报 `buildImagePrompt` 仍被引用，说明漏删调用点，需检查）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/myapplication/data/repository/ai/AiPromptExtrasService.kt app/src/main/java/com/example/myapplication/data/repository/ai/RoleplayShopPromptService.kt
git commit -m "refactor(roleplay): 移除商店 AI 失败的静默降级与重复兜底"
```

---

## Task 5: VM 统一兜底、失败提示与去重凑数 + 重写兜底内容 + prompt 微调

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/viewmodel/RoleplayWalletViewModel.kt`（`generateShop`、`generateShopDrafts`、`fallbackShopDrafts`、常量、删 UUID import）
- Modify: `app/src/main/java/com/example/myapplication/data/repository/ai/RoleplayShopPromptService.kt`（`buildRoleplayShopItemsPrompt` 两处文案）
- Test: `app/src/test/java/com/example/myapplication/data/repository/ai/RoleplayShopPromptServiceTest.kt`

**Interfaces:**
- Produces（VM 私有）：`data class ShopDraftResult(drafts: List<ShopItemDraft>, usedFallback: Boolean, fallbackReason: String)`；常量 `MIN_QUALITY_ITEMS = 3`、`TARGET_SHOP_ITEMS = 6`。

- [ ] **Step 1: prompt 先写失败测试**

在 `RoleplayShopPromptServiceTest.kt` 的 `buildRoleplayShopItemsPrompt_anchorsItemsToRecentContextAndMemory` 测试体末尾、`assertTrue(prompt.contains("\"items\""))` 之后补两条断言：

```kotlin
        assertTrue(prompt.contains("宁可贴着上文少编"))
        assertTrue(prompt.contains("name 必须互不相同"))
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.data.repository.ai.RoleplayShopPromptServiceTest"`
Expected: FAIL（断言找不到对应文案）。

- [ ] **Step 3: 改 prompt 文案**

在 `RoleplayShopPromptService.kt` 的 `buildRoleplayShopItemsPrompt` 中，`appendLine("【生成要求】")` 之后紧接插入一行：

```kotlin
    appendLine("（硬性原则：宁可贴着上文少编，也不要凭空生成与人物/剧情无关的道具。）")
```

并把原「8. 严格输出 JSON 对象…」一行改为「9.」，在其前插入新的第 8 条：

```kotlin
    appendLine("8. 6 个商品的 name 必须互不相同，不得重复或近似同名。")
    appendLine("9. 严格输出 JSON 对象，不要 Markdown，不要解释。格式：")
```

- [ ] **Step 4: 运行确认 prompt 测试通过**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.data.repository.ai.RoleplayShopPromptServiceTest"`
Expected: PASS。

- [ ] **Step 5: VM 重构 generateShopDrafts（统一兜底 + 去重凑数）**

在 `RoleplayWalletViewModel.kt` 中，将 `generateShopDrafts`（296-326 行）整体替换为：

```kotlin
    private suspend fun generateShopDrafts(context: ShopGenerationContext): ShopDraftResult {
        val provider = context.settings.resolveFunctionProvider(ProviderFunction.CHAT)
            ?: context.settings.activeProvider()
        val modelId = provider?.resolveFunctionModel(ProviderFunction.CHAT)
            ?.ifBlank { provider.selectedModel }
            ?.trim()
            .orEmpty()
        if (provider == null || modelId.isBlank()) {
            return ShopDraftResult(
                drafts = fallbackShopDrafts(context),
                usedFallback = true,
                fallbackReason = "还没配置生成模型，先放了备用商品",
            )
        }
        val aiDrafts = runCatching {
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
        }.getOrElse { emptyList() }
        val distinct = aiDrafts.distinctBy { it.name.trim() }
        val qualityInsufficient = distinct.size < MIN_QUALITY_ITEMS
        val filled = (distinct + fallbackShopDrafts(context))
            .distinctBy { it.name.trim() }
            .take(TARGET_SHOP_ITEMS)
        return ShopDraftResult(
            drafts = filled,
            usedFallback = qualityInsufficient,
            fallbackReason = if (qualityInsufficient) "这次没凑齐合适的商品，补了几件备用的，可重试" else "",
        )
    }
```

在 `ShopGenerationContext` 私有 data class 之后（611 行附近）新增：

```kotlin
    private data class ShopDraftResult(
        val drafts: List<ShopItemDraft>,
        val usedFallback: Boolean,
        val fallbackReason: String = "",
    )
```

在 `companion object` 内常量区补：

```kotlin
        private const val MIN_QUALITY_ITEMS = 3
        private const val TARGET_SHOP_ITEMS = 6
```

- [ ] **Step 6: VM 接入失败提示（generateShop）**

将 `generateShop` 内 `runCatching { ... }` 块（142-159 行）替换为：

```kotlin
            runCatching {
                val context = buildShopGenerationContext(style)
                val draftResult = generateShopDrafts(context)
                val items = economyRepository.replaceShopBatch(
                    scenarioId = scenarioId,
                    conversationId = context.session.conversationId,
                    style = style,
                    promptContext = context.promptContext,
                    drafts = draftResult.drafts,
                )
                val successNotice = if (draftResult.usedFallback) {
                    draftResult.fallbackReason
                } else {
                    "商店已经换新"
                }
                if (style == EconomyImageStyle.NONE) {
                    notice(successNotice)
                } else {
                    generateImagesForItems(
                        items = items,
                        context = context,
                    )
                    notice(
                        if (draftResult.usedFallback) {
                            successNotice
                        } else {
                            "商店已经换新，失败图片可单独重试"
                        },
                    )
                }
            }.onFailure { throwable ->
                fail(buildFriendlyError(throwable, "商店生成失败"))
            }
```

- [ ] **Step 7: 重写 fallbackShopDrafts（去黑名单道具）+ 删 UUID import**

将 `fallbackShopDrafts`（512-571 行）整体替换为：

```kotlin
    private fun fallbackShopDrafts(context: ShopGenerationContext): List<ShopItemDraft> {
        val owner = context.characterName.trim().ifBlank { "角色" }
        return listOf(
            ShopItemDraft(
                name = "手写便签本",
                description = "薄薄一本便签，适合写下只想给${owner}看的话。",
                priceCents = 1_200L,
                category = "日常",
                rarity = "普通",
                effectPrompt = "可触发一次私下留言、约定或临时心意的剧情。",
                imagePrompt = "a small handwritten note pad, warm paper texture, intimate everyday prop",
            ),
            ShopItemDraft(
                name = "常温热可可粉",
                description = "随手就能冲一杯的热可可，像一份不张扬的照顾。",
                priceCents = 1_800L,
                category = "补给",
                rarity = "普通",
                effectPrompt = "适合夜谈、降温、照顾或缓和气氛的剧情。",
                imagePrompt = "a packet of hot cocoa powder, cozy warm tone, simple daily-life item",
            ),
            ShopItemDraft(
                name = "旧照片冲洗券",
                description = "一张能把手机里的合影洗成实物的券。",
                priceCents = 2_600L,
                category = "纪念",
                rarity = "稀有",
                effectPrompt = "可引出合照、回忆或一次想被记住的瞬间。",
                imagePrompt = "a photo printing voucher, nostalgic keepsake, soft lighting",
            ),
            ShopItemDraft(
                name = "${owner}熟悉的那家点心",
                description = "据说是${owner}常去那家店，点心还带着体温。",
                priceCents = 2_200L,
                category = "约定",
                rarity = "稀有",
                effectPrompt = "可自然推进一次见面、等待或并肩同行。",
                imagePrompt = "a box of warm pastries from a familiar shop, gentle inviting mood",
            ),
            ShopItemDraft(
                name = "读到一半的小说",
                description = "书签停在某一页，像有人迟早会问起后续。",
                priceCents = 3_200L,
                category = "线索",
                rarity = "珍贵",
                effectPrompt = "可揭开一段未说出口的心事、共同话题或旧约定。",
                imagePrompt = "a half-read novel with a bookmark, quiet personal prop, dramatic soft light",
            ),
            ShopItemDraft(
                name = "随行保温杯",
                description = "杯身有轻微使用痕迹，像被人天天带在身边。",
                priceCents = 3_800L,
                category = "随身",
                rarity = "普通",
                effectPrompt = "可用于递水、借用、留物或制造短暂靠近的机会。",
                imagePrompt = "a well-used insulated travel mug, intimate daily companion object",
            ),
        )
    }
```

删除 `RoleplayWalletViewModel.kt:52` 的 `import java.util.UUID`（重写后不再使用）。

- [ ] **Step 8: 编译校验**

Run: `./gradlew.bat app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（无未使用 import 报错）。

- [ ] **Step 9: 全量单测回归**

Run: `./gradlew.bat app:testDebugUnitTest`
Expected: PASS（含 Task 1 与 prompt 测试）。

- [ ] **Step 10: 提交**

```bash
git add app/src/main/java/com/example/myapplication/viewmodel/RoleplayWalletViewModel.kt app/src/main/java/com/example/myapplication/data/repository/ai/RoleplayShopPromptService.kt app/src/test/java/com/example/myapplication/data/repository/ai/RoleplayShopPromptServiceTest.kt
git commit -m "feat(roleplay): 商店统一兜底、失败提示、去重凑齐并重写备用商品"
```

---

> 以下 Task 6-10 为「第三部分：界面与交互打磨」，优先级低于前五个任务，全部集中在 `RoleplayWalletScreen.kt`，验证以 `app:compileDebugKotlin` + 设备目测为主（Compose UI 不写单测）。可与前面合并为一个 PR，也可单独成 PR 后置。

## Task 6: 隐藏 effectPrompt + 稀有度彩色化

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayWalletScreen.kt`

**Interfaces:**
- Produces：私有 `@Composable fun RarityBadge(rarity: String)`、私有 `data class RarityVisual(container: Color, content: Color)`、私有 `@Composable fun rarityVisual(rarity: String): RarityVisual`。

- [ ] **Step 1: 新增稀有度配色与 badge**

在 `RoleplayWalletScreen.kt` 文件末尾（`formatLedgerTime` 之后）新增：

```kotlin
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
```

- [ ] **Step 2: ShopItemCard 用彩色稀有度并隐藏 effectPrompt**

在 `ShopItemCard` 中，把标签行（381-384 行）改为分类用低调 chip、稀有度用彩色 badge：

```kotlin
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.category.isNotBlank()) LabelChip(item.category)
                        if (item.rarity.isNotBlank()) RarityBadge(item.rarity)
                    }
```

并删除 `ShopItemCard` 内展示 effectPrompt 的整段（394-400 行的 `if (item.effectPrompt.isNotBlank()) { Text(...) }`）。

- [ ] **Step 3: 编译校验**

Run: `./gradlew.bat app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayWalletScreen.kt
git commit -m "feat(roleplay): 商品卡隐藏机关说明并为稀有度上色"
```

---

## Task 7: 商店顶部余额条 + 买不起态

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayWalletScreen.kt`

**Interfaces:**
- `ShopTab` 新增参数 `userAvailableCents: Long`；调用点（`RoleplayWalletScreen` 的 `when (selectedTab) { 1 -> ShopTab(...) }`）补传。

- [ ] **Step 1: ShopTab 增加余额参数与顶部条**

`ShopTab` 函数签名加 `userAvailableCents: Long`，在 `LazyColumn` 第一个 `item {}`（生成按钮）之前插入一个余额条 item：

```kotlin
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
```

- [ ] **Step 2: 调用点补传余额**

在 `RoleplayWalletScreen` 的 `1 -> ShopTab(` 调用块加：

```kotlin
                1 -> ShopTab(
                    items = uiState.economyState.shopItems,
                    userAvailableCents = uiState.economyState.userAccount?.availableCents ?: 0L,
                    isGeneratingShop = uiState.isGeneratingShop,
                    generatingImageItemIds = uiState.generatingImageItemIds,
                    onOpenStyleDialog = { showStyleDialog = true },
                    onPurchaseItem = callbacks.onPurchaseItem,
                    onRetryFailedImage = callbacks.onRetryFailedImage,
                )
```

- [ ] **Step 3: 买不起态（价格红 + 按钮禁用）**

`ShopItemCard` 增加参数 `affordable: Boolean`，价格 `Text` 的 `color` 改为 `if (affordable) LocalContentColor.current else MaterialTheme.colorScheme.error`，购买按钮 `enabled` 改为 `item.status == ShopItemStatus.AVAILABLE && affordable`。在 `ShopTab` 的 `items(...)` 里按 `userAvailableCents >= item.priceCents` 传入 `affordable`。补 import `androidx.compose.material3.LocalContentColor`。

```kotlin
                ShopItemCard(
                    item = item,
                    affordable = userAvailableCents >= item.priceCents,
                    isGeneratingImage = item.id in generatingImageItemIds,
                    onPurchase = { onPurchaseItem(item.id) },
                    onRetryImage = { onRetryFailedImage(item.id) },
                )
```

- [ ] **Step 4: 编译校验**

Run: `./gradlew.bat app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayWalletScreen.kt
git commit -m "feat(roleplay): 商店顶部显示余额与买不起态"
```

---

## Task 8: 商品详情底部弹层

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayWalletScreen.kt`

**Interfaces:**
- Produces：私有 `@Composable fun ShopItemDetailSheet(item: ShopItem, affordable: Boolean, onPurchase: () -> Unit, onDismiss: () -> Unit)`。

- [ ] **Step 1: 卡片可点击，记录选中项**

在 `RoleplayWalletScreen` 顶部 state 区新增：

```kotlin
    var detailItemId by rememberSaveable { mutableStateOf<String?>(null) }
```

`ShopTab` 增加参数 `onOpenDetail: (String) -> Unit`，`ShopItemCard` 的 `Card` 加 `modifier = Modifier.clickable { onOpenDetail(item.id) }`（补 import `androidx.compose.foundation.clickable`）。`ShopTab` 调用处传 `onOpenDetail = { detailItemId = it }`。

- [ ] **Step 2: 渲染底部弹层**

在 `RoleplayWalletScreen` 末尾（`ShopStyleDialog` 渲染之后）新增：

```kotlin
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
```

新增 Composable（文件内私有）：

```kotlin
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
```

补 import：`androidx.compose.material3.ModalBottomSheet`。

- [ ] **Step 3: 编译校验**

Run: `./gradlew.bat app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayWalletScreen.kt
git commit -m "feat(roleplay): 商品详情底部弹层"
```

---

## Task 9: 记忆风格 + 一键生成

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayWalletScreen.kt`

**Interfaces:**
- `ShopTab` 顶部生成区改为：主按钮一键生成 + 旁置「风格」图标按钮。

- [ ] **Step 1: 记忆风格 state 与默认推断**

在 `RoleplayWalletScreen` state 区新增：

```kotlin
    var rememberedStyle by rememberSaveable { mutableStateOf<EconomyImageStyle?>(null) }
    val effectiveStyle = rememberedStyle
        ?: uiState.economyState.shopItems.firstOrNull()?.imageStyle
        ?: EconomyImageStyle.ILLUSTRATED
```

`ShopStyleDialog` 的 `onSelect` 改为记忆并生成：

```kotlin
        ShopStyleDialog(
            isGenerating = uiState.isGeneratingShop,
            onDismiss = { showStyleDialog = false },
            onSelect = { style ->
                showStyleDialog = false
                rememberedStyle = style
                callbacks.onGenerateShop(style)
            },
        )
```

- [ ] **Step 2: ShopTab 生成区改为一键 + 风格入口**

`ShopTab` 增加参数 `effectiveStyle: EconomyImageStyle`、`onGenerateOneTap: () -> Unit`（替换原 `onOpenStyleDialog` 的语义：主按钮直接生成，图标按钮才弹对话框，仍保留 `onOpenStyleDialog`）。把生成按钮那个 `item {}` 改为：

```kotlin
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
```

- [ ] **Step 3: 调用点补传**

`1 -> ShopTab(` 调用块补：

```kotlin
                    effectiveStyle = effectiveStyle,
                    onGenerateOneTap = { callbacks.onGenerateShop(effectiveStyle) },
                    onOpenStyleDialog = { showStyleDialog = true },
```

- [ ] **Step 4: 编译校验**

Run: `./gradlew.bat app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayWalletScreen.kt
git commit -m "feat(roleplay): 商店记忆风格并支持一键生成"
```

---

## Task 10: 商店空状态美化

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayWalletScreen.kt`

- [ ] **Step 1: 商店专属空态**

在 `ShopTab` 的 `if (items.isEmpty())` 分支，把 `item { EmptyHint("还没有商品，先生成今日商店。") }` 替换为：

```kotlin
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
```

- [ ] **Step 2: 编译校验**

Run: `./gradlew.bat app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 全量单测 + 设备自检**

Run: `./gradlew.bat app:testDebugUnitTest`
Expected: PASS。

设备自检（`app:installDebug` 后）：进某场景 → 钱包页 → 商店 Tab：
- 一键生成、点风格图标切换风格、稀有度三档配色、点卡片看详情弹层、买得起/买不起态、空态。
- 在库存 Tab 送/用一件道具 → 回对话发一条消息 → 角色当场对该道具作出反应。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayWalletScreen.kt
git commit -m "feat(roleplay): 美化商店空状态"
```

---

## Self-Review（计划自查结论）

- **Spec 覆盖**：第一部分（事件回路）= Task 1/2/3；第二部分（grounding）= Task 4/5；第三部分（UI）= Task 6/7/8/9/10。「重：可见来源」已在 spec 标注为不做，无对应任务（符合预期）。
- **占位符**：无 TBD/TODO；每个代码步骤均给出完整代码与确切命令。
- **类型一致性**：`RoleplayEconomyEvent` / `RoleplayEconomyEventType` / `formatEconomyEventNote` 在 Task 1 定义，Task 2 一致引用；`ShopDraftResult`、`MIN_QUALITY_ITEMS`、`TARGET_SHOP_ITEMS` 在 Task 5 定义并使用；`RarityBadge` / `rarityVisual` 在 Task 6 定义，Task 8 复用；`effectiveStyle` 在 Task 9 串联。
- **风险点**：Task 4 删除 `buildImagePrompt` 依赖「仅 fallbackShopItems 使用」的事实，已用编译校验兜底（Step 3）。UI 任务无单测，靠编译 + 设备目测。
