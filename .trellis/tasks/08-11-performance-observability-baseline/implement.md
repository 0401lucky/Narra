# 2.2.6-dev 性能基准、Trace、Profile 与本地崩溃诊断：实施计划

## Completion Definition

PRD 的 AC1～AC12 全部满足：baseline 显式会话能够确定性准备 fixture 并以强 ready 驱动
启动、帧、导航和 Profile；Trace / Compose / Profile 原始证据可复核；本地退出诊断限量、
脱敏、默认离线且可由用户主动导出；dev / release 正常行为无回归。

没有固定物理设备时可以完成代码、JVM、设备源码编译、fixture / ready smoke、Compose 报告和
Profile 产物静态门禁，但不能把 AC4、AC8、Profile 安装或帧阈值标为通过，也不能归档为完整
性能验收。

## Pre-Start Gate

1. 加载 `trellis-before-dev`，阅读本任务 JSONL 注入的全部规范与研究。
2. 运行本目录 `task.py validate`，确认 PRD / design / implement / 两份 manifest 均有效。
3. 由父任务展示最终规划摘要；只有 lucky 在该摘要后明确批准，才运行 `task.py start`。
4. 与其它子任务确认文件所有权：本任务不修改 `AppGraph.kt`、`AppSettingsStore.kt`、
   `AiSettingsRepository.kt`、`AppRoot.kt`、`SettingsViewModel.kt`。
5. 冻结仓库当前 AndroidX Benchmark / Baseline Profile 工具版本；本轮前后对比中不升级。

## Dependency Graph

```text
A. 合约与测试模型
├── B. baseline-only session / fixture / ready
├── C. 本地退出诊断与主动导出
└── D. Compose 全量报告流水线

B → E. Startup / Frame / Navigation 场景
B → F. Baseline Profile 生成路径
A + E → G. Trace 消费与结果归档

启动设置子任务 / RP 子任务完成 Trace 发射与隔离接入
  → H. 父任务同设备集成验收
```

B、C、D 在文件不冲突时可并行；E、F 必须等待 B 的 fixture / ready 合约稳定。H 是跨子任务
集成依赖，不得通过本任务越权修改其它 owner 文件来抢跑。

## Ordered Checklist

### Wave 0：工作区与完成标准

1. 检查 `git status` 和相关文件差异，只修改本任务范围；现有用户改动不得回退。
2. 记录当前 Gradle、AGP、Kotlin / Compose Compiler、Benchmark、ProfileInstaller、JDK
   版本，作为工具链冻结基线。
3. 搜索现有 testTag、导出 launcher、`AppLogger` / `SensitiveTextRedactor`、AppJson、Profile
   生成和 benchmark helper，复用现有入口。
4. 先创建测试文件和失败用例，再实现对应合约；单元测试命令最大等待 60 秒。

### Wave 1：合约层

1. 新增 `BenchmarkSessionDescriptor`、`BenchmarkScenarioId`、`FixtureSpec`、
   `BenchmarkRuntimePolicy` 和 ready ID 目录。
2. 新增 `PerformanceTraceContract`，固定 section 名称、发射 owner、场景必需项和隐私规则。
3. 为渠道门禁、session schema / TTL、非法 scenario、过期 session、Trace 名称唯一 / 长度 / 无
   动态敏感字段补 JVM 测试。
4. 暴露窄接口给其它子任务：
   - 启动设置子任务只消费 runtime policy 并发射已登记 section；
   - RP 子任务只消费离线 replay spec 并发射 RP section；
   - 不向这些 owner 文件提交本任务改动。

### Wave 2A：baseline-only fixture 与 ready

1. 在 `app/src/baseline` 增加受 signature 权限保护的控制组件和 Manifest，仅 baseline 变体可见。
2. 将 fixture 控制放入独立 `:benchmark_fixture` 进程；增加最小 Application 进程谓词，证明该
   进程不会执行正常启动任务，主进程行为不变。
3. 实现 S0～S3 prepare：
   - S0 只创建会话，不预热主进程；
   - S1 固定合规、RP 场景 / 会话；
   - S2 固定普通聊天 / RP 各 100 条消息；
   - S3 复制脱敏旧快照并校验 SHA-256，准备进程不打开当前 Room。
4. 所有 fixture 使用固定 ID / 时钟 / 顺序；实现重复准备、原子写入、数量与安全摘要复核。
5. 在目的地包装层或页面根节点增加最小 ready reporter；baseline 显式会话下启用
   `testTagsAsResourceId`，不依赖 UI 文案，不修改 AppRoot。
6. 在 `baselineprofile` 建立唯一 `ReadyAssertions` / selector helper，替换所有被忽略的 wait 和
   nullable click。
7. 增加负向验证：无 session、错误渠道、过期 session、fixture 错误、错误 ready ID、目标控件
   缺失都必须失败且不留下“成功”结果。
8. 验证 fixture 无真实 Provider、密钥、附件和远端 URL；后台污染隔离策略由对应 owner 接入后
   再做父任务集成检查。

### Wave 2B：本地退出诊断与主动导出

1. 新增可替换 `ProcessExitInfoSource` 和 API 30+ Android 实现；API 26～29 明确返回
   Unsupported，不安装全局未捕获处理器。
2. 新增 allowlist mapper：仅保留异常 reason 分类、时间、版本、importance / status、PSS / RSS；
   不读取 trace stream、description、process state summary、PID、进程名或业务数据。
3. 新增 `LocalExitDiagnosticsStore`：`noBackupFilesDir`、原子更新、去重、20 条 / 256 KiB 双上限、
   最旧淘汰、损坏条目隔离和 fail-open。
4. 新增诊断服务，在正常页面首帧后使用 IO dispatcher 收集；显式 benchmark session 跳过。
5. 新增独立 Diagnostics ViewModel / 设置页面和 CreateDocument 导出 launcher，不向
   `SettingsViewModel` 增加字段。
6. 导出 JSON 只包含 schema、生成时间、应用版本、隐私声明和 allowlist events；取消为正常
   控制流，写失败保留本地记录并显示脱敏中文文案。
7. 增加 JVM 测试：API 分支、reason 过滤、去重、轮转、总大小、损坏文件、原子写失败、查询
   失败、benchmark 跳过、导出取消 / 失败、敏感哨兵不存在。
8. 增加设备测试：`noBackupFilesDir` 写入 / 重启读取、CreateDocument 写入代表性 URI、页面
   状态与主动导出交互；不制造真实 crash 作为普通测试流程。

### Wave 2C：Compose 全量报告流水线

1. 将正式报告 destination 参数化为 `variant/runId`，普通增量目录与正式归档目录分离。
2. 新增 debug / baseline production 全量报告任务：禁用 Kotlin incremental 和 build cache，
   `--rerun-tasks` 编译单一目标变体。
3. 新增报告验证器，检查 stability / skippability / metrics 文件和 AppRoot、聊天、RP、设置
   主源码哨兵；空 CSV、只有测试类或缺文件立即失败。
4. 生成 report manifest（variant、commit、工具版本、文件清单、计数、SHA-256）和 zip。
5. 在归档后运行一次 JVM 单测增量编译，验证 zip / manifest 哈希不变。
6. 若更新 CI，只上传正式任务产物；不上传 `app/build/compose_reports` 的共享增量目录。

### Wave 3：Macrobenchmark 场景与结果归档

1. 重构 `StartupBenchmarks` 为 S0～S3 参数化场景，None / Require Baseline 各 10 次。
2. 新增 F1 普通聊天、F2 RP 滚动：固定列表、固定手势比例、底部 → 顶部 → 底部、每项至少
   5 次，使用 `FrameTimingMetric`。
3. 新增 F3 RP 离线流式重放：固定 chunk、48 ms pump、无网络、固定结束 ready；RP 子任务完成
   replay 接入前该场景明确标记依赖，不伪造空测量。
4. 新增 N1 设置 / RP 阅读页导航，所有点击和返回后强等待目标 ready。
5. 为每个场景添加 Trace 指标 / 后处理，输出 presence、count、duration、missingSections。
6. 增加设备环境采集与 run manifest；复制 Macrobenchmark JSON / Perfetto 到独立 run 目录并
   计算 SHA-256，禁止后一次运行覆盖前一次。
7. fixture prepare、滚动复位、session 创建 / 清理、Home / force-stop、环境采集均放在测量区间外。

### Wave 4：应用 Baseline Profile

1. 重写 `BaselineProfileGenerator`，复用 S2 fixture、ready helper 和强 selector。
2. 覆盖启动、RP、普通聊天历史、设置、RP 阅读页和角色音色 Sheet；每一步失败立即中止。
3. 运行 Profile 生成，将当前源码 Profile 写入 generated 目录。
4. 新增验证任务：当前源码 Profile 与当前合并 Profile 均必须包含
   `Lcom/example/myapplication/`；仅依赖规则、空文件或读取旧 intermediate 时失败。
5. 固定物理设备上运行 None / Require Baseline 各 10 次，记录 Profile 安装状态、启动中位数和
   原始结果；目标改善不足时如实报告并检查路径，而不是手工放宽断言。

### Wave 5：跨子任务 Trace / 隔离集成

1. 确认启动设置子任务已在 owner 文件发射 AppGraph、Room、设置解析 / 安全值、首个消息映射
   section，并在 AppRoot 接入 benchmark 隔离策略。
2. 确认 RP 子任务已发射 `narra.roleplay.map_messages` 并接入离线 replay source。
3. 本任务只更新场景必需 section 清单和结果校验，不修改上述 owner 文件。
4. 重新运行 S0～S3、F3、N1，要求 `missingSections` 为空；任何缺失回报给对应 owner 修复。

### Wave 6：全量质量门禁与隐私复核

1. 运行定向 JVM / AndroidTest 编译 / baseline 构建 / Compose 报告 / Profile 验证。
2. 扫描 Manifest、依赖和源码，确认无 Crashlytics、Sentry、自动上传、ANR trace 导出、持久化
   Logcat 或额外网络权限。
3. 使用包含聊天、Prompt、API Key、Bearer、Base64、SQL、URI、绝对路径的注入样本导出，逐项
   搜索确认敏感哨兵不存在。
4. 固定物理设备和温控正常时运行完整 connected baseline suite；模拟器结果只标 smoke。
5. 检查 `git diff` / `git status`，确认未修改禁止 owner 文件、未提交设备原始隐私数据、旧构建
   intermediates 或无关工作区文件。

## Validation Commands

### 规划与静态检查

```powershell
python .\.trellis\scripts\task.py validate .trellis\tasks\08-11-performance-observability-baseline
rg -n "crashlytics|sentry|firebase-crashlytics|traceInputStream|processStateSummary" `
  app baselineprofile gradle .github
git diff -- app/src/main/AndroidManifest.xml app/build.gradle gradle/libs.versions.toml
```

### 定向 JVM 测试（每条最大等待 60 秒）

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.example.myapplication.system.performance.BenchmarkSessionContractTest" `
  --console=plain

.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.example.myapplication.system.performance.PerformanceTraceContractTest" `
  --console=plain

.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.example.myapplication.system.diagnostics.*" `
  --console=plain

.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.example.myapplication.ui.screen.settings.SettingsBenchmarkReadyTest" `
  --console=plain
```

实现时若最终测试类名不同，应把命令同步到本文件，不得保留不可执行占位。

### Compose 报告与构建

```powershell
.\gradlew.bat :app:generateDebugComposeCompilerReportArchive --console=plain
.\gradlew.bat :app:generateBaselineComposeCompilerReportArchive --console=plain
.\gradlew.bat :app:verifyComposeCompilerReportArchives --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat assembleBaseline --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

上述 Compose 任务是计划新增的稳定入口；实现后的真实任务名必须保持一致，或同步修改文档与
CI 调用方。

### 设备前置记录

```powershell
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
adb shell dumpsys battery
adb shell dumpsys thermalservice
adb shell settings get system peak_refresh_rate
adb shell settings get system min_refresh_rate
adb shell settings get global window_animation_scale
adb shell settings get global transition_animation_scale
adb shell settings get global animator_duration_scale
```

### Macrobenchmark 与 Profile

```powershell
# 完整基准必须在同一次 Gradle 调用中运行并归档，确保全套场景共用同一 runId。
.\gradlew.bat :baselineprofile:runAndArchiveNarraBenchmarks --console=plain

# 定向 smoke 仅用于排查单个类，不替代上面的完整归档入口。
.\gradlew.bat :baselineprofile:connectedBaselineAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.baselineprofile.StartupBenchmarks `
  --console=plain

.\gradlew.bat :baselineprofile:connectedBaselineAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.baselineprofile.FrameBenchmarks `
  --console=plain

.\gradlew.bat :baselineprofile:connectedBaselineAndroidTest --console=plain
.\gradlew.bat app:generateBaselineProfile --console=plain
.\gradlew.bat :app:verifyNarraBaselineProfile --console=plain
```

默认入口会自动生成不可覆盖的 runId。只有需要复现指定标识时才传
`-PnarraBenchmarkRunId=<runId>`；不要把 connected 与 archive 拆成两次 Gradle 调用，否则第二次
调用会重新准备 run，并按防覆盖门禁明确失败。

没有在线固定物理设备时，connected 命令只能记录“未取得结果”；不得用旧输出或模拟器数值
替代 AC4 / AC8。

## Acceptance Mapping

- AC1 → Wave 1、Wave 2A、Wave 5 的渠道 / 会话 / 隔离测试。
- AC2 → Wave 2A 的 S0～S3 fixture、幂等与摘要验证。
- AC3 → Wave 2A 的强 helper 和负向失败测试。
- AC4 → Wave 3 的场景、迭代、环境 manifest 与原始结果归档。
- AC5 → Wave 1 Trace 合约、Wave 3 消费、Wave 5 跨子任务集成。
- AC6 → Wave 2C 的 full compile、哨兵验证、独立归档与哈希复核。
- AC7～AC8 → Wave 4 的 Profile 生成、规则验证和同设备对比。
- AC9 → Wave 2B 的 API / 存储 / 轮转 / fail-open 测试。
- AC10～AC11 → Wave 2B、Wave 6 的主动导出和隐私 / 依赖扫描。
- AC12 → Wave 6 与全部真实命令结果。

## 执行与验证记录（2026-08-12）

### 已取得结果

- `verifyNarraBenchmarkContractDrift` 通过。
- 性能合约 JVM 测试共 28 项，0 failures、0 errors、0 skipped。
- 诊断相关测试、`DiagnosticsViewModelTest` 和 `SettingsBenchmarkReadyTest` 通过。
- `:app:compileDebugAndroidTestKotlin` 通过。
- debug / baseline Compose Compiler 正式归档生成并通过双归档校验；2026-08-12 复核任务再次通过。
- API 36 模拟器 P1 Profile smoke：1 test，0 failures、0 errors、0 skipped。
- `app:generateBaselineProfile`、`:app:verifyNarraBaselineProfile`、`assembleBaseline`、`assembleDebug` 均通过。
- 两份源码 Profile 均为 7662 行、1163034 bytes，SHA-256 为
  `e9f3c2fdb432ef505b81358fc870482167ba30c739fcc0eaf3bd116e02c5114b`。
- 最终 `lintDebug` 通过：0 errors、41 warnings、4 hints（以最终一次 Lint 报告为准）。
- 依赖与源码扫描未发现 Crashlytics、Sentry、Bugsnag、AppCenter、诊断自动上传、ANR trace
  读取、全局 `UncaughtExceptionHandler` 或 Logcat 持久化。
- Debug 合并 Manifest 不包含 benchmark 权限或控制 Provider；Baseline 合并 Manifest 使用
  signature 权限、`grantUriPermissions=false` 和独立 `:benchmark_fixture` 进程。
- `task.py validate`、`git diff --check` 均通过。

### 尚未验收

- AC1：`BenchmarkRuntimePolicy` 尚未接入应用更新检查、周期 Worker 和真实 Provider 的实际
  消费点；当前只完成会话解析、ready 暴露与退出诊断跳过。
- AC2 / AC3：尚无 S0～S3 在设备上连续 prepare 两次的完整验证，也缺错误 ready、缺失 action
  和 fixture 注入失败的运行时负向 smoke。
- AC5：RP 已发射映射、完整状态回写、自动贴底和 replay pump 四段 Trace；启动、Room、设置
  解析 / 安全值、首个消息映射等其它 owner 仍需结合完整设备归档复核。
- F3 baseline-only 链路已接通固定 100 条 fixture、约 48 ms 流式动作、起止 ready、
  `ROLEPLAY_REPLAY_PUMP` 和零 Provider 门禁；API 36 模拟器三条合约 smoke 已通过。
- `runAndArchiveNarraBenchmarks` 尚未在固定物理设备运行，未产生完整 S0～S3、F1～F3、N1
  FrameTiming / Perfetto 归档；当前缺口是原始性能证据，不是 F3 接线。
- 当前只有 API 36 模拟器交互 smoke，没有固定物理设备结果。
- 尚无 None / Require 启动对比、帧分位数与阈值、Profile 安装状态和温控证据。
- AC1～AC5、AC8 仍需按各自完整设备证据复核；AC6、AC7、AC9～AC12 已按现有真实证据验收。

### 防回归结论

- Compose `testTag` 不等同于 UIAutomator 可见资源 ID；ready 必须挂在真实可见实体节点，独立
  Sheet / Dialog 还必须在对应窗口安装资源 ID host。
- 动作查找、滚动、750 ms 宽限与二次点击必须共享同一动作 deadline；deadline 后只能采样
  安全状态，不能继续点击或滚动。
- 点击必须避开顶部状态栏与底部导航栏，不能只根据节点存在就执行 `UiObject2.click()`。
- 30 分钟 TTL 只允许 P1 + `PROFILE`；普通 `MEASURE` 场景继续限制为最多 5 分钟。

## Risky Files and Rollback Points

- `StartupBenchmarks.kt`、`BaselineProfileGenerator.kt`、新增 Frame 场景：强等待错误会让所有
  结果失真；先提交 helper 与负向测试，再迁移场景。
- `app/src/baseline` Manifest / 控制组件：exported 或权限配置错误可能扩大入口；baseline-only、
  signature 权限、渠道和 session 四重门禁必须一起验证，可整体删除 source set 回滚。
- Application 进程谓词：不得误跳主进程启动任务；用精确进程名测试并单独提交。
- S3 二进制快照：来源、版本或 SHA-256 不明立即回滚，不提交用户现场数据库。
- Compose Gradle 配置：不得清理共享源码或整个 build 根目录；每个任务只操作自己的输出目录。
- generated Baseline Profile：与生成器分开审查；规则不含应用 namespace 或同设备变慢时回滚
  Profile 文件但保留失败证据。
- 本地诊断文件：解析 / 写入异常必须 fail-open；若出现启动回归，先禁用收集入口，不自动删除
  已有记录。
- 设置诊断页面：不得把 diagnostics 塞入 `SettingsViewModel`；路由或导出失败可独立回滚，存储
  仍保持本地可读。
- 跨子任务 owner 文件：发现必须修改禁止列表时停止本任务改动，向父任务提交精确接口需求，
  由对应 owner 完成。
