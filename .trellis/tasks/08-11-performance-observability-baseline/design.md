# 2.2.6-dev 性能基准、Trace、Profile 与本地崩溃诊断：技术设计

## 1. Architecture Boundary

本任务把“测量控制面”和“正常产品面”分开：

```text
baselineprofile 测试进程
  → baseline-only 控制组件（fixture / session）
  → Narra baseline 主进程
  → 稳定 ready + Trace section
  → Macrobenchmark / Profile / Perfetto 原始结果

正常 dev / release 进程
  → 本地 ApplicationExitInfo 摘要
  → noBackupFilesDir 限量存储
  → 用户主动选择文件后导出
```

### 1.1 本任务拥有的实现面

- `baselineprofile/**`：场景驱动、强等待、指标、环境清单、Profile 路径和结果校验。
- `app/src/baseline/**`：仅 baseline 变体编译的控制组件、fixture、旧快照资产和 Manifest。
- 新的 `system/performance/**`：benchmark 会话策略、ready / Trace 合约和无敏感数据的结果模型。
- 新的 `system/diagnostics/**`：退出信息适配、映射、限量存储、导出编码与服务。
- 新的设置诊断页面 / 路由及其 ViewModel；不向现有 `SettingsViewModel` 增加职责。
- Compose Compiler 报告生成 / 归档配置、应用 Profile 生成产物和对应验证任务。

### 1.2 明确不修改的并行所有权

本任务不修改：

- `AppGraph.kt`
- `AppSettingsStore.kt`
- `AiSettingsRepository.kt`
- `AppRoot.kt`
- `SettingsViewModel.kt`

本任务提供 `PerformanceTraceContract` 和 `BenchmarkRuntimePolicy`。启动设置子任务在上述文件
内发射启动 / Room / 设置 Trace，并在 `AppRoot` 的更新与 Worker 入口消费 benchmark 隔离
策略；RP 子任务发射 RP 映射 Trace、接入离线 chunk 源。基准端只依赖稳定合约，不复制
各子任务实现。

## 2. Benchmark Session and Fixture

### 2.1 显式会话握手

采用版本化 `BenchmarkSessionDescriptor`：

- `schemaVersion`
- `sessionId`（测试生成的随机值）
- `scenarioId`
- `fixtureVersion`
- `phase`（prepare / measure / profile）
- `createdAtEpochMillis` 和短 TTL

控制组件同时校验：

1. 当前渠道必须是 `baseline`。
2. 调用必须来自 baseline-only Manifest 中声明的控制入口。
3. session ID、schema、场景和 TTL 必须有效。
4. 实际启动 Intent 必须引用同一 session ID。

控制入口只存在于 `app/src/baseline`，使用 signature 级权限保护，并运行在独立
`:benchmark_fixture` 进程。该进程不启动正常 AppGraph 后台任务；因此 S0 在清空数据后可以
只写入会话描述，不预热主进程、Room 或 DataStore。fixture 进程的跳过逻辑必须由最小、
可测试的 Application 进程谓词控制，不能按包名模糊判断。

主进程只读取仍有效的本地 session 描述。会话结束、过期或验证失败时自动回到正常策略；
dev / release 即使收到同名 Intent extra 也不能激活。

### 2.2 Fixture 模型

使用固定 `FixtureSpec`，不读取用户数据和远端配置：

- S0：空应用数据、未确认条款，仅准备 benchmark session。
- S1：已确认当前条款、固定 RP 场景 / 会话和小数据集。
- S2：在 S1 基础上准备普通聊天 100 条、RP 100 条固定消息。
- S3：复制仓库内脱敏、带校验和的受支持旧版本数据库 / 设置快照，实际主进程启动时执行
  正常升级；fixture 进程不得提前打开当前 Room。

所有 fixture 使用固定 ID、固定时钟起点和稳定次序。S2 不包含附件、语音、图片、真实
Provider、API Key、远端 URL 或随机正文。准备完成后返回：

- fixture 版本和场景；
- 每类记录数量；
- 仅由 ID、类型、顺序和长度生成的安全摘要；
- 成功 / 安全错误码。

重复 prepare 采用“清理 benchmark 自有固定记录 → 原子写入 → 重新查询校验”的流程；第二次
结果必须与第一次一致。S3 资产生成方式和 SHA-256 一并入库，防止二进制快照来源不明。

### 2.3 后台污染隔离

`BenchmarkRuntimePolicy` 是只读策略对象，只有有效 baseline session 时返回 true。已知污染源
必须通过各自所有者消费它：

- AppRoot 自动更新检查；
- Moments 周期 Worker 调度；
- 真实 Provider / telemetry；
- 正常启动后延迟执行的本地退出诊断。

fixture 所需本地 Room / DataStore 工作保留在准备区间；AppGraph 启动、Room 打开和设置读取
本身是启动测量对象，不因 benchmark 被整体跳过。隔离只移除与目标无关的网络和周期任务。

## 3. Ready Contract

### 3.1 状态定义

ready 是“数据条件成立且目标 Compose 层至少完成一帧”的组合，而不是页面文字出现：

- `first_run_consent_ready`：合规状态加载完成，门禁可交互。
- `returning_roleplay_ready`：当前场景、会话、参与者和首批消息均完成第一次观察。
- `chat_list_ready`：目标会话消息数量匹配 fixture，列表完成布局并位于指定锚点。
- `roleplay_list_ready`：目标 RP 消息数量匹配 fixture，列表完成布局并位于指定锚点。
- `roleplay_stream_ready`：离线 replay 已安装、初始气泡可见且尚未开始 pump。
- `settings_ready`、`reading_ready`、`voice_profile_ready`：目标页面状态加载并完成首帧。

ready 标记使用稳定 `testTag`，在显式 benchmark session 下通过
`testTagsAsResourceId` 暴露给 UIAutomator。标记放在目的地包装层或页面根节点，不向用户显示
文案，也不改变布局尺寸。AppRoot 不承担 ready 实现。

### 3.2 强等待 API

`ReadyAssertions` 提供唯一入口：

```text
prepareFixtureOrThrow(scenario)
startScenarioOrThrow(session, targetReady)
waitForReadyOrThrow(readyId, timeout)
findAndClickOrThrow(selector, timeout)
```

任何 false、null、错误状态或超时都抛出带安全场景信息的断言异常。Profile 和所有
Macrobenchmark 共用同一 helper；禁止直接调用 `device.wait(...)` 后忽略结果，也禁止 nullable
控件点击。

## 4. Scenario and Result Model

### 4.1 场景矩阵

- S0～S3：`StartupTimingMetric`，`StartupMode.COLD`，None / Require Baseline 各 10 次。
- F1：普通聊天底部 → 顶部 → 底部固定手势，`FrameTimingMetric`，至少 5 次。
- F2：RP 相同轨迹，`FrameTimingMetric`，至少 5 次。
- F3：固定 chunk 按 48 ms 离线重放，`FrameTimingMetric` + Trace，至少 5 次。
- N1：设置页与 RP 阅读页往返，固定点击 / 返回序列，至少 5 次。
- P1：启动、RP、聊天历史、设置、阅读页和角色音色 Sheet 的 Profile 路径。

所有动作使用稳定 selector、固定手势坐标比例和明确终点 ready。滚动位置恢复、fixture prepare、
会话清理和 Home / force-stop 操作位于测量区间外。

### 4.2 原始结果目录

每次运行创建不可覆盖的 run 目录，包含：

- Macrobenchmark JSON 与 Perfetto trace；
- 环境 manifest；
- Trace section presence / duration 汇总；
- Profile 源码 / 合并校验结果；
- 所有文件的 SHA-256。

环境 manifest 记录 `2.2.6-dev` 版本、versionCode、commit、fixture 版本、场景、迭代、编译
模式、AndroidX Benchmark 版本、AGP / Gradle / JDK、设备型号、API、构建指纹、刷新率、动画
缩放、电量和温控。该 manifest 只用于实验复现，不进入用户诊断导出。

## 5. Trace Contract

### 5.1 唯一名称目录

新建 `PerformanceTraceContract`，所有名称为静态常量，禁止拼接会话 ID、消息 ID、标题或
正文。建议名称：

- `narra.startup.app_graph`
- `narra.startup.room_open`
- `narra.settings.decode`
- `narra.settings.secure_read`
- `narra.messages.first_map`
- `narra.roleplay.map_messages`
- `narra.benchmark.fixture_prepare`
- `narra.diagnostics.exit_collect`
- `narra.diagnostics.export`

同步 CPU 工作使用 `androidx.tracing` 的同步 section，并保证 `finally` 配对关闭；跨挂起边界
只有在确有必要时使用带稳定 cookie 的 async section。不能用一个长 section 包住网络等待或
用户交互。

### 5.2 发射者与消费者

- 本任务发射 fixture、诊断收集和导出 section。
- 启动设置子任务发射 AppGraph、Room、设置 decode / secure read、首个消息映射 section。
- RP 子任务发射 RP map section。
- `baselineprofile` 为已登记 section 配置 `TraceSectionMetric` 或等价 Perfetto 后处理，输出
  presence、count、总耗时和分位数。

结果模型必须保留 `missingSections`。缺失和 0 ms 是两个不同状态；父任务集成检查在所有发射
子任务完成后要求 `missingSections` 为空。

## 6. Compose Compiler Report Pipeline

现有共享目录继续允许普通增量构建写入，但不得再作为正式报告来源。正式报告走独立任务：

1. 接收 `variant` 与唯一 `runId`。
2. 为该 run 使用独立 destination。
3. 只执行目标 production Kotlin 编译，禁用 Kotlin incremental 和 build cache，使用
   `--rerun-tasks` 保证完整输入。
4. 校验 stability、skippability、classes、composables / module metrics 等预期文件存在。
5. 校验主源码哨兵至少包含 AppRoot、聊天、RP 和设置入口，拒绝“只有 Test 类”或空 CSV。
6. 写入编译工具版本、variant、commit、文件清单和 SHA-256，再生成 zip 归档。

debug 与 baseline 使用不同目录。归档完成后再运行单测增量编译，校验 zip 和 manifest 哈希
不变。CI 若上传报告，必须调用同一正式任务，不能直接上传共享增量目录。

## 7. Application Baseline Profile

`BaselineProfileGenerator` 与宏基准共用 fixture、selector 和 ready helper。每个导航动作采用
“找到 → 点击 → 等待目标 ready”，任何一步失败立即中止。

生成后由独立验证任务检查：

1. `app/src/main/generated/baselineProfiles` 中存在本次生成文件。
2. 源码 Profile 至少包含一条 `Lcom/example/myapplication/` 规则。
3. 当前构建的合并 Profile 也至少包含一条应用规则。
4. 不能读取旧时间戳 intermediate 代替当前构建结果。
5. 物理设备 Require Baseline 运行确认 Profile 已安装并取得可比较结果。

应用 Profile 源文件属于可提交成果；Macrobenchmark JSON、Perfetto 和 Compose zip 属于构建
证据，默认保存在输出目录，不把设备实验产物混入产品源码。

## 8. Local ApplicationExitInfo Diagnostics

### 8.1 数据源与安全映射

通过可替换 `ProcessExitInfoSource` 封装 Android API，便于 JVM 测试。API 30+ 查询有限数量的
历史退出记录；API 26～29 返回 Unsupported，不安装 `UncaughtExceptionHandler`。

只把异常类别映射为内部枚举：Java crash、native crash、ANR、low memory、excessive
resource、initialization failure、dependency died 和 unknown abnormal。用户主动退出、包更新、
正常 self exit 等噪声不写入诊断。

持久化模型只含：

- schemaVersion / eventId；
- exit timestamp / observed timestamp；
- reason category / importance / status 的安全数值；
- app versionName / versionCode；
- PSS / RSS。

eventId 只由退出时间、原始 reason、安全化 importance / status 与 PSS / RSS 生成，仅用于去重；
不得包含 observed timestamp 或应用版本，否则同一历史退出会在后续版本再次入库。重复 eventId
合并时保留首次观察到的时间和应用版本，不用后续启动值覆盖。明确不读取 `traceInputStream`，
不保存 description、process state summary、PID、进程名、堆栈、设备标识或业务数据。

### 8.2 限量存储

`LocalExitDiagnosticsStore` 位于 `noBackupFilesDir/diagnostics/`，使用 `AtomicFile` 或等价
“临时文件 → fsync / close → replace”流程。建议默认上限为 20 条和 256 KiB；两项任一超限
都从最旧记录开始淘汰。

读取时采用：文件不存在 → 空列表；单条损坏 → 丢弃该条并保留其它；整体损坏 → 隔离旧文件
并从空记录继续。所有失败经 `AppLogger` 写安全阶段和计数，不拼接原文件内容 / 路径；诊断
写入失败绝不抛回启动主链。

历史退出收集在正常页面首帧之后使用 IO dispatcher 执行；显式 benchmark session 跳过，避免
污染启动结果。进程级诊断服务可以由 `ChatApplication` 持有窄依赖，不访问 Room、网络或用户
设置，也不新增第二套业务 DI 图。

### 8.3 主动导出

设置中新增独立“本地诊断”页面，不向 `SettingsViewModel` 增加字段。页面显示记录数量、最近
异常时间、隐私说明和“导出诊断信息”操作。

导出使用 `ActivityResultContracts.CreateDocument("application/json")`。编码在 IO dispatcher
执行，格式含 schema、生成时间、应用版本、隐私声明和 allowlist events。用户取消视为正常
结束；写失败保留本地记录并通过安全中文 Snackbar 反馈。导出结束不自动分享、不联网，也
不把全局 AppLogger sink 改为持久化日志 sink。

## 9. Compatibility, Risk, and Rollback

- baseline-only 控制组件不进入 dev / release Manifest；渠道门禁测试失败时立即回滚控制入口。
- 旧快照资产可能因 schema 变化失效；资产必须带来源版本和校验和，更新时单独提交。
- ready 条件过早会产生伪基准，过晚会超时；每个 ready 同时验证状态、首帧和负向失败。
- Trace section 只能增加低开销静态切片；若观测到明显时序扰动，保留合约并缩小切片范围。
- Compose 报告任务不得清理整个工作区，只操作自己的 build 输出目录。
- Profile 生成与生成文件单独回滚；如果 Profile 让启动变慢，保留基准证据并回退生成规则。
- 诊断读取 / 写入默认 fail-open；出现启动回归时先禁用收集入口，保留本地文件和导出能力，
  不删除用户已有诊断。
- 跨子任务 Trace / 隔离接入缺失时，本任务结果标记“基础设施完成、集成未验收”，不得伪装
  为全专项已通过。
