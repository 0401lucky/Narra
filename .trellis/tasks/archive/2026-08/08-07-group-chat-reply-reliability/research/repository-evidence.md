# 群聊多角色回复可靠性：仓库证据

## 研究范围

本记录仅汇总实现该子任务所需的现有仓库事实。结论来自当前源码和测试，不把现场尚未
复现的推测写成已证实根因。

## 1. 当前执行链

1. `RoleplayViewModel.kt:1585-1600` 将发送和指定群成员发言委托给
   `RoleplaySendActionSupport`，并保存返回的 sending Job。
2. `RoleplaySendActionSupport.kt:671-876` 创建 sending run、进入发送状态、准备会话和
   用户输入；`:792-806` 对群聊调用 `executeGroupRoleplaySend`。
3. `RoleplaySendActionSupport.kt:976-1085` 持久化用户消息、加载群成员并生成 turns：
   - NATURAL 使用 `RoleplayGroupDirector.kt:58-99`；导演调用失败会回退本地 Planner。
   - ALL_MEMBERS / MANUAL_ONLY 使用
     `RoleplaySendActionSupport.kt:1060-1067` 的本地 Planner。
4. `RoleplaySendActionSupport.kt:1088-1225` 串行执行 turns：
   - `:1132-1143` 为当前角色创建带 speaker 元数据的 Loading。
   - `:1146-1200` 最多尝试两次；上限常量位于 `:1318`。
   - `:1177-1181` 首次 Append、重试 Upsert 同一消息 id。
   - `:1201-1225` 汇总失败角色；存在成功回合时使用 Notice 并继续。
5. `RoleplayRoundTripExecutor.kt:110-167` 先持久化 Loading，再组装本轮上下文。
6. `RoleplayRoundTripExecutor.kt:364-518` 通过
   `ConversationAssistantRoundTripRunner` 执行流式生成和消息解析。
7. `ConversationAssistantRoundTripRunner.kt:84-113` 构造完成消息并通过 `:94-98`
   Upsert；失败/取消结果在 `:114-147` 统一返回。
8. `RoleplayRoundTripExecutor.kt:532-623` 在完成持久化后继续执行协议、脚本、观察、
   摘要、语音和照片等后续处理。

结论：当前真实结构是顺序回合，不存在“多个角色同时请求导致并发冲突”的源码证据。

## 2. 已有可靠性保护

- 单回合最多两次尝试：`RoleplaySendActionSupport.kt:1146-1200,1318`。
- 重试复用 Loading id，避免同一回合新增多条占位：
  `RoleplaySendActionSupport.kt:1151-1181`。
- 后续回合只把 COMPLETED 且可发送的消息送入模型：
  `RoleplaySendActionSupport.kt:1258-1291`。
- run 和会话门禁分布于 `RoleplaySendActionSupport.kt:1115-1117,1146-1149,1213`，
  防止失活 run 更新当前 UI。
- Room 观察在发送期间不覆盖乐观内存状态：
  `RoleplayObservationSupport.kt:196-211`。
- `ConversationRepository.kt:90-129` 通过 Store 同步更新会话元数据和消息；真实 Room
  实现使用 `RoomConversationStore.kt:109-113` 和
  `ConversationDao.kt:56-64` 的事务性 Upsert。
- OpenAI/Anthropic 流在取消时取消 Call 并关闭响应：
  `AiGateway.kt:1359-1450,1953-2014`。

## 3. 已证实的结构性缺口

### 3.1 已完成正文可能被后处理异常覆盖

- 完成消息在 `ConversationAssistantRoundTripRunner.kt:84-113` 已持久化。
- 随后的 `RoleplayRoundTripExecutor.kt:532-623` 仍可能抛出普通异常。
- 外层 `RoleplayRoundTripExecutor.kt:675-701` 会重新构造 ERROR 消息，并在 `:701`
  再次 Upsert。

因此代码确实存在“模型正文已经成功，但后处理失败后消息显示发送失败”的路径。现场是否
命中该路径仍需通过新增测试和阶段日志确认。

### 3.2 RoundTrip 外异常没有 UI 终态兜底

- 主发送协程 `RoleplaySendActionSupport.kt:707-876` 只有 `try/finally`，finally 调用
  `onSendingFinished`。
- `RoleplayViewModel.kt:1900-1904` 的 `finishSendingRun` 仅把 `sendingJob` 置空，
  不恢复 `isSending` 或 `streamingContent`。
- 成员读取和用户消息持久化发生在 `RoleplaySendActionSupport.kt:976-1037`，属于
  RoundTrip 通用异常处理之外。
- `RoleplayObservationSupport.kt:210` 在 `isSending=true` 时忽略 Room 帧。

因此这些步骤异常时存在发送状态与消息同步长期不恢复的结构性风险。

## 4. 已知但本任务暂不修改的网关行为

- OpenAI SSE 在读取循环自然 EOF 后无条件发送 Completed：
  `AiGateway.kt:1421-1437`。
- Anthropic SSE 同样在自然 EOF 后发送 Completed：
  `AiGateway.kt:1982-2001`。
- `ChatStreamingSupport.kt:53-75` 即使没有观察到显式 Completed，只要 Flow 正常结束，
  也会完成 UI pump。

这说明静默断流可能被当作正常流结束，但兼容代理可能本就不发送 `[DONE]`。该行为是
后续排查线索，不足以在本任务中直接实施全局严格终止标记校验。

## 5. 当前测试证据

- `RoleplayViewModelTest.kt:1396`：群聊用户消息保留引用信息。
- `RoleplayViewModelTest.kt:1619-1724`：首请求 500，首角色重试成功，第二角色继续成功；
  `:1707-1709` 断言发送结束且共三次请求，`:1711-1723` 断言两条角色回复 COMPLETED。
- `RoleplayGroupReplyPlannerTest.kt:17-73`：自然、禁言、全员和手动选角规则。
- `RoleplayGroupReplyPlannerTest.kt:77-163`：群聊导演提示、文本/语音/照片解析和流式预览。
- `AiGatewayTest.kt:1622-2596`：主要流式协议、HTTP 错误和 SSE 错误帧回归。

当前没有直接覆盖：

- 第一角色成功、第二角色最终失败。
- 三角色中间失败后继续。
- 所有角色最终失败。
- 完成消息提交后脚本/观察/协议后处理异常。
- 群成员读取或用户消息持久化失败。
- 取消发生在两个 turn 之间、场景切换与失败并发。
- SSE 空 EOF 或不完整 JSON EOF 的群聊最终状态。

## 6. 实现约束与建议

- 优先修改 `RoleplayRoundTripExecutor.kt` 的生成提交边界和
  `RoleplaySendActionSupport.kt` 的群聊终态兜底。
- 复用现有 run id、会话检查、稳定 Loading id 和两次尝试常量，不另建并行队列。
- 后处理失败不能通过重新生成正文补偿；尤其是转账等副作用，重复生成可能造成重复动作。
- 使用 `AppLogger` 记录角色 id、turn、attempt、stage 和安全错误类别，不记录内容。
- 不修改数据库 schema，不在没有独立兼容证据时调整 AiGateway 全局 SSE 完成语义。

## 7. 相关项目规范

- `.trellis/spec/backend/ui-state-guidelines.md`
- `.trellis/spec/backend/network-guidelines.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
