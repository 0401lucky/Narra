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

## T6 · 拆分 `AiPromptExtrasService`（1657 行，多职责）  ⬜
**源**：A3 | **预计**：1-2 天

**问题**：覆盖标题、摘要、suggestion、memory 提议、翻译等 5+ 条 prompt 路径 → SRP 严重违反。

**方案**：
1. 按职责切 5 个 `*PromptService`：
   - `TitleSummaryPromptService`
   - `ConversationSummaryPromptService`
   - `SuggestionPromptService`
   - `MemoryProposalPromptService`
   - （翻译继续留在现有 `AiTranslationService`）
2. 共用底层 `AiChatGateway`（新建 thin 接口，包住 `AiGateway.sendMessage`），各 service 注入它。
3. 调用点（`ChatViewModel`、`RoleplayViewModel` 等）改为依赖具体子 service，而非 "what-ever-extras" 大锅。
4. `AppGraph` 拆 5 个 `by lazy`。

**验收**：
- 每个新 service ≤ 300 行。
- `AiPromptExtrasServiceTest` 拆成 5 份对应测试。
- 旧 `AiPromptExtrasService` 作为 `@Deprecated` thin facade 保留 1 版本，仅转发到新 service（方便大调用点迁移）。

---

## T7 · 把 `SettingsViewModel` 的 7 连 launch 下沉为 Coordinator  ⬜
**源**：A2（局部） | **预计**：半天

**问题**：`SettingsViewModel.kt:944-1050` 连续 7 个 `viewModelScope.launch { … }`，纯 IO 路径没理由占 VM 体积。

**方案**：
1. 按领域归到 2-3 个 Coordinator（现有 `SettingsPersistenceCoordinator`/`SettingsAssistantCoordinator` 即可）。
2. VM 只保留"收集 UI 状态 + 转发命令"的入口，每个命令 ≤ 5 行。

**验收**：`SettingsViewModel.kt` ≤ 800 行；单元测试不变。

---

## T8 · Compose 消息气泡稳定性审计  ⬜
**源**：B3 | **预计**：1 天（含 metrics 采集）

**方案**：
1. 开启 Compose Compiler metrics：
   ```
   kotlinOptions.freeCompilerArgs += listOf(
     "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.buildDir}/compose_reports",
     "-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${project.buildDir}/compose_reports",
   )
   ```
2. 跑一次 `./gradlew.bat :app:assembleDebug`，读 `*-classes.txt` / `*-composables.txt`。
3. 给消息气泡 UiModel 加 `@Immutable`/`@Stable` 注解（`MessageBubblePresentation`/`RoleplayMessageBubbles` 的输入模型）。
4. 根据报告拆重组热点（可能只需 2-3 处）。

**验收**：Compose reports 中 `unstable` 参数数量减少 ≥ 50%；消息气泡 recomposition 手测更稳。

---

## T9 · 剧情场景联动逻辑下沉到 domain  ⬜
**源**：C6 | **预计**：1-2 小时

**方案**：
1. `model/RoleplayScenario.kt` 补：
   - `fun withInteractionMode(mode): RoleplayScenario` —— 内部处理 `longformModeEnabled`/`enableRoleplayProtocol` 联动。
   - `fun withLongform(enabled): RoleplayScenario`。
2. `RoleplayScenarioEditScreen.kt` 的 FilterChip + SwitchRow lambda 只改单字段调用上面方法。
3. 新增 `RoleplayScenarioSpecTest` 覆盖联动。

**验收**：UI 层不再写 3 处分支联动；测试覆盖 3 种模式切换。

---

## T10 · 拆分剩余大 Compose 文件  ⬜
**源**：B3（延伸） | **预计**：视 T8 结果而定

**目标文件**：
- `RoleplayMessageBubbles.kt` 1085 行
- `MessageBubblePresentation.kt` 1019 行
- `ChatSpecialPlaySheets.kt` 997 行
- `ChatDrawerComponents.kt` 886 行
- `ChatScreen.kt` 880 行

**方案**：按"外壳容器（长按菜单/滑动）"与"内容渲染（长文/图片/工具卡）"拆两层。具体拆分由 T8 的 metrics 指引。

---

## T11 · `RoleplayDiaryEntry` / `RoleplayDiaryDraft` 共用接口  ⬜
**源**：C4 | **预计**：30 分钟

**方案**：抽 `RoleplayDiaryCore` interface 提供共用字段 getter；两个 data class 实现之。Draft → Entry 的 `copy` 操作走扩展函数。

---

## T12 · 服务层 CoroutineScope 生命周期审计  ⬜
**源**：B5 | **预计**：1 小时

**方案**：
- 抽查 `ScreenTranslatorService` / `SelectionAccessibilityService` 的 `onDestroy()` 是否 `scope.cancel()`。
- `AppGraph.startupScope` 改为私有 + `fun scheduleStartup(block)` 外露，降低滥用风险。

**验收**：两个 Service 确认 `scope.cancel()` 存在；`AppGraph` 无直接 `startupScope` 字段暴露。

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
