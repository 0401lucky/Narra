# 2.2.6-dev RP Compose 与流式帧性能：实施计划

## Completion Definition

AC1～AC10 满足：三处 RP 列表拥有稳定唯一 identity 与 `contentType`，固定 48 ms 离线
流式场景取得可复查的前后结果，贴底、光标和语音卡不再制造已确认的高频任务 / 分配，且
streaming 状态只在 Trace 触发时拆分。缺少物理设备或优化前基准时不得标记性能验收完成。

## Pre-Start Gate

1. 基准 / 可观测性子任务需明确 baseline-only fixture、强 ready、结果归档和 RP 场景扩展
   接口；本任务不得另建平行基础设施。
2. 审查工作区差异，确认 `RoleplayActionMessageCards.kt` 的语音卡播放器只由本任务修改，
   TTS 网络 / Base64 子任务不触碰该文件。
3. lucky 审阅本规划并明确批准后，才可运行 `task.py start`；本计划阶段不得修改产品代码。

## Ordered Checklist

### Wave 1：优化前证据与 identity 回归

1. 加载 `trellis-before-dev`，读取 UI state、错误处理、质量和复用规范。
2. 接入基准子任务的 RP fixture / ready 合约，新增独立 RP F3 宏基准类，不改写通用启动
   基准；准备 100 条固定消息和约 48 ms 固定 chunk。
3. 在同一设备记录优化前 FrameTiming / Perfetto；无设备则停止性能结论，只继续可静态验证的
   correctness 工作。
4. 先补 `uiItemId` 的正文增长、前插、删除、多分段、action / special、streaming preview
   失败测试。
5. 在 mapper 建立唯一 identity 契约，三处 Lazy 列表改用 `uiItemId` 并提供稳定
   `contentType`；删除旧正文哈希 / 全局 index key helper。

### Wave 2：贴底、光标与语音卡

1. 对照普通聊天，抽取或实现 RP feature 内共享贴底策略：单一生命周期协程、约 64 ms
   合并信号、底部附近守卫、流式短滚动和用户离底保护。
2. 为普通对话与在线手机接入同一策略；保留首次定位、手动跳顶 / 跳底和引用定位行为。
3. 缓存只依赖正文的流式文本，把光标 alpha 移到独立节点 / 绘制层，补语义与长正文测试。
4. 将语音卡播放器收敛为可测试状态机，使用 `prepareAsync()`；补 preparing 重复点击、
   completion、error、key 变化、dispose 和幂等 release 测试。
5. 仅在 Playing 分支创建无限波形动画，静止 / 准备 / 错误状态使用缓存的常量波形。

### Wave 3：复测与 streaming 闸门

1. 以与 Wave 1 相同的设备、构建、fixture、刷新率和温控重复 F3 至少 5 次，保存原始结果。
2. 记录 `mapMessages`、完整 UiState 回写、贴底调用与超预算帧的对应关系。
3. 若 Trace 未触发闸门：在研究记录中写明“跳过状态拆分”及证据，继续 Wave 4。
4. 若 Trace 触发闸门：先补流式顺序、完成、失败、取消和会话切换测试，再新增窄
   `RoleplayStreamingState`，把持久消息映射移出 chunk 高频路径。
5. 对拆分前后再次同环境复测；P95 改善不足 15% 或出现功能回归时回退状态拆分，保留其它
   已验证改动。

### Wave 4：质量与范围收口

1. 运行 mapper / identity、贴底决策、观察层、播放器状态机的定向 JVM 测试。
2. 运行相关 Compose / AndroidTest 源码编译；有设备时执行用户离底、光标与语音卡交互测试。
3. 运行 RP F3 宏基准、`lintDebug`、`assembleBaseline` 和 `assembleDebug`。
4. 检查 diff：不得包含设置共享、TTS 网络 / Base64、Room / mutation、普通聊天重构或其它
   未点名 Compose 微优化。
5. 将优化前后结果、Trace 闸门结论、设备条件和未取得结果写入任务研究记录，供父任务集成。

## Validation Commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.roleplay.RoleplayMessageUiMapperTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.roleplay.RoleplayMessageUiIdentityTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ui.component.roleplay.RoleplayStreamingAutoScrollTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ui.component.roleplay.RoleplayVoicePlaybackControllerTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.RoleplayObservationSupportTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.RoleplayOfflineReplaySupportTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.system.performance.F3BenchmarkAiDependencyPolicyTest" --console=plain
.\gradlew.bat :baselineprofile:verifyNarraF3BenchmarkIntegration --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.baselineprofile.F3BenchmarkContractSmokeTest' --console=plain
.\gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.baselineprofile.FrameBenchmarks' --console=plain
.\gradlew.bat assembleBaseline --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

- 计划中的新测试类名可在实现时按最终职责微调，但必须保留对应行为覆盖。
- 单个 JVM 测试命令最大等待 60 秒；超时只能记录为未取得结果。
- F3 正向 smoke 必须同时通过首次 ready、动作节点和结束 ready；零 Provider 由 fail-closed
  依赖注入、违规追踪器以及上述 smoke 的成功共同证明，不能只凭 fixture 中没有密钥来宣称。
- 设备测试前记录型号、API、刷新率、电量、温控、动画缩放、构建 commit 和 benchmark
  工具版本；前后任一条件不一致时不做百分比结论。

## Acceptance Mapping

- AC1～AC2 → Wave 1 identity / `contentType`。
- AC3～AC4 → Wave 1、Wave 3 的固定离线流式基准。
- AC5 → Wave 2 自动贴底。
- AC6 → Wave 2 光标文本。
- AC7 → Wave 2 语音卡动画与异步播放器。
- AC8～AC9 → Wave 3 Trace 闸门与条件式状态拆分。
- AC10 → Wave 4 全量质量与范围检查。

## Risky Files / Rollback Points

- `RoleplayMessageUiModel.kt`、`RoleplayMessageUiMapper.kt`：新增必填 identity 会影响大量构造点；
  必须通过编译错误逐一补齐，不能用随机 / 正文派生默认值绕过。
- `RoleplayDialoguePanel.kt`、`RoleplayOnlinePhoneContent.kt`、`RoleplayReadingMode.kt`：key
  变化会影响 item 状态复用和引用滚动，三处同时验证但分开审查。
- `RoleplayDialoguePanelParts.kt`：光标拆层不能改变换行、字号和可访问正文。
- `RoleplayActionMessageCards.kt` 及新播放控制器：监听器竞态可能导致双重释放或释放后回调；
  使用单一状态机和 identity 守卫。
- `RoleplayObservationSupport.kt`、`RoleplayUiState.kt`、导航收集点：只有 Trace 触发后才可修改，
  并作为独立提交 / 回滚点。
- `baselineprofile` RP 基准与 baseline-only app hook：ready 伪成功会污染全部结论；先证明等待
  失败会中止测试，且不触发真实网络。
- 工作区可能存在 lucky 或其它子任务改动；实施时按文件 ownership 协调，不覆盖或回退他人
  内容。
