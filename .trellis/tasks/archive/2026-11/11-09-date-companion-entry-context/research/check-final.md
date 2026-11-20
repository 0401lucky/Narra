# D2 独立检查交接

2026-11-11：代码检查、自修和本机工程门禁通过，状态为 **CHECK_PASSED_PENDING_NATIVE_DEVICE**。本块版本为 2.4.1；APK 仍使用既有 2.3.0-dev/20300 验证标签，未提交、推送或发布。主会话应完成隔离设备验收后再推进 D3，不将本报告视为整轮 2.4.1 已交付。

## Findings (fixed)

### 1. 刷新后的免费补存与完整性动作错误

- File: `viewmodel/RoleplayCompanionViewModel.kt`、`conversation/CompanionTurnCoordinator.kt`、`data/repository/roleplay/RoomCompanionRepository.kt`。
- Issue: 首次 artifact 写入失败后，刷新只看 REQUESTED/UNCERTAIN，把仍在本进程的结果改成 GENERATE；已落盘的截断结果则被恢复成 SAVE。
- Fix: `pendingArtifact` 读取 D1 实际有界缓存，并通过与保存共用的 `canSaveGeneratedArtifact` 守卫核对 owner/来源/运行。有效缓存恢复 SAVE 与正文；已持久非完整结果保留显式 GENERATE。结束/淘汰或来源失效后释放失效缓存，未扩大 8 槽上限。设置/阅读暂离与结束场次分别处理，未知请求不伪装成已收结果。
- Evidence: `refreshAfterArtifactFailure_keepsFreeSave`、`incompleteRefresh_preservesExplicitRegeneration` 先红后绿；另覆盖开场缓存刷新、进程缓存丢失及原线上离场后权限失效。

### 2. claim 存储失败后没有恢复动作

- File: `viewmodel/RoleplayCompanionViewModel.kt`。
- Issue: INPUT 已存而 REQUESTED 写入失败时，仅显示通用错误，`recoveryAction=NONE`，无法从当前界面继续处理该回合。
- Fix: `generatePreparedAttempt` 在存储异常后保留显式重新请求动作，继续保留同一已存输入；取消仍按取消处理。
- Evidence: `claimFailure_exposesUnsentRecovery` 先红后绿，真实 SQLite 触发器验证首次零 HTTP、恢复只增加一次请求且不重复输入。

### 3. 旧场已持久结果在产品中无法补存

- File: `CompanionRepository.kt`、`RoomCompanionRepository.kt`、`CompanionRequestCoordinator.kt`、`RoleplayCompanionViewModel.kt`、`CompanionRouteContent.kt`、`CompanionPendingResultsBar.kt`。
- Issue: 原路径只能 hydrate 当前非 ENDED 场次最后一轮。线上返回或新 B 场建立后，D1 能保存的旧 A RESULT_READY 没有任何产品入口。
- Fix: 当前角色/persona/run 的 `listPendingResults` 在 Room 使用 hash、完整性及原提交守卫筛选；实际路由增加“待保存结果”，通过 `savePendingResult` 只补原场。普通线上与新 ACTIVE B 均可访问；不激活旧场、不换运行/模式、不发新模型请求。未接受预览、空 marker、截断、跨身份与失效来源不会进入有效列表。
- Evidence: `CompanionPendingRecoveryTest` 的文件型 Room 关闭重开、线上补存、新 B 补 A、来源修订拒绝、身份/预览/截断过滤，以及已接受开场 reroll 补存。实际 Composable/NavHost 回调另有 4 项原生测试，已编译，设备执行由主会话完成。

### 4. 完整产物失效后连显式重试也被阻止

- File: `data/repository/roleplay/RoomCompanionRepository.kt`。
- Issue: RESULT_READY 的 COMPLETED 标志原先无条件阻止新 attempt；引用来源更正后旧结果不能提交，也不能通过显式重试恢复。
- Fix: 有效完整结果继续优先免费补存。旧结果已无法通过提交守卫时，只有明确 `allowUncertainRetry=true` 才可建立新 attempt，D2 重新冻结授权来源；不重复创建输入。
- Evidence: `staleCurrentArtifact_explicitRetryUsesNewSources` 先红后绿；第二次实际 HTTP 使用更正后的来源，同一输入只有一条，合法完整结果仍受原免费补存保护。

### 5. 同 scenario 的旧入口可能短暂显示

- File: `ui/navigation/CompanionRouteContent.kt`。
- Issue: wrapper 原只比较 scenarioId，显式 A→B 路由在 VM 切换前可短暂显示同会话旧候选/动作。
- Fix: 显式路由同时核对 entryId；无 entryId 的旧路由继续恢复实际持久选择。
- Evidence: 在既有 `CompanionEntryNavigationTest` 加入同场景旧 entry 的排除断言，保留原跨场景/Back 验证；成功 fixture 使用真实匹配的 entryId。该类已编译，设备执行待主会话。

### 6. 图片草稿在 INPUT 落库前被 busy 状态清空

- File: `RoleplayScreenCallbacks.kt`、`RoleplayScreen.kt`、`RoleplayNavGraph.kt`。
- Issue: 旧 Screen 用 isSending 清图片，而陪伴任意 busy 在输入写入前就被合并为 isSending。存储失败可能丢图，普通线上补存/刷新也不应伪装成发送。
- Fix: 新可选 `onSendWithInputSaved(parts, ack)` 仅在 beginTurn 保存成功后确认；Screen 只清与已提交快照仍相等的图片。普通线上没有 activeCompanion 时不合并陪伴 busy。旧 callback 为 null 保持原行为。
- Evidence: VM 的输入写入失败测试仍保证零确认/零模型调用；新增 4 项真实 RoleplayScreen/合成 PhotoPicker 测试，覆盖未确认保图、失败同 URI 重发、迟到确认不清新 URI、旧回调兼容，已编译并打入测试 APK。

## Findings (not fixed)

无已确认但未修复的代码问题。原生交互与真实模型效果的尚未执行部分列在下方，不以源码存在或 Fake 回复冒充通过。

## Verification

- Lint: **pass**，0 errors、56 warnings；原始报告 `check-lint-results-debug.xml`。修复涉及文件没有 Lint 诊断，未为本任务改动其它告警位置。
- TypeCheck: **pass**，Debug、JVM test、AndroidTest 与 baseline Kotlin 编译通过。
- Tests: 最后 **109 项/11 类** Companion JVM 通过。与作者 361/80 两轮原始 XML 按 class+method 合并为 **397 个唯一用例/28 类**，全部通过；不是单次 397 项运行。12 个本轮新增回归中，4 个先保留真实失败再修复。
- Screenshots: **35 项/3 类 pass**，E04 14、H01 18、H02 3；只 verify，未 record 或替换基准。
- Build: **pass**，Debug 和 AndroidTest APK 构建完成；完整日志 `check-final-build-01.log`。
- Native: **21 项/4 类已编译、未运行**。测试 APK 的 dex 中核验四类均存在，manifest 为 `androidx.test.runner.AndroidJUnitRunner` → `com.narra.app.dev`。主会话在 `Narra_Memory_V24` 执行；这组测试包含实际 Screen 与 NavHost wrapper，但不等于整个 AppGraph 端到端启动验收。
- Formatter: Android Studio 对本轮 17 个修复/新增文件完成格式化；`git diff --check` 和范围内尾随空白检查通过。

实际命令与完整汇总见 `check-final-validation.json`。原始 JVM 为 `check-final-jvm-results/`，去重用例表为 `check-verified-jvm-tests.json`；截图 XML 为 `check-screenshot-results/`。失败证据保存在 `check-red-recovery-01-results/` 和 `check-red-stale-artifact-01.xml`。

## D2 验收追踪

| 条款 | 本次核对与行为证据 | 尚待部分 |
| --- | --- | --- |
| AC1 新旧入口/三开场/接受 | EntryRepository、EntryViewModel、RequestCoordinator：DIRECT 零请求、旧 greeting/run 保留、预览不成为原文、连点接受唯一、免费接受恢复 | 真实控件设备执行 |
| AC2 导航/恢复/迟到/原路返回 | 持久 entrySelectionId/来源校验、VM A→B 取消、同路由重建、在线/新 B 旧结果补存；wrapper 实际绑定与回调已审查 | 21 项原生测试中的导航/IME/小屏/图片回执 |
| AC3 继续/重试/reroll | 三协议真实 HTTP、typed CONTROL 与普通“继续”输入区分、输入唯一、旧答位置/时间保留、内置/自定义预设兼容 | 真实模型表达效果 |
| AC4 原文授权 | Room 与实际 HTTP 的角色/persona/独立运行/群私有/空集合/断点/删源矩阵；回执准备挂起后再次核验；来源失效后显式新请求 | 真实模型对已授权材料的使用效果 |
| AC5 召回/世界书/时间 | 每请求 recall 关闭/失败不借旧注入、depth 0 的 role/order、冻结时区/跨年/请求前后设置变化、真实 DataStore CAS | 实际模型对时间/人物规则的服从效果 |

未调用真实付费模型/TTS，未操作用户数据库或用户设备；D3 的文风/正文/观测与后续 D4–D6 能力仍依父任务推进。本报告不把这些后续责任列成 D2 已完成。

## 精确范围与冻结产物

- `check-final-source-hashes.json`：**1396 项**，最后复核无漂移；SHA-256 `dd3838c53bd0f408940a93ca5ec31bd28373ebd965c59972371ec8cd7d1ed302`。
- 相对 D2 开始的 **50 个 Kotlin 文件**：`check-files.json`；相对作者最终冻结的 **17 个自修/新增文件**：`check-fixes-files.json`。
- **273 项**旧 schema/截图/参考保护资料逐字节未变；schema 仍为 55，SHA-256 `5b33ea781a198962487b9324f054d2a243e0903f49c65ce00bf93b853b696033`。
- 最后 109 项通过后的生产/JVM 源码与最终冻结相同；其后只补充/格式化最终图片原生测试，并完成全源集编译和 Lint。未重复无变化的全量 JVM 套件。

| 产物 | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/debug/Narra-v2.3.0-dev-20300-dev.apk` | `d74b4822fd51de451667b8a85f180f29bef454597331eae6ec435c254b1a7204` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `2207dc5ffba65e291494937bdee4b347893e351743302009b5671372e62cd232` |

两包 apksigner 校验通过，证书 SHA-256 均为 `3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8`。APK 是本地验证产物，没有上传或发布。

## 所有权与下一步

已核对主会话更新的 `companion-entry-context-guidelines.md`，新接口和实际边界一致。D3 可继续复用既有设置映射/请求描述，补完整表现配置快照；本次没有提前改写 D3 parser。

本项目 Gradle daemon **11840** 已用日志最后 `currentDir` 和 IDLE 状态确认归属，再执行 `gradlew --stop` 正常停止；其 Kotlin 子进程 **1140** 随之正常退出。两个 PID 均已消失，没有强制终止或处理其它工程/用户进程。证据为 `check-daemon-cleanup.json` 及对应 status/stop 日志。

**生产代码、全部测试、formatter 与 Gradle 所有权现已释放给主会话。** 本检查者没有仍在运行的构建/格式化任务。主会话可用上述冻结 APK 进行 21 项隔离设备验收，并保存/审阅三张原生 UI 候选；设备通过后再完成 D2 状态与 D3 交接。
