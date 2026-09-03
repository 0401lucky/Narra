# 2.2.6-dev 性能基准、Trace、Profile 与本地崩溃诊断

## Goal

为 Narra 建立一套可重复、会在前置条件不满足时明确失败、能够保存原始证据的性能与
稳定性观测基线。交付覆盖确定性 fixture / ready 合约、启动与帧基准、Trace 消费、全量
Compose Compiler 报告、应用自身 Baseline Profile，以及默认离线的
`ApplicationExitInfo` 脱敏诊断和用户主动导出。

本任务只建立测量与诊断基础设施，不以主观流畅度替代数据，也不把其它性能子任务的代码
优化纳入本任务成果。

## User Value

- lucky 可以在相同设备和相同 fixture 下复测启动、滚动、流式与重页面导航，不再依赖
  设备残留状态或 UI 文案猜测“是否测到了目标页面”。
- 每项优化都能关联优化前后原始结果、Trace 与 Profile 证据；缺设备或等待失败时不会产生
  伪成功结论。
- 用户遇到异常退出或 ANR 后，可以在不自动联网、不导出聊天内容和密钥的前提下，主动
  导出一份限量、脱敏的本地退出摘要用于排查。

## Confirmed Facts

- `StartupBenchmarks.kt:24-53` 只覆盖冷启动，两个 UI 等待结果均被忽略；
  `BaselineProfileGenerator.kt:20-39` 的等待和点击也允许静默失败。
- `AppRoot.kt:84-138` 的合规门禁、设备残留数据和 RP 导航起点会改变现有基准实际测量的
  页面，当前没有稳定 ready 合约。
- 当前没有 `FrameTimingMetric` 场景；普通聊天 / RP 长列表、RP 48 ms 离线流式重放和
  重页面导航均没有可复核原始结果。
- `app/src/main/generated/baselineProfiles` 当前为空；旧合并 Profile 中没有
  `com/example/myapplication` 规则，依赖库规则不能证明 Narra 核心路径已收集。
- Compose Compiler 报告写入共享目录，现有输出曾只包含单测类或空 CSV，增量 / 变体输出
  会覆盖，不能作为全量应用报告。
- 项目没有 `ApplicationExitInfo`、持久化 crash / ANR 摘要或主动诊断导出；现有
  `AppLogger` 默认只写 Logcat。
- 三份 `hs_err_pid*.log` 是 Gradle daemon native OOM，不是 Narra 应用退出记录，必须与
  用户端诊断分开。
- 当前会话没有在线物理设备，因此尚未取得 2.2.6-dev 的真实启动、帧、Profile 安装、
  crash / ANR 或 PSS / RSS 结果。

## Scope and Ownership

本子任务负责：

- `baselineprofile` 模块中的确定性启动、滚动、RP 离线流式重放、导航与 Profile 路径。
- 仅 baseline 渠道、仅显式 benchmark 会话可用的 fixture / ready 测试契约及其负向失败
  验证。
- Trace 名称、隐私和结果消费合约；基准必须报告必需 section 的存在、缺失和耗时。
- 全量 Compose Compiler 报告的独立生成、校验、归档和元数据记录。
- Narra 应用 Profile 的生成、源码 / 合并产物校验和同设备对比。
- API 30+ `ApplicationExitInfo` 的本地脱敏、限量轮转、容错读取与用户主动导出。

为避免并行文件冲突，本子任务不修改 `AppGraph.kt`、`AppSettingsStore.kt`、
`AiSettingsRepository.kt`、`AppRoot.kt`、`SettingsViewModel.kt`。这些文件中的启动、设置、
Room 和共享流 Trace 发射由“启动、设置共享与导航性能”子任务负责；本任务只定义统一
Trace 合约并消费结果。RP 映射 Trace 发射由 RP 性能子任务负责。

## Requirements

- **R1 渠道与会话隔离**：fixture、离线流式重放、ready 状态和后台污染隔离只在
  `BuildConfig.APP_CHANNEL == "baseline"` 且收到版本化的显式 benchmark 指令时生效。
  dev / release 的正常启动、更新、Worker、网络和用户数据行为不得改变。
- **R2 确定性 fixture**：提供可幂等重建的 S0 first-run、S1 returning core、S2 populated
  和 S3 supported-upgrade 状态。S2 固定普通聊天和 RP 各 100 条无附件、无密钥消息；固定
  ID、时间戳、场景、会话、滚动起点和离线 chunk，重复准备后的数量与摘要必须一致。
- **R3 强 ready 合约**：ready 必须基于稳定测试标记和真实状态条件，不依赖中文文案。
  fixture 失败、状态未满足、目标控件不存在或等待超时都必须终止测试；不得跳过步骤后
  继续产出结果。
- **R4 场景与测量区间**：覆盖 S0～S3 冷启动、F1 普通聊天滚动、F2 RP 滚动、F3 RP
  48 ms 离线流式重放、N1 设置 / RP 阅读页导航和 P1 Profile 路径。fixture 写入、合规
  准备、后台隔离、滚动复位和预热均在 `measureBlock` 外完成。
- **R5 可比结果**：启动按 None 与 Require Baseline 各运行 10 次；帧场景各至少运行 5 次，
  输出 P50 / P90 / P95 和原始 JSON / Perfetto。每次运行记录应用版本、commit、fixture
  版本、benchmark 依赖版本、Gradle / JDK、设备、API、刷新率、动画缩放、电量和温控。
- **R6 Trace 合约**：Trace 名称必须静态、短小、无用户标识和正文；至少登记启动任务、
  Room 打开、设置解析 / 安全值读取、首个消息映射和 RP 全量映射。基准消费端必须区分
  “section 未发射”和“section 耗时为零”，缺失必需 section 时结果不可判定。
- **R7 Compose 报告**：目标变体使用全量 production Kotlin 编译生成 stability、
  skippability 和 metrics 报告；debug 与 baseline 分目录、分运行归档。单测或增量编译不得
  覆盖已归档结果，也不得被标记为全量报告。
- **R8 应用 Baseline Profile**：Profile 路径必须复用确定性 fixture 和强 ready，覆盖启动、
  RP、普通聊天历史、设置、RP 阅读页和角色音色 Sheet。源码 Profile 与合并 Profile 都必须
  含 Narra 应用命名空间规则，不能只依赖第三方库规则通过 Require 模式。
- **R9 本地退出诊断**：API 30+ 下一次正常启动读取 `ApplicationExitInfo` 的允许字段，仅保存
  异常退出原因、时间、应用版本、PSS / RSS 和安全分类摘要；不得读取或持久化 ANR trace、
  description、process state summary、聊天、Prompt、API Key、Base64、路径、SQL 或完整
  DataStore / Room 数据。API 26～29 安全降级为“不支持历史退出查询”，不安装全局未捕获
  上报器。
- **R10 存储与容错**：诊断记录写入 `noBackupFilesDir`，有条数与总大小上限、去重和原子
  更新；读取、解析、轮转和写入失败只产生脱敏日志，不得成为新的启动崩溃源。
- **R11 用户主动导出**：诊断仅在用户明确点击后通过系统文件选择器导出；默认不联网、
  不自动分享、不申请额外网络权限。导出在后台线程完成，取消不显示失败，错误文案脱敏。
- **R12 结果诚实性**：模拟器只用于 fixture / ready / 交互 smoke；启动、帧和 Profile 阈值
  必须来自固定物理设备。缺设备、温控异常、超时或 Profile 未安装只能记录“未取得结果”。

## Acceptance Criteria

- [ ] **AC1（R1）**：自动化测试证明 benchmark 指令在 dev / release 或缺少显式会话时被
      拒绝 / 忽略；baseline 显式会话期间不触发真实 Provider 请求、自动更新检查或周期
      Worker，退出会话后正常行为恢复。
- [ ] **AC2（R2）**：S0～S3 均可由测试自行准备并校验；S2 精确得到普通聊天 100 条、RP
      100 条固定消息。每个 fixture 连续准备两次后 ID、数量、顺序和安全摘要完全一致，且
      不包含密钥、附件或远端 URL。
- [ ] **AC3（R3）**：所有基准和 Profile 步骤统一使用强 ready helper；请求不存在的 ready、
      fixture 注入失败和目标控件缺失的负向测试均在超时后失败，不能产生成功结果文件。
- [ ] **AC4（R4、R5）**：S0～S3 的 None / Require Baseline 冷启动各 10 次；F1～F3、N1
      各至少 5 次。原始 JSON / Perfetto、环境清单和文件 SHA-256 被保存到独立运行目录，
      fixture 与复位动作不在测量区间。
- [ ] **AC5（R6）**：仓库存在唯一 Trace 名称目录和发射者映射；基准结果逐项输出 presence、
      count、总耗时和分位数。缺少任一必需 section 时验证任务明确失败或标记不可判定，
      不以 0 ms 冒充已测量。跨子任务 section 的最终存在性由父任务集成门禁复核。
- [x] **AC6（R7）**：fresh production 编译生成的 debug / baseline 报告分别归档，包含
      AppRoot、聊天、RP 和设置等主源码哨兵，而非只有测试类；随后运行单测增量编译不会
      改写归档文件或其 SHA-256。
- [x] **AC7（R8）**：Profile 生成路径的每次等待和点击均有强断言；生成的源码 Profile 和
      合并 Profile 至少各有一条 `com/example/myapplication` 规则，验证任务在仅有依赖规则
      时失败。
- [ ] **AC8（R5、R8、R12）**：固定物理设备上 Require Baseline 冷启动中位数不慢于 None，
      目标改善至少 10%；其它核心场景相对同环境优化前结果不得回退超过 5%。无法满足设备
      前提时明确保留为未验收。
- [x] **AC9（R9、R10）**：API 30+ 的正常、重复、空列表、查询异常、损坏文件和超限轮转
      测试通过；API 26～29 no-op 测试通过。记录超出上限会淘汰最旧项，诊断失败不会阻断
      应用启动。
- [x] **AC10（R9～R11）**：使用包含聊天正文、Prompt、API Key、Base64、SQL 和绝对路径的
      注入样本导出后，文件只包含允许字段且所有敏感哨兵均不存在；用户取消不写文件，写入
      失败不清空本地记录且显示安全中文提示。
- [x] **AC11（R9～R11）**：依赖与 Manifest 扫描确认未接入 Crashlytics、Sentry 或其它远程
      崩溃 SDK，未新增诊断自动上传、ANR trace 导出、设备唯一标识采集或额外网络权限。
- [x] **AC12（R12）**：定向 JVM / 设备测试、Compose 报告任务、
      `connectedBaselineAndroidTest`、`app:generateBaselineProfile`、`assembleBaseline`、
      `lintDebug` 和 `assembleDebug` 均保留真实结果；任何未执行项都如实标记原因。

## Out of Scope

- 接入 Crashlytics、Sentry、远程 telemetry、自动上传、后台同步或远程诊断面板。
- 持久化 Logcat、完整异常堆栈、ANR trace、用户正文、Prompt、密钥、完整数据库行或设备
  唯一标识。
- API 26～29 的全局 `UncaughtExceptionHandler` crash 文件方案。
- 修改 `AppGraph.kt`、`AppSettingsStore.kt`、`AiSettingsRepository.kt`、`AppRoot.kt`、
  `SettingsViewModel.kt` 中的启动异常、设置共享流或 Trace 实现。
- 本任务内优化设置解析、RP 重组、导航动画、主线程 IO、会话 mutation 或数据库事务；
  这些由其它子任务完成后使用本基准复测。
- Room schema 变化、Paging、ABI 拆包、R8 / release-like 专项、升级现有 benchmark 工具版本、
  构建或发布正式 release 包。
