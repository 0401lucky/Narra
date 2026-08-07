# 群聊多角色回复可靠性修复

## Goal

修复 2.2.5-dev 群聊中部分角色回复失败后状态不一致、已完成回复被后处理覆盖、
Loading 残留及后续角色无法稳定继续的问题，使每一轮回复都能结束于可解释、可恢复的
状态。

## Confirmed Facts

- 当前群聊已是串行回合，不是并发请求：
  `RoleplaySendActionSupport.kt:1088-1225` 逐个执行 turn，
  `:1146-1200` 为单个角色提供最多两次尝试，并在部分失败时继续后续角色。
- 现有 `RoleplayViewModelTest.kt:1619-1724` 已覆盖“首请求 500、首角色重试成功、
  下一角色继续成功”，但没有覆盖后续角色最终失败、RoundTrip 外异常和成功后的
  后处理异常。
- `ConversationAssistantRoundTripRunner.kt:84-113` 会先持久化完成消息；随后
  `RoleplayRoundTripExecutor.kt:532-623` 执行协议、脚本和观察等后处理，而
  `:675-701` 的通用异常路径仍可能把同一消息改写为 ERROR。
- 群聊发送协程的 `finally` 只调用 Job 清理；
  `RoleplayViewModel.kt:1900-1904` 不负责恢复 `UiState.isSending`。如果成员读取、
  用户消息持久化等 RoundTrip 外步骤抛错，整体发送状态缺少统一兜底。
- `RoleplayObservationSupport.kt:196-211` 在发送期间忽略 Room 消息帧，因此发送状态
  若未恢复，内存与数据库同步也会持续受阻。

更完整的仓库证据见 `research/repository-evidence.md`。

## Requirements

- **R1 串行与有界重试**：保留现有逐角色串行执行；单角色重试必须有上限，不能
  扩大为所有错误的无差别重试或并行发送。
- **R2 部分失败隔离**：一个角色最终失败时，已成功角色保持可见，后续角色继续；
  每个失败回合最多保留一条 ERROR 消息，不得留下重复 Loading。
- **R3 完成提交边界**：模型正文已经成功持久化后，协议、脚本、观察或其他附加
  后处理失败不得把正文覆盖成“发送失败”；副作用失败应独立诊断并给出非阻断反馈。
- **R4 总流程状态兜底**：成员读取、用户消息持久化、Planner 或 RoundTrip 外步骤
  抛错时，当前有效 run 必须恢复 `isSending`、`streamingContent` 和消息状态。
- **R5 取消与会话隔离**：取消、切换场景或切换会话后，旧 run 不得更新新会话；
  `CancellationException` 保持正常取消语义，数据库和内存不得残留 Loading。
- **R6 脱敏诊断**：记录 turn index、participant/assistant id、attempt、失败阶段和
  安全错误类别；禁止记录聊天正文、提示词、完整响应、密钥或 Base64。
- **R7 兼容边界**：不修改数据库 schema、自然导演选角规则、Provider 配置或全局
  SSE 完成语义；只有测试证实需要时才调整群聊局部重试节奏。

## Acceptance Criteria

- [ ] **AC1 正常多角色**：两名及以上角色按计划顺序完成，speaker 元数据正确，
      最终无重复或残留 Loading，`isSending=false`。
- [ ] **AC2 瞬时失败恢复**：首角色遇到一次瞬时 5xx 后重试成功，后续角色继续成功；
      `RoleplayViewModelTest.kt:1619-1724` 继续通过。
- [ ] **AC3 部分失败继续**：首角色成功、后续角色连续两次失败，或三角色中间角色
      最终失败时，其他角色结果保留并继续；失败角色仅一条 ERROR，UI 显示脱敏的
      部分失败 Notice。
- [ ] **AC4 全部失败收口**：所有计划回合最终失败时，UI 显示安全 Error，
      `isSending=false`，数据库与内存均无 Loading。
- [ ] **AC5 后处理隔离**：完成消息持久化后注入脚本、观察或协议后处理异常，正文仍为
      COMPLETED；副作用失败有脱敏日志或 Notice，且不会触发整轮重新生成。
- [ ] **AC6 异常、取消与切换**：成员读取/用户消息写入异常、发送取消以及会话/场景
      切换均能恢复状态；旧 run 不写入新会话。
- [ ] **AC7 诊断可用且安全**：每个失败回合可从日志确定角色、尝试次数和失败阶段，
      日志不包含聊天正文、提示词、密钥、完整响应或 Base64。
- [ ] **AC8 质量门禁**：群聊相关窄测、`testDebugUnitTest`、`lintDebug` 和
      `assembleDebug` 通过；单元测试命令由执行环境设置 60 秒上限，超时如实报告。

## Out of Scope

- 改变自然导演选角规则、角色提示词产品策略或群聊 UI 交互设计。
- 并行发送多个角色请求，或新增 Provider/全局网络重试拦截器。
- 修改 Room schema、消息字段或迁移。
- 强制所有兼容 Provider 必须发送 SSE `[DONE]`；流完整性收紧需单独评估兼容风险。
