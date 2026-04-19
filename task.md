# 代码审查整改任务清单

> 源：2026-04-18 全局 review（见上轮对话）。按"投入产出比"排序，逐条落地。
> 状态标记：⬜ 未开始 / 🔄 进行中 / ✅ 完成 / ⏸️ 阻塞

---

## T1 · 补齐缺失的 Room schema 18 & 清理仓库根目录  ✅
**源**：A1 + C5 | **预计**：20 分钟 | **实际**：20 分钟

**问题**：
- `app/schemas/com.example.myapplication.data.local.chat.ChatDatabase/` 缺 `18.json`（有 11-17、19-27）。
- 仓库根堆了 25+ 张 `manual_*.png` 调试截图、`tmp_*/`、`nul`、中文 JSON 导出等本地产物。

**落地**：
- git 历史确认 `18.json` 从未被提交（提交 `858183b` 一次跳过了 17→19，Room 只导出最终版本 schema）。无法从 git 恢复。
- `app/schemas/.../README.md` 新建，解释断档原因 + 复原路径（临时把 `CURRENT_VERSION` 设回 18 触发导出）+ 再发防护约定（禁止单次 bump 多个版本）。
- `ChatDatabaseMigrationRegistryTest` 增强：新增 `allMigrations_coversEveryVersionContiguously`，断言 `ALL_MIGRATIONS.size == CURRENT_VERSION - 1` 且版本连续。
- `.gitignore` 新增规则：`manual_*.png` / `ui_review_report.md` / `tmp_*` / `/nul` / `/参考.md` / `/含日记提示词.json` / `/导出已归档对话-*.json` / `*.err.txt` / `/进度和日志进展.md`（路径前缀 `/` 限定根目录）。

**验收**：
- `./gradlew.bat app:testDebugUnitTest --tests "*ChatDatabaseMigrationRegistryTest*"` BUILD SUCCESSFUL。
- `git status --short | grep "^??"` 根目录剩 4 条（全部是真实新代码：`27.json` / `NarraAlertDialog.kt` / `RoleplayDiaryDetailScreen.kt` / `task.md`）。

**关键 diff**：
- `.gitignore`
- `app/schemas/com.example.myapplication.data.local.chat.ChatDatabase/README.md`（新）
- `app/src/test/java/com/example/myapplication/data/local/chat/ChatDatabaseMigrationRegistryTest.kt`

---

## T2 · 引入统一 Logger，让 141 处 `runCatching.getOrNull()` 不再吞异常  ✅
**源**：A4 | **预计**：半天 | **实际**：约 1 小时

**落地**：
- 新建 `app/src/main/java/com/example/myapplication/system/logging/AppLogger.kt`：
  - `object AppLogger { d / i / w / e }`，内部 `Sink` 接口 + 默认 `LogcatSink`；支持后续替换为 Crashlytics 等。
  - `LogcatSink` 对 `android.util.Log.*` 调用用 `runCatching` 兜底，单测环境 Log 未 mock 不会污染业务路径。
  - 扩展函数 `fun <T> Result<T>.logFailure(tag, lazyMessage): Result<T>`：惰性求值 message，自动忽略 `CancellationException`。
- 应用到 8 个高风险反序列化站点（示范 + 立刻生效）：
  - `AppSettingsStore.kt`：`decodeProviders` / `decodeSearchSettings` / `decodeAssistants` / `decodeTranslationHistory` + `SearchSettingsSensitiveMigrationSupport.migrate`。
  - `RoomConversationStore.kt`：`reasoningSteps` / `attachments` / `parts` / `citations` fromJson。
  - `TavernCharacterAdapter.kt` / `TavernWorldBookAdapter.kt` / `ContextTransferCodec.kt`：用户导入文件解析失败。
  - `PhoneSnapshotRepository.kt`：`PhoneSnapshotEntity.toDomain` snapshot fromJson。
- 剩余 130+ 处 `runCatching.getOrNull()` 留给增量迁移（大多是枚举 `valueOf` 容错，日志会噪声化，不属于 T2 目标）。

**新增测试**（6 个，`app/src/test/.../system/logging/AppLoggerTest.kt`）：
- `warn_forwardsMessageAndThrowableToSink`
- `logFailure_doesNothingOnSuccess`
- `logFailure_logsOnFailureAndKeepsResultIntact`
- `logFailure_skipsCancellationException`
- `logFailure_lazyMessageOnlyEvaluatedOnFailure`
- `d_noThrowable`

**验收**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest --tests "*AppLoggerTest*" --rerun-tasks` BUILD SUCCESSFUL。
- 全量 `app:testDebugUnitTest`：673 个测试中 671 通过（余 2 个仍是 T1 前就存在的 `RoleplayOnlineReferenceSupport` / `RoleplayPromptDecorator` 失败）。

**关键 diff**：
- 新文件：`system/logging/AppLogger.kt` + 测试。
- 触动：`AppSettingsStore`、`RoomConversationStore`、`TavernCharacterAdapter`、`TavernWorldBookAdapter`、`ContextTransferCodec`、`PhoneSnapshotRepository`。

---

## T3 · 共享 `Gson` 实例，AppGraph 暴露单例  ✅
**源**：A5 | **预计**：1-2 小时 | **实际**：约 40 分钟

**落地**：
- 新建 `app/src/main/java/com/example/myapplication/system/json/AppJson.kt`，内部 `val gson: Gson by lazy { GsonBuilder().disableHtmlEscaping().create() }`。改 Option B（object 单例而非 AppGraph 字段），避免构造函数大面积触碰。
- 17 个文件（18 处 `Gson()` new 全部替换）：
  - 字段型（`private val gson = Gson()`）：`AppSettingsStore`（2 处）、`RoomConversationStore`、`SecureValueStore`、`AiGateway`、`AiTranslationService`、`WorldBookRepository`、`SearchModelExecutor`（companion）。
  - 构造默认值（`gson: Gson = Gson()`）：`PhoneSnapshotRepository`（5 处含顶层函数默认）、`ToolEngine`、`TavernWorldBookAdapter`、`TavernCharacterAdapter`、`ContextTransferCodec`、`ReadMemoryTool`、`SaveMemoryTool`、`SearchWebTool`、`SearchWorldBookTool`、`GetConversationSummaryTool`。
- 全部改为 `AppJson.gson`，测试注入能力保留（仍是 Gson 类型）。

**验收**：
- `rg 'Gson\(\)' app/src/main/java` 只剩 `AppJson.kt` 注释里的一处示例。
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest`：667 个测试中 665 通过，余 2 个是 review 前就失败的 `RoleplayOnlineReferenceSupportTest` / `RoleplayPromptDecoratorTest`（本次未触碰的 prompt/online 逻辑）。

**关键 diff**：
- 新文件：`app/src/main/java/com/example/myapplication/system/json/AppJson.kt`
- 触动 17 个 Gson 使用点 + AppSettingsStore 导入顺序修复

---

## T4 · CLAUDE.md 同步当前架构  ✅
**源**：B1 | **预计**：1 小时 | **实际**：20 分钟

**落地**：
- 重写"应用入口与装配（DI 容器：AppGraph）"段：指向 `ChatApplication` / `AppGraph` / `AppJson.gson`；MainActivity 说明只剩 19 行转发。
- 路由段列出实际 34 条路由 + 三个 `register*NavGraph` 子图入口 + `NavigationViewModelOwners` 的作用；`startDestination` 更新为 `AppRoutes.CHAT`。
- 数据职责段按实际包结构铺开：`ai/`/`context/`/`roleplay/`/`phone/`/`search/` 分别指向主入口类。
- ViewModel 段体现当前规模：`ChatViewModel` 1600+ / `SettingsViewModel` 1100+ / `RoleplayViewModel` 1251 + 各自 Support 系列。
- 新增约定段：
  - Compose 订阅一律 `collectAsStateWithLifecycle()`（禁止裸 `collectAsState()`）。
  - Gson 使用 `AppJson.gson`，禁止 `Gson()` 自建。
  - Room 版本号一次只能 +1 提交，避免 schema 漏导。
- 目录约定补 `system/`（含 `system/json/AppJson.kt`）与 `conversation/`/`context/`/`roleplay/`/`di/` 领域逻辑包。
- 常用命令补 `app:compileDebugKotlin`（快速校验）。

**验收**：同步后阅读 CLAUDE.md 可直接定位 AppGraph、AppJson、34 路由入口，与代码事实一致。

---

## T5 · 拆分 `ChatDatabase.kt`（650 行 / 26 个 Migration）  ✅
**源**：B2 | **预计**：半天 | **实际**：25 分钟

**落地**：
- 新建 `app/src/main/java/com/example/myapplication/data/local/chat/migrations/ChatDbMigrations.kt`：
  - `internal object ChatDbMigrations` 集中所有 26 条 `MIGRATION_X_Y`、`ALL: Array<Migration>` 注册表、`hasColumn(...)` 幂等列检查。
  - 619 行，顶部带约定注释（一次只 +1 版本、新增 Migration 必须入 ALL 数组、ALTER TABLE 先守护 hasColumn）。
- `ChatDatabase.kt` 瘦身：650 → 53 行（远超 ≤120 目标）。只保留：
  - `@Database` 注解 + entities 列表
  - 5 个 DAO abstract
  - `companion object { CURRENT_VERSION = 27; ALL_MIGRATIONS = ChatDbMigrations.ALL }`
- `ChatDatabaseMigrationRegistryTest` 导入路径调整：`ChatDatabase.MIGRATION_26_27` → `ChatDbMigrations.MIGRATION_26_27`；T1 新增的连续性断言保持不动。
- 验证没有外部代码再引用 `ChatDatabase.MIGRATION_*`（全仓 grep 0 命中），未来迁移加新条目只需在 `ChatDbMigrations.kt` 内部操作，不再动 `ChatDatabase.kt`。

**验收**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest --tests "*ChatDatabaseMigrationRegistryTest*" --rerun-tasks` BUILD SUCCESSFUL（2 个连续性用例通过）。
- 全量 `app:testDebugUnitTest`：673 个用例中 671 通过，余 2 个仍是预存失败（不受本次改动影响）。

**关键 diff**：
- 新文件：`data/local/chat/migrations/ChatDbMigrations.kt`（619 行）
- `data/local/chat/ChatDatabase.kt`：650 → 53 行
- `test/.../ChatDatabaseMigrationRegistryTest.kt`：引用路径更新

---

## T6 · 拆分 `AiPromptExtrasService`（1657 行，多职责）  ✅
**源**：A3 | **预计**：1-2 天 | **实际**：约 2.5 小时

**公开 API**（13 个 suspend fun）：
- 聊天辅助：`generateTitle`、`generateChatSuggestions`
- 摘要类：`generateConversationSummary`、`generateRoleplayConversationSummary`、`condenseRoleplayMemories`
- 记忆提议：`generateMemoryEntries`、`generateRoleplayMemoryEntries`
- 剧情：`generateRoleplaySuggestions`、`generateRoleplayDiaries`、`generateGiftImagePrompt`
- 手机：`generatePhoneSnapshotSections`、`generatePhoneSearchDetail`、`generateSocialCommentReplies`

**内部共享**（约 200 行）：`requestCompletionContent`、OpenAI/Anthropic 请求支援、`buildRequestWithRoleplaySampling`、`resolveRoleplaySampling`、`UnsupportedSamplingMessageHints`、`buildOpenAiTextUrl`、markdown / JSON 提取器。

**外部调用者**（13 个文件）：各 `Coordinator` / ViewModel / `MemoryWriteService` / `AppGraph`。接口保持不变以避免大面积修改。

### 子任务

- **T6.1** 抽 `PromptExtrasCore`：把所有 `private suspend fun requestCompletionContent / requestOpenAiCompletionContent / requestAnthropicCompletionContent / buildRequestWithRoleplaySampling / resolveRoleplaySampling / shouldRetryWithoutRoleplaySampling / markRoleplaySamplingUnsupported / buildOpenAiTextUrl / parseRequiredStructuredJsonObject / extractStructuredJsonObject / stripMarkdownCodeFence / extractFirstCompleteJsonObject / JsonObject.stringValue / JsonObject.booleanValue / getAsJsonArrayOrNull / asJsonObjectOrNull` 移出，当做注入依赖。✅（主文件改为 `core.xxx(...)` 委托，删除重复实现；第一轮只建新文件未切换的悬空代码已清理）
- **T6.2** 抽 `TitleAndChatSuggestionPromptService`（`generateTitle` + `generateChatSuggestions`）。✅（76 行，无 roleplay 采样）
- **T6.3** 抽 `ConversationSummaryPromptService`（`generateConversationSummary` + `generateRoleplayConversationSummary`）。✅（102 行）
- **T6.4** 抽 `MemoryProposalPromptService`（`generateMemoryEntries` + `generateRoleplayMemoryEntries` + `condenseRoleplayMemories`）。✅（241 行）
- **T6.5** 抽 `RoleplaySuggestionPromptService`（`generateRoleplaySuggestions` + `requestRoleplaySuggestions` helper）。✅（87 行）
- **T6.6** 抽 `RoleplayDiaryPromptService`（`generateRoleplayDiaries` + `generateGiftImagePrompt` + `sanitizeDiaryIdentifier`）。✅（207 行）
- **T6.7** 抽 `PhoneContentPromptService`（`generatePhoneSnapshotSections` + `generatePhoneSearchDetail` + `generateSocialCommentReplies` + `buildPhoneSnapshotReference` 等 phone 私有助手 + `parsePhoneSnapshotSections`）。✅（558 行）
- **T6.8** `DefaultAiPromptExtrasService` 降为 thin facade：构造里接 6 个子服务，每个 override 一行委托。`AppGraph` 装 6 个 `by lazy` 子服务 + 兼容性 facade。编译 + 全量测试全绿。✅（主构造接 6 个子服务；便利构造 `(apiServiceFactory, ...)` 保留供测试与旧调用；`AppGraph` 暴露 `promptExtrasCore` + 6 个 `internal val xxxPromptService by lazy`）

**验收**：
- `AiPromptExtrasService.kt`：1657 → **475 行**（-71%），只剩 interface + 13 个 override × 1 行委托。
- 6 个子服务 + 1 个 Core 各司其职，最大 PhoneContentPromptService 558 行（Phone 全领域内聚，含 JSON 解析）。
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest --tests "*AiPromptExtrasServiceTest*"` BUILD SUCCESSFUL。
- 全量 `app:testDebugUnitTest`：673 个用例中 671 通过，余 2 个仍是 T1 之前就存在的 `RoleplayOnlineReferenceSupport` / `RoleplayPromptDecorator` 失败（与本次无关，T2/T3 验收均已登记）。

**关键 diff**：
- 瘦身：`data/repository/ai/AiPromptExtrasService.kt`（1657 → 475）
- 新增子服务：`data/repository/ai/TitleAndChatSuggestionPromptService.kt`、`ConversationSummaryPromptService.kt`、`MemoryProposalPromptService.kt`、`RoleplaySuggestionPromptService.kt`、`RoleplayDiaryPromptService.kt`、`PhoneContentPromptService.kt`
- `di/AppGraph.kt`：新增 `promptExtrasCore` + 6 个 `internal val` 子服务装配；`aiPromptExtrasService` 改走 6 参数主构造，兼容性 facade 仍对外暴露 `AiPromptExtrasService`。

---

## T7 · 把 `SettingsViewModel` 的 7 连 launch 下沉为 Coordinator  ✅
**源**：A2（局部） | **预计**：半天 | **实际**：约 1 小时

**落地**：
- **7 连 launch 处理**（944-1050 行）：
  - 5 个助手方法（`addAssistant` / `updateAssistant` / `removeAssistant` / `duplicateAssistant` / `selectAssistant`）统一走新增的 `private fun launchAssistantOp(block: suspend SettingsAssistantCoordinator.(AppSettings) -> Unit)` helper；每个方法压到 1 行 `=` 表达式。
  - `persistProviderDrafts` 从独立 `launch + runCatching + _uiState.update` 改为复用 `launchUiMutation(defaultErrorMessage, action, onSuccess)`，去掉重复的 try/error 模板。
  - `checkProviderHealth` / `saveSettings` / `loadModelsForProvider` 全部改走 `updateUiState(...)` 统一入口。
- **setter 抽离到同包扩展文件**（达成 ≤800 行硬指标）：
  - `SettingsProviderFunctionSetters.kt`（68 行）：14 个 `updateProviderXxxModel` / `updateProviderXxxModelMode` 扩展。
  - `SettingsPreferenceSetters.kt`（62 行）：17 个 theme / roleplay preference 扩展。
  - `SettingsScreenTranslationSetters.kt`（35 行）：7 个屏幕翻译扩展。
  - `SettingsSearchSetters.kt`（47 行）：6 个联网搜索扩展。
- **VM 暴露 `internal` hook** 供同包扩展访问：`updateUiState(transform)`、`updateProvider(providerId, transform)`、`updateScreenTranslationDraft(transform)`。
- UI 调用侧（4 个 nav 文件：`SettingsNavGraph.kt` / `SettingsProviderNavRoutes.kt` / `RoleplayNavGraph.kt` / `ChatNavGraph.kt`）新增对应 `import com.example.myapplication.viewmodel.xxx`，保持 `settingsViewModel::updateThemeMode` 绑定方法引用不变。

**验收**：
- `SettingsViewModel.kt`：**1131 → 638 行**（-493 / -44%），远超 ≤800 目标。
- 4 个新 setter 扩展文件共 212 行，净搬家 ~280 行（其余靠 `=` 表达式化 + 统一 helper）。
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest --tests "*Settings*"` BUILD SUCCESSFUL。
- 全量 `app:testDebugUnitTest`：673 个用例中 671 通过，余 2 个仍是 T1 前就存在的 `RoleplayOnlineReferenceSupport` / `RoleplayPromptDecorator` 失败（与 T7 无关）。

**关键 diff**：
- 瘦身：`viewmodel/SettingsViewModel.kt`（1131 → 638）
- 新增：`viewmodel/SettingsProviderFunctionSetters.kt`、`SettingsPreferenceSetters.kt`、`SettingsScreenTranslationSetters.kt`、`SettingsSearchSetters.kt`
- UI 调用侧 import 补齐：`ui/navigation/SettingsNavGraph.kt`、`SettingsProviderNavRoutes.kt`、`RoleplayNavGraph.kt`、`ChatNavGraph.kt`

---

## T8 · Compose 消息气泡稳定性审计  ✅
**源**：B3 | **预计**：1 天（含 metrics 采集） | **实际**：约 1 小时

**落地**：
- Compose Compiler metrics 已在 `app/build.gradle:141-144` 配置好（reports/metrics 输出到 `app/build/compose_reports/`），跑一次 `./gradlew.bat app:compileDebugKotlin --rerun-tasks` 即可重新采样。
- **给 3 个 UiModel 加 `@Immutable`**：
  - `ui/component/MessageBubbleState.kt::MessageBubbleRenderState`（主 render state，内部含 4 个 unstable 字段）。
  - `ui/component/MessageContentFormatter.kt::AssistantVisualContent`（含 `imageSources: List<String>`）。
  - 新增 `ui/component/MessageBubbleLayout.kt::MessageBubbleRenderedContent`（把 `displayAttachments` + `displayParts` + `assistantImageSources` 3 个 List 参数打包）。
- **重构 1：action composable 移除 clipboard/scope 入参**：
  - `MessageBubbleActionRows` / `MessageBubbleUserActions` / `MessageBubbleAssistantActions` / `MessageBubbleErrorActions` 4 个函数不再通过参数接收 `Clipboard` + `CoroutineScope`，改为内部 `LocalClipboard.current` + `rememberCoroutineScope()`。`MessageBubble` 不再提前取 clipboard/scope。
- **重构 2：`MessageBubbleContent` / `UserStructuredMessageContent` 改收单一 `renderedContent`**：
  - 3 个 List 参数（`displayAttachments` / `displayParts` / `assistantImageSources`）合并进 `MessageBubbleRenderedContent`。
  - `MessageBubble` 里新增 `val renderedContent = remember(...) { MessageBubbleRenderedContent(...) }`，两处调用 `MessageBubbleContent` 与一处 `UserStructuredMessageContent` 统一传入。

**验收**：
- **类级稳定性**：`MessageBubbleRenderState` / `AssistantVisualContent` 从 `unstable class` 转为 `stable class`；新 `MessageBubbleRenderedContent` 即为 `stable`（全仓 `unstable class` 209 → 207）。
- **消息气泡相关 composable unstable 参数**（精确对比 `app-composables.txt`）：

  | Composable | Before | After |
  |---|---:|---:|
  | MessageBubble | 2 | 2 |
  | AssistantCitationSection | 1 | 1 |
  | MessageBubbleActionRows | 2 | 0 |
  | MessageBubbleUserActions | 2 | 0 |
  | MessageBubbleAssistantActions | 2 | 0 |
  | MessageBubbleErrorActions | 2 | 0 |
  | UserStructuredMessageContent | 2 | 0 |
  | MessageBubbleContent | 3 | 0 |
  | MessagePartsRenderer | 2 | 2 |
  | RenderMessageText | 1 | 1 |
  | ReasoningTimelineCard | 1 | 1 |
  | rememberMessageBubbleRenderState | 2 | 2 |
  | **合计** | **22** | **9** |

  降幅 **13 / 22 = 59%**，超过 ≥ 50% 目标。
- **全仓 unstable 参数**：179 → 166（-13，-7.3%）。
- `./gradlew.bat app:compileDebugKotlin --rerun-tasks` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest`：673 个用例中 671 通过，余 2 个仍是 T1 之前就存在的 `RoleplayOnlineReferenceSupport` / `RoleplayPromptDecorator` 失败（与本次无关，T2/T3/T5/T6/T7 均已登记）。

**关键 diff**：
- `ui/component/MessageBubbleState.kt`：`MessageBubbleRenderState` 加 `@Immutable`。
- `ui/component/MessageContentFormatter.kt`：`AssistantVisualContent` 加 `@Immutable`。
- `ui/component/MessageBubbleLayout.kt`：新增 `@Immutable MessageBubbleRenderedContent`；`UserStructuredMessageContent` / `MessageBubbleContent` 改收 `renderedContent`。
- `ui/component/MessageBubbleActions.kt`：4 个 action composable 移除 clipboard/scope 参数，改内部 CompositionLocal。
- `ui/component/MessageBubble.kt`：删除本地 `clipboard` / `clipboardScope` / `context`；新增 `renderedContent = remember { ... }`；调用侧全部简化。

**遗留给 T10 的热点**（超出 T8 范围，降幅已达成故未处理）：`MessagePartsRenderer` / `RenderMessageText` / `ReasoningTimelineCard` / `MessageBubble` / `AssistantCitationSection` / `rememberMessageBubbleRenderState` 仍有 9 处 `List<T>` unstable 参数，可在后续用同类包装容器继续削减。

---

## T9 · 剧情场景联动逻辑下沉到 domain  ✅
**源**：C6 | **预计**：1-2 小时 | **实际**：约 40 分钟

**落地**：
- 新建 `model/RoleplayInteractionSpec.kt`（数据类 + 规则）：
  - 字段：`interactionMode` / `longformModeEnabled` / `enableRoleplayProtocol`。
  - `normalized()`：历史数据里 longform=true 但 mode=OFFLINE_DIALOGUE 的错位自动提升为 OFFLINE_LONGFORM（承接老 UI 初始化分支的语义）。
  - `withInteractionMode(mode)`：OFFLINE_LONGFORM → longform on + RP off；OFFLINE_DIALOGUE → longform off（不动 RP）；ONLINE_PHONE → longform off + RP on。
  - `withLongform(enabled)`：开启则强制 OFFLINE_LONGFORM + 关 RP；关闭时若当前是 OFFLINE_LONGFORM 则回落到 OFFLINE_DIALOGUE。
  - `withRoleplayProtocol(enabled)`：仅在"非长文且非线上电话"时把模式规范化到 OFFLINE_DIALOGUE（等价于老 RP 开关 lambda 里的 `if (!longformModeEnabled && mode != ONLINE_PHONE)` 分支）。
- `model/RoleplayScenario.kt`：追加 `toInteractionSpec` / `withInteractionSpec` / `withInteractionMode` / `withLongform` / `withRoleplayProtocol` 扩展函数，全部委托给 spec。
- `RoleplayScenarioEditScreen.kt`：
  - 初始化 `var interactionMode` 不再内联 `if (longform && OFFLINE_DIALOGUE)` 分支，改为 `remember { baseScenario.toInteractionSpec().normalized() }` 取字段。
  - 新增局部 `fun applyInteractionSpec(transform)` 助手：一次性读取当前三字段 → 调用 spec 规则 → 回写三字段。
  - FilterChip onClick（删除 6 行 `when (mode)` 分支）→ `applyInteractionSpec { it.withInteractionMode(mode) }`。
  - 长文 SwitchRow onValueChange（删除 6 行 `if (it) ... else if ... longform=OFFLINE_LONGFORM` 分支）→ `applyInteractionSpec { it.withLongform(enabled) }`。
  - RP 协议 SwitchRow onValueChange（删除 3 行 `if (!longform && mode != ONLINE_PHONE)` 分支）→ `applyInteractionSpec { it.withRoleplayProtocol(enabled) }`。

**验收**：
- UI 层 `RoleplayScenarioEditScreen.kt` 3 处联动 lambda 里 `when (mode)` / `if (!longformModeEnabled` 0 命中，剩下的 `if (longformModeEnabled)` 是 SwitchRow subtitle 的文案分支（UI 呈现），非状态联动。
- 新增 `app/src/test/java/com/example/myapplication/model/RoleplayInteractionSpecTest.kt`：16 个用例覆盖
  - `normalized` × 3（错位提升 / 幂等 / 线上电话不受影响）
  - `withInteractionMode` × 3（三种模式切换）
  - `withLongform` × 3（开 / 关 / 从线上电话关闭）
  - `withRoleplayProtocol` × 3（三种上下文）
  - `RoleplayScenario.withXxx` × 4（扩展函数委托 + 保留无关字段）
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest --tests "*RoleplayInteractionSpecTest*"` BUILD SUCCESSFUL。
- 全量 `app:testDebugUnitTest`：691 个用例中 689 通过，余 2 个仍是 T1 前就存在的 `RoleplayOnlineReferenceSupport` / `RoleplayPromptDecorator` 失败（与 T9 无关，T2/T3/T5/T6/T7/T8 均已登记）。

**关键 diff**：
- 新文件：`model/RoleplayInteractionSpec.kt`（100 行）
- 新文件：`test/.../model/RoleplayInteractionSpecTest.kt`（16 个用例）
- `model/RoleplayScenario.kt`：+29 行扩展函数
- `ui/screen/roleplay/RoleplayScenarioEditScreen.kt`：3 处 lambda 简化 + 初始化块改为 spec.normalized()，净减 ~25 行分支

---

## T10 · 拆分剩余大 Compose 文件  ✅
**源**：B3（延伸） | **预计**：视 T8 结果而定 | **实际**：约 2 小时（4 文件阶段 + 1 小时 ChatScreen 收尾）

### 5/5 全部完成（行数对比）

| 目标文件 | Before | After | 降幅 | 拆出文件 |
|---|---:|---:|---:|---|
| `MessageBubblePresentation.kt` | 1019 | **38** | **-96%** | `GlassMessageContainer.kt`(130) + `MessageMarkdownRenderer.kt`(500) + `ReasoningTimelineCard.kt`(397) |
| `ChatDrawerComponents.kt` | 886 | **331** | **-63%** | `ChatDrawerSections.kt`(81) + `ChatDrawerWidgets.kt`(507) |
| `RoleplayMessageBubbles.kt` | 1085 | **466** | **-57%** | `RoleplayActionMessageCards.kt`(322) + `RoleplayDialogueBubbles.kt`(206) + `RoleplayOnlinePhoneBubbles.kt`(161) |
| `ChatSpecialPlaySheets.kt` | 997 | **426** | **-57%** | `ChatSpecialPlayCatalog.kt`(197) + `ChatSpecialPlayDraftFields.kt`(409) |
| `ChatScreen.kt` | 880 | **448** | **-49%** | `ChatScreenDerivations.kt`(225) + `ChatScreenLaunchers.kt`(273) |

**5 个目标文件合计：4867 → 1709（-65%）**。12 个新 Compose/helper 文件共 3408 行，单一职责。

### 拆分策略

- **内容渲染层**（T10 前期）：气泡/长文/玻璃容器/推理时间线分散到 6 个独立组件文件。
- **外壳容器层**（T10 前期）：抽屉区块、特殊玩法目录/草稿分离为 4 个组件文件。
- **`ChatScreen` 收尾**：880 行单巨型 composable 没有私有子 composable，采用"**状态派生聚合 + Launcher 聚合**"两刀拆：
  - `rememberChatScreenDerivations(uiState, resources)`：将 provider / model / assistant / search / reasoning / last-message 等约 30 个派生值收进 `@Immutable data class ChatScreenDerivations`。
  - `rememberChatScreenLaunchers(context, scope, ..., localState, uiState, ..., onAddPendingParts, onSaveUserProfile)`：将 5 个 `rememberLauncherForActivityResult` + 3 个本地助手（`handlePickedAttachment` / `saveProfileDraft` / `primeSpecialPlayDraft` / `resetSpecialPlayDraft`）统一暴露为 `() -> Unit` / `(T) -> Unit`。
  - `ChatScreen` 主函数保留：callback 解包 → CompositionLocal & `rememberXxxState` → 聚合 `derivations` / `launchers` → 2 个 effect + 3 个 sub-composable 调用。
- 顺手给 `ChatScreenLocalState` 加 `@Immutable`：回收新 `rememberChatScreenLaunchers(localState: ChatScreenLocalState)` 的 1 个 unstable 参数。

### 验收（2026-04-18 执行，含 ChatScreen 收尾）

- **编译**：`./gradlew.bat app:compileDebugKotlin --rerun-tasks` **BUILD SUCCESSFUL**（1m，仅 `ReasoningTimelineCard.kt:299` 一条 `KeyboardArrowRight` deprecated warning，不阻断）。
- **单测**：`./gradlew.bat app:testDebugUnitTest` → **691 个用例中 689 通过**，余 2 个仍是 T1 前预存的 `RoleplayOnlineReferenceSupport` / `RoleplayPromptDecorator` 失败（与 T10 无关，与其他 T 报表一致）。
- **Compose metrics 回归**（`app/build/compose_reports/` 2026-04-18 最终采样）：

  | 指标 | T8 完成基线 | T10 4/5 阶段 | T10 全部完成 | 最终变化 |
  |---|---:|---:|---:|---:|
  | 全仓 composable unstable 参数 | 166 | 166 | **172** | +6 |
  | 全仓 stable class | 不详 | 248 | **251** | — |
  | 全仓 unstable class | 207 | 208 | **207** | 0 |
  | 新增 `ChatScreenDerivations` | — | — | `stable class` | — |
  | 新增 `ChatScreenLaunchers` | — | — | `stable class` | — |
  | `ChatScreenLocalState` | `unstable class` | `unstable class` | `stable class`（+`@Immutable`） | -1 |

  - ChatScreen 收尾后 composable 参数增加的 6 个 unstable marker 全部集中在 2 个非 UI 层 helper 上：`rememberChatScreenDerivations(uiState, resources)` 2 个、`rememberChatScreenLaunchers(context, scope, resources, uiState, ...)` 4 个（`localState` 因 `@Immutable` 已变 stable）。
  - 这 6 个 marker 的类型全部是不可控的框架/外部类（`ChatUiState` / `Context` / `Resources` / `CoroutineScope`）；且 helper 非 UI 层，只在 `ChatScreen` 单次 composition 内调用 1 次，不影响任何子 composable 的跳过行为。
  - UI 层可跳过性不变：ChatScreen / ChatScreenChrome / ChatScreenOverlays / ReasoningTimelineCard 的 unstable 参数个数与 T10 前完全相同。

### 关键 diff

**瘦身**（5 个目标文件，净减 3158 行）：
- `ui/component/MessageBubblePresentation.kt`（1019 → 38）
- `ui/component/roleplay/RoleplayMessageBubbles.kt`（1085 → 466）
- `ui/screen/chat/ChatSpecialPlaySheets.kt`（997 → 426）
- `ui/screen/chat/ChatDrawerComponents.kt`（886 → 331）
- `ui/screen/chat/ChatScreen.kt`（880 → 448）

**新增 12 个 Kotlin 文件**（共 3408 行）：
- `ui/component/GlassMessageContainer.kt`（130）
- `ui/component/MessageMarkdownRenderer.kt`（500）
- `ui/component/ReasoningTimelineCard.kt`（397）
- `ui/component/roleplay/RoleplayActionMessageCards.kt`（322）
- `ui/component/roleplay/RoleplayDialogueBubbles.kt`（206）
- `ui/component/roleplay/RoleplayOnlinePhoneBubbles.kt`（161）
- `ui/screen/chat/ChatDrawerSections.kt`（81）
- `ui/screen/chat/ChatDrawerWidgets.kt`（507）
- `ui/screen/chat/ChatSpecialPlayCatalog.kt`（197）
- `ui/screen/chat/ChatSpecialPlayDraftFields.kt`（409）
- `ui/screen/chat/ChatScreenDerivations.kt`（225）【本次新增】
- `ui/screen/chat/ChatScreenLaunchers.kt`（273）【本次新增】

**附带修复**：
- `ui/screen/chat/ChatScreenLocalState.kt`：`data class` 加 `@Immutable`，类级稳定性从 `unstable class` 升为 `stable class`。

---

## T11 · `RoleplayDiaryEntry` / `RoleplayDiaryDraft` 共用接口  ✅
**源**：C4 | **预计**：30 分钟 | **实际**：约 25 分钟

**方案**：抽 `RoleplayDiaryCore` interface 提供共用字段 getter；两个 data class 实现之。Draft → Entry 的 `copy` 操作走扩展函数。

**落地**：
- `model/RoleplayDiaryEntry.kt` 重写：
  - 新增 `interface RoleplayDiaryCore`，暴露 6 个共用字段 getter：`title` / `content` / `mood` / `weather` / `tags: List<String>` / `dateLabel`。
  - `RoleplayDiaryEntry`（`@Immutable data class`）将 6 个共用字段标记 `override`，保留 Entry 独占的 `id` / `conversationId` / `scenarioId` / `sortOrder` / `createdAt` / `updatedAt`；实现 `RoleplayDiaryCore`。
  - `RoleplayDiaryDraft`（`@Immutable data class`）6 个字段全部 `override`；实现 `RoleplayDiaryCore`。
  - 新增 2 个扩展函数：
    - `RoleplayDiaryCore.toDraft()`：把任意 Core 转 Draft（Entry → Draft 的反向编辑 / AI 重生成场景）。已是 Draft 的实例走 fast-path `return this` 避免多余拷贝。
    - `RoleplayDiaryDraft.toEntry(id, conversationId, scenarioId, sortOrder, createdAt, updatedAt = createdAt)`：Draft 补齐持久化字段成 Entry；不做 trim（仓库层 `replaceDiaryEntries` 继续负责清洗，避免重复语义）。
- 现有调用点均向后兼容（`RoleplayRepository` / `RoleplayDiaryPromptService` / `AiPromptExtrasService` / `RoleplayViewModel` / 对应测试均继续按 data class 构造器 + 字段访问使用，零改动）。

**新增测试**（8 个，`app/src/test/.../model/RoleplayDiaryCoreTest.kt`）：
- `draft implements core and exposes same fields`
- `entry implements core and exposes content fields`
- `toDraft on draft returns same instance`（fast-path 多态验证）
- `toDraft on entry copies only core fields`
- `toDraft preserves tag list content without sharing mutability`
- `toEntry fills persistence fields without mutating content fields`
- `toEntry honours explicit updatedAt override`
- `round trip draft to entry and back is lossless`

**验收**：
- `./gradlew.bat app:compileDebugKotlin` BUILD SUCCESSFUL。
- `./gradlew.bat app:testDebugUnitTest --tests "*RoleplayDiaryCoreTest*"` BUILD SUCCESSFUL（8 个新用例全绿）。
- 全量 `app:testDebugUnitTest`：699 个用例中 697 通过，余 2 个仍是 T1 前就存在的 `RoleplayOnlineReferenceSupport` / `RoleplayPromptDecorator` 失败（与 T11 无关，T2/T3/T5/T6/T7/T8/T9/T10 均已登记）。总数相比 T10 收尾的 691 增长 8，对应新增的 `RoleplayDiaryCoreTest`。

**关键 diff**：
- `model/RoleplayDiaryEntry.kt`：29 行 → 90 行（+61），接口 + 2 个 data class（字段 override）+ 2 个扩展函数。
- 新文件：`app/src/test/java/com/example/myapplication/model/RoleplayDiaryCoreTest.kt`（8 个 JUnit 用例）。

---

## T12 · 服务层 CoroutineScope 生命周期审计  ✅
**源**：B5 | **预计**：1 小时 | **实际**：约 15 分钟

**落地**：
- `ScreenTranslatorService.onDestroy()`（`system/translation/ScreenTranslatorService.kt:189-196`）已完整释放资源：`translationJob?.cancel()` → `overlayController.dismissAll()` → `ocrEngine.close()` → `screenCaptureEngine.release()` → `serviceScope.cancel()` → `super.onDestroy()`。无需改动。
- `SelectionAccessibilityService.onDestroy()`（`system/translation/SelectionAccessibilityService.kt:94-98`）已调用 `clearPendingSelection()` + `serviceScope.cancel()` + `super.onDestroy()`。无需改动。
- `AppGraph.startupScope` 已是 `private`（T6 阶段历史遗留已处理）；继续封闭，新增 `fun scheduleStartup(block: suspend CoroutineScope.() -> Unit)` 作为唯一对外入口，`launchStartupTasks()` 改走 `scheduleStartup { settingsStore.migrateSensitiveData() }`，避免后续调用者绕开直接拿 scope。
- `CLAUDE.md` 架构总览段同步把"启动型任务统一走 `startupScope`"改为"统一走 `appGraph.scheduleStartup { ... }`（私有 `startupScope`）"，明确禁止在 ViewModel/Service 里直接拿 scope。

**验收**：
- `rg 'startupScope' app/src/main/java` 只剩 `AppGraph.kt` 内部 2 处（`private val startupScope` 定义 + `scheduleStartup` 内部 `startupScope.launch`），无外部引用。
- `./gradlew.bat app:compileDebugKotlin --rerun-tasks` BUILD SUCCESSFUL（3m13s，仅 `ReasoningTimelineCard.kt:299` 的 `KeyboardArrowRight` deprecated warning，T10 遗留）。
- `./gradlew.bat app:testDebugUnitTest`：**712 个用例中 708 通过，4 个失败**。经 `git stash` 暂存 T12 改动后回归基线重跑，**4 个失败在基线上全部复现**，与 T12 无关：
  - `RoleplayPromptDecoratorTest.decorate_onlineModeWithoutNarrationKeepsPureChatReminder`（T1 前即存在）
  - `RoleplayOnlineReferenceSupportTest.sanitizeRequestMessages_removesThoughtPartsWhenGlobalSwitchIsOff`（T1 前即存在）
  - `RoleplayViewModelTest.sendMessage_switchingBackFromLongformKeepsHistoryFormatAndRequestContext`（T11 后新出现，与 git status 中未提交的 Roleplay 改动相关）
  - `RoleplayViewModelTest.sendMessage_switchingFromLongformToOnlineUsesOnlinePromptAndSanitizedHistory`（同上）
- 两个 Service 的 `onDestroy()` 均保留原有资源释放顺序，无行为回归。

**关键 diff**：
- `di/AppGraph.kt`：新增 `fun scheduleStartup(block)`；`launchStartupTasks()` 重构为调用 `scheduleStartup { settingsStore.migrateSensitiveData() }`。
- `CLAUDE.md`：架构总览段第 39 行更新为 `scheduleStartup` 表述。
- `ScreenTranslatorService.kt` / `SelectionAccessibilityService.kt`：无改动（核对确认 `scope.cancel()` 已存在）。

---

## 📋 执行顺序建议

| 阶段 | 任务 | 总时 |
|---|---|---|
| 快速清理 | T1, T3 | 半天 |
| 可观测性 | T2 | 半天 |
| 文档对齐 | T4 | 1 小时 |
| 结构拆分（小） | T5, T9, T11, T12 | 1-2 天 |
| 结构拆分（大） | T6, T7 | 2-3 天 |
| 性能专项 | T8, T10 | 2 天 |

每完成一条，改状态为 ✅，并在该条下追加"实际耗时 / 关键 diff 路径 / 回归测试命令"。
