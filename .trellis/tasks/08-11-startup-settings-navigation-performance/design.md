# 2.2.6-dev 启动、设置共享与导航性能：技术设计

## 1. 边界、所有权与依赖顺序

本子任务拥有以下产品文件：

- `AppGraph.kt`
- `AppSettingsStore.kt`
- `AiSettingsRepository.kt`
- `AppRoot.kt`
- `SettingsViewModel.kt` 的设置订阅、持久快照与草稿同步区域

新增或扩展的定向测试也由本子任务负责。宏基准、fixture、ready、Compose 报告、Profile、
通用 Trace 基础设施和 `ApplicationExitInfo` 由
`08-11-performance-observability-baseline` 负责。

实施依赖顺序固定为：

```text
observability 子任务
  ├── 固定 N1 设置/阅读页导航场景与优化前原始结果
  └── 提供统一 Trace 入口/依赖
        ↓
本子任务
  ├── 启动异常边界与 DataStore IOException 降级
  ├── AppSettings 应用级共享与后台解析
  ├── 根主题窄投影与 SettingsViewModel 单轨订阅
  └── 在归属文件中增加具体 section 并复测 N1
        ↓
父任务
  └── 同设备集成复测、跨子任务回归和最终结论
```

若 observability 子任务尚未提供 Trace 入口或优化前 N1 结果，可以先完成纯逻辑和故障注入
测试，但不得自行修改其基准文件，也不得宣称导航性能已验收。

本任务完成后，运行时子任务才可在同一 `SettingsViewModel.kt` 中修改声音样本读取 /
Base64 区域；两者不得并行编辑，运行时子任务必须保留本任务的订阅改动。

## 2. 当前与目标数据流

### 2.1 当前结构

```text
DataStore.data（冷流）
  → 每个 collector 各自读取 SecureValueStore
  → 每个 collector 各自解码多组 JSON
  → AppRoot 原始设置 collector
  → SettingsViewModel.stateIn collector
  → SettingsViewModel.init collector
  → AppGraph / Chat / RP / Moments / Phone / first() 等更多 collector
```

这会让同一 Preferences emission 重复执行 Keystore、Base64、Gson 和完整对象构造，并让
根组合同时受到完整设置和完整设置页草稿状态影响。

### 2.2 目标结构

```text
DataStore.data
  → IOException 精确降级
  → 后台 dispatcher：安全值读取 + JSON 解码 + AppSettings 构造
  → applicationScope 共享，replay = 1
  → SettingsStore.settingsFlow（所有 Store/Repository 消费者共用）
       ├── 完整 AppSettings → 各业务消费者
       └── ThemeSettings(themeMode, appColorTheme)
             → distinctUntilChanged
             → AppRoot

SettingsViewModel：共享 settingsFlow
  → onEach 同步真实存储值到草稿
  → stateIn 暴露 storedSettings
```

共享流使用 replay 而不是把 `AppSettings()` 作为已加载状态。这样新 collector 可立即拿到
最近真实快照，而首次读取完成前 `AppRoot` 仍能维持 `null` / loading 门禁，避免默认主题
被误当成持久化已就绪。

## 3. AppGraph 启动任务合同

### 3.1 命名任务边界

将无名 `scheduleStartup {}` 收敛为可测试的命名调度入口。每个任务都在自身协程内执行：

1. 开始稳定 trace section。
2. 执行业务块。
3. 捕获 `CancellationException` 后立即重新抛出，不写失败日志。
4. 捕获其它异常后调用该任务的降级动作，再用 `AppLogger` 写脱敏诊断。
5. 结束当前任务，不取消同一 `SupervisorJob` 下的其它启动任务。

调度器 / 边界逻辑放在 `AppGraph.kt` 内并允许注入测试 scope 与失败观察器，避免 JVM 测试
必须初始化完整 Android `Application`、Room 和所有 AppGraph lazy 依赖。

### 3.2 三项任务的失败策略

- 敏感数据迁移：best-effort。失败时不删除旧值、不写完成标记，下次启动继续重试；日志只
  记录任务名和异常，不记录 Key、JSON 或 Preferences 内容。
- 内置预设确保：best-effort。失败时现有用户数据保持不变，本次不假定内置预设已经就绪；
  下次启动依靠幂等 `ensureBuiltInPresets()` 重试。
- ContextLog 设置观察：订阅共享设置流。DataStore `IOException` 已在 Store 层降级为默认
  设置；若仍遇到非 IO 异常，记录稳定任务名并停止该观察任务，不影响其它启动任务。不能
  用全局 `CoroutineExceptionHandler` 代替这三处局部策略。

## 4. AppSettingsStore 共享与错误设计

### 4.1 可测试依赖

`AppSettingsStore` 保留现有 `AppSettingsStore(context)` 调用方式，同时提供内部可注入边界：

- `DataStore<Preferences>` 或等价数据源。
- 应用级 `CoroutineScope`。
- 设置解析 dispatcher，生产默认 `Dispatchers.IO`。
- 安全值访问适配器，生产实现继续委托现有 `SecureValueStore`。
- 可选的测试观察器，仅用于统计解析次数 / 线程，不写入用户日志。

安全值适配器定义与默认实现留在 `AppSettingsStore.kt`，不修改
`SecureValueStore.kt`，也不新增第二套加密实现。

### 4.2 读取错误

在 `map` 之前只处理 DataStore 上游读取 `IOException`：

```text
IOException
  → AppLogger.w（固定消息，无原始数据）
  → emit(emptyPreferences())
  → 继续生成默认 AppSettings

其它 Throwable
  → 原样抛出
  → 测试/诊断可观察，不能伪装成默认设置成功
```

现有 `saveXxx()` / `edit` 写入异常不进入该 catch 范围，继续由调用方处理。

### 4.3 共享与线程

解析管线完成 `flowOn(settingsDispatcher)` 后，在 AppGraph 提供的应用级 scope 中使用
`SharingStarted.Eagerly`、`replay = 1` 共享。`SettingsStore.settingsFlow` 继续声明为
`Flow<AppSettings>`，从而不迫使所有调用方与测试 Fake 同步改为 `StateFlow`。

应用级唯一 Store 是共享所有者，所以直接调用 `settingsStore.settingsFlow.first()` 与经
`AiSettingsRepository` 收集都命中同一 replay，不会重新执行 DataStore / Keystore / Gson。
写入 DataStore 后的新 emission 仍会重新解析一次并替换 replay 快照。

## 5. Repository、AppRoot 与 SettingsViewModel

### 5.1 主题投影

在 `AiSettingsRepository.kt` 定义最小不可变主题投影，只包含：

- `ThemeMode`
- `AppColorTheme`

Repository 从共享完整设置流映射该投影并执行 `distinctUntilChanged()`。投影不包含 provider、
消息、loading、草稿或完整 `AppSettings`，因此非主题持久 emission 不会通知 `AppRoot`。

旧版 `AppRoot` 直接读取 `SettingsUiState.themeMode/appColorTheme`，设置页选择主题时会即时换肤。
为保留该行为但避免订阅整个 `SettingsUiState`，`SettingsViewModel` 额外暴露可空的窄主题草稿
预览，只包含相同两个字段。首个真实持久设置到达前预览保持 `null`；之后仅主题草稿变化才
更新预览，其它草稿、loading 和 message 变化不得更新该 Flow。

### 5.2 AppRoot

`AppRoot` 只收集可空的持久主题投影和窄主题草稿预览：

- 首个真实投影前保持当前 loading / 合规门禁顺序。
- 主题 ready 后按 `ThemeMode.SYSTEM/LIGHT/DARK` 和 `AppColorTheme` 安装主题；存在草稿预览时
  优先使用预览，从而保留设置页即时换肤。
- 仍创建并向导航层传递应用级 `SettingsViewModel`，但不再收集
  `SettingsViewModel.uiState`。
- 合规通过前不创建 `AppNavHost`、不触发更新检查和 Worker 的现有合同不变。

### 5.3 SettingsViewModel

将 `_uiState` 建立在共享设置管线之前，并使用一条上游完成两项工作：

```text
settingsRepository.settingsFlow
  → onEach(仅真实 emission：syncStoredSettings)
  → stateIn(viewModelScope, Eagerly, AppSettings())
  → storedSettings
```

`stateIn` 的默认初值不会经过 `onEach`，因此不会把默认 Provider / 主题误同步成真实草稿；
真实 emission 同时更新 `storedSettings` 和草稿。删除 `init` 中第二条原始 collect。

## 6. Trace 合同

具体名称须与 observability 子任务的 Trace API 对齐，建议使用下列稳定 section；最终名称
一旦用于优化前后对比就不得中途改变：

- `narra.startup.sensitive_migration`
- `narra.startup.ensure_presets`
- `narra.startup.context_log_settings`
- `narra.settings.decode`
- `narra.settings.secure_read`
- `narra.settings.project_theme`
- `narra.settings.sync_view_model`

section 名称不得拼接设置值、用户 ID、Provider、API Key、JSON 长度以外的原文、文件路径或
异常文本。`narra.settings.decode` 与 `narra.settings.secure_read` 必须能在 Perfetto 中证明不在
Main；AppGraph section 用于区分三个任务，不能用一个大而模糊的“startup”区间替代。

## 7. 验证设计

### 7.1 JVM 故障与共享测试

- 启动调度器：成功、迁移失败、预设失败、长期观察失败、取消四类用例；确认 sibling 继续、
  取消不记录、日志脱敏。
- DataStore：`IOException` 发默认值，非 IO 异常不回退；写异常不受读取 catch 影响。
- 共享流：两个长期 collector 加多个 `first()`，验证每 emission 解析一次、replay 可用、第二次
  写入只新增一次解析。
- 线程：使用可关闭的命名测试 dispatcher，记录安全值读取与解析线程，断言不在测试 Main。
- Repository：非主题字段变化不产生新主题投影，两个主题字段分别变化会产生新值。
- SettingsViewModel：单一订阅、默认初值不污染草稿、真实 emission 同步；窄主题预览仅响应
  两个主题草稿，现有即时换肤、保存 / 脏状态测试继续通过。

### 7.2 设备与 Trace

复用 observability 子任务的 N1 场景：固定合规状态，从 RP 起点进入设置页与阅读页并返回，
至少 5 次。优化前后固定设备、APK / commit、刷新率、动画缩放、温控和 fixture，保存原始
Perfetto / JSON，报告：

- 每个 Preferences emission 的 `narra.settings.decode` 次数。
- 设置解析和安全值读取所在线程及持续时间。
- N1 `frameOverrunMs` P50 / P90 / P95。
- AppGraph 三任务失败注入下是否出现未捕获崩溃。

没有物理设备时只运行 JVM、AndroidTest 源码编译、Lint 和构建，不使用模拟器数字替代性能
阈值。

## 8. 兼容、风险与回滚

- 共享流风险：上游意外完成或 scope 被错误取消会让所有消费者失去更新。共享改动与 UI
  迁移分开提交 / 回滚，并测试第二次 emission。
- 启动风险：过宽 catch 会掩盖编程错误。只恢复已定义的迁移 / 预设任务失败与 DataStore
  `IOException`，其它异常保持可诊断。
- 主题风险：错误的初值会造成主题闪烁或提前构建导航。保留可空 ready 语义，并成对回滚
  Repository 投影与 AppRoot 改动。
- 草稿风险：若默认 `stateIn` 值进入同步逻辑，会覆盖真实草稿。必须保持 `onEach` 位于
  `stateIn` 上游并补回归测试。
- 安全风险：replay 中包含进程内完整 `AppSettings`，行为与现有 ViewModel stateIn 一致；
  不新增磁盘明文、日志或跨进程缓存，进程结束即释放。
- 指标无改善时保留正确性修复、共享解析和有效测试，回退没有证据支持的额外导航 / UI
  调整；本设计默认不包含后者。
