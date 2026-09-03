# 2.2.6-dev RP Compose 与流式帧性能：技术设计

## 1. 边界、依赖与实施顺序

本任务只拥有 RP UI identity、RP 专属离线流式场景、两种对话模式的自动贴底、流式光标、
语音卡播放 UI，以及经 Trace 触发后的 RP streaming 状态拆分。

```text
基准子任务的 baseline-only fixture / ready 合约
  → RP 100 条消息 + 48 ms 离线重放（优化前）
  → FrameTiming + RP Trace
  → 稳定 uiItemId / contentType
  → 贴底、光标、语音卡修复
  → 同环境复测
  → Trace 仍指向整表映射？
      ├── 否：记录跳过，不拆状态
      └── 是：拆 RP streaming 状态，再复测并验证 ≥15% P95 改善
```

- 通用 fixture、合规准备、后台污染隔离、ready 语义和结果归档由基准 / 可观测性子任务
  提供；本任务通过其公开测试合约增加 RP 场景，不复制命令通道或改写通用基准类。
- 若通用基准合约尚未合入，可先完成纯 identity 测试设计，但性能改动开始前必须先取得
  优化前 F3 数据。缺设备时不得把本任务标记为性能验收完成。
- 三处列表可并行接入同一 identity 契约；状态拆分必须串行等待第一次复测与 Trace 判定。

## 2. 稳定 UI identity 与 Lazy content type

### 2.1 identity 所有权

`RoleplayMessageUiModel` 新增必填 `uiItemId`，由 `RoleplayMessageUiMapper` 统一生成：

- `sourceMessageId` 保留领域关联、删除、重试和引用定位语义，不能直接假设它等于气泡 ID。
- 普通文本 / 长文分段采用“源消息 ID + 消息内稳定分段标识”。分段标识可由固定类型与
  该源消息内部的局部序号组成，但不得使用整表 index 或正文哈希。
- action / special 气泡优先使用持久 `actionId`；缺失时使用 mapper 内确定性的局部分段标识。
- 流式预览绑定当前 loading assistant message / turn，并使用固定 `streaming-preview` 后缀；
  chunk 内容增长、`nowProvider()` 变化都不能改变它。
- mapper 输出前通过测试验证列表内唯一性；不使用随机 UUID、当前时间或运行时对象 identity
  掩盖重复分段缺陷。

三处列表继续可以使用 `itemsIndexed` 获取前一条消息或时间戳信息，但：

```kotlin
key = { _, message -> message.uiItemId }
contentType = { _, message -> roleplayLazyContentType(message) }
```

`roleplayLazyContentType` 是纯函数，只返回有限稳定类别。优先按现有
`RoleplayContentType` 区分；ACTION 内确有不同组合结构时，可按固定 action subtype 细分为
语音 / 图片 / 通用动作，但不得包含正文、播放状态、loading 或列表位置。

### 2.2 兼容性

- `uiItemId` 只影响 Compose identity，不写入 Room、不进入网络协议、不改变复制 / 引用 ID。
- `animateItem`、`remember` 与播放器局部状态将随真正气泡 identity 保留；删除后不会误复用到
  相邻消息。
- 阅读模式只接入 key / `contentType`，不增加贴底或流式副作用。

## 3. RP 离线流式基准与 Trace 合约

### 3.1 固定场景

- 使用 baseline 渠道和显式 benchmark 指令，幂等准备 100 条固定 RP 消息、固定场景、固定
  会话和底部滚动位置；不包含真实密钥、附件或远端请求。
- 在目标应用内按固定 chunk 列表、约 48 ms 间隔重放一条 assistant 回复。重放入口只在
  baseline benchmark 会话可用，dev / release 产品行为不改变。
- fixture 准备、导航和初始滚动在 `measureBlock` 外完成；测量区间从触发重放开始，到稳定
  `rp_stream_replay_complete` ready 标记出现为止。超时、缺控件或状态不匹配直接失败。
- `FrameTimingMetric` 至少 5 次；保存 P50 / P90 / P95、`frameOverrunMs`、原始 JSON、
  Perfetto、设备 / 构建 / 刷新率 / 温控信息。

### 3.2 Trace section

RP 专属 section 至少覆盖：

- streaming 帧清洗与 `RoleplayStateSupport.applyStreamingContent` 发布；
- `RoleplayMessageUiMapper.mapMessages`；
- mapped messages 回写完整 `RoleplayUiState`；
- 自动贴底信号消费与实际滚动调用。

section 名称固定且不包含聊天正文、Prompt、角色名或消息 ID。Trace 判定记录需列出调用次数、
总 / 单次耗时、与超预算帧的重叠，以及稳定 identity / 滚动 / 动画修复后的剩余占比。

## 4. 自动贴底

普通对话和在线手机模式共用一个 feature 内部策略，阅读模式不接入：

1. 初次进入会话或会话 identity 变化时，只执行一次定位到底部。
2. 通过 `snapshotFlow` 或等价的合并信号观察末项 `uiItemId`、streaming 状态和内容版本，
   在单一长生命周期 `LaunchedEffect(listState, conversationIdentity)` 中消费。
3. 流式跟随信号采用约 64 ms 的合并 / 节流窗口；每个窗口先重新判断用户是否仍在底部附近。
4. 流式帧使用短距离 `scrollBy` / 必要时 `scrollToItem`，不反复启动
   `animateScrollToItem`；新完成消息可在仍贴底时只动画一次。
5. 用户手势离开底部后关闭自动跟随；手动跳到底部或再次滚回阈值内后恢复。

优先复用普通聊天 `ChatScreenBehavior` 已验证的限步滚动与用户离底保护。只有生命周期和参数
确实无法共享时，才保留 RP feature 内部实现；不得创建应用级无语义的通用滚动工具。

## 5. 流式光标

- 正文 `AnnotatedString` 使用 `remember(content)` 或纯缓存函数构建，只在正文变化时更新。
- 光标作为独立轻量节点、末尾 overlay 或 draw / graphics layer；alpha 在绘制 / 图层阶段读取，
  不进入正文构建。
- 布局必须保持现有字号、行高、颜色和末尾光标视觉；语义树继续暴露完整正文，不能把光标
  读成额外业务字符。
- Compose 测试覆盖空正文、长正文增长、光标存在和正文语义；性能 Trace 确认光标帧不再执行
  完整正文构建。

## 6. 语音卡动画与异步播放

将 `RoleplayActionMessageCards.kt` 内的播放器生命周期收敛为 feature 专属、可测试的小型
控制器 / 工厂，不扩展成全局音频框架：

```text
Idle --点击--> Preparing --onPrepared--> Playing
  ↑                │                       │
  ├── 第二次点击 / dispose / key 变化 ─────┤
  └── onCompletion / onError / stop ───────┘
```

- listener 在 `prepareAsync()` 前注册；只有 `onPrepared` 可以调用 `start()` 并进入 Playing。
- Preparing 中重复点击按取消处理：释放当前实例并回到 Idle，不再创建第二个播放器。
- completion、error、消息 key 变化和 `DisposableEffect.onDispose` 共用幂等 release；释放后回调
  不得再写当前卡片状态。
- 播放状态由当前控制器状态派生，不使用可在进程重建后残留的 `rememberSaveable(isPlaying)`
  伪造正在播放。
- 12 段波形的静态 seed 继续缓存；只有 Playing 分支创建 / 读取无限 transition，Idle、
  Preparing 和 Error 使用常量高度。
- 错误继续通过现有脱敏中文文案展示；不修改语音生成、TTS 网络、Base64 或音频文件协议。

## 7. streaming 状态拆分闸门

第一次复测后，只有同时满足以下条件才进入拆分：

- Trace 在超预算帧中反复出现完整 `mapMessages` / mapped messages UiState 回写；
- identity、贴底、光标和语音卡修复后，该链路仍是可归因的主要应用侧热点；
- 判定依据和拆分前 F3 结果已写入任务研究记录，可供检查代理复核。

触发后采用普通聊天同类模式，但保持 RP 唯一状态所有权：

- 新建窄 `RoleplayStreamingState`，仅包含流式预览需要的高频字段和稳定 preview identity。
- `RoleplayUiState.messages` 只在原始消息、场景、assistant 或设置等低频输入变化时重新映射。
- 导航 / 页面入口生命周期感知收集 streaming 状态，只把它传到末尾流式气泡；展示层按
  `uiItemId` 合并，不复制持久消息列表的领域所有权。
- 完成、失败、取消和会话切换由 ViewModel / Support 一处原子清理，旧会话流式帧不得写入
  新页面。

若未触发闸门，保留现有状态形状，只交付已证实的 identity、滚动、光标和语音卡修复。

## 8. 测试、风险与回滚

- identity 使用 JVM 纯逻辑测试覆盖正文增长、前插、删除、多分段、actionId 与流式 preview。
- 自动贴底将“是否跟随 / 信号合并”抽成纯决策测试，Compose 设备测试覆盖用户离底和恢复。
- 播放器通过可替换 factory 验证 `prepareAsync()`、重复点击、回调顺序与单次释放。
- benchmark 与产品优化分开提交；identity、贴底 / 光标、语音卡和条件式状态拆分各自形成
  独立回滚点。
- 若 `uiItemId` 暴露 mapper 分段不唯一，修复 mapper 分段契约，不回退到正文哈希 / index。
- 若设备结果无改善，回退对应性能实现，保留正确 identity、有效基准和回归测试。

