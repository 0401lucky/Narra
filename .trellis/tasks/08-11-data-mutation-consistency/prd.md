# 会话 mutation 与跨表一致性

## Goal

在 2.2.6-dev 中建立统一的会话级 mutation 顺序和数据库事务边界，防止编辑、删除、重试、
发送及旧 run 清理并发时用旧快照覆盖新消息，并确保剧情删除 / 重启等跨表操作在任一步
失败时不会留下部分提交或孤儿数据。

## Background

- 现有消息删除已经定义 `MessageDeletionResult`、单一 Room 事务和删除期间门禁，本子任务
  必须复用该契约，不得另建冲突方案。
- `ChatViewModel.kt:607-638` 与 `RoleplayScenarioActionSupport.kt:171-208` 在异步任务
  外先读取内存快照，再整体替换；快速“编辑 → 发送”可能形成 last-writer-wins。
- `ConversationRepository.kt:323-340` 分步删除会话、手机快照和观察记录；
  `RoleplayRepository.kt:241-256,328-354` 的剧情删除 / 重启跨多个表和文件分步执行，
  没有统一 `withTransaction`。
- 工作区包含之前消息删除任务的既有改动，实施前必须逐文件核对，禁止覆盖。

## Requirements

- **R1 单一所有者**：同一会话的编辑、删除、重试、撤回、发送、POKE、自动补偿和旧 run
  Loading 清理必须遵守同一 mutation 所有权；读取最新消息必须发生在互斥 / 事务边界内。
- **R2 防旧快照覆盖**：任何等待前读取的旧列表不得在新消息提交后整体写回；旧 run 只能
  清理自己追踪的 Loading id。
- **R3 数据库原子性**：会话关联清理、剧情删除和剧情重启的 Room 范围写入必须在单一事务
  中全部成功或全部回滚。
- **R4 外部副作用**：图片 / 文件删除不伪装成 Room 事务；只在数据库提交后执行，失败时
  记录脱敏、可重试的清理状态，不回滚已提交数据库。
- **R5 兼容边界**：不修改 Room schema，不删除消息附件文件，不改变 RP 撤回与物理删除
  的既有产品语义。
- **R6 状态与错误**：取消原样传播；事务失败保留原 UI / 数据并显示脱敏中文错误；
  `NotFound` 和重复操作保持零写入、可幂等。
- **R7 测试**：使用可控 dispatcher / Fake 构造编辑与发送交错，使用真实 Room 故障注入
  验证每个跨表步骤回滚，并重新观察 Flow 验证最终状态。

## Acceptance Criteria

- [ ] **AC1（R1、R2）**：编辑挂起期间启动发送、删除或重试不会产生并发整体替换；恢复后
      新消息、正确 Loading 和消息顺序全部保留。
- [ ] **AC2（R1、R2）**：旧 run 取消 / 失活只移除其追踪的 Loading id，不删除新 run 消息，
      不复活已删除消息。
- [ ] **AC3（R3）**：会话关联清理、剧情删除和剧情重启在任一 DAO 写入故障时整体回滚；
      重观察所有相关 Flow 不存在部分删除或孤儿记录。
- [ ] **AC4（R3）**：剧情重启成功时，新会话、开场消息、Session 和关联数据一次性可见，
      不出现“旧数据已删但新 Session 未建”的中间提交。
- [ ] **AC5（R4）**：文件删除只在 Room 提交后执行；文件失败不破坏数据库成功结果，日志不
      包含本地路径或用户内容，并具备明确的后续清理策略。
- [ ] **AC6（R5、R6）**：重复删除 / 重启、目标不存在、空 id 和取消路径行为稳定；无 Room
      schema 变化，附件文件保留规则和 RP 撤回语义不变。
- [ ] **AC7（R7）**：Chat / RP ViewModel 交错测试、Repository / Store 测试和真实 Room
      故障注入测试通过；相关设备测试源码、Lint、单测和 debug 构建结果有记录。
- [ ] **AC8**：没有为性能目的引入 Paging、全局 Actor、跨会话串行瓶颈或新的网络重试。

## Out of Scope

- RP Compose 列表 / streaming 性能、设置共享流、TTS / Base64 资源边界。
- Room schema / FK 迁移、附件文件物理删除和全应用单线程队列。
- 重做已经完成的单条消息删除产品 UI；只复用并扩展其 mutation 契约。

## Confirmed Decisions

- 复用 `.trellis/spec/backend/conversation-message-mutation-guidelines.md`，不建立第二套删除
  或 Loading 清理语义。
- 串行化粒度为单会话 / 单场景，不阻塞互不相关的会话。
- 数据库事务与文件副作用分离；本轮不依赖 schema 变化实现原子性。
