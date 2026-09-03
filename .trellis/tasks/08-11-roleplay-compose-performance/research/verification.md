# 2.2.6-dev RP Compose 与流式帧性能：验证记录

## 已实现范围

- `RoleplayMessageUiModel` 使用 mapper 统一生成的稳定唯一 `uiItemId`；普通 RP、在线手机和
  阅读模式三处 Lazy 列表均使用该 identity，并提供稳定 `contentType`。
- 普通 RP 与在线手机使用同一套流式自动贴底策略：约 64 ms 合并、用户离底保护、短距离
  跟随、场景切换重置；F3 状态条安装后仅执行一次非动画确定性贴底。
- 流式光标正文缓存只随正文变化，alpha 在绘制阶段读取，不再让闪烁帧重建完整正文。
- 语音卡已使用 `prepareAsync()` 状态机和幂等释放；只有 `Playing` 状态创建无限波形动画。
- F3 baseline-only 链路已接通：固定 100 条 RP fixture、约 48 ms chunk、初始 / 动作 / 完成
  ready、离线 replay pump、四段 RP Trace，以及 fail-closed `AiGateway` / Prompt extras 与
  session-aware Provider 违规追踪。
- 本次未引入 `RoleplayStreamingState`。当前没有固定物理设备 Trace 证明整表映射 / 完整
  `RoleplayUiState` 发布仍是优化后的主要热点，因此不触发状态拆分闸门。

## 已取得验证结果

- RP 定向 JVM：10 个测试类、93 项测试，0 failures、0 errors、0 skipped。
- `:baselineprofile:verifyNarraF3BenchmarkIntegration`：通过；静态门禁覆盖动作 / ready、四段
  Trace、fail-closed AI 依赖注入和违规追踪器接线。
- F3 API 36 模拟器合约 smoke：3 项，0 failures、0 errors、0 skipped：
  - `mismatchedSession_doesNotExposeF3ActionOrReadies`；
  - `nonF3Session_doesNotExposeF3ActionOrReadies`；
  - `f3Session_initialReadyAndReplayCompletionContractCompletes`。
- F3 正向 smoke 已真实完成“初始 ready → 动作节点 → 离线重放 → 完成 ready”；成功结果与
  fail-closed 依赖共同证明本次有效会话中，RP ViewModel 注入的 `AiGateway` 与 Prompt Extras
  路径未发生 Provider 外调；该结论不代表进程级所有外部网络请求均为零。
- 设备结果 XML：
  `baselineprofile/build/outputs/androidTest-results/connected/benchmarkRelease/TEST-Codex_Pixel_7_Large(AVD) - 16-_baselineprofile-.xml`。
- `:app:compileDebugAndroidTestKotlin`、`lintDebug`、`assembleBaseline`、`assembleDebug`：通过；
  Lint 为 0 errors、41 warnings、4 hints。
- `git diff --check` 在代码检查阶段通过，仅有工作区既有 LF→CRLF 提示。

## READY 超时根因与防回归

- 旧实现把 F3 ready 挂在第 101 个 Lazy item，并同时依赖末项像素几何、不可下滚和停止状态；
  状态条出现后 viewport 缩小，容易让已经处理过的贴底信号失效。
- ready 已迁移到真实可见的状态文案实体；F3 viewport 契约只保留末项可见、不可继续下滚、
  滚动停止，不再依赖像素精确边界。
- `ReadyAssertions` 超时诊断改为一次 `benchmark_*` 资源 ID 扫描并按登记目录过滤，避免逐 ID
  全树扫描把真实 10 秒超时放大成数十秒。
- 新增纯逻辑测试覆盖末项不可见、仍可下滚、仍在滚动和稳定到底四类状态；对应规则已写入
  `.trellis/spec/backend/performance-benchmark-guidelines.md`。

## 尚未取得的性能证据

- 当前没有可用于正式验收的固定物理设备；模拟器结果只证明 fixture、ready、动作、重放和
  零 Provider 合约可运行。
- 没有在同一固定物理设备、构建、刷新率和温控条件下取得优化前后各 5 次
  `FrameTimingMetric` 与 Perfetto 原始证据。
- AC4、AC8、AC9 保持未验收；不得据此给出 P50 / P90 / P95、帧率、jank、
  `frameOverrunMs` 阈值或改善百分比。
- `narra.roleplay.auto_scroll` 是包含挂起时间的异步 wall-time Trace，不能直接当作 CPU 热点
  耗时或与帧预算作一比一归因。
- 本子任务没有固定设备崩溃统计，因此不宣称崩溃率已经量化改善。
- Baseline / benchmark 构建仍报告大量陈旧 Profile 方法失配警告，包括新增消息模型构造器；
  构建成功不等于旧 Profile 已完全命中当前源码。
