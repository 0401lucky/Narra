# Research: UI / Compose 性能扫描

- Query: 扫描普通聊天、角色扮演（RP）、设置、记忆与辅助功能页面的 Compose 性能风险，区分源码已确认问题、需要基准量化的热点和已排除误报，并给出可执行的测量与修复方向。
- Scope: mixed
- Date: 2026-08-11
- Task: `.trellis/tasks/08-07-app-performance-optimization`
- Target version: `2.2.6-dev`

## Findings

### 结论摘要

当前最高优先级风险集中在 RP 流式消息链路。源码可以确认：每个约 48ms 的流式帧先更新整份 `RoleplayUiState`，随后观察层又全量映射原始消息并再次更新整份状态；三个 RP 消息列表同时使用包含正文哈希和索引的非稳定 key。流式正文增长会改变 item identity，并叠加全表筛选、自动贴底、光标文本重建和语音卡无限动画开销。

另一个跨页面风险来自设置数据源：`AppSettingsStore.settingsFlow` 是未共享的冷流，每个订阅者都会重复执行 Android Keystore 解密及多组 JSON 反序列化。`AppRoot`、根级 `SettingsViewModel`、聊天 / RP / 动态 / 手机页 ViewModel 可形成多个独立订阅，其中多数由 Main 上的 `produceState` 或 `viewModelScope` 收集。源码可以确认重复工作结构，具体主线程耗时仍需 Trace 量化。

普通聊天链路已具备独立流式状态、稳定消息 ID、`contentType`、纯文本快速路径和滚动节流，因此不能把 RP 的问题泛化为全局聊天问题。

### 严重级别

- P0 / 已确认结构性风险：RP 流式帧触发整页状态发布与全消息重映射。
- P0 / 已确认正确性与性能风险：RP 三条 Lazy 列表 key 随正文和索引变化，破坏 item identity。
- P1 / 已确认高频开销：在线手机模式每个流式正文变化都重新启动 `animateScrollToItem`。
- P1 / 已确认高频分配：RP 流式光标动画帧持续重建完整 `AnnotatedString`。
- P1 / 已确认常驻动画：每个可见 RP 语音卡无条件创建并读取 12 个无限动画。
- P1 / 已确认重复计算结构：未共享的设置冷流被多个 Main 侧订阅者重复解密和反序列化。
- P1 / 已确认订阅过宽：`AppRoot` 因完整 `SettingsUiState` 的无关字段变化重组，并额外直接订阅同一设置源。
- P1 / 已确认高频分配：RP 普通对话和在线手机模式在组合阶段每次 `filter` 全部消息。
- P2 / 待 Trace 量化：导出、分享、筛选、记忆分组和重页面导航动画中的主线程工作。

### 1. RP 流式刷新链放大为整页状态刷新和整表映射

源码链路：

1. `ChatStreamingSupport.kt:14-49` 以约 48ms 的发布窗口产生流式帧。
2. `RoleplaySendActionSupport.kt:736` 的发送任务使用调用方传入的 scope；该 scope 由 `RoleplayViewModel.kt:322-325` 注入为 `viewModelScope`。
3. `RoleplayRoundTripExecutor.kt:1093-1135` 每帧执行正文清洗、协议解析，并更新 `RoleplayUiState.streamingContent`。
4. `RoleplayObservationSupport.kt:225-263` 观察 `streamingContent`，每次变化都调用 `RoleplayMessageUiMapper.mapMessages` 重映射全部原始消息，再次更新完整 `RoleplayUiState`。
5. `RoleplayNavGraph.kt:355-395` 收集完整 `RoleplayUiState` 并将整页参数传入 `RoleplayScreen`。

这条链路至少存在“流式字段更新”和“全消息映射结果更新”两次状态发布候选。全量映射的实际 CPU 时间、每帧重组范围与是否跨过 16.67ms 帧预算仍需 Perfetto / Macrobenchmark 量化，但刷新放大结构已由源码确认。

修复方向：

- 将高频流式内容从低频 RP 页面状态中拆出，参照普通聊天的 `ChatStreamingState`。
- 不要用流式字段变化驱动全部持久消息重新映射；流式气泡应在展示层按稳定消息 ID 合并。
- 对持久消息映射使用基于消息集合版本或实体 identity 的缓存，而不是每帧全量执行。
- 通过 Compose Compiler 报告确认 `RoleplayScreen`、消息模型和回调参数的稳定性，再决定是否需要不可变集合或稳定包装。

### 2. RP 三条消息列表使用非稳定 key，且缺少 `contentType`

发现位置：

- `RoleplayDialoguePanel.kt:242-246,376-391`
- `RoleplayOnlinePhoneContent.kt:572-576,800-815`
- `RoleplayReadingMode.kt:191-195`

三处 key 都包含 `content.hashCode()` 和 `index`。因此：

- 流式正文每次增长都会改变 key，Compose 会把同一条流式消息识别为新 item。
- 在列表前部插入、删除或重排时，后续 item 的 index 改变，identity 整体漂移。
- 三处都未提供 `contentType`，不同消息形态无法帮助 Lazy 布局复用组合。

`RoleplayMessageUiMapper.kt:131-133` 的注释声称复用 `createdAt` 可以保持 key 稳定，但 key 仍包含正文哈希，属于实现与注释的明确矛盾。

`RoleplayOnlinePhoneMessageListKeyTest.kt:12-27` 只验证不同 index 得到不同 key，没有覆盖同一消息正文增长、前插消息或删除消息后的稳定性，因此当前测试无法阻止此回归。

修复方向：

- 为每个可展示气泡增加稳定且唯一的 `uiItemId`；不能只使用 `sourceMessageId`，因为同一底层 assistant 消息可能拆成多个 UI 气泡。
- 建议由 mapper 生成“底层消息 ID + 源消息内稳定分段 ID / 局部序号”；流式占位在整个生成周期内固定使用同一 identity。
- key 仅使用稳定 `uiItemId`，不使用正文、可变状态或全局位置索引。
- 按消息类别提供有限且稳定的 `contentType`，例如文本、图片、动作、语音、系统消息。
- 新增回归测试：正文增长 key 不变；前插/删除其他消息后原消息 key 不变；不同消息 ID 不冲突。

### 3. RP 自动贴底会产生高频滚动与动画取消重启

- `RoleplayDialoguePanel.kt:191-203` 把流式正文长度放进 `LaunchedEffect` key。正文每次增长都会取消并重启 effect；虽然内部使用 64ms 窗口限制调用频率，但流式期间仍持续执行 `scrollToItem`。
- `RoleplayOnlinePhoneContent.kt:403-410` 每次正文增长直接执行 `animateScrollToItem`，没有节流。48ms 左右的流式发布频率会使滚动动画反复取消并重新启动。

普通聊天的可复用对照：

- `ChatScreenBehavior.kt:167-186` 使用 64ms 节流。
- `ChatScreenBehavior.kt:52-67` 的 `keepConversationEndInView` 使用限步 `scrollBy`，避免每次重新启动长动画。
- `ChatScreenBehavior.kt:106-133` 在用户手动离开底部后停止强制贴底。

修复方向：把 RP 两种模式统一到同一自动贴底策略；只在用户仍位于底部附近时跟随流式内容，使用单一长生命周期协程消费节流后的滚动信号，避免以正文长度反复重启 `LaunchedEffect`。

### 4. RP 流式光标动画帧持续重建完整文本

`RoleplayDialoguePanelParts.kt:402-424` 创建无限光标动画，在组合阶段读取 `cursorAlpha`，随后执行：

- `buildAnnotatedString`
- `append(content)`
- 追加光标及透明度样式

读取动画值会让这段组合逻辑随动画帧重新执行，进而对不断增长的正文重复分配和复制完整字符串。正文越长，单帧成本越高。

修复方向：把正文 `AnnotatedString` 缓存到只依赖正文内容的 `remember`，光标作为独立轻量节点、绘制层或末尾 overlay 动画；不要让光标 alpha 的变化触发正文重建。

### 5. 每个可见 RP 语音卡无条件运行 12 个无限动画

- `RoleplayActionMessageCards.kt:406-426` 无论消息是否播放，都创建并读取一整组 12 个 `animateFloat`。
- `RoleplayActionMessageCards.kt:546-551` 表明这些脉冲值只在播放态展示时有用。
- `RoleplayActionMessageCards.kt:460-481` 还在点击回调路径同步调用 `MediaPlayer.prepare()`，本地或网络数据源都可能阻塞主线程。

长列表中只要语音卡可见，即使静止未播放也会维持无限动画时钟和重组。修复时应只在 `isPlaying` 为真时创建/读取动画，静止态使用常量高度；播放器准备切换为异步 API，并明确释放、失败和重复点击状态。

### 6. 设置冷流重复解密，且 `AppRoot` 订阅过宽

- `AppRoot.kt:47-52` 直接订阅 repository 的 `settingsFlow`。
- `AppRoot.kt:76-93` 又收集完整 `SettingsUiState`，实际只使用其中两个主题字段。
- `SettingsUiState` 在 `SettingsViewModel.kt:44-80` 包含 provider、模型、草稿、loading 等大量与根主题无关的字段。
- `SettingsViewModel.kt:157-168` 自身也订阅同一个 repository flow。
- `SettingsViewModel.kt:182-189` 等字段编辑会更新完整 UI state；例如设置页草稿每次输入都可能让 `AppRoot` 重新组合。

持久化设置变化还可能同时沿 repository flow 和 ViewModel UI state 两条路径抵达根组合。更底层的放大点如下：

- `AppSettingsStore.kt:156-302` 的 `settingsFlow` 直接对 `context.dataStore.data` 做 `map`，没有 `flowOn`、`shareIn` 或 `stateIn`。
- 每次收集都会执行 `SecureValueStore.getString/getStringMap`（`AppSettingsStore.kt:158-163`）；`SecureValueStore.kt:27-32,44-50,78-93,96-100` 同步访问 SharedPreferences、Android Keystore、AES/GCM 解密并解析 JSON。
- `DefaultAiSettingsRepository` 只是透传该冷流（`AiSettingsRepository.kt:8-14`），没有在应用图范围内共享已解析结果。因此每个 collector 都重新执行上述 map 体，而不是共享一个 `AppSettings` 实例。
- `SettingsViewModel.kt:141-145` 以 `SharingStarted.Eagerly` 建立一条上游订阅，`SettingsViewModel.kt:157-168` 又直接 collect 原始 repository flow，至少形成两次解析。设置页各导航子路由收集的是已经共享的 `storedSettings`（例如 `SettingsAssistantNavRoutes.kt:55-76`、`SettingsDataNavRoutes.kt:125-143`），不会再增加原始冷流解析，但完整 `AppSettings` 变化仍会扩大这些路由的重组范围。
- `ChatViewModel.kt:155-159`、`RoleplayViewModel.kt:152-156`、`MomentsViewModel.kt:61-65`、`PhoneCheckViewModel.kt:72-76` 各自建立 `stateIn`；其中 Chat 的 `observeSettings`（`ChatViewModel.kt:1169-1177`）和 RP 的 `RoleplayObservationSupport.observeSettings`（`RoleplayObservationSupport.kt:36-49`）在 `viewModelScope` 内持续收集，使 `WhileSubscribed` 在 ViewModel 存活期间实际上保持上游活动。
- `MomentsViewModel.kt:74-85` 还在 `combine` 中直接订阅 repository flow，可能与其 `settings` stateIn 叠加。

仅根级常驻路径就至少包含 `AppRoot` 一次原始收集和 `SettingsViewModel` 两次原始收集；进入聊天、RP、动态等图后还会按各 ViewModel 生命周期继续增加独立 map 执行。

这些 collector 大多运行在默认 Main 上下文：`AppRoot` 的 `produceState`（`AppRoot.kt:47-52`）和各 ViewModel 的 `viewModelScope` 没有在此处切换 dispatcher。按 Kotlin Flow 的上下文保持规则，`map` 中的解密 / Gson 工作会在下游收集上下文执行；实际是否造成可见掉帧仍需主线程 Trace 证明，但重复工作与潜在 UI 线程位置已由源码确认。

修复方向：

- 在应用图中建立单一、生命周期明确的共享 `StateFlow<AppSettings>`，只解析一次；对安全字段保持现有加密边界，不把明文写入不安全持久缓存。
- 将解析管线移到合适的后台上下文（Keystore / SharedPreferences 等阻塞工作优先考虑 IO dispatcher，纯 JSON 转换再按测量决定是否拆到 Default），并验证 DataStore/Keystore 线程安全与异常语义。
- 为根节点提供只含主题相关字段的投影流（例如 `map { ThemeState(...) }.distinctUntilChanged()`），`AppRoot` 不再收集完整草稿状态，也不再额外直接 collect 原始设置。
- Settings ViewModel 的草稿同步应复用共享的已解析流，避免 `storedSettings` 与直接 repository collect 双轨解析。

### 7. 已确认但需要量化的次级热点

- `RoleplayDialoguePanel.kt:122` 每次组合都执行 `messages.filter { ... }` 创建新的 `storyMessages`；`RoleplayOnlinePhoneContent.kt:353-359` 同样每次组合全量过滤 `visibleMessages`。RP 流式帧让这两个 O(n) 过滤路径随帧重复，即使最终只有最后一个 item 内容变化。应在 ViewModel / mapper 层提供按模式投影，或以稳定输入配合 `remember` / `derivedStateOf`，并以基准确认收益。
- `RoleplayReadingMode.kt:247-249,290-292,363-364` 已缓存段落切分，但 `RoleplayReadingMode.kt:264-277,299-312,365-378` 每次组合仍直接调用 `buildQuotedDialogueAnnotatedString` / `buildCharacterDialogueAnnotatedString`，没有像 `RoleplayLongformText.kt:133-140` 那样缓存 `AnnotatedString`。阅读页不是流式主路径，但长文、导航过渡或消息状态变化时会重复正则扫描与字符串构建。
- `ContextLogScreen.kt:93-109` 在 Activity Result 主线程回调中同步执行 Gson 序列化和文件写入。
- `ChatScreenLaunchers.kt:131-172` 同步构建长会话 Markdown 并写入文件；`ChatScreen.kt:387-395` 分享前也同步构建完整 Markdown。
- `ChatUtilitySheets.kt:329-366` 导出过程遍历全部消息、parts、reasoning 和图片链接，长会话可能产生明显主线程暂停和峰值分配。
- `AssistantExtensionsScreen.kt:54-68,88-93` 每次组合重复进行多轮世界书筛选与排序。
- `MemoryManagementScreen.kt:179-209` 每次组合同时计算核心、场景、摘要、全部和时间线多套列表，即使当前只展示一个 section。
- `RoleplayNavGraph.kt:514-527,688-701` 的设置页 / 阅读页导航动画持续 240–400ms。动画本身不是缺陷，但会放大重页面首次组合与数据转换的掉帧，需要在真实导航场景下测量。
- `RoleplaySystemBars.kt:89-100,108-117,125-134` 三个 padding 扩展只按参数选择已有 Modifier，却使用 `Modifier.composed`；现有 lint 已在 `app/build/reports/lint-results-debug.txt:133-141` 报告 `Unnecessary use of Modifier.composed`。这是低单次成本但可重复的额外组合组 / 分配，建议改为普通 Modifier 链后再用 lint 验证。

这些路径应先用主线程 Trace、分配采样和长数据 fixture 量化，再决定是否搬到 `Dispatchers.Default/IO`、使用 `derivedStateOf` / `remember`，或只计算当前可见 section。

### 8. 普通聊天链路已排除的误报

- `ChatViewModel.kt:117-127,164-167` 已把流式内容拆成独立 `ChatStreamingState`。
- `ChatConversationPane.kt:294-345` 使用稳定消息 ID 和 `contentType`，并且只向流式气泡传递高频状态。
- `MessageBubbleState.kt:109-118` 为流式气泡启用快速纯文本模式。
- `MessageMarkdownRenderer.kt:212-220` 的快速路径直接使用 `Text`，不会在每个流式帧解析 Markdown。
- `MessageBubbleLayout.kt:360-364` 流式期间不启用 `animateContentSize`。
- `MessageBubbleAttachments.kt:226-228,307-315` 使用 `remember` 缓存图片模型，实际解码交给 Coil；UI 包内未发现直接使用 `BitmapFactory` 或 `ImageDecoder` 解码。
- 已检查的 ViewModel 状态收集均使用生命周期感知版本，未发现裸 `collectAsState()`。
- `ImmersiveGlass.kt:300-320` 仅在 `LocalImmersiveHazeState` 非空时启用玻璃模糊，而当前 UI 搜索未发现 provider；`ImmersiveGlass.kt:1043` 默认值为 `null`。在证明运行时注入 haze state 前，不应把玻璃模糊列为当前掉帧原因。
- RP 隐藏模式不会在稳态同时组合：`RoleplayScreen.kt:421-497` 以互斥 `if/else` 只组合在线手机或线下场景；阅读模式是独立导航目的地（`RoleplayNavGraph.kt:689-724`）。导航转场期间 outgoing / incoming destination 可能短暂共存，但这应作为过渡基准测量，不能描述成三个模式长期同时重组。

### 9. 测试与性能基准缺口

- `StartupBenchmarks.kt:24-53` 只配置 `StartupTimingMetric`，等待结果没有断言，可能在目标页面未就绪时仍得到“成功”运行。
- `BaselineProfileGenerator.kt:20-39` 的等待和点击都允许静默失败，生成路径可能没有真正覆盖目标交互。
- `baselineprofile/src` 当前只包含上述启动基准和 Profile 生成器，没有 `FrameTimingMetric` 长列表或流式场景。
- `app/src/main/generated/baselineProfiles` 当前为空，尚无可审查的已生成规则。
- `app/build.gradle:137-144` 已启用 Compose Compiler metrics / reports，可直接使用生成结果检查 unstable 与 skippable 情况。

现有 Compose Compiler 报告状态不能直接代表当前主源码：

- `app/build/compose_reports/app-classes.txt` 当前只含 `RoleplayViewModelTest`，`app-composables.txt` 为空，时间为 2026-08-07；共享目标目录已被最近一次 debugUnitTest / 增量编译覆盖。
- `app/build/compose_reports/debug/app-module.json` 只统计 13 个 composable，也不是完整应用快照。
- `app/build/compose_reports_baseline` 保存了 2026-04-18 的历史全量快照。它显示 `RoleplayScreen`、`RoleplaySceneContent`、`RoleplayDialoguePanel`、`RoleplayOnlinePhoneContent`、`RoleplayReadingMode` 均为 restartable + skippable（`app-composables.txt:675-708,1660-1747,1831-1864`），`RoleplayMessageUiModel` 与 `RoleplayScreenCallbacks` 被判定 stable（`app-classes.txt:1926-1947,2785-2834`），但 `AppSettings`、`RoleplayUiState` 及 `List<RoleplayMessageUiModel>` 参数为 unstable（`app-classes.txt:1185-1235,3299-3335`；`app-composables.txt:1831-1864`）。
- 历史报告还显示 Strong Skipping 已启用，但由于快照早于当前代码约四个月，只能说明检查方向，不能作为当前稳定性验收结果。

因此，本次不能把“Composable 不可跳过”列为已确认根因。更准确的结论是：当前源码持续创建新的完整 messages 列表和完整 UI state，而历史报告把这些列表 / 设置参数视为 unstable；必须重新生成指定 debug 主变体的全量报告后验证实际跳过行为。建议为不同 variant 配置独立目录，或明确最后执行 `:app:compileDebugKotlin` 并立即归档报告，避免测试编译覆盖。

建议新增基准：

1. 普通聊天与 RP 各准备 100 条固定 fixture，分别执行首屏进入、快速上滚、快速下滚和回到底部，记录 `FrameTimingMetric`。
2. RP 流式使用离线 fake stream，以约 48ms 固定发布帧，避免网络抖动污染结果；Perfetto 中检查 `RoleplayMessageUiMapper.mapMessages`、重组次数、滚动协程取消 / 重启和主线程帧时间。
3. 为设置页和 RP 阅读页增加导航动画场景，测量 240–400ms 过渡期间的 `frameOverrunMs`。
4. 测量前后保存 Compose Compiler stability / skippability 报告，对比 `RoleplayScreen`、回调参数与消息模型。
5. 基准必须对等待条件和目标控件存在性做断言，避免静默跳过步骤。
6. 设置页 / 聊天 / RP 首次进入各采集一次 Perfetto，在 `AppSettingsStore` map、`SecureValueStore.decrypt` 与 Gson decode 周围增加 trace section，统计单次解析耗时、同一 DataStore emission 的重复次数和 Main 线程占比。

### 10. 依赖与外部参考

项目中识别到的相关版本：

- Compose BOM `2026.02.01`
- Kotlin / Compose Compiler `2.2.10`
- Navigation Compose `2.9.7`
- Markdown Renderer `0.39.2`
- Coil `3.1.0`
- Macrobenchmark / Baseline Profile `1.5.0-alpha03`

外部参考（2026-08-11 核对方向）：

- Android Developers, *Jetpack Compose performance best practices*：稳定 key、推迟状态读取、避免不必要重组与高频计算。
- Android Developers, *Use a baseline profile* 与 *Macrobenchmark overview*：使用真实交互路径生成 Profile，并以 `FrameTimingMetric` 衡量帧性能。
- Android Developers, *Diagnose stability issues* / *Fix stability issues*：使用 Compose Compiler 报告识别 unstable 参数和不可跳过组合。
- Android Developers, *Lists and grids*：为 Lazy 列表提供稳定且唯一的 key，并使用 `contentType` 改善异构 item 复用。
- Perfetto / Android system tracing 文档：用系统 Trace 验证主线程工作、协程调度和帧超时，避免仅凭源码估算绝对耗时。
- Kotlin Coroutines Flow 文档，*Context preservation*、`flowOn`、`shareIn` / `stateIn`：下游转换默认继承收集上下文；共享冷流可避免每个 collector 重复执行上游转换。

### Files found

- `app/src/main/java/com/example/myapplication/viewmodel/ChatStreamingSupport.kt` — RP 与聊天共用的流式帧发布窗口。
- `app/src/main/java/com/example/myapplication/viewmodel/RoleplaySendActionSupport.kt` — RP 发送任务 scope 与流式执行入口。
- `app/src/main/java/com/example/myapplication/viewmodel/RoleplayRoundTripExecutor.kt` — RP 流式帧解析和状态更新入口。
- `app/src/main/java/com/example/myapplication/viewmodel/RoleplayObservationSupport.kt` — 流式状态观察及全消息 UI 映射。
- `app/src/main/java/com/example/myapplication/roleplay/RoleplayMessageUiMapper.kt` — RP 消息映射和 key 稳定性相关注释。
- `app/src/main/java/com/example/myapplication/ui/component/roleplay/RoleplayDialoguePanel.kt` — RP 对话列表及自动贴底。
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayOnlinePhoneContent.kt` — 在线手机列表及未节流动画滚动。
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayReadingMode.kt` — RP 阅读模式列表。
- `app/src/main/java/com/example/myapplication/ui/component/roleplay/RoleplayDialoguePanelParts.kt` — 流式光标与正文渲染。
- `app/src/main/java/com/example/myapplication/ui/component/roleplay/RoleplayActionMessageCards.kt` — 语音卡无限动画与播放器准备。
- `app/src/main/java/com/example/myapplication/ui/navigation/RoleplayNavGraph.kt` — 完整 RP 状态收集、屏幕参数和导航动画。
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayScreen.kt` — 在线 / 线下模式互斥分支及 RP 页面参数。
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayScreenContent.kt` — 线下场景的消息派生状态和对话面板入口。
- `app/src/main/java/com/example/myapplication/ui/AppRoot.kt` — 根组合的设置状态订阅。
- `app/src/main/java/com/example/myapplication/viewmodel/SettingsViewModel.kt` — 完整设置 UI 状态与 repository 订阅。
- `app/src/main/java/com/example/myapplication/data/local/AppSettingsStore.kt` — 未共享的设置冷流及完整解析管线。
- `app/src/main/java/com/example/myapplication/data/local/SecureValueStore.kt` — 同步 Keystore / AES-GCM 解密和安全字段 JSON 读取。
- `app/src/main/java/com/example/myapplication/data/repository/ai/AiSettingsRepository.kt` — 当前直接透传冷流，未建立共享缓存。
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplaySystemBars.kt` — lint 标记的三处不必要 `Modifier.composed`。
- `app/src/main/java/com/example/myapplication/ui/screen/chat/ChatScreenBehavior.kt` — 普通聊天滚动节流与用户离底保护对照。
- `app/src/main/java/com/example/myapplication/ui/screen/chat/ChatConversationPane.kt` — 普通聊天稳定 key、`contentType` 和流式状态隔离对照。
- `app/src/main/java/com/example/myapplication/ui/component/MessageMarkdownRenderer.kt` — 流式纯文本快速路径。
- `baselineprofile/src/main/java/com/example/myapplication/baselineprofile/StartupBenchmarks.kt` — 仅覆盖启动时序的宏基准。
- `baselineprofile/src/main/java/com/example/myapplication/baselineprofile/BaselineProfileGenerator.kt` — 当前 Profile 生成交互路径。
- `app/build.gradle` — Compose Compiler metrics / reports 配置及依赖版本入口。
- `app/build/compose_reports_baseline/app-composables.txt` — 2026-04-18 的历史 Compose skippability 快照，不代表当前源码。
- `app/build/compose_reports_baseline/app-classes.txt` — 2026-04-18 的历史 stability 快照，不代表当前源码。

### Related specs

- `.trellis/workflow.md` — 研究、实现和质量门禁流程。
- `.trellis/spec/backend/ui-state-guidelines.md` — UI state、Flow、生命周期感知收集和状态所有权约定。
- `.trellis/spec/backend/quality-guidelines.md` — 测试、静态检查与验证要求。
- `.trellis/spec/guides/cross-layer-thinking-guide.md` — ViewModel、领域层与 UI 的数据流边界。
- `.trellis/spec/guides/code-reuse-thinking-guide.md` — 共用滚动策略、共享设置流和重复转换的复用判断。
- `.trellis/tasks/08-07-app-performance-optimization/prd.md` — 本轮性能优化目标与范围。
- `.trellis/tasks/08-07-app-performance-optimization/design.md` — 性能优化设计方案。
- `.trellis/tasks/08-07-app-performance-optimization/implement.md` — 当前任务实施拆分与约束。

## Caveats / Not Found

- 本报告目前是源码静态扫描结论；尚未获得设备上的 Perfetto、帧时间、重组计数或内存分配数据，因此不宣称具体毫秒收益。
- 当前 `app/build/compose_reports` 已被测试 / 增量编译覆盖，无法据此验收当前主源码；`compose_reports_baseline` 是 2026-04-18 历史快照，所有 stability / skippability 引用都带有这一时效限制。
- Kotlin Flow 上下文分析表明设置 map 可能在 Main collector 上执行，但 Android 设备、Keystore 实现和首次 / 热缓存状态会影响绝对耗时，必须用 Trace 区分冷启动与后续 emission。
- 现有 `RoleplayOnlinePhoneMessageListKeyTest.kt:12-27` 只证明不同全局 index 产生不同 key；`RoleplayMessageUiMapperTest.kt:1150-1183` 只覆盖流式消息复用 `createdAt`。现有 RP 删除 UI 测试验证操作流程，不验证插入 / 删除前后 item identity，因此不能作为稳定 key 回归保障。
- RP 稳态模式分支已确认互斥；只保留导航动画期间 outgoing / incoming destination 短暂共存这一待测 caveat。
- RP 长文主渲染器已缓存 `AnnotatedString`，阅读模式的多处分支没有缓存；在线手机复用 `RoleplayMessageItem`，其主要新增开销是每次组合的全表过滤和流式滚动动画。
- 未发现裸 `collectAsState()`、UI 线程直接图片解码或当前有效的 haze provider；除非后续运行证据推翻，不应把这些项列为现存缺陷。
