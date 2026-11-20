# v2.4.1 D3–D6 恢复与执行记录

2026-11-18，用户明确恢复剩余实施，沿用已批准完整方案及现有任务。
旧会话“先收口”不再限制本轮；不新建任务，不重复确认已批准范围。
本轮功能固定归属 2.4.1，不改版本构建参数或更新 JSON，不提交、推送或发布。

## 所有权与工作区

- 会话通过 `task.py start` 绑定既有 D3；15 条实施、19 条检查上下文校验通过。
- 起点 550 项折叠工作区改动全部保留；展开状态和 1425 项源码/配置哈希见
  `resume-2026-11-18/baseline/`。281 项旧 schema、截图、UI 漂移及版本/更新
  参数单独保护。此前 UI agent 的 12 项漂移不回滚、不重录基准。
- 初查没有活跃 Trellis channel、Java/Gradle/模拟器进程；本轮主会话是唯一
  Gradle 执行者。构建持有 `.trellis/.runtime/companion-build-lock.json`，
  每轮固定命令、日志、前后源码哈希和原始 XML，源码变化时不宣称对应源码通过。
- `d3_check_resume` 负责 D3 独立检查和已确认问题修复。主会话拥有父任务、
  验收矩阵、版本记录、规范和整合取证。下块仅在前置交接后接手共享接口。
- 所有验证使用合成数据、Fake/MockWebServer 和专用隔离环境；不调用真实
  付费模型/TTS。截图仅 verify，候选保留，未经接纳不写基准。

## D3 本轮结果

- 已读取 R4/R5 交接、结构化检查记录及设备计划。两处产品修复和 6 项回归
  已存在，旧 254 项只属于旧源码，不计入新修复验证。
- `d3-regression-01`：实际运行 3 类 37 项，36 通过、1 失败、0 错误/跳过。
  失败为 `CompanionProjectionRepositoryTest.summaryRequest_excludesObservationAndKeepsRawSourceCoverage`，
  在 `result.updated` 断言失败；检查者继续定位。源码前后逐项一致。
- 首轮 XML 取证遇 Windows 路径长度限制，已改短文件名并恢复同一轮原始
  XML，没有为恢复报告重复运行测试。原始 Gradle 日志保留 BUILD FAILED；
  包装器失败与 Gradle 原始退出码分开记录，不伪造成功。
- `d3-engineering-02`：失败摘要方法定向复验通过；与首轮按方法去重后 37 项
  全部通过，包括 6 项新增回归。Debug/UnitTest/AndroidTest/Baseline Kotlin、
  Lint、Debug/AndroidTest 双 APK 通过，源码 1425 项前后一致。
- `d3-screenshots-03`：实际 35 项，1 匹配、34 像素差异，零渲染/逻辑错误；
  68 张 actual/compare 与哈希已固定。代表图可对应既有 UI 主题、气泡和底栏
  改动；视觉待接纳，281 项保护文件未改，未 record。
- `d3-device-04`：实际 APK 选择器先核对 6 类 37 项，然后在专用合成 AVD
  执行；18 通过、19 失败、0 跳过，ADB exit 0 不代表 instrumentation 通过。
  17 项是 `RoleplayCompanionUiState` / `CompanionEntryCallbacks` 旧构造器链接
  错误，另有模板输入语义与观测窄屏触控断言失败。源码前后仍一致，双 APK
  哈希、原始日志、方法结果及两张原生设置候选已固定。独立检查者接手必要
  修复；先比对当前源/class/APK ABI，不以兼容构造器遮盖旧缓存。
- Gradle 9.3.1 文档确认单 task 的 `--rerun` 可定向强制执行，不需要
  `--rerun-tasks` 重跑所有依赖；实际采用命令以修复交接为准。
- `d3-observation-build-07`：独立配置 Context/ComposeView 夹具、新编译、Lint
  与测试 APK通过，源码无漂移；原观测文本、滚动/48dp/关闭/Back断言保留。
- `d3-device-08`：受共同 fixture 提取影响的7项观测设备复验全部通过。
  最终按方法去重37/37通过；宿主/Dialog/节点/实际TextLayoutResult fontScale
  全部实测2.0，320×600dp真实窗口、scroll max1963、末项完整可见、窗口与
  基础字体恢复通过。原生观测候选已目视检查，未写基准。
- D3必要工程/原生行为验收完成，最新交接为
  `../11-09-date-companion-style-observation/research/handoff-2026-11-18.md`。
  281保护项全部未改；旧UI像素差异与真实模型效果继续单列。

## D4 当前执行

现有D4任务已通过task.py start进入in_progress，16条实施/14条检查上下文
有效；基线保存于D4 research/resume-2026-11-18/baseline。
原生Codex及其本地CLI默认模型多次TPM限流，已保留真实失败，不更改配置。
Trellis channel `v241-companion-resume` 中沿项目agent卡默认后端的
`d4-implement-default` 接手D4生产/测试源码。主会话继续唯一Gradle；D3
worker已停止并保留交接，D4避免12个UI漂移文件，必须的小adapter先协调。
当前只有实施开始结论，D4尚未宣称实现或验收，D5/D6仍等待依赖。

### 实际执行后端阻塞

D4原生/Codex CLI反复实际TPM限流；短上下文8项重派及同代理恢复仍失败。
默认agent卡Claude CLI经10轮重试后返回本机3425 ECONNREFUSED，0模型回合/
0费用，无D4产品源码写入。已停止失效worker，未更改任何API凭据、模型配置
或本机代理配置。D4保持in_progress，meta明确等待可用worker后端，不能把
激活或预读记成实现。主会话已用异步选择请求用户明确指定gpt-6-astra或等待
原配额；协作工具规则要求模型覆盖由用户明确指定，未擅自覆盖模型。
获明确答复后直接恢复D4，D5/D6范围/顺序与原批准方案保持。

## 后续顺序

D3 必要验证及独立交接 → D4 本地演出/素材/连续阅读 → D5 MiMo/翻译/缓存/
取消/独立收藏 → D6 历史修订/全量原文导出/资料白名单/跨模块验收。
主会话已读取 D4、D5、D6 各自 PRD、设计及执行计划；这些读取不代表下块已实现。
每块更新实际状态、验收证据与可续接交接，未验证的模型效果、视觉接纳或物理
设备表现独立记录。
