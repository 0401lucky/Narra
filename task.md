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

### T15 · 批次 B（P1 UI 重做 + 匹配体验）✅

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

#### T15-B6 · Matcher 支持 scanDepth ✅

**落地**：
- Assistant 新增字段 `worldBookScanDepth: Int = 2`（`DEFAULT_WORLD_BOOK_SCAN_DEPTH = 2`），`AppSettingsStore.normalizeAssistant` 兜底负值。
- 抽出纯函数 `buildWorldBookSourceText(userInputText, recentMessages, scanDepth, maxChars = 2000)`（`context/WorldBookSourceTextBuilder.kt`）：
  - `scanDepth <= 0` → 只看 `userInputText`，空则回退到最近一条 USER；
  - `scanDepth > 0` → 当前输入 + 最近 N 条消息（含 assistant）按时间顺序（旧→新）拼接；
  - 总长度超 `maxChars` 从头截断，保证最新输入留在末尾。
- `WorldBookMatcher.buildSourceText` 私有方法删除，直接调用上述纯函数；Matcher 内通过 `assistant?.worldBookScanDepth` 拼 scan 窗口。
- `AssistantExtensionsScreen` 新增"世界书扫描深度（scanDepth）"数字输入框，保存路径写字段。
- 调整 2 条既有 matcher 测试显式声明 `worldBookScanDepth = 0` 以保留老契约；新增 3 条 scanDepth 测试（0/1/2）+ 5 条 Builder 纯函数测试。

**预计**：~90min | **实际**：~50min（纯函数 TDD + 老测试契约调整）。

**关键 diff**：
- `model/Assistant.kt`（ff5c033）
- `data/local/AppSettingsStore.kt`（ff5c033）
- `context/WorldBookSourceTextBuilder.kt` 新建 + 5 条 `Test`（76f2d7b，含顺手补 `PromptContextAssemblerTest` 的预存缺陷）
- `context/WorldBookMatcher.kt` + `WorldBookMatcherTest.kt`（a1fac50）
- `ui/screen/settings/AssistantExtensionsScreen.kt`（92b78d5）

**回归**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL
- `./gradlew.bat app:testDebugUnitTest` BUILD SUCCESSFUL（原 ~660 + 本项新增 8 条）
- 待真机：scanDepth=0/2 切换后触发世界书注入差异；默认 2 的新助手进入扩展页能正确显示。

#### T15-B7 · 正则尊重 caseSensitive ✅

**落地**：
- `parseRegexLiteral(pattern, caseSensitive)` 签名扩展；解析时若 entry.caseSensitive=false 自动补 `RegexOption.IGNORE_CASE`（已显式写 `/i` 的情况尊重原写法）。
- `runCatching { Regex(body, options) }.logFailure("WorldBookMatcher") { ... }.getOrNull()` 解析失败落一行日志，便于排查。
- 新增 3 条测试：默认小写命中、caseSensitive=true 保持严格、`/X/i` 显式 flag 覆盖。

**预计**：~30min | **实际**：~15min。

**关键 diff**：
- `context/WorldBookMatcher.kt` + `WorldBookMatcherTest.kt`（f92603a）

**回归**：
- `./gradlew.bat app:testDebugUnitTest --tests "...WorldBookMatcherTest"` 全绿。
- 待真机：`/Foo/` 条目 caseSensitive=off 时"foo bar"能命中；on 时不命中。

#### T15-B8 · CJK 整词匹配（默认开）✅

**落地**：
- 新增枚举 `WorldBookMatchMode { CONTAINS, WORD_CJK, REGEX }`（`model/WorldBookMatchMode.kt`）+ `fromStorageValue`；
- 新增 `matchesContainsCjkAware(pattern, source, caseSensitive)` 纯函数（`context/WorldBookMatcherTextUtils.kt`）：
  - 边界判断用 ASCII-only（`isAsciiLetterOrDigit()`），让 CJK 字符天然成为英文关键词的词边界；
  - 关键词两端都是 CJK 时退化为 contains（中文没有词边界概念）；
  - 头/尾非 CJK 时要求该侧字符不是 ASCII 字母数字；
  - 7 条独立纯函数单测。
- `WorldBookEntry`/`WorldBookEntryEntity` 加 `matchMode: WorldBookMatchMode` / `matchMode: String`；`WorldBookRepository.toDomain/toEntity` 映射；Room v27→v28 迁移（`MIGRATION_27_28` 给 `worldbook_entries` ALTER ADD `matchMode TEXT NOT NULL DEFAULT 'word_cjk'`）；`ChatDatabaseMigrationRegistryTest` 同步更新。
- `WorldBookMatcher.matchesPattern` 按 matchMode 分派；`/.../` 字面量语法跨 matchMode 保留 escape hatch；REGEX 模式下整条 keyword 不按逗号拆。
- 编辑页"命中规则"段最前面新增"匹配模式：包含 / 整词 / 正则"FilterChip 三选一 + 动态说明文案；Saver 基于 `enum.name`。
- 新增 matcher 测试 3 条（CONTAINS 保持子串、WORD_CJK 要求 latin 边界、REGEX 整条 keyword 当正则）。

**预计**：~2h | **实际**：~75min（纯函数 + Room 迁移 + UI 三段并行推进）。

**关键 diff**：
- `model/WorldBookMatchMode.kt` + `context/WorldBookMatcherTextUtils.kt` + `TextUtilsTest.kt`（8e750dc）
- `model/WorldBookEntry.kt` + `data/local/worldbook/WorldBookEntryEntity.kt` + `WorldBookRepository.kt` + `ChatDatabase.kt`（v28）+ `ChatDbMigrations.kt`（MIGRATION_27_28）+ `ChatDatabaseMigrationRegistryTest.kt`（5d35d20）
- `context/WorldBookMatcher.kt` + `WorldBookMatcherTest.kt`（aaa6775）
- `ui/screen/settings/worldbook/WorldBookEditScreen.kt` 加 matchMode FilterChip 组（b151765）

**回归**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL
- `./gradlew.bat app:testDebugUnitTest` BUILD SUCCESSFUL（migrations registry + matcher 18 + textutils 7 + souretext 5）
- 待真机：新建条目默认 matchMode=整词；"foo" 关键词不命中 "football"；切回"包含"后命中；英文关键词紧邻中文命中（说foo呢 hits "foo"）。

#### T15-B9 · SearchWorldBookTool 加 total / truncated 字段 ✅

**落地**：
- `SearchWorldBookTool.description` 追加"此为全文搜索，不等同于自动注入时的关键词命中"说明；
- `execute(...)` payload 新增 `total`（全部匹配条数）、`truncated`（是否因 limit 被截）两字段；
- `toPayloadItem(entry)` 返回的每条 entry 增加 `content_truncated` 布尔（content 超过 400 字时为 true）；
- 新增 3 条测试覆盖 total/truncated/content_truncated 以及 description 契约。

**预计**：~45min | **实际**：~20min。

**关键 diff**：
- `data/repository/ai/tooling/SearchWorldBookTool.kt` + `SearchWorldBookToolTest.kt`（aa77a13）

**回归**：
- `./gradlew.bat app:testDebugUnitTest --tests "...SearchWorldBookToolTest"` 全绿。
- 待真机：触发工具、large 条目（>400 字）payload 中 `content_truncated: true`；超过 limit 时 `truncated: true` + `total` 反映全量。

#### T15-B2 · 编辑页信息分层 + 顶栏保存 ✅

**落地**：
- 新建 `ui/screen/settings/worldbook/WorldBookEditSections.kt`：`WorldBookEditExpandedState`（rememberSaveable 支持的四段折叠态）+ `WorldBookCollapsibleSection` 组件（可点击 header + `AnimatedVisibility` body）。
- 改写 `WorldBookEditScreen.kt`：
  - 顶栏 `SettingsTopBar(actionLabel = if (canSave) "保存" else null, onAction = if (canSave) onSaveClick else null)`；
  - 主体四段折叠："基本信息"、"命中规则"（并入次级关键词）、"作用域"、"状态与高级"（并入高级插入顺序）；前两段默认展开；
  - 底部移除"保存"按钮；`!isNew` 时单独留一个"危险操作"卡片放"删除这条"，点击弹 `NarraAlertDialog(isDestructive = true)` 二次确认。
- 新建 `WorldBookContentFullscreenSheet.kt`：`ModalBottomSheet(skipPartiallyExpanded = true)` + 全屏 OutlinedTextField + "保存并返回"，content 字段 trailingIcon（`Icons.Outlined.Edit`）触发。

**预计**：~4h | **实际**：~75min（分两次 commit：骨架重构 + 全屏 sheet）。

**关键 diff**：
- `ui/screen/settings/worldbook/WorldBookEditSections.kt` + `WorldBookEditScreen.kt`（c5030fc 骨架，4a861fa 全屏 sheet）

**回归**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL
- 待真机：四段折叠切换；canSave=false 时顶栏无保存按钮；删除必须二次确认且文字红色；content 点击右上 Edit icon 进入全屏编辑，保存同步回主字段。

#### T15-B3 · "所属世界书" 改下拉 ✅

**落地**：
- `WorldBookEditScreen` 签名新增 `existingBookNames: List<String> = emptyList()` 参数；
- NavGraph 透传 `worldBookState.entries.mapNotNull { trim sourceBookName }.distinct().sorted()`；
- 新增私有 `SourceBookNameDropdown` Composable：`ExposedDropdownMenuBox` 包一个可自由输入的 `OutlinedTextField`；有候选时才显示 trailing icon 和下拉菜单，点击候选项回填输入框；没有候选时退化为普通输入框。

**预计**：~60min | **实际**：~25min。

**关键 diff**：
- `ui/screen/settings/worldbook/WorldBookEditScreen.kt` + `ui/navigation/SettingsDataNavRoutes.kt`（f84126c）

**回归**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL
- 待真机：书名下拉显示去重排序后的候选；点击候选回填；自由输入新书名也能保存。

#### T15-B4 · ATTACHABLE scope 加"去助手挂载"入口 ✅

**落地**：
- `WorldBookEditScreen` 新增 `onOpenAssistantMount: () -> Unit = {}` 回调；
- ATTACHABLE 分支 Text 提示后挂 `TextButton("去助手页挂载")`；
- NavGraph 连接到 `AppRoutes.SETTINGS_ASSISTANTS`（助手列表）+ `launchSingleTop`。

**范围降级说明**：
- 本 PR 只做"入口跳转"。task.md 原要求的"带着 entryId/bookId 进去 + 到达后显示 TipCard 指向具体挂载位置"需要 AssistantExtensionsScreen 接受 query param + 渲染高亮，不在本 PR 范围；留到后续 PR 或批次 D。

**预计**：~45min | **实际**：~10min。

**关键 diff**：
- `ui/screen/settings/worldbook/WorldBookEditScreen.kt` + `ui/navigation/SettingsDataNavRoutes.kt`（1232cac）

**回归**：
- 待真机：切到 ATTACHABLE scope 后提示下出现"去助手页挂载"，点击跳到助手列表。

---

### T15 · 批次 C（P1 / P2 兼容性 + 细节）✅

- **C1 · TavernWorldBookAdapter 增加 extrasJson 兜底**（`TavernWorldBookAdapter.kt` + Entity，~3h）：✅
  - 落地：commit `17a8b3e`（Room v29）+ `e8c41ae`（补提 v28.json）
  - 预计：3h / 实际：约 1.2h
  - 关键 diff：`WorldBookEntry.kt` / `WorldBookEntryEntity.kt` +`extrasJson: String = "{}"`；`ChatDbMigrations.kt` +MIGRATION_28_29 （ALTER ADD COLUMN NOT NULL DEFAULT '{}' + hasColumn 守护）；`TavernWorldBookAdapter.kt` 新增 `KNOWN_FIELD_KEYS` 白名单、`buildExtrasPayload()` 把 `probability` / `depth` / `role` / `logic` 等未识别键原样留存；`RoomWorldBookRepository` toDomain/toEntity 来回映射（空字符串回退 `"{}"`）。
  - 回归：`./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.data.repository.context.TavernWorldBookAdapterTest" --tests "com.example.myapplication.data.repository.context.WorldBookRepositoryTest" --tests "com.example.myapplication.data.local.chat.ChatDatabaseMigrationRegistryTest"` 全绿；`./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL（KSP 自动产出 `app/schemas/…/29.json`）。
  - 待真机：导入带未知字段的 Tavern 角色卡 → 再导出 → 比对 extrasJson 无损。

- **C2 · Tavern 导入稳定 ID 优先用 uid**（`TavernWorldBookAdapter.kt:78-90`，~30min）：✅
  - 落地：commit `119e7cd`
  - 预计：30min / 实际：约 20min
  - 关键 diff：新增 `deriveStableId()` helper；uid 非空时 `UUID.nameUUIDFromBytes("bookName|uid")`，uid 空时 fallback 到 `bookName|index|title|content[:256]` hash，保留老数据的确定性。
  - 回归：`./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.data.repository.context.TavernWorldBookAdapterTest"` 3 个新用例（同 uid 不同内容 → 同 ID；不同 uid → 不同 ID；无 uid 相同内容 → 同 ID）全绿。
  - 待真机：同一本 Tavern 书二次导入、微改正文 → 不再增生孪生条目。

- **C3 · Entity 默认时间戳修正**（`WorldBookEntryEntity.kt`，~20min）：✅
  - 落地：commit `2823bfe`
  - 预计：20min / 实际：约 20min
  - 决策：不改 ColumnInfo defaultValue（需再次 Room +1 且老数据已入库），改在 `RoomWorldBookRepository.toEntity` 入库路径里兜底。
  - 关键 diff：`val now = System.currentTimeMillis(); val resolvedCreatedAt = entry.createdAt.takeIf { it > 0L } ?: now; val resolvedUpdatedAt = entry.updatedAt.takeIf { it > 0L } ?: resolvedCreatedAt`。
  - 回归：`./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.data.repository.context.WorldBookRepositoryTest"` 4 个时间戳用例（0 兜底、显式保留、updatedAt=0 沿用 createdAt）全绿。

- **C4 · DAO 查询去重复**（`WorldBookDao.kt`，~20min）：✅
  - 落地：commit `f3c7cdc`
  - 预计：20min / 实际：约 15min
  - 关键 diff：把 `observeEntries()` / `listEntries()` 共用的排序 SQL 抽为顶层 `private const val DEFAULT_LIST_QUERY`，KSP 编译期内联回 `@Query` 注解；未来调整默认排序只需改一处，降低漏改风险。
  - 回归：`./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL（KSP 接受顶层常量）+ 单测全绿。

- **C5 · 文案统一**（全模块，~30min）：✅
  - 落地：commit `9156887`
  - 预计：30min / 实际：约 15min
  - 关键 diff：`WorldBookBookDetailScreen.kt` "重命名/删除这本书" → "重命名/删除整本世界书"（与弹窗标题 "删除整本世界书" 对齐）；`AssistantExtensionsScreen.kt` "这本书下的" → "这本世界书下的"。ViewModel 现有 message 已满足 C5 要求，不改；"次级键 / 附加键" 经 grep 确认不存在（T15-A1 已统一为"次级关键词"）；pill "常驻注入" 已对齐。
  - 回归：`./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL；相关单测不断言中文字符串，无回归风险。

- **C6 · buildWorldBookBooks 分组 key 用 resolvedBookId()**（`WorldBookListScreen.kt:541-572`，~20min）：✅
  - 落地：commit `119c8ec`
  - 预计：20min / 实际：约 25min（修复时发现 `filteredStandaloneEntries` 判定不对偶，一并修）
  - 关键 diff：`buildWorldBookBooks` 去掉 `entry.sourceBookName.isNotBlank()` 前置条件；`filteredStandaloneEntries` 改为 `it.resolvedBookId().isBlank()`，两者对偶，防止同一条目在"独立"与"书内"同时渲染。
  - 回归：`./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.ui.screen.settings.worldbook.WorldBookListScreenGroupingTest"` 3 个用例（原分组 + 空 sourceBookName 合并 + 都空跳过）全绿。

- **C7 · 所有 `contentDescription = null` 补语义**（全 UI，~45min）：✅
  - 落地：commit `8a5fb29`
  - 预计：45min / 实际：约 20min（审计后只需改 1 处）
  - 决策：Upload/Search leadingIcon 位于带 text/label 的按钮/输入框内，属装饰性，保留 null 避免 TalkBack 重复朗读；IconButton（清空搜索 / 全屏编辑正文）、折叠箭头已具备语义 label。
  - 关键 diff：`WorldBookListScreen.kt` 空态 Icon `contentDescription = spec.title`（对应"还没有世界书" / "当前筛选下没有条目" / "没有匹配结果"）。
  - 回归：`./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。

- **C8 · 自动过期 snackbar**（复用 T15-A3 的事件通道，~10min）：✅
  - 落地：commit `773ccc5`
  - 预计：10min / 实际：约 20min（含对事件通道流水的推演）
  - 场景：编辑页保存后 popBackStack，"世界书已保存" 因两屏都不含此关键字被过滤掉，`rememberSettingsSnackbarHostState` 的 `showSnackbar → onConsumeMessage` 路径不走，ViewModel.internal.message 被卡住直到下一个消息覆盖。
  - 关键 diff：`WorldBookListScreen` / `WorldBookBookDetailScreen` 各加一个 `LaunchedEffect(uiMessage)`，属于本屏语义的消息（含"删除"/"重命名"）不干预，其余消息延迟 3s 兜底 consume。
  - 回归：`./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
  - 待真机：编辑页保存 → 返回列表页 → 不弹 snackbar，且 3s 后再次保存能触发新 snackbar。

**批次 C 整体回归：** `./gradlew.bat app:testDebugUnitTest --rerun-tasks` BUILD SUCCESSFUL（全量重跑 660+ 原用例 + 10 个新增用例全绿）。

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
