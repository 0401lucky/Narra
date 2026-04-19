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

## 执行顺序建议

| 阶段 | 任务 | 预计 |
|---|---|---|
| 数据安全 | T13-A1 / A2 / A3 | 半天 |
| 交互一致性 | T13-B1 → B9 | 1-1.5 天 |
| 视觉规范 | T13-C1 → C8 | 半天 |
| 扩展与 a11y | T13-D1 → D3 | 半天 |

每完成一条，改状态为 ✅，并在该条下追加"实际耗时 / 关键 diff / 回归命令"。
