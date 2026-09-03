# 2.2.6-dev RP Compose 与流式帧性能

## Goal

在 2.2.6-dev 中消除 RP 长列表与离线流式展示中已确认的 identity、滚动和常驻动画
放大问题，使长会话在正文增长、前插 / 删除消息、用户主动离底和语音卡播放时保持稳定、
可预测且可测量。性能结论必须来自固定离线重放和同环境 Trace，不以主观流畅度代替。

## Background

- `ChatStreamingSupport.kt:14-49` 约每 48 ms 发布一帧；
  `RoleplayRoundTripExecutor.kt:1093-1135` 更新完整 `RoleplayUiState` 后，
  `RoleplayObservationSupport.kt:225-263` 又因流式正文变化重新映射全部消息并再次发布状态。
- `RoleplayDialoguePanel.kt:242-246,376-391`、
  `RoleplayOnlinePhoneContent.kt:572-576,800-815` 和
  `RoleplayReadingMode.kt:191-195` 的 Lazy 列表 key 包含正文哈希或全局 index，正文增长、
  前插和删除会改变既有 item identity；三处均未提供 Lazy `contentType`。
- `RoleplayDialoguePanel.kt:191-203` 以正文长度重启贴底 effect；
  `RoleplayOnlinePhoneContent.kt:403-410` 每次正文增长都重新启动长滚动动画。
- `RoleplayDialoguePanelParts.kt:402-424` 的光标 alpha 每帧触发完整
  `AnnotatedString` 重建；`RoleplayActionMessageCards.kt:406-481` 在静止语音卡上仍创建
  12 个无限动画，并同步调用 `MediaPlayer.prepare()`。
- 普通聊天已有稳定消息 ID、Lazy `contentType`、独立流式状态和节流贴底策略，可作为
  仓库内对照，但 RP streaming 状态是否拆分仍缺少当前设备 Trace 证据。
- 本子任务复用基准 / 可观测性子任务提供的 baseline-only fixture、强 ready 和结果归档
  合约，只增加 RP 专属离线流式场景，不复制通用基准基础设施。

## Requirements

- **R1 证据顺序**：先取得固定 RP 离线流式优化前基准与 Trace，再复测稳定 identity、
  贴底、光标和语音卡改动；稳定 key 属于已确认正确性缺陷，可直接补测试并修复。只有 Trace
  仍证明整表映射 / 完整 UiState 发布是主要热点时，才允许拆分 RP streaming 状态。
- **R2 稳定唯一 identity**：`RoleplayMessageUiModel` 必须提供由 mapper 负责生成的稳定唯一
  `uiItemId`。同一底层消息拆成多个气泡时不得冲突；正文增长、在列表前部插入或删除其它
  消息时，既有气泡 identity 不得变化。identity 不得包含正文、随机值、当前时间或全局
  列表 index。
- **R3 Lazy 复用契约**：普通对话、在线手机和阅读模式三处列表均只使用 `uiItemId` 作为
  key，并提供有限、稳定的 Lazy `contentType`；类型不得随正文、播放进度或临时 UI 状态
  改变。
- **R4 离线流式基准**：使用 100 条固定 RP 消息和固定 chunk 内容，以约 48 ms 间隔离线
  重放，不调用真实 Provider 网络；状态准备在测量区间外完成，ready / replay 完成等待失败
  必须让测试失败。`FrameTimingMetric` 至少运行 5 次并保存 P50 / P90 / P95、
  `frameOverrunMs` 与原始 Perfetto。
- **R5 自动贴底**：离线对话与在线手机模式复用同一策略；仅当用户仍位于底部附近时跟随
  流式内容，使用单一长生命周期协程消费节流信号，不以正文长度反复取消 / 重启 effect 或
  长动画。用户主动离底后停止强制跟随，手动跳到底部后可恢复。
- **R6 光标文本**：流式正文的文本构建只依赖正文变化；光标 alpha 由独立轻量节点、绘制
  或图层处理，不得因光标动画帧重复复制完整正文，视觉文案和可访问文本保持一致。
- **R7 语音卡**：未播放语音卡不得创建或读取无限波形动画；播放态才启用动画。
  `MediaPlayer` 必须使用异步 prepare，并明确定义 preparing、playing、completion、error、
  重复点击、键变化和 Composable dispose 时的幂等释放与状态恢复。
- **R8 条件式 streaming 状态拆分**：若 Trace 触发拆分，高频 streaming 内容必须由窄状态
  所有者发布，持久消息映射不再随每个 chunk 执行；完成、失败、取消和会话切换仍由唯一
  ViewModel 状态所有者收口。若证据不成立，必须记录跳过结论且不得为“优化”而拆分状态。
- **R9 兼容与回归**：不得改变 RP 输出解析、消息顺序、气泡内容、时间戳、引用定位、删除、
  重试、流式取消、会话切换、手动跳转和语音文字兜底行为；普通聊天行为保持不变。
- **R10 可验证性与边界**：新增纯逻辑 / JVM 测试、必要的 Compose / 设备测试和宏基准；
  缺少物理设备时只能完成代码与静态门禁，不能宣称帧性能阈值已经通过。

## Acceptance Criteria

- [ ] **AC1（R2）**：测试证明同一流式气泡正文增长时 `uiItemId` 不变；在其前部插入或
      删除其它消息后 identity 不变；同一 source message 拆出的多个气泡全部唯一。
- [ ] **AC2（R3）**：三处 RP Lazy 列表均以 `uiItemId` 为唯一 key，并显式提供稳定
      `contentType`；代码中不再用正文哈希、当前时间或全局 index 组成 key。
- [ ] **AC3（R4）**：baseline-only 场景可幂等准备 100 条固定消息并完成 48 ms 离线
      chunk 重放；真实网络调用为零，任何 ready / replay 超时都会使宏基准失败。
- [ ] **AC4（R4、R10）**：优化前后在同一设备、构建、刷新率和温控条件下各运行至少
      5 次，保存 FrameTiming 原始结果与 Perfetto；F3 场景 P95 不得回退超过 5%。
- [ ] **AC5（R5）**：流式期间不会为每个正文长度变化重新启动长滚动动画；用户离开底部
      后滚动位置不被抢回，回到底部后恢复跟随，普通对话与在线手机行为一致。
- [ ] **AC6（R6）**：正文不变时，光标动画不会重建完整正文 `AnnotatedString`；正文增长、
      光标显示和可访问文本的 Compose 测试通过。
- [ ] **AC7（R7）**：静止语音卡不存在无限波形动画；异步 prepare 不阻塞 UI，覆盖准备中
      重复点击、完成、失败、切换消息和 dispose，且每条路径最多释放播放器一次。
- [ ] **AC8（R8）**：状态拆分前保存可审查的 Trace 判定记录。若未证明整表映射 / 完整
      UiState 发布仍是主要热点，最终差异不得包含 streaming 状态拆分。
- [ ] **AC9（R8）**：若 Trace 触发拆分，F3 对应 P95 相对拆分前改善不低于 15%，且流式
      顺序、闪烁、重复、完成、失败、取消和会话切换回归测试全部通过。
- [ ] **AC10（R9、R10）**：相关 JVM / Compose / 宏基准源码编译、`lintDebug`、
      `assembleBaseline` 和 `assembleDebug` 有真实结果；diff 不包含本任务 Out of Scope 内容。

## Out of Scope

- 全局设置冷流共享、AppRoot 主题窄投影、设置页或导航动画优化。
- TTS 网络响应、声音样本 Base64 / 大小门禁、语音生成协议或远端音频下载策略。
- 会话编辑 / 发送串行化、跨表事务、Room schema、Paging 或消息持久化架构重写。
- 普通聊天 streaming 架构、RP 组合阶段通用 `filter`、阅读模式普通长文缓存、
  `RoleplaySystemBars` 修整及其它未被本范围点名的 Compose 微优化。
- 构建或发布正式 release 包、修改正式签名或生产更新配置。

## Confirmed Decisions

- 本任务归属 **2.2.6-dev**。
- 三处 RP 列表共同采用 mapper 所有的 `uiItemId`，`sourceMessageId` 继续只承担领域关联。
- 阅读模式只纳入 identity / `contentType`，不增加自动贴底行为。
- 语音卡静止动画与 `MediaPlayer.prepareAsync()` 完整归本子任务，不与 TTS 网络 / Base64
  资源边界子任务交叉。
- streaming 状态拆分是 Trace 驱动的条件分支，不是默认交付数量指标。

