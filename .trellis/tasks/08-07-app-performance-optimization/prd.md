# 2.2.6-dev 应用性能与稳定性专项

## Goal

针对界面切换轻微卡顿、核心页面性能不足和偶发崩溃反馈，建立可重复、可失败、可对比的
全项目性能与稳定性基准，先阻断代码级确认的进程风险，再只优化 Trace 或基准证实的主要
热点。最终交付必须包含真实验证结果，不能以“主观感觉更流畅”作为完成依据。

## Background

- lucky 已确认沿用独立任务 `08-07-app-performance-optimization`，本次归属
  **2.2.6-dev**。
- 旧规划只覆盖冷启动、聊天 / RP 长列表和 RP 流式性能；本次扩展到导航切换、设置流、
  主线程 IO、协程异常、资源上限、数据并发、崩溃诊断、Baseline Profile、Compose 指标
  和真实设备验证。
- 三路静态扫描已完成，完整证据见 `research/`。当前没有在线设备，因此尚未取得真实
  crash / ANR 堆栈、Perfetto、帧分位数或峰值内存。

## Confirmed Facts

### 性能与基准

- `StartupBenchmarks.kt:24-53` 的等待不做强断言，`BaselineProfileGenerator.kt:20-39`
  允许静默跳过目标控件；`AppRoot.kt:84-138` 的合规门禁和 RP 起点又会让结果依赖设备
  残留状态。
- 当前没有 `FrameTimingMetric`，`app/src/main/generated/baselineProfiles` 为空。
- `AppSettingsStore.kt:156-302` 是未共享冷流；启动至少由 `AppRoot.kt:47-52`、
  `SettingsViewModel.kt:141-168` 和 `AppGraph.kt:430-436` 重复收集，每次执行 Keystore
  解密和多组 JSON 解析。
- `RoleplayRoundTripExecutor.kt:1093-1135` 与
  `RoleplayObservationSupport.kt:225-263` 会把约 48 ms 的流式帧放大为完整状态发布和
  全消息映射；三个 RP Lazy 列表 key 依赖正文哈希和 index。

### 稳定性与崩溃

- `AppGraph.kt:101,401-438` 的正常启动根协程没有每任务异常边界；迁移、Room 写入或
  DataStore 收集异常可到达默认未捕获处理器。
- `AppSettingsStore.kt:156-302` 未处理 DataStore 读取 `IOException`。
- 翻译、更新、Worker、脚本和语音路径存在取消被当作失败或旧任务写入新状态的问题。
- 声音样本读取 / Base64、同步 `DownloadManager.query()`、TTS 无界响应和音频解码存在
  主线程停顿或内存峰值风险。
- 现有 `AppLogger` 默认只写 Logcat；没有可持久化的应用 crash / ANR 诊断闭环。
- 三份 `hs_err_pid*.log` 是 Gradle/JVM native OOM，不是用户端应用崩溃。

## Requirements

- **R1 证据优先**：所有性能改动必须绑定优化前基准或 Trace；源码结构已确认的正确性 /
  崩溃缺陷可直接修复，但收益声明仍需复测。
- **R2 基准确定性**：建立 first-run、returning-user、普通聊天长列表、RP 长列表、RP
  离线流式重放和重页面导航场景；fixture、合规状态、滚动位置和后台活动必须可控，所有
  ready 等待失败必须让测试失败。
- **R3 可观测性**：增加稳定 ready 标记、关键 trace section、全量 Compose Compiler
  报告归档及应用 crash / ANR 诊断 MVP；不得记录聊天正文、API Key 或完整用户数据。
- **R4 启动稳定性**：启动迁移、预设写入和设置收集必须有明确异常、降级和取消策略；
  DataStore 读取 `IOException` 不得终止进程，非 IO 编程错误不得被静默吞掉。
- **R5 设置与导航性能**：同一设置快照在应用范围只解析一次；安全字段的加密边界不变；
  AppRoot 只订阅主题所需的窄状态，设置草稿或无关字段变化不得触发根级重组。
- **R6 RP 界面性能**：三个 RP 列表使用稳定唯一 identity 和 `contentType`；流式正文增长、
  前插 / 删除消息不得改变既有 item identity；滚动、光标和语音动画不得产生无意义的高频
  任务或整段文本重建。拆分 RP streaming 状态必须由 Trace 证据触发。
- **R7 主线程与资源安全**：文件读取、Base64、导出写入和同步平台查询不得在 Main 上
  执行；TTS 响应、Base64 解码和大图路径必须有前置大小 / 生命周期边界。
- **R8 取消与并发一致性**：`CancellationException` 必须原样传播；旧任务不得覆盖新任务；
  编辑、删除、重试和发送需遵守会话级 mutation 顺序；数据库范围的跨表删除 / 重启必须
  原子提交或整体回滚。
- **R9 Profile 与结果可比**：生成的源码和合并 Profile 必须包含 Narra 应用规则；前后
  对比固定设备、构建、刷新率、温控、工具版本和 fixture，并保存原始结果。
- **R10 范围与回归**：不得因性能优化破坏流式顺序、贴底、取消、会话切换、主题、更新、
  语音或数据完整性；无证据时不引入 Paging、Room schema、全局网络重写或缓存体系。

## Acceptance Criteria

- [ ] **AC1（R2）**：干净数据和已使用数据两类启动基准都能自行准备状态并强断言
      `ready`；自动更新、周期 Worker 和真实 Provider 网络不进入测量区间。
- [ ] **AC2（R2、R9）**：普通聊天 / RP 各使用 80～120 条固定消息，完成启动、上下滚动、
      RP 48 ms 离线流式重放和设置 / 阅读页导航场景；滚动场景至少 5 次，启动每模式 10 次。
- [ ] **AC3（R3）**：关键启动、设置解析、Room 打开、消息映射有可读 trace section；
      Compose 报告覆盖完整目标变体并独立归档，不再被单测增量输出冒充全量结果。
- [ ] **AC4（R3）**：崩溃诊断采用本地脱敏记录、`ApplicationExitInfo` 和用户主动导出；
      记录限量轮转、默认不联网，不包含聊天正文、Prompt、API Key 或完整用户数据。
- [ ] **AC5（R4）**：对迁移失败、预设写入失败和 DataStore `IOException` 做故障注入，
      应用不发生未捕获崩溃，状态和日志符合既定降级策略；非 IO 异常仍可被测试发现。
- [ ] **AC6（R5）**：同一设置 emission 只触发一次完整解析；安全解密 / JSON 解析不占用
      Main；修改设置草稿不会导致 AppRoot 因无关字段重组。
- [ ] **AC7（R6）**：RP 流式正文增长、前插和删除后 key 保持稳定且唯一，三处列表提供
      `contentType`；语音卡静止时无无限动画，自动贴底不反复取消 / 重启长动画。
- [ ] **AC8（R6、R9）**：若 Trace 证明整表映射是主要热点，拆分 streaming 状态后对应
      P95 目标改善不低于 15%；若证据不成立则明确跳过，不以改代码数量代替收益。
- [ ] **AC9（R7）**：声音样本、导出和下载查询的线程测试通过；TTS / Base64 略低于、等于、
      略高于上限的边界测试通过，超限输入在大分配前被拒绝。
- [ ] **AC10（R8）**：快速连续翻译、Worker / 更新取消、编辑与发送交错、跨表故障注入测试
      证明旧任务不污染新状态，数据库异常时无部分提交。
- [ ] **AC11（R9）**：生成的 Profile 源文件和合并结果包含 `com/example/myapplication`
      规则；Baseline Profile 冷启动中位数不慢于 None，目标改善至少 10%，其它核心场景
      相对优化前不得回退超过 5%。
- [ ] **AC12（R10）**：相关 JVM / Compose / Room / 设备测试、`generateBaselineProfile`、
      `assembleBaseline`、`lintDebug` 和 `assembleDebug` 均有真实结果；缺设备或环境异常只能
      标为未取得结果，不能宣称性能验收完成。

## Out of Scope

- 直接引入 Room Paging、数据库 schema 变化、全面重写聊天 / RP 架构或全局网络重试。
- 默认启用当前已禁用的悬浮翻译和无障碍组件；只处理其启用前必须消除的安全边界。
- 2.2.6-dev 内实施 ABI 拆包、更新源按架构分发或完整 APK 体积专项；本轮只记录体积基线。
- 在前后对比中途升级 Macrobenchmark / Baseline Profile 工具版本；先冻结当前版本取基线。
- 接入 Crashlytics、Sentry 或其它远程崩溃 / ANR SDK；需要跨设备聚合时另立隐私专项。
- 构建或发布正式 release 包、改正式签名或执行生产发布。

## Confirmed Decisions

- 本任务独立归属 **2.2.6-dev**，不再挂回 2.2.5-dev。
- 采用“诊断 / 基准基础设施 → 阻断 P0 → 单点优化 → 同环境复测”的顺序。
- Baseline 渠道可在显式 benchmark 指令下隔离自动更新、周期 Worker 和远端请求，dev /
  release 行为不受影响。
- 同时保留 first-run gate 与 returning-user core 两类启动指标。
- 首轮冻结仓库现有 benchmark 工具版本，避免前后对比中途发生工具漂移。
- APK ABI 拆分、Paging、schema 和远程网络架构不属于本轮默认实现。
- 崩溃诊断采用“本地脱敏记录 + `ApplicationExitInfo` + 用户主动导出”，默认不联网；
  Crashlytics / Sentry 不进入 2.2.6-dev。
