# D3 作者续接交接

2026-11-17；当前状态：**AUTHOR_HANDOFF_PARTIAL_PENDING_R4_R5_CHECK**。
**所有产品/测试源码、formatter 与 Gradle 执行权已释放给主会话。**

本记录恢复旧会话 `01a1c985-b2c3-7371-9f12-be565d5080d4` 的实际产物。
用户已批准完整 2.4.1 的 D1–D6 方案，D1/D2 已验收；本块尚未通过最终检查。
本次续接未修改产品或测试源码，原 D3 44 个源码文件改动保持。

## 已恢复的有效证据

- 旧作者命令在会话中断后继续完成：`implementation-jvm-02.log` 的
  `BUILD SUCCESSFUL in 1h 30m 20s` 和 `implementation-jvm-02-exit.json` 的退出 0
  相互对应。原始 24 份 XML 已复制到 `implementation-jvm-02-results/`。
- 实际 **24 类、254 个唯一方法全部通过**，0 failure/error/skipped，
  无 `initializationError`；逐方法清单为 `implementation-verified-jvm-tests.json`。
  旧命令完整参数没有单独保存，不能把按 XML 恢复的选择器写成原始命令。
- 恢复时（2026-11-17 14:44 UTC）1415 项源码与该次
  `implementation-jvm-02-source-hashes.json` 完全一致；
  D3 开始时的 274 项保护文件逐字节不变。恢复检查见
  `implementation-resume-recovery.json`，当前冻结见
  `implementation-resume-source-hashes.json`。
- `implementation-format-01.log` 记录 44 文件扫描与格式化全部成功。
  `implementation-red-green-audit.json` 逐一确认历史 core/preservation 的
  6 个失败方法均在恢复的绿测通过；这不覆盖下方新发现。

## 构建期间同期源码漂移

主会话和只读 checker 提示后，作者在 15:01:26 UTC 核对出 **12 项**相对本次
构建前冻结变化。完整 before/after SHA、mtime、size 见
`implementation-build-01-source-drift.json`，对应当前快照为
`implementation-build-01-source-drift-hashes.json`。
最新文件 mtime 为 15:01:07 UTC，早期 7 项为 14:47:54–14:58:28 UTC。
作者没有写这些文件、没有运行 formatter。用户随后明确 UI 正由另一个 agent
修改且仍在工作；来源已确认，全部漂移保留，不追查或恢复。
取消后的补充快照与实际范围见 `implementation-cancelled-source-drift.json`；
因另一 agent 仍在写入，该快照不是最终工作树。

因此恢复的 254 项仅是其历史冻结的有效证据，不能称最新漂移源码通过。
当前任务图即使退出 0，也无法证明 APK 与最终源码一致；正式 checker 接管后须按
主会话协调后的最新源码重新冻结与验证。不能恢复、覆盖或偷偷删除同期改动。

## 当前工程门禁

本次启动 `implementation-build-01-command.json` 中的实际命令：Debug、UnitTest、
AndroidTest、baseline Kotlin 编译，`lintDebug`、dev APK 和 AndroidTest APK。
使用单次 `-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8`、offline、
no-daemon 和单 worker，未修改仓库或全局配置。

日志已完成 Debug/UnitTest 缓存编译与 AndroidTest/baseline 编译，随后进入
`lintAnalyzeDebug`；这些是历史命令/编译证据，不证明同期漂移后的最终源码。
用户要求收住本轮并询问进度，主会话据此指示停止低收益长构建：本次 executor
session 14774 发送 Ctrl+C 后实际退出 1；没有可靠 Gradle 退出码。
Lint 未取得结果，双 APK 任务尚未执行，未产生本次最终 APK 或哈希。
见 `implementation-build-01-cancellation.json` 和 `implementation-validation.json`。
Ctrl+C 后本次已知 wrapper/daemon PID 均不存在，没有额外终止其他进程。
本次创建的 Kotlin 编译器 PID 17556 在全部 Kotlin 编译完成、进入 Lint 后释放；
PID/创建时间/CommandLine 与完成条件证据见
`implementation-build-01-memory-release.json`。之后的进程清理按主会话要求等整次
Gradle 结束；未关闭用户进程。

## 独立预审确认的待修复问题

1. **摘要补全会重新加入观测 raw**：
   `ConversationSummaryCoverageSupport.sourceText` 保留权威 raw，
   `completeInput` 在格式化输入不包含 raw 时追加“完整来源补充”；
   `SummaryCoordinator` 再传给实际 `generateSummary`，导致 formatter 已排除的
   `<observe>` 返回摘要输入。raw 指纹与覆盖契约必须保留，不能简单改变所有
   `sourceText` 使用方。正式 checker 须以实际摘要链红测、修复及覆盖回归验收。
2. **超长 observe 属性绕过正文边界**：
   `CompanionReplyParser.observeOpen` 的属性 `{0,4096}` 上限导致 5000 字符属性的
   `<observe>` 根本不被识别/排除；legacy 去标记后 field 值可成为 beats，
   经 `hasReadableBody` 进入 canonical/semantic。例如 `pad` 为 5000 个 `a`、
   唯一 field 为 `OBS_BOUNDARY_SENTINEL` 的 observe-only 回复。
   正式 checker 须同时证明此结果不能提交，以及异常块前后合法正文保留。

以上由主会话转述独立只读审查的源码结论，作者尚未运行新红测或修改。
主会话已调整本轮范围：作者停止长构建并交接，正式 checker 只处理这两项
已确认问题及最小相关逻辑回归，不扩大排查或推进后续块。

## 剩余验证

- 本次尚未运行 D3 截图 verify；前置 D2 的 35 项通过不计为 D3 结果。
  受影响选择器为 E04 14、H01 18、H02 3，禁止 record 或替换基准。
- 原生源码确认 6 类 **37 方法**：D3 设置 9、观测含实际阅读宿主 7，D2 21。
  见 `device-test-plan.json`。AndroidTest 编译通过不等于设备执行；最终 checker
  修复后须重新核对 APK/源码冻结与打包方法，主会话再执行隔离 AVD 验收。
- 正式 checker 尚待两项新问题红测/修复及最小相关逻辑回归和交接。
  联合编译/Lint/双 APK、截图及 37 项设备验收等双方共享源码稳定后继续；
  本轮不推进 D4–D6。没有本次可称为 D3 最终验收的 APK。
- 真实模型/TTS、实际计费、物理设备与完整 AppGraph 端到端未验证。
  未接纳新截图、提交、推送或发布；构建仍为 2.3.0-dev/20300，schema 55。

## 已落地的下游接口

- `CompanionReplyProjector.project(message)` / `project(messages)` / `preview`：
  纯投影，保留 raw 与 UTF-16 半开 span，不写 canonical、修订或请求模型。
  缺失/失效 `@Transient CompanionReplyMetadata` 保留正文、禁用观测，不从当前
  配置补历史标签；机器来源资格继续走 D1 的实际关联图。
- `ParsedCompanionReply` / `CompanionBeat`：source 含源 ID/修订、协议/parser/rawHash、
  attempt/configurationRevision/characterId；spriteKey、自由 emotion 与
  `CompanionVoiceEmotion` 分开，协议和 parser 版本均为 1。
- `CompanionCharacterSettings.writing` 继续角色小映射与 CAS；
  `descriptor.settingsRevision` 是本次 live 守卫，
  `presentation.sourceSettingsRevision` 是实际表现来源。
  `CompanionAttemptIntent.RETRY` 保留原表现/hint，`REGENERATE` 采用新快照。
- `CompanionSettingsViewModel`/Screen 已接真实“陪伴表达”路由，固定错误区与
  SavedState 草稿；读取最新、丢弃、保存和返回有独立动作。
- `RoleplayReadingMode.companionObservationEnabledFor(characterId)` 约束历史观测
  的当前显示 gate，实际 RoleplayScreen 已传来源角色偏好；历史标签保持旧快照。

源码完整范围见 `implementation-resume-recovery.json` 的
`d3_files_changed_from_start`。最终可消费合同仍需正式 checker 修复后交接校准。
