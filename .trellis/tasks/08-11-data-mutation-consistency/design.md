# 会话 mutation 与跨表一致性：技术设计

## 1. 会话级 mutation 所有权

```text
用户动作 / send run
  → 获取 conversationId / scenarioId 对应所有权
  → 在边界内重新读取最新持久化状态
  → transform + 差量写入 / 单一事务
  → 提交后发布 UI
  → finally 释放自己的进行中状态
```

- 优先扩展现有发送执行器 Mutex 和消息删除进行中门禁；只有复用无法满足多个入口时，
  才抽取会话级 Coordinator。
- 锁内只保护状态读取与写入决策，不覆盖网络生成等长耗时过程。
- 每个 run 保存自己的 Loading id / run id，取消清理不能使用任务开始前的完整快照。
- mutation key 以 conversation / scenario 为粒度，不让不同会话互相阻塞。

## 2. Room 事务边界

- 单条消息删除继续使用现有 `RoomConversationStore.deleteMessageWithAssociations` 契约。
- 会话删除：会话、消息、手机快照 / 观察、摘要等数据库写入进入同一
  `database.withTransaction`。
- 剧情删除 / 重启：所有属于 `ChatDatabase` 的场景、Session、会话、参与者、日记、邮箱、
  经济和手机数据在一个事务内完成；新 Session / 开场消息必须与旧数据清理同批提交。
- Repository 负责领域编排，DAO / Store 不向 ViewModel 暴露 Entity。

## 3. 文件副作用

- 图片、音频等文件不属于 Room 事务，统一在数据库提交后清理。
- 文件失败记录安全标识 / 数量，不记录完整路径和用户内容；根据现有基础设施重试或留下
  明确可人工清理状态。
- 数据库回滚时不执行文件删除，避免出现数据库仍引用但文件已消失。

## 4. 错误、取消与 UI

- `CancellationException` 原样传播；必要的旧 Loading 清理可使用 `NonCancellable`，但仍
  只能作用于当前 run 和当前会话。
- 事务异常整体抛给用户动作边界，UI 保留原数据并恢复按钮 / sending 状态。
- `NotFound`、重复删除和空 id 是幂等业务结果，不转换为数据库错误。

## 5. 测试设计

- Fake / 可控 dispatcher：在 replace 前挂起编辑，随后发送，再恢复编辑；反向顺序也覆盖。
- ViewModel：删除 / 编辑进行中时发送、重试、撤回、POKE 和自动补偿均不启动旧快照写入。
- Room androidTest：在每个 DAO 步骤注入失败，事务后重新观察所有相关表 / Flow。
- 文件：数据库提交前绝不调用，提交后失败不会改变事务结果且日志脱敏。

## 6. 兼容与回滚

- 实施前先读取当前工作区与已归档消息删除任务的差异，保留用户既有门禁和测试。
- mutation 所有权、会话清理事务、剧情重启事务分别形成独立回滚点。
- 若单一 Room 事务暴露当前 Repository 边界无法复用，回到规划评估最小 Store / DAO API，
  不以 schema 迁移绕过问题。
