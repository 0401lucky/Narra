# 群聊多角色回复可靠性：技术设计

## 1. 设计边界

保持现有调用结构：导演/Planner 生成 turns，
`RoleplaySendActionSupport.executeGroupReplyTurns` 串行调用
`RoleplayRoundTripExecutor`。不引入并发队列、不修改导演选角和数据库 schema。

本次只收敛三个边界：

1. 单个角色的生成结果与后处理结果分离。
2. 群聊总流程无论在哪个阶段失败，都有唯一的终态恢复路径。
3. 每个回合具备可定位且脱敏的阶段日志。

源码依据统一记录在 `research/repository-evidence.md`。

## 2. 状态与提交边界

```text
用户消息持久化
  → 生成角色 Loading
  → 模型流式生成
  → 完成消息持久化（生成提交点）
  → 协议 / 脚本 / 观察等后处理（独立失败域）
  → 下一角色
  → 群聊总流程终态恢复
```

- 每个 turn 继续使用稳定的 Loading message id；首次写入使用 Append，重试使用
  Upsert，确保失败回合只有一条消息。
- `ConversationAssistantRoundTripRunner` 成功持久化完成消息后，即视为生成已提交。
  后处理异常不得再进入“把 Loading 改成 ERROR”的生成失败路径。
- 后处理按副作用分段捕获；取消必须重新抛出，普通失败写入脱敏日志并汇总为非阻断
  Notice。涉及经济结算等一致性副作用时保留原始失败，不通过重新生成正文补偿。

## 3. 群聊总流程终态

群聊发送由当前 sending run 和 conversation id 共同约束：

- **正常完成**：根据成功/失败回合汇总 Error 或 Notice，清空流式内容并结束发送。
- **RoundTrip 外普通异常**：若 run 和会话仍有效，保留已持久化消息，清理残留
  Loading，恢复发送状态并显示安全 Error。
- **取消或切换**：原样传播 `CancellationException`；旧 run 只做自身资源清理，
  不写当前新会话 UI。
- **Job 清理**：Job 引用清理与 UI 终态分开；UI 终态不能只依赖
  `finishSendingRun()`。

为避免双重结束，具体实现应保证只有当前有效 run 可以写最终 UI；已有
`isSendingRunActive` 和当前会话检查继续作为门禁。

## 4. 回合结果与重试

保留 `GROUP_TURN_MAX_ATTEMPTS = 2`。回合结果至少区分：

- 成功：完成消息已提交。
- 可重试生成失败：在剩余次数内重试同一 Loading id。
- 最终生成失败：保留单条 ERROR，并继续下一角色。
- 取消/失活：立即终止后续角色，不产生 Error。
- 后处理失败：正文成功，记录副作用失败，不消耗生成重试次数。

本任务不修改全局网关重试。若新增群聊局部等待，只能针对测试证实的瞬时失败，且
必须有固定上限；不得对内容校验失败、配置错误或取消进行重试。

## 5. 诊断契约

统一使用 `AppLogger`。建议稳定字段：

- `conversationId` 仅记录短哈希或省略；不得记录完整本地标识链。
- `participantId` / `assistantId`、turn index、attempt。
- `stage`：prepare、persist-loading、stream、commit、post-process、finalize。
- 安全错误类别或 HTTP 状态码。

不记录角色名、聊天正文、提示词、完整响应、密钥、附件路径或 Base64。

## 6. 测试策略

- `RoleplayViewModelTest`：覆盖 AC1-AC4、AC6，并断言消息顺序、speaker 元数据、
  请求次数、Notice/Error、`isSending` 和 Loading 清理。
- `ConversationAssistantRoundTripRunnerTest` 或 RoundTrip 专用测试：覆盖 AC5，证明
  完成提交后后处理失败不会回写 ERROR。
- 可注入失败的 Store/Repository Fake：覆盖用户消息写入和成员读取失败。
- `RoleplayGroupReplyPlannerTest`：作为选角规则未变化的回归保护。
- `AiGatewayTest`：只运行现有协议回归；本任务不收紧 `[DONE]` 语义。

## 7. 兼容、风险与回滚

- 不改变消息 schema 和现有 ERROR 气泡语义。
- 后处理隔离可能暴露此前被“发送失败”掩盖的经济、脚本或观察错误；这些错误应通过
  Notice/日志处理，不能回退为整轮 AI 重发。
- 严格要求 SSE 终止标记可能破坏兼容代理，因此不属于本次修复。
- 回滚时可分别回退“后处理隔离”和“总流程兜底”，但新增回归测试与脱敏诊断应保留。
