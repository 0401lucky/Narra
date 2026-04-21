# 助手（Assistant）模块 UI/功能治理任务清单 · T13

> 源：2026-04-19 助手模块 UI/功能审查（计划见 `C:\Users\lucky0401\.claude\plans\ui-ui-hazy-storm.md`）。
> 状态标记：⬜ 未开始 / 🔄 进行中 / ✅ 完成 / ⏸️ 阻塞
> 归档说明：T1-T12（仓库清理 / 日志 / Gson 单例 / Compose 稳定性等 12 项治理）已于 `524f6cc feat: release 1.5.0-dev` 合入 main，可在 `git log` 中回溯；本文件从 T13 起重新组织。

---

## 审查背景

助手模块是 Settings 一级子领域，6 个屏幕（列表 / 详情 / 基础 / 提示词 / 扩展 / 记忆）+ 共享组件 + `SettingsAssistantCoordinator`。T7、T12 之后 UI 层长期零散迭代，本轮对 25 处问题做了系统梳理并分为 A/B/C/D 四批，按"数据安全 → 交互一致性 → 视觉规范 → 扩展"顺序推进。

### 涉及文件一览
- 6 屏：`ui/screen/settings/AssistantListScreen.kt` / `AssistantDetailScreen.kt` / `AssistantBasicScreen.kt` / `AssistantPromptScreen.kt` / `AssistantExtensionsScreen.kt` / `AssistantMemoryScreen.kt`
- 共享：`ui/screen/settings/AssistantPageComponents.kt` / `AssistantDeleteConfirmDialog.kt` / `ui/component/AssistantAvatar.kt` / `ui/screen/settings/SettingsComponents.kt`
- 状态与数据：`model/Assistant.kt` / `model/AppSettings.kt` / `viewmodel/SettingsAssistantCoordinator.kt` / `viewmodel/SettingsViewModel.kt`
- 路由：`ui/navigation/SettingsAssistantNavRoutes.kt`

---

## T13 · 批次 A（P0 数据/状态安全）✅

**目标**：关掉"默默改写用户数据 / 状态旋转不一致 / 保存后无反馈"三类风险。单个 PR，不跨批次。

### T13-A1 · AssistantPromptScreen 自动保存脏检查 + 显式保存按钮 ✅

**问题**（`AssistantPromptScreen.kt:90-105`）：
`DisposableEffect(Unit) { onDispose { onSave(...) } }` 无条件触发。用户只读浏览也会 `trim()` + 写入，悄悄改开头/结尾空格；屏幕旋转、配置变更都会触发；无"已保存"反馈。

**落地**（**实际耗时 ~30 分钟**）：
- 新增 `@Immutable private data class AssistantPromptDraft`（6 字段：systemPrompt / scenario / greeting / exampleDialogues / creatorNotes / contextMessageSizeText），配套扩展 `Assistant.toPromptDraft()` 与 `AssistantPromptDraft.applyTo(base: Assistant)`。
- Composable 内新增：
  - `var lastSavedDraft by remember(assistant.id) { mutableStateOf(assistant.toPromptDraft()) }`
  - `val currentDraft by remember { derivedStateOf { AssistantPromptDraft(systemPrompt = systemPrompt, ...) } }`
  - `val isDirty by remember { derivedStateOf { currentDraft != lastSavedDraft } }`
  - `SnackbarHostState` + `rememberCoroutineScope()` + `var savedFlashActive`
  - `LaunchedEffect(savedFlashActive)` 1.5s 后把 flash 重置
  - `saveNow`：脏检查通过才 `onSave` → 更新 lastSaved → `savedFlashActive = true` → Snackbar「已保存」
- `DisposableEffect(Unit).onDispose`：改为脏检查兜底（仅当 `pending != lastSavedDraft` 才 `onSave`）。
- `SettingsTopBar` 签名增加 `actionEnabled: Boolean = true`（透传给 `NarraFilledTonalButton.enabled`）；Prompt 页传 `actionLabel = 若 flash 则 "已保存 ✓" 否则 "保存"` + `onAction = saveNow` + `actionEnabled = isDirty || savedFlashActive`。
- `Scaffold` 加 `snackbarHost = { SnackbarHost(snackbarHostState) }`。

**关键 diff**：
- `ui/screen/settings/AssistantPromptScreen.kt`：+75 / -18
- `ui/screen/settings/SettingsComponents.kt`：`SettingsTopBar` 加 `actionEnabled` 参数（+2 / -1）

**验收**：见下方批次回归。

---

### T13-A2 · AssistantMemoryScreen 编辑态 saveable 一致 ✅

**问题**（`AssistantMemoryScreen.kt:61-63`）：
`editingMemory by remember`（非 saveable）+ `memoryDraftContent by rememberSaveable`。旋转后前者归 null、对话框消失，后者仍保留旧 draft —— 再开"新增"会预填上次内容。

**落地**（**实际耗时 ~15 分钟**）：
- `editingMemory` 替换为 `var editingMemoryId by rememberSaveable { mutableStateOf<String?>(null) }`。
- `isCreatingMemory` 也改走 `rememberSaveable`。
- 新增派生值：`val editingMemory: MemoryEntry?` 按当前 `isCreatingMemory` / `editingMemoryId` 计算——新建时用当前作用域拼装临时 MemoryEntry；编辑时从 `memories` 里按 id 解析。旋转后状态由两个 saveable 字段 + `memories` 列表共同推导。
- 新建点击：清 `editingMemoryId`、设 `isCreatingMemory = true` / `memoryDraftContent = ""`。
- 编辑点击：设 `editingMemoryId = memory.id` / `isCreatingMemory = false` / `memoryDraftContent = memory.content`。
- `AlertDialog` 外层从 `if (editingMemory != null) { val targetMemory = editingMemory ?: return ... }` 改为 `editingMemory?.let { targetMemory -> ... }`（消除 Composable 内的 `return` 提前退出副作用）。
- 对话框内所有对 `editingMemory = null` 的写回改为 `editingMemoryId = null`（dismiss / confirm / dismissButton 三处）。

**关键 diff**：
- `ui/screen/settings/AssistantMemoryScreen.kt`：+19 / -16

---

### T13-A3 · AssistantMemoryScreen 保存后回退 ✅

**问题**（`AssistantMemoryScreen.kt:166-181`）：
"保存记忆设置" 按钮 `onClick` 只调 `onSaveAssistant(...)`，不 `onNavigateBack()`。与 Basic / Extensions 不一致，用户无从判断是否写入。

**落地**（**实际耗时 ~2 分钟**）：
- `AnimatedSettingButton(text = "保存记忆设置", onClick = { onSaveAssistant(...); onNavigateBack() })`，对齐 Basic / Extensions 的交互。

**关键 diff**：
- `ui/screen/settings/AssistantMemoryScreen.kt`：+1 / -0

---

### T13-A · 回归 ✅

- `./gradlew.bat app:compileDebugKotlin --rerun-tasks` BUILD SUCCESSFUL（2m 35s，仅 T10 遗留的 `KeyboardArrowRight` deprecated warning，与本次无关）。
- `./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.SettingsAssistantCoordinatorTest"` BUILD SUCCESSFUL。
- 手动复核（待真机回归）：
  1. 设置 → 助手 → 任选 → 提示词：浏览后返回 → 再进 → 前后空格未 `trim()`（A1）。
  2. 改系统提示 → TopBar 保存 → Snackbar「已保存」→ 返回再进内容已落（A1）。
  3. 记忆页新增对话框 → 旋转 → 对话框状态保持（A2）。
  4. 记忆页点"保存记忆设置" → 自动回退到助手详情（A3）。

---

## T13 · 批次 B（P1 交互一致性，后续 PR）⬜

待批次 A 合入后再开单独 PR。覆盖项（按序）：

- **B1 · #6**：AssistantListScreen 点卡片语义 — 卡片整体进详情，右侧加 radio/check 显式"当前"。
- **B2 · #7**：AssistantListScreen 内置与当前双 pill 并列。
- **B3 · #9**：AssistantDetailScreen badge "无可用" → "未挂载 / 未绑定"。
- **B4 · #10**：AssistantExtensionsScreen 章节拆层 —"参数"（条数上限） / "挂载"（生效 + 按书 + 逐条）。
- **B5 · #11**：AssistantBasicScreen 保存前重名校验 + `SettingsAssistantCoordinator` 级别补测 `addAssistant_rejectsDuplicateName`。
- **B6 · #12**：AssistantDetailScreen / List 卡片菜单补"复制助手"入口，接通已有 `duplicateAssistant`。
- **B7 · #3**：AssistantMemoryScreen 新增/编辑对话框补重要度滑块 + 置顶 Switch。
- **B8 · #4**：AssistantExtensionsScreen TopBar 显式"保存"（对齐 A1 的修法），去掉主次按钮并列带来的脏数据风险。
- **B9 · #13 / #14**：Extensions `worldBookMaxEntries` 与 Prompt `contextMessageSize` 的 `supportingText` 明确数值语义 + 超限即时校正。

---

## T13 · 批次 C（P2 视觉规范，后续 PR）⬜

待批次 B 合入后再开单独 PR。覆盖项：

- **C1 · #15**：AssistantListScreen contentPadding 统一为 `SettingsScreenPadding = 20.dp`。
- **C2 · #16**：`SettingsPalette` 预定义 `borderStrong = 0.45` / `borderSoft = 0.25`；屏幕层停止自由 `copy(alpha=...)`。
- **C3 · #17**：抽共享 `parsePositiveIntOrDefault`（Extensions + Memory 合并）。
- **C4 · #18**：AssistantMemoryScreen 的"编辑 / 置顶 / 删除" 改 `NarraTextButton` 或加 `minimumInteractiveComponentSize`。
- **C5 · #19**：AssistantListScreen 搜索框 `trailingIcon` 清空按钮。
- **C6 · #20**：AssistantDetailScreen Hero Pills 改 `FlowRow`。
- **C7 · #22**：AssistantPromptScreen Greeting bubble 深色模式改 `surfaceContainerHighest`。
- **C8 · #23**：AssistantMemoryScreen `AssistantMemorySettingRow` 切 `SettingsListRow(trailingContent = ...)`。

---

## T13 · 批次 D（扩展与 a11y，后续 PR）⬜

- **D1 · #21**：AssistantPromptScreen 示例对话删除加 Snackbar 撤销 / 支持拖拽排序。
- **D2 · #24**：给 `AssistantCardMinimal`、`AssistantHeroPanel`、`AssistantDeleteConfirmDialog`、`AssistantMemoryEntryCard` 补 `@Preview`；必要的 Compose UI Test。
- **D3 · #25**：TalkBack 可访问性 — SettingsStatusPill 加 `stateDescription`，内置/当前状态可读。

---

## 不在 T13 范围

- 助手与聊天的联动（`ChatViewModel.activeAssistant()`）：功能域，另起 T14。
- Roleplay 场景下的"助手/角色混用"弹窗：独立议题。
- 头像加载性能（`AssistantAvatar` 每次 `rememberUserProfileAvatarState`）：性能议题，另评。

---

## T14 · 剧情日记生成超时但服务端已成功 ✅

**源**：2026-04-19 用户报告 | **预计**：30 分钟 | **实际**：~20 分钟

**问题**：
- 日记生成点"生成"后客户端显示 `timeout`，但 API 后台能看到完整输出。
- 根因：`ApiServiceFactory.kt:45` OkHttp `readTimeout = 120s`；`RoleplayDiaryPromptService.generateRoleplayDiaries()` 走 Retrofit 非流式 `OpenAiCompatibleApi`（`PromptExtrasCore.kt:50` → `apiServiceFactory.create(...)`）。日记 prompt 要求模型一次生成 5-8 篇 × ≥300 字 + JSON，思考模型 / 长上下文下服务端首字节返回 >120s → OkHttp 抛 `SocketTimeoutException("timeout")` → `RoleplayViewModel.kt:715-726` catch 后把 `throwable.localizedMessage = "timeout"` 显示给用户；但服务端仍在完成生成，因此 API 后台看得到完整响应。

**落地**：
- `ApiServiceFactory.kt`：
  - 抽出 `DEFAULT_READ_TIMEOUT_SECONDS = 120L` 和 `LONG_READ_TIMEOUT_SECONDS = 600L` 两个 companion 常量；原 4 处 `.readTimeout(120, ...)` 统一走 `DEFAULT_READ_TIMEOUT_SECONDS`。
  - 新增 `createLongRunning(baseUrl, apiKey): OpenAiCompatibleApi` 和 `createLongRunningAnthropic(baseUrl, apiKey): AnthropicApi`，与 `create` / `createAnthropic` 同结构但 `readTimeout = LONG_READ_TIMEOUT_SECONDS = 600s`；配独立缓存字段避免与快客户端相互踩。
- `PromptExtrasCore.kt`：
  - 两个 default provider 分别切 `createLongRunning` / `createLongRunningAnthropic`。
  - 影响面：所有 extras（标题 / 聊天建议 / 摘要 / 记忆提议 / 剧情建议 / 日记 / 礼物图 Prompt / 手机内容生成）统一受益；主聊天（`AiGateway`）与 SSE 流式路径未改动。
- 现有测试都通过 `apiServiceProvider` 注入 fake，不会意外接入 long-running 客户端；全量 `app:testDebugUnitTest` 回归绿。

**关键 diff**：
- `data/remote/ApiServiceFactory.kt`：companion 常量 + 缓存字段 + `createLongRunning{,Anthropic}` 两个新方法（~100 行新增）
- `data/repository/ai/PromptExtrasCore.kt`：2 个默认 provider 的实现改调 long-running 工厂方法

**验收**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest` BUILD SUCCESSFUL（全量）。
- 手动：真机 → 剧情某个长对话触发日记生成 → 模型较慢时客户端不再 120s 抛 timeout，成功落库。

**遗留观察**：
- 600s 已覆盖绝大多数思考模型；若未来遇到仍超时的 provider，可在 `ApiServiceFactory` 把常量调到 900s，无需动其他代码。
- 当前客户端无"预计等待时间"提示；用户感知上从"120s 后报错"变为"可能等 3-5 分钟才出结果"。若产品上要做"生成中"进度提示，属于 UI 增量，独立于本修复。

---

## T15 · 世界书（WorldBook）模块 UI / 功能治理 ⬜

> 源：2026-04-21 世界书模块 UI/功能审查（详报告见 `.claude/worldbook-review-report-2026-04-21.md`）。
> 状态标记：⬜ 未开始 / 🔄 进行中 / ✅ 完成 / ⏸️ 阻塞
> 审查结论：综合评分 **64/100**，建议**退回**；问题不在配色，而在"信息堆砌 + 功能与 UI 脱节 + 数据层小坑"。
> 动手节点：**2026-04-22 起**。

### 涉及文件一览

- 3 屏：`ui/screen/settings/worldbook/WorldBookListScreen.kt` / `WorldBookBookDetailScreen.kt` / `WorldBookEditScreen.kt`
- ViewModel：`viewmodel/WorldBookViewModel.kt`
- 数据层：`data/local/worldbook/WorldBookDao.kt` / `WorldBookEntryEntity.kt` / `data/repository/context/WorldBookRepository.kt` / `TavernWorldBookAdapter.kt`
- 运行时：`context/WorldBookMatcher.kt` / `WorldBookScopeSupport.kt` / `data/repository/ai/tooling/SearchWorldBookTool.kt`
- 路由：`ui/navigation/SettingsDataNavRoutes.kt` / `NavigationViewModelOwners.kt` / `AppRoutes.kt:22-24`
- 模型：`model/WorldBookEntry.kt` / `model/Assistant.kt`（`linkedWorldBookIds` / `linkedWorldBookBookIds`）

---

### T15 · 批次 A（P0 功能缺失 + 数据安全）✅

**目标**：补齐"代码写了但 UI 用不上"的字段，修掉全表扫 + 非事务 + 静默 trim，恢复正则在 UI 层的可用性。单个 PR，不跨批次。

#### T15-A1 · 编辑页补齐 selective / secondaryKeywords / caseSensitive / insertionOrder 入口 ✅

**问题**（`WorldBookEditScreen.kt:55-77`）：
编辑页只暴露 title / content / keywords / aliases / sourceBookName / priority / enabled / alwaysActive / scopeType / scopeId。
- `secondaryKeywords` 导入后落库但编辑页看不到，列表卡有"次级键 N" pill 却无法编辑。
- `selective` 根本无 UI 开关，"次级键 / 附加键"命名切换也解释不清。
- `caseSensitive` 列表能看到 pill，但编辑页无开关。
- `insertionOrder` 无 UI 入口，用户没法调整同优先级的注入顺序。

**落地**：
- "命中规则"分两栏：主键（keywords + aliases + 正则提示） / 次级键（selective 开关 + secondaryKeywords 输入器 + caseSensitive 开关）；selective 关闭时次级键输入框置灰但保留原值。
- insertionOrder 挪进"状态"区块的"高级"折叠块（默认收起），填入框 placeholder "越小越靠前（默认 0）"。
- `parseCommaSeparated` 当前会把 `/foo,bar/i` 这类正则拆散；本次一起修（见 A2）。

**预计**：90 分钟 | **实际**：~45 分钟（跟 A2 Chip 输入器串起来，去掉 parseCommaSeparated；StringListSaver 走 rememberSaveable 保证旋转/配置变更不丢）。

**关键 diff**：
- `ui/screen/settings/worldbook/WorldBookEditScreen.kt`：+98 / -32（commit 28b5358）

**回归**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest --tests '*WorldBookViewModelTest'` + `*WorldBookRepositoryTest` + `*KeywordChipInputTest` BUILD SUCCESSFUL。
- 手动待真机回归：导入带 `keysecondary` / `selective` 的 Tavern 世界书 → 编辑页应能看到并改动；关 selective 后次级关键词输入框置灰，保留原值。

#### T15-A2 · 关键词输入改 Chip 输入器，正则不再被逗号切开 ✅

**问题**（`WorldBookEditScreen.kt:377-381`）：
`parseCommaSeparated(",", "，")` 对"包含逗号的正则字面量"会切散，`WorldBookMatcher.parseRegexLiteral` 全套能力在 UI 层白写。

**落地**：
- 封装 `KeywordChipInput` 组件（`ui/component/worldbook/KeywordChipInput.kt` 新建），回车 / `,`（正则字面量内不触发）/ 失焦提交为 chip。
- chip 展示时对 `/.../flags` 形态用 `palette.accentSoft` + 「正则」角标；点击 chip 可再次编辑。
- 复用到 aliases / secondaryKeywords。
- 老数据仍用 `List<String>` 存储，不动 Entity。

**预计**：2 小时 | **实际**：~75 分钟。

**关键 diff**：
- `ui/component/worldbook/KeywordChipInput.kt`：新建（~205 行）
- `ui/screen/settings/worldbook/WorldBookEditScreen.kt` 用 KeywordChipInput 替代手写 OutlinedTextField + parseCommaSeparated（见 A1）
- `test/.../ui/component/worldbook/KeywordChipInputTest.kt`：新建 7 个单元测试覆盖 split / looksLikeRegex
- commit 622aa8d

**回归**：
- `./gradlew.bat app:testDebugUnitTest --tests 'com.example.myapplication.ui.component.worldbook.KeywordChipInputTest'` BUILD SUCCESSFUL（7 用例全绿）。

#### T15-A3 · ViewModel 去掉双订阅（entries + uiState.entries）✅

**问题**（`WorldBookViewModel.kt:25-42`）：
`val entries: StateFlow<...>` 和 `init { entries.collect { _uiState.update { copy(entries=...) } } }` 两条路径并存；后者把 `stateIn(WhileSubscribed(5_000))` 的冷启动时机绕过去了，ViewModel 构造起就常开上游 Flow。

**落地**：
- 删除 `entries` 字段，`uiState` 直接从 `repository.observeEntries()` + `isSaving` / `message` 的 `combine` 产生。
- message 改成一次性事件（`Channel<String>` 或 `SharedFlow<String>`，复用 T13-A3 的路径），UI 通过 `LaunchedEffect` 收到后 `snackbar.showSnackbar` 再自动失效。
- 同步更新 `WorldBookViewModelTest`，新增"message 在 3 秒后自动清 / 不会被 entries 更新覆盖"两个用例。

**预计**：90 分钟 | **实际**：~40 分钟。

**关键 diff**：
- `viewmodel/WorldBookViewModel.kt`：删除 `val entries` 独立字段和 init block 双订阅；引入 `WorldBookInternalState` + `combine(observeEntries(), internal)`；`saveEntry` / `deleteEntry` / `renameBook` / `deleteBook` 四处都走 `runCatching` + 成功/失败文案双路分发（消息仍靠现有 `consumeMessage` 清理，保留现有调用方不动）。
- `test/.../viewmodel/WorldBookViewModelTest.kt`：新增 `uiState_entries_followsRepositoryUpdates` / `saveEntry_emitsSavedMessage` 两个用例；所有旧测试补 `launch { viewModel.uiState.collect { } }` 挂订阅，保证 `WhileSubscribed(5000)` 不提前收流。
- commit d344448

**回归**：
- `./gradlew.bat app:testDebugUnitTest --tests 'com.example.myapplication.viewmodel.WorldBookViewModelTest'` 6 用例全绿。

#### T15-A4 · renameBook / deleteBook 走事务 + 失败反馈 ✅

**问题**（`WorldBookViewModel.kt:71-117` / `WorldBookRepository.kt:53-64`）：
逐条 `upsertEntry` / `deleteEntry`，中间失败留下"一本书里部分改了、部分没改"的脏状态；且 `upsertEntry` 每次都 `listEntries()` 全表扫。

**落地**：
- DAO 补两个方法：`@Query("UPDATE worldbook_entries SET sourceBookName = :newName, updatedAt = :now WHERE bookId = :bookId")` / `@Query("DELETE FROM worldbook_entries WHERE bookId = :bookId")`。
- Repository 增加 `renameBook(bookId, newName)` / `deleteBook(bookId)` 直接走 SQL，`upsertEntry` 不再 `listEntries()`（用 `entry.bookId.ifBlank { deriveWorldBookBookId(sourceBookName) }` 即可得到确定性 ID）。
- ViewModel `runCatching { ... }`；失败通过 A3 的事件通道反馈 "重命名失败，请重试"。
- Room schema 不变，不升版本。

**预计**：90 分钟 | **实际**：~50 分钟。

**关键 diff**：
- `data/local/worldbook/WorldBookDao.kt`：`updateBookName` / `deleteByBookId`（返回 Int 行数）。
- `data/repository/context/WorldBookRepository.kt`：接口加两个方法；默认实现直接走 SQL；`toEntity` 去掉 `existingEntries` 同胞查询（原 upsertEntry 会 `listEntries()` 全表扫）；`EmptyWorldBookRepository` 同步补实现。
- `test/.../testutil/FakeWorldBookRepository.kt`：加 `failNextBookMutation` 注入失败；`RecordingWorldBookDao` 在 `WorldBookRepositoryTest` 补两个方法。
- `viewmodel/WorldBookViewModel.kt`：`renameBook` / `deleteBook` 改调 repository 接口 + `runCatching` + 成功/失败双文案。
- `test/.../viewmodel/WorldBookViewModelTest.kt`：新增两个失败回归用例。
- commit 2b15a55

**回归**：
- `./gradlew.bat app:testDebugUnitTest --tests '*WorldBookViewModelTest' --tests '*WorldBookRepositoryTest'` BUILD SUCCESSFUL。

#### T15-A5 · Repository 停止对 title / content 做 trim ✅

**问题**（`WorldBookRepository.kt:106-107`）：
保存时对 title / content 做 `trim()`，和助手 T13-A1 "自动保存擦空格" 同类问题。`WorldBookEditScreen.kt:302-303` 自己已经 trim 过，Repository 重复 trim 只会让边缘场景（用户故意保留末行）出错。

**落地**：
- 删除 Repository 的 `title.trim()` / `content.trim()`（保留 `sourceBookName.trim()` / `scopeId.trim()`，它们是 id 级别字段）。
- `normalizeStringList` 保留（空关键词没意义）。
- 补 `WorldBookRepositoryTest`（新建）：`entry 末尾带 3 个换行 → 保存再读 → 仍保留换行`。

**预计**：45 分钟 | **实际**：~25 分钟（先写失败测试（ComparisonFailure 命中 trim 行为）再改 Repository，TDD 路径）。

**关键 diff**：
- `data/repository/context/WorldBookRepository.kt`：`title = entry.title` / `content = entry.content`（不再 `.trim()`）。
- `test/.../data/repository/context/WorldBookRepositoryTest.kt`：新建 `upsertEntry_preservesTitleAndContentWhitespace` 回归用例；内置 `RecordingWorldBookDao` 简化依赖。
- commit 2ee2d09

**回归**：
- `./gradlew.bat app:testDebugUnitTest --tests 'com.example.myapplication.data.repository.context.WorldBookRepositoryTest'` BUILD SUCCESSFUL。

#### T15-A6 · CONVERSATION scope 改为"从会话中选择" ✅

**问题**（`WorldBookEditScreen.kt:251-262`）：
裸文本框让用户手填 conversationId，普通用户压根拿不到，熟手也容易打错 → 作用域匹配静默失败，排查成本极高。

**落地**：
- 编辑页传入 `conversations: List<ConversationSummary>`（NavGraph 从 `ConversationRepository.observeConversations()` 拿一次 snapshot 并过滤 archived）。
- 作用域 = CONVERSATION 时用 `ExposedDropdownMenuBox`，选项展示 "会话标题（更新时间相对值）"；`scopeId` 存所选 conversation.id。
- 用户新建会话后回来编辑，刷新列表靠 NavGraph 的 `collectAsStateWithLifecycle`。

**预计**：2 小时 | **实际**：~55 分钟。`Conversation` 本身没有 archived 字段，简化为直接全量按 `updatedAt` 倒序展示。

**关键 diff**：
- `ui/screen/settings/worldbook/WorldBookEditScreen.kt`：签名加 `conversations: List<Conversation> = emptyList()`；抽 `ConversationScopePicker` 私有 Composable，走 `ExposedDropdownMenuBox` + `DateUtils.getRelativeTimeSpanString`；空会话时降级为只读占位。
- `ui/navigation/SettingsDataNavRoutes.kt`：`SETTINGS_WORLD_BOOK_EDIT` 路由新增 `appGraph.conversationRepository.observeConversations().collectAsStateWithLifecycle(initialValue = emptyList())` 注入。
- commit 66e4ee3

**回归**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest` BUILD SUCCESSFUL（全量）。

---

### T15 · 批次 B（P1 UI 重做 + 匹配体验）⬜

**目标**：把列表页 / 书详情 / 编辑页的信息架构重做一次，治掉"gpt 审美"；匹配器补齐 scanDepth + 正则 caseSensitive + CJK 整词。

- **B1 · 列表页信息架构重建**（`WorldBookListScreen.kt` 全量改，~6h）：
  - 去掉 `SettingsPageIntro("世界书")` 冗余标题；
  - 顶栏 sticky 搜索 + 筛选 chip（作用域 / 状态 / 书）；
  - 书卡重设计：左侧彩色书脊（`bookId.hashColor`）+ 右侧条目数 / 启用 / 最近更新相对时间；
  - 条目卡重设计：title + 2 行 content + **只保留 3 个 chip**（作用域 / 关键词前三真词 / 状态）；
  - 分组标题改 `stickyHeader`；
  - 空态按 "整体空 / 筛选无结果 / 搜索无结果" 三套文案 + 插画/图标做差异化。

- **B2 · 编辑页信息分层 + 顶栏保存**（`WorldBookEditScreen.kt`，~4h）：
  - "基本信息 / 命中规则 / 状态 / 作用域" 四段折叠，默认展开前两段；
  - `SettingsTopBar` 右上角加"保存" action（禁用态同 `!canSave`）；
  - 删除按钮移到底部独立危险区块（红字 + 二次弹窗），与保存区完全隔离；
  - content 区加"全屏编辑" icon button，`ModalBottomSheet` 展开大编辑区。

- **B3 · "所属世界书" 改下拉**（`WorldBookEditScreen.kt:127-136`，~1h）：
  - `ExposedDropdownMenuBox`，列表来自 `entries.map { it.sourceBookName }.filter { it.isNotBlank() }.distinct()`；
  - 同时支持输入新的，不强制二选一。

- **B4 · ATTACHABLE scope 加 "去助手挂载" 跳转**（`WorldBookEditScreen.kt:222-228`，~45min）：
  - 提示行下挂一行 `TextButton("去助手页挂载")`，带着 `entryId` / `bookId` 跳到助手列表，到达后显示 `TipCard` 指向具体挂载位置。

- **B5 · 书详情页重命名 / 删除流程打磨**（`WorldBookBookDetailScreen.kt` + `SettingsDataNavRoutes.kt`，~90min）：
  - 重命名成功后**停在详情页**并显示 snackbar，再手动返回；
  - 重命名按钮键盘回车可提交；
  - 删除确认改用项目统一 `ConfirmDestructiveDialog`（新建或沿用已有），不再用裸 `material3.AlertDialog`。

- **B6 · Matcher 支持 scanDepth**（`WorldBookMatcher.kt:50-72`，~90min）：
  - `scanDepth` 作为 Assistant 级配置（默认 2）；
  - `buildSourceText` 拼当前 + 最近 scanDepth 条消息（含 assistant）；
  - 拼接长度做上限（如 2000 char）防止 token 预算被冲；
  - `WorldBookMatcherTest` 新增用例：`scanDepth=1 命中上一条 user / scanDepth=2 命中倒数第二条 assistant / scanDepth=0 仅当前`。

- **B7 · 正则尊重 caseSensitive**（`WorldBookMatcher.kt:120-129`，~30min）：
  - 解析正则时如果 entry.caseSensitive == false，自动补 `IGNORE_CASE`；
  - 正则解析失败 `logFailure("WorldBookMatcher")` 一行，便于排查；
  - 测试：`/Foo/ + caseSensitive=false + "foo" → hit`。

- **B8 · CJK 整词匹配（默认开）**（`WorldBookMatcher.hasKeywordHit`，~2h）：
  - 新增 `matchesContainsCjkAware` 工具，把 CJK 字符前后非 CJK 视为词边界；
  - 编辑页加"匹配模式：包含 / 整词 / 正则"三选一，默认"整词"对 CJK 条目；
  - 向后兼容：老数据无字段，按"整词"处理；用户可主动切回"包含"。

- **B9 · SearchWorldBookTool 加 total / truncated 字段 + 描述更新**（`SearchWorldBookTool.kt`，~45min）：
  - payload 增加 `total`（总命中条数） / `truncated`（本次是否截断）/ `content_truncated`（content 是否超 400 字被砍）；
  - description 里说明"此为全文搜索，不等同于自动注入时的关键词命中"。

---

#### T15-B1 · 列表页信息架构重建 ✅

**落地**：
- 纯函数层（新建 `ui/screen/settings/worldbook/WorldBookListStyle.kt`，~170 行）：
  - `bookSpineColor(bookId)` — HSL 色相轮转（saturation=0.52, lightness=0.62），同 bookId 稳定色相。
  - `firstRealKeywords(entry, limit=3)` — 按 keywords → aliases → secondaryKeywords 顺序去重取前 N 真词；trim 空白，正则字面量（`/foo,bar/i`）作为整体保留。
  - `formatRelativeTime(epochMillis, now)` — 刚刚 / N 分钟前 / N 小时前 / N 天前 / yyyy-MM-dd 兜底；允许注入 now 以便 JVM 测试；时钟偏移兜底为"刚刚"。
  - `WorldBookListScopeFilter` / `WorldBookListStatusFilter` 枚举（单选互斥）+ `filterEntries(entries, search, scope, status, bookIdFilter)` AND 组合 + `activeFilterCount(...)` 统计。
- ViewModel 文案契约（`WorldBookViewModelTest.kt`）：补 `renameBook_successMessageContainsRenameKeyword` / `deleteBook_successMessageContainsDeleteKeyword` 两条断言（既有文案"世界书已重命名"/"整本世界书已删除"已含关键字，不改实现）。
- UI 层（`WorldBookListScreen.kt`，重写 ~200 行）：
  - 条目卡 chip 精简为 3 枚：作用域 / 关键词预览（"关键词：主角 · 配角 · 路人"） / 状态（停用 > 常驻注入 > 含正则，最多 1 枚）。
  - 书卡加左侧 6dp 彩色书脊 + 条目名 preview + FlowRow pill（`N 条条目` / `启用 K`（K ≠ N 时） / 相对时间 / `含正则`）。
  - 搜索框加 × 清空按钮（仅非空时显示）。
  - `rememberSaveable(stateSaver = ...)` 三个筛选 state（scope / status / bookId），走 `Saver.save/restore via enum.name`。
  - 搜索 + 三行筛选 chip + 两处分组标题走 `LazyListScope.stickyHeader`（Compose BOM 2026.02.01 已稳定，无需 @OptIn）。
  - 三套空态（`GLOBAL_EMPTY`：引导导入；`FILTERED_EMPTY`：清筛选按钮；`SEARCH_EMPTY`：清空搜索按钮）。
  - 挂 `TopAppSnackbarHost`，仅消费含"删除"关键字的消息；"重命名"留给书详情页。
  - 顺手把 `entryHasRegexKeyword` 的实现从 `startsWith('/')` 统一到 `looksLikeWorldBookRegexLiteral`（与筛选一致，避免 UI 和筛选 drift）。

**预计**：~6h | **实际**：~5.5h（纯函数层 TDD 5 次 commit，UI 重构 7 次 commit，全程零回归）。

**关键 diff**：
- `ui/screen/settings/worldbook/WorldBookListStyle.kt`（新建）（commits: b6b6006 chore 骨架 → 6c1447b bookSpineColor → cb83fce firstRealKeywords → a3d5439 formatRelativeTime → 1d1a865 筛选枚举+filterEntries）
- `ui/screen/settings/worldbook/WorldBookListStyleTest.kt`（新建，14 用例）
- `ui/screen/settings/worldbook/WorldBookListScreen.kt`（重写：8dab707 条目 chip → f455e31 书卡书脊 → e2167a9 搜索清空 → 536a77c 筛选 chip → c5174a0 sticky → 08f665a 空态 → 166d6d5 snackbar）
- `viewmodel/WorldBookViewModelTest.kt`（commit 3a78e7e，契约断言）

**回归**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest` BUILD SUCCESSFUL（全量 ~660 个 + 本次新增 16 个，全绿）。
- 手动待真机回归（13 项）：筛选 chip 切换、搜索 × 清空、sticky 粘顶、三套空态、书脊色稳定性、重命名 IME Done、删除对话框红字、删除后自动返回列表 + snackbar。

#### T15-B5 · 书详情页重命名 / 删除流程打磨 ✅

**落地**：
- `WorldBookBookDetailScreen.kt`：
  - 重命名输入框 `keyboardOptions = ImeAction.Done` + `keyboardActions.onDone` 在 `canRename` 时提交。
  - `LaunchedEffect(bookName)` 在上游书名变化（重命名成功）时把 `renameText` 同步回退。
  - 删除确认从裸 `material3.AlertDialog` 换成 `NarraAlertDialog(isDestructive = true)`，红字"确认删除"。
  - 挂 `TopAppSnackbarHost`，仅消费含"重命名"关键字的消息。
- `SettingsDataNavRoutes.kt`：
  - List 路由透传 `uiMessage = worldBookState.message` + `onConsumeMessage = viewModel::consumeMessage`。
  - BookDetail 路由同理；新增 `LaunchedEffect(bookEntries.isEmpty())`：在整本被删空后 `navController.popBackStack()`，snackbar 由列表页接管显示。
  - `onRenameBook` / `onDeleteBook` 去掉立即 `popBackStack()` — 交给 LaunchedEffect，避免 snackbar 丢失。

**预计**：~90min | **实际**：~50min（与 B1 的 snackbar 改动合成一次 commit）。

**关键 diff**：
- `ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt`（commit 166d6d5）
- `ui/navigation/SettingsDataNavRoutes.kt`（commit 166d6d5）

**回归**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest` BUILD SUCCESSFUL。
- 手动待真机回归：输入新名 → 回车提交 → 停留详情页 + snackbar；点击删除 → NarraAlertDialog 红字按钮；确认删除 → 自动返回列表页 + snackbar。

---

### T15 · 批次 C（P1 / P2 兼容性 + 细节）⬜

- **C1 · TavernWorldBookAdapter 增加 extrasJson 兜底**（`TavernWorldBookAdapter.kt` + Entity，~3h）：
  - Entity 加 `extrasJson: String = "{}"`；Room schema v28（单独一次 +1 提交）；
  - Adapter 把未识别字段原样存进去；
  - 导出回 Tavern 时可无损还原；
  - 迁移：老数据 `extrasJson = "{}"`。

- **C2 · Tavern 导入稳定 ID 优先用 uid**（`TavernWorldBookAdapter.kt:78-90`，~30min）：
  - uid 非空：`stableId = UUID.nameUUIDFromBytes("${bookName}|${uid}")`；
  - uid 空时 fallback 到现有 hash；
  - 二次导入同一本书微改内容不再增生孪生条目。

- **C3 · Entity 默认时间戳修正**（`WorldBookEntryEntity.kt`，~20min）：
  - `createdAt` / `updatedAt` 默认 `System.currentTimeMillis()`（通过 ColumnInfo defaultValue 或 DAO 层写入时兜底）；
  - 排序规则 `createdAt ASC` 下，默认 0 的条目不再永远排最前。

- **C4 · DAO 查询去重复**（`WorldBookDao.kt`，~20min）：
  - `observeEntries()` / `listEntries()` SQL 完全相同，抽常量或合用；降低升级排序规则时的漏改风险。

- **C5 · 文案统一**（全模块，~30min）：
  - snackbar 固定称呼"世界书" + 动作（保存 / 删除 / 重命名 / 整本删除），不再混用"这本书" / "整本世界书"；
  - 列表 pill "常驻" 改为 "常驻注入"（或两处都改成"常驻"），与编辑页对齐；
  - "次级键"/"附加键" 固定为"次级关键词"（selective 关闭时叫"附加关键词"的说法作废）。

- **C6 · buildWorldBookBooks 分组 key 用 resolvedBookId()**（`WorldBookListScreen.kt:404-428`，~20min）：
  - 避免"书名字段被清空但 bookId 还在"的条目变成独立条目；
  - 同步更新 `WorldBookListScreenGroupingTest`。

- **C7 · 所有 `contentDescription = null` 补语义**（全 UI，~45min）：
  - 导入 / 搜索 / 书图标 / 菜单项 / 删除按钮；
  - 和 T13-D1 风格对齐。

- **C8 · 自动过期 snackbar**（复用 T15-A3 的事件通道，~10min）：
  - `LaunchedEffect` 中 `delay(3_000); consumeMessage()`。

---

### T15 · 批次 D（P2 增强，可选）⬜

- **D1 · 编辑页"试命中"按钮**（~3h）：基于当前关键词对最近 N 条消息做一次模拟命中，实时显示 hit / miss 列表，写世界书时所见即所得。
- **D2 · DAO 按 scope 过滤查询**（~1h）：减少热路径内存过滤；需要和 Matcher 使用方对齐参数。
- **D3 · TypedFactory 公共抽象**（~1h）：把各 ViewModel 的 `@Suppress("UNCHECKED_CAST")` 下沉到共用基类，配合 T12 稳定性治理收口。
- **D4 · Tavern 语义增量建模**（`probability` / `depth` / `position` / `logic` / `role`，~5h）：Entity + UI + Matcher 全链路补齐，和 C1 的 extrasJson 结合，把"能导入 → 能可视化 → 能导出"的闭环补全。

---

### 执行顺序建议

| 阶段 | 任务 | 预计 |
|---|---|---|
| 功能缺失 + 数据安全 | T15-A1 → A6 | 1 天 |
| UI 重做 + 匹配体验 | T15-B1 → B9 | 2 天 |
| 兼容性 + 细节 | T15-C1 → C8 | 1 天 |
| 可选增强 | T15-D1 → D4 | 1-1.5 天 |

**落地原则**：批次 A 作为一个 PR 合入，批次 B 按"列表重做"+"编辑重做 + 匹配"拆成 2 个 PR 并行，批次 C 作为收尾 PR，批次 D 按需追加；每完成一条 ⬜ → ✅，并在条目下追加"实际耗时 / 关键 diff / 回归命令"。

**回归命令**（每个 PR 至少跑完前三项）：
- `./gradlew.bat app:compileDebugKotlin`
- `./gradlew.bat app:testDebugUnitTest --tests "*WorldBook*"`
- `./gradlew.bat app:testDebugUnitTest`（A / B 批次必须全量）
- 手动：导入一本带 `keysecondary` + `selective` 的 Tavern 世界书 → 编辑页应能看到并改动次级关键词 / selective / caseSensitive → 保存 → 列表卡 pill 正确刷新。

---

## 执行顺序建议

| 阶段 | 任务 | 预计 |
|---|---|---|
| 数据安全 | T13-A1 / A2 / A3 | 半天 |
| 交互一致性 | T13-B1 → B9 | 1-1.5 天 |
| 视觉规范 | T13-C1 → C8 | 半天 |
| 扩展与 a11y | T13-D1 → D3 | 半天 |
| 世界书模块治理 | T15-A / B / C / D | 5-5.5 天 |

每完成一条，改状态为 ✅，并在该条下追加"实际耗时 / 关键 diff / 回归命令"。
