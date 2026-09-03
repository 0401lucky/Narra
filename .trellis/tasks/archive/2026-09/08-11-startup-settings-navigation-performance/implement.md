# 2.2.6-dev 启动、设置共享与导航性能：实施计划

## Completion Definition

AC1～AC9 全部满足：AppGraph 三项启动任务具备可测试的局部异常与取消合同，DataStore
读取 `IOException` 能安全降级，应用级唯一设置流每 emission 只解析一次且不在 Main，
AppRoot 只接收去重主题投影，SettingsViewModel 只保留一条上游订阅，具体 Trace 可在
Perfetto 中识别，相关功能 / 质量门禁通过。缺少物理设备或 observability 前置基线时，
不得把导航帧性能部分标记为完成。

## Pre-Start Gate

1. 确认 `08-11-performance-observability-baseline` 已明确统一 Trace 入口、section 命名约束、
   N1 设置 / 阅读页导航场景、fixture / ready 合同和优化前原始结果的保存位置。
2. 与 observability 子任务确认文件所有权：其不修改本任务五个产品文件，本任务不修改
   benchmark、fixture、ready、Compose report、Profile 与诊断文件。
3. 与运行时子任务确认 `SettingsViewModel.kt` 区域所有权：本任务只处理设置订阅 / 草稿
   同步，完成后再由运行时子任务处理声音样本读取 / Base64；禁止并行编辑。
4. 加载 `trellis-before-dev`，复核 backend index、UI / DataStore / 错误 / 日志 / 质量规范及
   五个归属文件的当前工作区差异，不覆盖 lucky 或其它代理的改动。
5. 展示父子任务最终规划摘要并等待 lucky 的新一轮明确批准；本子任务自身不得运行
   `task.py start`。

## Ordered Checklist

### Phase A：测试支点与启动异常边界

1. 在 `AppGraph.kt` 内建立可注入、可单测的命名启动任务边界，保留应用级
   `SupervisorJob` 隔离语义。
2. 为敏感数据迁移、内置预设确保、ContextLog 设置观察分配稳定任务名和独立 trace。
3. 明确捕获顺序：先重抛 `CancellationException`，再执行任务级降级与脱敏日志；禁止使用
   全局 handler 代替局部恢复。
4. 增加启动任务测试：全部成功、迁移失败、预设失败、观察失败、取消；验证 sibling 继续、
   取消无错误日志、异常信息不泄漏设置值。

### Phase B：DataStore IOException 与应用级共享设置

1. 为 `AppSettingsStore` 增加内部可注入的 DataStore、应用 scope、解析 dispatcher 和安全值
   访问适配器；生产构造继续使用现有 Context、DataStore 和 `SecureValueStore`。
2. 在 DataStore 读取 Flow 的 `map` 之前只捕获 `IOException`，写固定 WARN 并发出
   `emptyPreferences()`；非 IO 异常原样抛出，写入路径不进入该 catch。
3. 将安全值读取、JSON 解码和完整 `AppSettings` 构造放入统一后台解析管线，并接入
   `narra.settings.secure_read`、`narra.settings.decode` trace。
4. 在应用 scope 使用 eager、replay=1 的共享流，并保持 `SettingsStore.settingsFlow` 的
   `Flow<AppSettings>` 兼容类型。
5. 增加 Store 测试：IOException / 非 IO、并发 collector、多个 `first()`、replay、第二次
   emission、后台线程、安全值读取不随 collector 倍增。
6. 运行既有 `AppSettingsStoreSensitiveMigrationTest`，确认密钥迁移与普通 JSON 清空规则无
   回归。

### Phase C：主题窄投影与 SettingsViewModel 单轨订阅

1. 在 `AiSettingsRepository.kt` 定义最小主题投影并对共享设置流执行
   `distinctUntilChanged()`，接入 `narra.settings.project_theme` trace。
2. 为主题投影增加测试：仅非主题字段变化无 emission，两个主题字段变化分别产生 emission。
3. 修改 `AppRoot.kt`：只订阅可空持久主题投影与同形状的窄主题草稿预览，删除完整设置与
   `SettingsViewModel.uiState` 的根级收集；仅两个主题草稿保留既有即时换肤，其它草稿不触发
   根主题更新，并保留首个真实快照前的 loading、合规门禁、更新检查和导航创建顺序。
4. 调整 `SettingsViewModel.kt` 字段顺序，以
   `settingsFlow.onEach(syncStoredSettings).stateIn(...)` 同时驱动草稿同步和
   `storedSettings`，删除 `init` 中第二条 collect，并接入
   `narra.settings.sync_view_model` trace。
5. 扩展 `SettingsViewModelTest`：统计上游订阅数、验证默认初值不污染草稿、真实 emission 同步、
   窄主题预览只响应两个主题草稿，以及 theme/provider 保存和 `hasDraftChanges()` 无回归。

### Phase D：定向验证与同环境复测

1. 先运行新增长度最窄的 JVM 测试，每条命令最长等待 60 秒；失败先修复并重跑。
2. 运行全部相关设置 / Repository / ViewModel 既有 JVM 测试，确认共享语义没有破坏 Fake 与
   `first()` 调用。
3. 运行 AndroidTest 源码编译、Lint、debug 构建；检查 trace API 在目标变体可编译。
4. 在有合格物理设备时，使用 observability 子任务固定的 N1 过滤命令复测至少 5 次，保存
   Perfetto / JSON；不得修改该子任务的 benchmark 文件来让结果通过。
5. 对比优化前后解析次数、线程和 N1 P50 / P90 / P95；P95 回退超过 5% 时检查 fixture、
   热状态与改动，无法解释则回滚引起回退的优化。
6. 运行 `rg` 和 diff 审查，确认没有修改 RP、TTS、会话事务、Profile、诊断或发布文件，也
   没有记录 API Key、JSON、用户文本与本地路径。

## Validation Commands

### 定向 JVM 测试（每条最长 60 秒）

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.example.myapplication.di.AppStartupTaskSchedulerTest" `
  --console=plain

.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.example.myapplication.data.local.AppSettingsStoreTest" `
  --console=plain

.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.example.myapplication.data.repository.ai.AiSettingsRepositoryTest" `
  --console=plain

.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.example.myapplication.viewmodel.SettingsViewModelTest" `
  --console=plain

.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.example.myapplication.data.local.AppSettingsStoreSensitiveMigrationTest" `
  --console=plain
```

测试类最终名称若按仓库现有命名微调，命令同步更新到任务记录，不保留失效示例。

### 质量门禁

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

### 设备 / Trace（依赖 observability 子任务）

```powershell
adb devices -l
.\gradlew.bat :baselineprofile:connectedBaselineAndroidTest --console=plain
```

- 优先使用 observability 子任务记录的 N1 class / method 过滤器窄跑，再决定是否跑完整设备集。
- 运行前记录型号、API、刷新率、电量、温控、动画缩放、APK 与 commit。
- 无在线物理设备、温控异常、ready 失败或前置基线缺失时，只记录“未取得结果”。

## Acceptance Mapping

- AC1 → Phase A。
- AC2～AC4 → Phase B。
- AC5～AC6 → Phase C。
- AC7 → Phase A～C 的具体 section 与 Phase D Perfetto 审查。
- AC8 → Phase D 的同环境 N1 复测。
- AC9 → Phase D 全部门禁与范围审查。

## Risky Files / Rollback Points

- `AppGraph.kt`：错误边界过宽会掩盖必需失败。命名调度器作为独立回滚点，取消与非 IO
  异常测试不过不得继续。
- `AppSettingsStore.kt`：共享流是最大影响面。先提交 / 验证错误恢复与共享测试，再迁移 UI；
  若第二次 emission 丢失或 scope 生命周期错误，整体回滚共享实现，不保留双流兼容代码。
- `AiSettingsRepository.kt` + `AppRoot.kt`：主题投影和根订阅必须成对提交 / 回滚，避免主题
  loading 永久不结束或合规前提前创建导航。
- `SettingsViewModel.kt`：`onEach` 与 `stateIn` 顺序错误会把默认值同步到草稿。单轨迁移单独
  回滚，不恢复第二条原始 collect 作为长期兼容方案。
- Trace：若 observability 统一入口尚未就绪，暂停具体 section 接入；不得自行新增另一依赖或
  修改 benchmark 文件绕开依赖。

## Handoff

- 向 observability 子任务提供最终稳定 section 名称和需要采集的 N1 结果字段，不向其转交
  本任务五个产品文件。
- 向父任务报告功能门禁、解析计数、线程、Trace 原始结果、N1 分位数及任何“未取得结果”；
  由父任务执行跨子任务集成复测与 2.2.6-dev 最终结论。
