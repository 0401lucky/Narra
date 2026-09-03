# 2.2.6-dev 启动、设置共享与导航性能

## Goal

阻断正常启动阶段可恢复异常到达默认未捕获处理器的通道，让同一份应用设置在进程内只
完成一次 DataStore、Keystore 与 JSON 解析，并让 `AppRoot` 只订阅主题所需的窄状态。
在不删除导航动画、不改变设置持久化与敏感字段边界的前提下，通过可读 Trace 和既有
导航基准验证启动、设置打开与页面切换没有功能回归或性能回退。

## Background

- 本子任务归属父任务 `08-07-app-performance-optimization`，目标版本固定为
  **2.2.6-dev**。
- `AppGraph.kt:101,401-438` 使用应用级 `SupervisorJob` 作用域启动敏感数据迁移、
  内置预设写入和设置长期收集；根 `launch` 当前没有逐任务异常边界。
- `AppSettingsStore.kt:156-302` 直接映射 `DataStore.data`，未对读取
  `IOException` 执行官方回退，且该冷流的每个 collector 都会重复读取安全存储并解析多组
  JSON。
- `AppRoot.kt:47-52,76-93` 同时收集原始设置和完整 `SettingsUiState`，实际只需要
  `themeMode` 与 `appColorTheme`；`SettingsViewModel.kt:141-168` 又以
  `stateIn` 和直接 `collect` 双轨订阅同一上游。
- 设置页与阅读页 240～400 ms 的导航动画本身不是已确认根因。当前没有同设备
  Perfetto / `FrameTimingMetric` 结果，不能以主观流畅度或简单删除动画作为验收依据。

## Requirements

- **R1 范围与所有权**：本子任务只修改 `AppGraph.kt`、`AppSettingsStore.kt`、
  `AiSettingsRepository.kt`、`AppRoot.kt`、`SettingsViewModel.kt` 及对应测试，并只在这些
  归属文件中增加具体 trace section。基准类、fixture / ready 基础设施、Compose 报告配置、
  Profile 生成和本地 `ApplicationExitInfo` 诊断由
  `08-11-performance-observability-baseline` 子任务负责。
  对 `SettingsViewModel.kt` 的所有权仅限设置订阅、持久快照和草稿同步区域；声音样本读取 /
  Base64 区域由运行时子任务在本任务完成后串行修改。
- **R2 启动异常合同**：AppGraph 的敏感数据迁移、内置预设写入和设置长期观察必须具有
  稳定任务名、局部异常边界和明确降级策略。`CancellationException` 原样传播；迁移或预设
  失败只影响本任务，记录脱敏诊断并在下次启动重试，不得阻止其它启动任务运行，也不得
  伪装为成功。
- **R3 DataStore 读取故障**：只对 `DataStore.data` 抛出的 `IOException` 使用空
  Preferences / 默认 `AppSettings` 降级并记录不含用户数据的 WARN。其它异常继续抛出或
  进入明确的不可用诊断，不得被 `catch`、`runCatching` 或默认值静默掩盖；设置写入失败的
  现有传播语义不变。
- **R4 应用级共享设置**：同一个 `AppSettingsStore` 实例在应用生命周期内只建立一个上游
  解析管线，并向所有 collector 重放最新完整 `AppSettings`。一次 Preferences emission
  只能触发一次安全值读取、JSON 解码和完整对象构造；解析管线不得运行在 Main，且不得把
  API Key 明文写入 DataStore、额外缓存或日志。
- **R5 根主题窄投影**：Repository 提供只包含 `themeMode` 与 `appColorTheme` 的去重持久
  投影。`AppRoot` 不再收集完整 `SettingsUiState` 或原始完整设置来决定主题；首个持久主题
  快照和这两个持久字段变化可以更新根主题。为保留设置页既有的即时换肤行为，
  `SettingsViewModel` 可额外暴露同样只含这两个字段的窄草稿预览；只有主题草稿变化可触发
  该预览，无关设置、loading、message 或其它草稿编辑不得触发根主题更新。
- **R6 SettingsViewModel 单轨同步**：`storedSettings` 与设置草稿同步必须来自同一条共享
  emission，保留“初始默认值不覆盖真实草稿”的现有合同，并删除对 Repository 的第二条
  原始订阅。保存、脏状态判断、模型加载和各设置子页现有行为保持不变。
- **R7 Trace 与证据**：在 AppGraph 三个启动任务、完整设置解析、安全值读取、主题投影和
  SettingsViewModel 设置同步处使用稳定、无动态用户值的 trace section。具体 section 复用
  observability 子任务提供的统一 Trace 入口；本子任务不新建第二套追踪工具。
- **R8 导航回归**：复用 observability 子任务提供的确定性设置 / 阅读页导航场景、fixture、
  ready 合同和前置基线，在同一设备、构建、刷新率、温控与动作下复测。没有 Trace 证据时
  不修改导航时长、路由协议或页面架构；没有物理设备时只能完成行为与静态门禁，不能宣称
  帧性能验收通过。

## Acceptance Criteria

- [ ] **AC1（R2）**：故障注入分别令敏感迁移和预设写入抛异常时，应用级测试证明无未捕获
      崩溃、其它启动任务仍执行、日志包含稳定任务名且不含设置值；取消测试证明
      `CancellationException` 未被记录或降级。
- [ ] **AC2（R3）**：设置读取 Flow 抛 `IOException` 时至少产出一份默认
      `AppSettings`，根设置订阅可继续进入安全状态；同位置抛非 IO 异常时测试必须失败或
      明确观察到不可用诊断，不能得到伪造的成功快照。
- [ ] **AC3（R4）**：两个以上并发长期 collector 与多个 `first()` 读取同一 Store 时，首个
      Preferences emission 的完整解析计数为 1；第二个 emission 总计只再增加 1。测试同时
      证明解析运行在注入的后台 dispatcher，安全值读次数不会随 collector 数量倍增。
- [ ] **AC4（R4）**：敏感字段仍由 `SecureValueStore` 提供，普通 DataStore JSON、共享
      replay 缓存与日志中均未新增密钥明文；旧明文迁移兼容测试继续通过。
- [ ] **AC5（R5）**：持久主题投影对仅修改 provider 或其它非主题字段的 emission 不产生
      新值；修改 `themeMode` 或 `appColorTheme` 才产生新值。窄草稿预览只在这两个主题草稿
      变化时产生新值，loading、message、provider 与其它草稿编辑不产生根主题更新。
      `AppRoot` 不再收集 `SettingsViewModel.uiState`，首个真实主题快照到达前仍保持现有安全
      loading / 合规门禁顺序。
- [ ] **AC6（R6）**：SettingsViewModel 测试证明 Repository 只有一个长期订阅；真实设置到达
      后 `storedSettings` 与草稿同步一致，`stateIn` 的默认初值不会被当成真实存储值，已有
      保存与 `hasDraftChanges()` 相关测试无回归。
- [ ] **AC7（R7）**：Perfetto 可识别 AppGraph 三任务、设置完整解析、安全值读取、主题投影和
      设置同步 section；名称不包含 API Key、JSON、用户文本、文件路径或其它动态内容，设置
      解析 section 不落在 Main。
- [ ] **AC8（R8）**：在 observability 子任务准备的同环境 N1 导航场景中至少复测 5 次并保存
      原始结果；优化后 P95 `frameOverrunMs` 不得比优化前回退超过 5%，设置解析次数降为每个
      emission 1 次。若缺物理设备或前置基线，只能标记“未取得性能结果”。
- [ ] **AC9（R1～R8）**：定向 JVM 测试、相关既有设置测试、AndroidTest 源码编译、
      `lintDebug` 与 `assembleDebug` 均有真实结果；每个 JVM 测试命令最长等待 60 秒，超时或
      环境失败不得写成通过。

## Out of Scope

- RP 三处 Lazy 列表 identity / `contentType`、RP streaming、滚动、光标与语音卡优化。
- TTS / Base64 上限、声音样本 IO、下载轮询、翻译取消与其它运行时资源边界。
- 聊天 / RP 会话 mutation 串行化、跨表事务、Room schema 或 Paging。
- 新建或修改宏基准类、baseline-only fixture、ready 标记基础设施、Compose Compiler 报告
  配置、Baseline Profile 生成路径和 `ApplicationExitInfo` 本地诊断。
- 删除或缩短导航动画、重写导航图、修改合规门禁顺序、引入远程崩溃 SDK、正式 release
  构建或发布。

## Confirmed Decisions

- 共享层放在应用级唯一 `AppSettingsStore` 上游，确保直接使用 Store 与经
  `AiSettingsRepository` 使用的消费者都复用同一解析结果。
- 保留 `SettingsStore.settingsFlow` 的 Flow 兼容表面，运行时实现使用可重放共享流；首个真实
  emission 前不把默认 `AppSettings()` 冒充持久化已就绪。
- DataStore 只对读取 `IOException` 降级；写入异常和非 IO 编程错误不扩大吞错范围。
- 先取得 observability 子任务的前置基线与统一 Trace 入口，再落具体 section 和实现改动；
  两个子任务不同时修改同一产品文件。
- 本任务先完成 `SettingsViewModel` 订阅区域，运行时子任务随后处理同文件的声音样本区域；
  两者不得并行编辑或回退对方改动。
