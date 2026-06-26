# 角色扮演商店优化设计

- 日期：2026-06-26
- 核心目标：让道具有「参与感」，并真正长在人物/剧情/记忆上（grounding）
- 次要目标：商店界面与交互打磨
- 视觉调性：跟随现有 Material3 / Narra 组件风格
- 涉及功能：角色扮演钱包页「商店」Tab（路由 `roleplay/play/{scenarioId}/wallet`）及其回流进对话的链路

## 背景与诊断

商店由前一轮开发完成，后端上下文采集与 prompt 设计相当完善，但用户的核心反馈是：**道具没有参与感，而且像是随便编的，应该和人物、剧情、记忆有关**。逐文件追踪整条「生成 → 购买 → 回流进对话」的链路后，定位到问题分输入、输出两侧。

### 输出侧（参与感弱的真因 —— 本次重点）

回流回路**是通的**：`RoleplayEconomyRepository.buildPromptContext` 生成的「可用道具 / 最近道具动作 / 经济事件」，每轮对话都通过 `economyDirectorNote` 拼进发给 AI 的导演注记（`RoleplayRoundTripExecutor.kt:224-272`）。但参与感弱，因为：

1. **道具是「被动清单」而非「事件」**：AI 每轮只看到一句「可用道具：X：效果（截断 80 字）」，导演注记里关于经济的唯一指示是「手头紧就表达为先缓缓」（`RoleplayEconomyRepository.kt:632`）——只管钱的语气，**没有任何「主动把道具织进剧情」的指令**。
2. **光「买」几乎不参与**：`最近道具动作` 只收录 `已赠送/已使用` 的道具（`RoleplayEconomyRepository.kt:590-591`）；买下放库存（AVAILABLE）时 AI 那边毫无波澜。赠送/使用按钮还藏在库存 Tab 深处。
3. **买/送/用之后没有「即时反应」**：动作只能等下一轮被动带上，没有「用户刚刚送了你 X，请当场回应」这种一次性强信号，角色不会主动 acknowledge。

### 输入侧（grounding 名义做了，但被兜底击穿）

- prompt（`buildRoleplayShopItemsPrompt`）确实按「近期互动 > 记忆 > 人设」的优先级采集上下文，设计是对的。
- **头号真凶：静默降级到被禁道具**。VM 调 `aiPromptExtrasService.generateRoleplayShopItems`，service 内部 AI 一旦失败用 `.getOrElse` **静默吞掉异常**，返回 `fallbackShopItems`（`AiPromptExtrasService.kt:613-636`），即 prompt 第 70 行明令禁止的 票根/钥匙扣/薄荷糖/便利店袋/明信片/围巾，且界面照常弹「商店已经换新」。用户看到的「随便编的烂大街道具」往往正是兜底在背锅。
- 两套几乎重复的兜底数据：service 的 `fallbackShopItems` 与 VM 的 `fallbackShopDrafts`，内容都是上述被禁道具。
- `parseShopItems` 用 `distinctBy { name }.take(6)`，AI 返回重名时商品不足，货架显空。
- 生成出来的道具**没有「它从哪段剧情/记忆来」的可见痕迹**，用户无法判断它是否真长在故事上。

## 设计目标与取舍

- **核心**：道具变成「会被角色当场接住的事件」+ 修复 grounding 被兜底击穿的问题。
- **次要**：界面/交互打磨（用户已认可，但非优先）。
- 约束：**不改 Room schema、不改 DataStore、不改 ViewModel 公开接口语义**；遵循 surgical change 与 YAGNI。

## 方案

### 第一部分（核心）：事件驱动的道具参与感

**机制**：在钱包操作（买/送/用）与对话执行器之间，用一个 **AppGraph 内存事件总线** 传递「刚刚发生的道具事件」，对话下一轮消费一次即清，转成一条强导演指令让角色当场反应。

1. **新增 `RoleplayEconomyEventBus`**（`data/repository/economy/RoleplayEconomyEventBus.kt`，纯内存、按 scenarioId 维护一个待处理事件列表）：
   - `post(scenarioId, event)`：钱包操作成功后投递。
   - `consume(scenarioId): List<RoleplayEconomyEvent>`：取出并清空（消费一次）。
   - 事件模型：`RoleplayEconomyEvent(type: PURCHASED|GIFTED|USED, itemName, effectPrompt)`。
   - 内存实现即可：进程存活期间跨「钱包页 ↔ 对话页」有效；进程被杀后丢失一次性反应可接受（不是持久业务数据）。
2. **AppGraph 装配**：新增 `val roleplayEconomyEventBus by lazy { RoleplayEconomyEventBus() }`（应用级单例）。
3. **钱包侧投递**（`RoleplayWalletViewModel`）：注入 event bus；在 `purchaseItem` / `markGifted` / `markUsed` 成功分支投递对应事件。
4. **对话侧消费（关键接缝，零侵入执行器）**：`RoleplayRoundTripExecutor` 已注入 `buildEconomyPromptContext: suspend (String) -> String`，目前在 `NavigationViewModelOwners.kt:49` 指向 `economyRepository::buildPromptContext`。改为一个**组合 lambda**：
   ```
   buildEconomyPromptContext = { scenarioId ->
       val eventNote = formatEconomyEventNote(eventBus.consume(scenarioId))
       val baseContext = economyRepository.buildPromptContext(scenarioId)
       listOf(eventNote, baseContext).filter(String::isNotBlank).joinToString("\n\n")
   }
   ```
   - 一次性事件注记放在**最前**，最醒目。
   - **只有对话回路走这条组合 lambda；商店生成仍直接调 `economyRepository.buildPromptContext`（`RoleplayWalletViewModel.kt:267`），不会误消费事件。**
5. **强指令文案** `formatEconomyEventNote`（示例）：
   > 【道具刚刚发生的事】用户刚刚把《X》送给你（它在剧情里的作用：…）。请在本次回复中自然地承接这一动作、作出符合人设的即时反应，不要无视。
   - PURCHASED/GIFTED/USED 用不同动词；带上 effectPrompt 作为剧情作用提示。
6. **被动清单强化**（轻量、配合）：`buildPromptContext` 的「可用道具」段补一句主动指令（鼓励角色在合适时机把道具织进叙事，而非仅作背景），effectPrompt 截断从 80 放宽到约 120 字。

### 第二部分（核心）：grounding 质量

1. **统一兜底为单一来源 + 失败可见化**：
   - `AiPromptExtrasService.generateRoleplayShopItems` 移除 `.getOrElse { fallbackShopItems(...) }` 的静默降级，失败异常向上传播。
   - 删除 service 的 `fallbackShopItems`（重复死代码，仅此一处调用）。
   - VM `generateShopDrafts` 捕获异常/空结果，统一走 VM 单一兜底，并置标志位用于提示。
2. **失败兜底策略 = 填备用货 + 明确提示**（用户已选定）：AI 翻车或返回不可用时用改写后的备用货架填充，snackbar 明确提示「本次用了备用商品，可重试」（与正常「商店已经换新」区分）；未配置 provider/model 时提示去配置。
3. **重写兜底内容**（`fallbackShopDrafts`）：去掉所有 prompt 黑名单道具（钥匙扣/围巾/明信片/薄荷糖/票根及变体），换成不在黑名单、尽量带入当前场景标题/角色名的安全中性道具。
4. **去重凑数**：AI 返回去重后若不足合理数量（阈值 < 3 视为质量不足）则用兜底补齐到 6 个，避免空/半空货架。去重保留在 service 的 `parseShopItems`，凑数放 VM。
5. **prompt 微调（低风险）**：在 `buildRoleplayShopItemsPrompt` 生成要求中补「6 个商品名称必须互不相同」；并强化「必须可在近期互动/记忆中找到来源」的措辞。

### 第三部分（次要）：界面与交互打磨

> 用户已认可，但优先级低于前两部分。实施时若时间/复杂度紧张可单独成 PR 后置。

A. **隐藏 `effectPrompt`**：它是给后续 AI 的机关说明，不该展示给用户——从 `ShopItemCard` 与详情移除（数据与回流逻辑不变）。
B. **稀有度彩色化**：新增 `rarityStyle(rarity)`，把 普通/稀有/珍贵（含近义词）映射到 中性灰 / 蓝青 / 琥珀金 三档配色，用带背景色 badge 取代灰 `AssistChip`；配色取自 `colorScheme`，不硬编码品牌色。
C. **商店顶部状态条**：展示「我的余额 ¥X · 共 N 件」，逛店不切 Tab 即可判断能否购买；余额不足时价格/购买按钮置灰提示。
D. **商品详情底部弹层** `ShopItemDetailSheet`（`ModalBottomSheet`，项目通用模式）：大图 + 完整名称 + 彩色稀有度 + 分类 + 完整描述 + 价格 + 余额对比 + 购买；不显示 effectPrompt；UI 层局部 state，不改 ViewModel 接口。
E. **记忆风格 + 一键生成**：主按钮「生成今日商店」直接用上次风格生成；旁边小「风格」入口才弹选择。默认风格取 `shopItems.firstOrNull()?.imageStyle ?: ILLUSTRATED` + `rememberSaveable` 记忆；不动 DataStore、不改 ViewModel 签名。
F. **空状态美化**：商店专属空态（图标 + 引导）替换纯文字 `EmptyHint`。

## 不做（YAGNI）

- 不加搜索 / 分类筛选 / 心愿单 / 议价 / 限时刷新。
- 不做「重：可见灵感来源」（让 AI 额外输出来源字段并展示）——本次只到事件驱动反应。
- 不改钱包/库存/流水三个 Tab 的既有业务逻辑（商店顶部条仅复用余额数据）。
- 不改 Room schema、不改 DataStore、不改 ViewModel 公开接口语义。

## 受影响文件

| 文件 | 改动 | 归属 |
| --- | --- | --- |
| `data/repository/economy/RoleplayEconomyEventBus.kt`（新增） | 内存事件总线 + 事件模型 + `formatEconomyEventNote` | 一 |
| `di/AppGraph.kt` | 装配 `roleplayEconomyEventBus` | 一 |
| `ui/navigation/NavigationViewModelOwners.kt` | `buildEconomyPromptContext` 改为组合 lambda（消费事件）；向钱包 VM 注入 bus | 一 |
| `viewmodel/RoleplayWalletViewModel.kt` | 注入并投递事件；兜底统一 + 提示标志；重写 `fallbackShopDrafts`；去重凑数 | 一、二 |
| `data/repository/economy/RoleplayEconomyRepository.kt` | `buildPromptContext` 可用道具段加主动指令、放宽 effectPrompt 截断 | 一 |
| `data/repository/ai/AiPromptExtrasService.kt` | 移除静默降级 | 二 |
| `data/repository/ai/RoleplayShopPromptService.kt` | 删除 `fallbackShopItems`；prompt 微调 | 二 |
| `ui/screen/roleplay/RoleplayWalletScreen.kt` | 隐藏 effectPrompt、稀有度配色、顶部条、详情弹层、一键生成、空态 | 三 |
| `ui/navigation/RoleplayNavGraph.kt` | 若钱包 VM 工厂签名变化需同步 | 一 |

## 验证

- 编译：`./gradlew.bat app:compileDebugKotlin`。
- 单测：
  - event bus 的 post/consume 消费一次语义。
  - VM 兜底凑数、去重、AI 失败提示路径。
  - `formatEconomyEventNote` 三种事件文案。
  - 现有 `RoleplayShopPromptServiceTest` 回归。
  - 运行 `./gradlew.bat app:testDebugUnitTest`。
- 设备自检：场景内 → 送/用一件道具 → 回对话发一条消息 → 角色当场对该道具作出反应；商店一键生成、风格切换、稀有度配色、详情弹层、买得起/买不起、AI 失败提示。

## 成功标准

1. 在钱包页买/送/用道具后，**下一轮对话角色会主动、当场地承接该道具**（事件驱动参与感）。
2. 可用道具不再只是被动清单，导演注记包含「主动织入剧情」的指令。
3. AI 失败时不再静默展示被禁烂大街道具，且有明确提示；备用货架不含黑名单道具。
4. 货架不出现半空（去重后凑齐）。
5. （次要）effectPrompt 不再展示给用户；稀有度三档有明显配色；逛店可见余额；点卡片看完整详情；生成不再每次弹对话框。
