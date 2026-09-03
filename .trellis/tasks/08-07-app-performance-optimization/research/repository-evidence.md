# 2.2.6-dev 应用性能与稳定性专项：仓库证据汇总

## 研究结论

本轮已完成 UI / Compose、运行时稳定性与协程、构建 / Baseline Profile / 可观测性
三路只读扫描。当前可以确认，界面切换轻微卡顿并非单一动画参数造成，而是启动和导航时
重复设置解析、RP 高频状态放大、主线程同步 IO 以及缺少有效基准共同叠加。偶发崩溃目前
没有真实设备堆栈，但源码已存在可到达默认未捕获异常处理器的启动根协程通道。

完整原始报告：

- `research/ui-compose-performance-scan.md`
- `research/runtime-crash-concurrency-scan.md`
- `research/build-baseline-observability-scan.md`

## 1. 已确认缺陷与结构性风险

### 1.1 现有性能基准不可作为有效结论

- `StartupBenchmarks.kt:24-53` 只测冷启动，等待“欢迎/历史”的返回值未断言。
- `BaselineProfileGenerator.kt:20-39` 的等待和点击允许静默失败。
- `AppRoot.kt:84-138` 在合规确认后才建立以 RP 为起点的导航，现有基准却没有固定
  合规和页面状态。
- `app/src/main/generated/baselineProfiles` 为空；旧合并 profile 没有 Narra 应用命名空间
  规则，不能证明核心路径已收集。
- 当前没有 `FrameTimingMetric`、TTFD、长列表、流式重放或页面切换基准。

### 1.2 正常启动存在未捕获根协程异常通道

- `AppGraph.kt:101,401-438` 使用应用级 `SupervisorJob` 作用域启动敏感数据迁移、
  内置预设写入和长期设置收集，根 `launch` 没有每任务异常边界。
- `ChatApplication.kt:47` 在正常启动路径调用上述任务。
- `AppSettingsStore.kt:156-302` 没有处理 DataStore `data` Flow 的读取
  `IOException`；该异常可传播到 `AppRoot`、ViewModel、AppGraph 或 Service 的根收集器。
- `SupervisorJob` 只隔离子任务，不消费根 `launch` 异常，因此这些路径属于代码级
  确认的进程稳定性风险。

### 1.3 设置冷流重复执行 Keystore 与 JSON 解析

- `AppSettingsStore.kt:156-302` 的 `settingsFlow` 是未共享冷流，没有 `flowOn`、
  `shareIn` 或应用级 `stateIn`。
- `SecureValueStore.kt:27-116` 的读取包含 SharedPreferences、Android Keystore、
  AES/GCM、Base64 和 Gson。
- 启动至少存在 `AppRoot.kt:47-52`、`SettingsViewModel.kt:141-168` 两路订阅，
  后者自身又重复订阅；`AppGraph.kt:430-436` 还会长期收集。
- Chat、RP、Moments、Phone 等 ViewModel 会继续建立独立订阅。重复计算结构和潜在
  Main 上下文已经由源码确认，绝对耗时仍需 Trace 定量。

### 1.4 RP 流式 UI 存在刷新放大与不稳定 identity

- `ChatStreamingSupport.kt:14-49` 约每 48 ms 发布可见帧。
- `RoleplayRoundTripExecutor.kt:1093-1135` 每帧更新完整 `RoleplayUiState`；
  `RoleplayObservationSupport.kt:225-263` 又因流式内容变化全量映射消息并再次发布状态。
- `RoleplayDialoguePanel.kt:376-391`、`RoleplayOnlinePhoneContent.kt:800-815`、
  `RoleplayReadingMode.kt:191-195` 的列表 key 包含正文哈希和 index，正文增长或前插消息
  都会改变 item identity，且三处缺少 `contentType`。
- 在线手机模式每次流式正文变化重启 `animateScrollToItem`；流式光标每动画帧重建完整
  `AnnotatedString`；每个可见语音卡无条件运行 12 个无限动画。
- 普通聊天已经具备独立流式状态、稳定 ID、`contentType`、纯文本快速路径和滚动节流，
  可作为 RP 修复的仓库内对照。

### 1.5 主线程同步 IO 与内存峰值风险

- `SettingsViewModel.kt:640-663,943-970` 和
  `VoiceSynthesisCoordinator.kt:202-231` 在进入网络 IO 前全量读取声音样本并 Base64
  编码，且完成分配后才检查上限。
- `AppUpdateViewModel.kt:232-254` 每秒在 Main 调用同步
  `DownloadManager.query()`；实际查询位于 `AndroidAppUpdateController.kt:53-76`。
- `ChatScreenLaunchers.kt:131-172`、`ContextLogScreen.kt:93-109` 在 Activity Result
  主线程回调中构建长文本、序列化并写文件。
- `MimoTtsClient.kt:158-176` 无上限读取完整响应，`AudioFileStorage.kt:91-107`
  无上限解码 Base64，存在外部输入放大的 OOM 面。

### 1.6 取消、旧任务写入和数据原子性风险

- `TranslationViewModel.kt:191-238` 取消旧任务后立即启动新任务，但 `runCatching`
  会把 `CancellationException` 当失败处理，旧任务可能关闭新任务的 loading 或覆盖状态。
- `MomentsAutoGenerationWorker.kt:22-30`、`AppUpdateRepository.kt:54-77`、
  `AndroidRoleplayScriptEngine.kt:71-80`、`VoiceSynthesisCoordinator.kt:105-147`
  均存在宽泛捕获取消的问题。
- `ChatViewModel.kt:607-638` 与 `RoleplayScenarioActionSupport.kt:171-208` 基于旧内存
  快照异步整体替换，可能与发送并发形成 last-writer-wins。
- `ConversationRepository.kt:323-340`、`RoleplayRepository.kt:241-256,328-354`
  的跨表删除 / 重启没有统一 Room 事务，失败时可能留下部分数据或孤儿记录。

### 1.7 当前没有用户端崩溃诊断闭环

- `AppLogger.kt:20-94` 默认只写 Logcat；未接入 `ApplicationExitInfo`、StrictMode、
  全局未捕获异常本地记录、Crashlytics 或 Sentry。
- 当前没有连接设备，无法取得真实 Narra crash、ANR、PSS 或 Perfetto 证据。
- 本地三份 `hs_err_pid*.log` 均为 Gradle daemon 在 `-Xmx2048m` 下的 native OOM，
  属于构建进程崩溃，不是用户端 Narra 崩溃，必须分开统计。

## 2. 必须经过 Trace、故障注入或大数据 fixture 才能定案的热点

- settingsFlow：记录一次 DataStore emission 的解析次数、线程、Keystore / Gson 分项耗时。
- Room 长会话：分别用 100、1,000、5,000 条带真实 JSON 大小的消息测量单行更新后的
  查询、映射、GC 和帧耗时；没有证据前不引入 Paging。
- RP streaming 状态拆分：先稳定 item identity，再用离线 48 ms chunk 重放确认整表映射
  是否仍占主要帧预算。
- 导航动画：设置、RP 阅读页 240–400 ms 过渡本身不是缺陷，需要结合首次组合测量。
- OCR / MediaProjection：组件当前禁用；四识别器和多份全屏 Bitmap 的峰值需在启用前测量。
- APK 体积 / ABI：旧 dev APK 约 80 MB，四 ABI OCR native 库是大头；ABI 拆分会改变
  更新分发协议，不纳入本轮默认实现。

## 3. 已排除或需要正确归类的项目

- 普通聊天流式链路已经有状态隔离、稳定 key、`contentType` 和 Markdown 快速路径，
  不能把 RP 缺陷泛化到普通聊天。
- 当前未发现裸 `collectAsState()`、UI 线程直接图片解码或有效 haze provider。
- RP 三种模式在稳态互斥，仅导航转场可能短暂同时存在。
- 悬浮翻译 Service / AccessibilityService 当前在 Manifest 中禁用且无启用代码；其根协程、
  OCR 和截图风险属于“启用前必须修复”，不是当前正常 UI 可达的崩溃来源。
- Gradle/JVM OOM 与应用 crash/ANR 分属不同问题域，不能用构建日志解释用户偶发崩溃。

## 4. 推荐的实施优先级

1. 建立确定性基准、Trace section、Compose 报告归档和崩溃诊断 MVP。
2. 阻断 AppGraph / DataStore 的进程级异常通道。
3. 共享设置状态并缩窄 AppRoot 订阅，测量导航切换收益。
4. 稳定 RP item identity、补 `contentType`，再按 Trace 处理 streaming、滚动与动画。
5. 移出主线程同步 IO，增加 TTS / Base64 输入上限和取消语义测试。
6. 分离处理会话 mutation 串行化与跨表事务，避免与 UI 性能文件发生写冲突。

## 5. 当前验证限制

- 本轮报告为静态只读扫描，没有修改产品代码。
- 没有在线设备，尚无真实帧时间、启动时间、崩溃率、ANR、PSS 或 ProfileInstaller 结果。
- 当前 Compose Compiler 输出被增量 / 测试编译覆盖；旧全量报告只用于确定复核方向。
- 2.2.6-dev 的前后对比必须使用同一物理设备、同一刷新率 / 温控状态、同一工具版本和
  固定 fixture，否则只能报告“不可判定”。
