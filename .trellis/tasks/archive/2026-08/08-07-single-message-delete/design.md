# 单条消息删除：技术设计

## 1. 设计结论

普通聊天和 RP 复用 `ConversationRepository.deleteMessage` 这一领域入口；底层新增
明确的单条删除 Store 契约，并由 `RoomConversationStore` 在一个
`ChatDatabase.withTransaction` 中完成消息及关联数据清理。

不采用“先删消息，再依次调用 MemoryRepository 和 SummaryRepository”的顺序编排。
删除不可逆，顺序编排会产生部分成功；原子事务才能满足 AC8。

建议复用现有 `ConversationMessageTransforms` 承载纯消息列表变换：移除目标消息并清除
其他消息中的引用。Store 负责事务和 DAO 写入，Repository 负责校验会话、更新时间并
返回领域结果，ViewModel 只负责状态门禁和用户反馈。

## 2. 组件边界

### UI

- 普通聊天：扩展 `ChatMessageActionSheet` 和 `ChatMessageCallbacks`，由
  `ChatScreen` 持有待确认的消息 id，确认后调用 ViewModel。
- RP：扩展 `RoleplayMessageMenuWrapper`、`RoleplayMessageCallbacks` 和页面级待确认
  状态；离线 `RoleplayDialoguePanel` 与在线 `RoleplayOnlinePhoneContent` 都传递删除
  回调。
- 确认对话框只表达不可逆删除，不包含撤回语义。

### ViewModel

- `ChatViewModel.deleteMessage(messageId)` 与
  `RoleplayViewModel.deleteMessage(sourceMessageId)` 调用同一 Repository 方法。
- 两者在启动协程前再次校验：当前会话存在、非发送态、目标存在且非 `LOADING`。
- RP 删除成功后，仅当 `replyToMessageId` 等于目标 id 时清空当前引用草稿。
- 异常由 ViewModel 使用现有脱敏映射写入 `errorMessage`；事务失败不做乐观删除。

### Domain / Repository

- 新增领域结果：

```text
MessageDeletionResult
├── Deleted(updatedMessages)
└── NotFound
```

- 数据库异常继续抛给 ViewModel，不在 Repository 中包装为“成功但需补清理”。
- `ConversationRepository.deleteMessage` 保留会话标题和模型，只刷新 `updatedAt`。

### Store / Room

- `ConversationStore` 新增语义明确的原子方法，例如
  `deleteMessageWithAssociations(conversation, conversationId, messageId)`。
- `ConversationDao` 新增按 `conversationId + messageId` 删除单条消息的查询。
- `MemoryDao` 新增 `DELETE ... WHERE sourceMessageId = :messageId`。
- `RoomConversationStore` 复用现有领域映射和 `database.withTransaction`，不通过
  `updateConversationMessages` 模拟删除；后者只会 upsert 变化项，无法删除缺失行。

## 3. 数据流

```text
用户点击“删除”
  → 页面记录待确认 sourceMessageId
  → 用户确认
  → ChatViewModel / RoleplayViewModel 二次校验
  → ConversationRepository.deleteMessage
  → ConversationStore.deleteMessageWithAssociations
  → ChatDatabase.withTransaction
       1. 读取当前会话消息并确认目标存在
       2. 计算剩余消息与 replyTo* 清理结果
       3. upsert 仅有引用变化的剩余消息
       4. 删除目标 messages 行
       5. 按 sourceMessageId 删除 memory_entries
       6. 删除 conversation_summaries 与 summary_segments
       7. 更新 conversation.updatedAt
  → 返回 Deleted / NotFound
  → Room Flow 更新普通聊天与 RP 列表
  → RP 按需清空当前引用草稿
```

## 4. 消息变换不变量

纯变换对同一会话的消息列表执行以下规则：

1. 只移除 `id == targetMessageId` 的目标消息。
2. 对其他消息，仅当顶层 `replyToMessageId == targetMessageId` 时同时清空三个顶层
   `replyTo*` 字段。
3. 对其他消息的 `parts`，仅当分段 `replyToMessageId == targetMessageId` 时同时清空
   该分段三个 `replyTo*` 字段，并重新走现有 parts 规范化。
4. 不修改其他消息的 id、角色、内容、状态、时间、模型、推理、附件、特殊玩法、
   引用外链 citations 或角色元数据。
5. 保持剩余消息原顺序；目标不存在时返回 `NotFound` 且不写数据库。

顶层和分段引用都必须处理，因为 `MessageEntity.kt:33-37` 保存两类容器，而
`RoleplayMessageUiMapper.kt:270-272` 会优先读取分段引用。

## 5. 原子事务与关联数据

### 记忆

删除全部 `memory_entries.sourceMessageId == targetMessageId` 的记录，不限定 scope。
这同时清除“记住这条”生成的 pinned 记忆和其他带同一来源 id 的记忆。普通聊天的
`rememberedMessageIds` 来自 Memory Flow，无需手工维护第二份集合。

没有 `sourceMessageId` 的待确认记忆提案和提案历史无法与单条消息可靠对应，保持不变。

### 摘要

无论目标消息是否位于当前摘要覆盖窗口，删除成功都清除该会话总摘要和所有分段。
原因是摘要包含覆盖数量、起止消息 id 和合并文本，局部修补无法证明正确。后续摘要由
现有触发规则重新生成，本任务不主动发起网络摘要请求。

### 引用

只清空指向目标消息的引用元数据，不删除引用它的消息，也不改写其正文。RP 当前输入
草稿属于 ViewModel 局部状态，不在 Room 事务内；数据库事务成功后再按 id 清空。

### 附件

事务只删除消息行及其中的 URI/JSON 引用，不调用 `ImageFileStorage`、
`LocalImageStore`、ContentResolver 删除或文件 API。这样避免误删共享 URI 或用户相册
内容，并落实 AC6。

## 6. RP 特殊边界

- 一个底层 `ChatMessage` 可由多个 parts 映射成多个 `RoleplayMessageUiModel`，所有
  派生项共享 `sourceMessageId`；删除底层行后必须一起消失。
- 合成的流式 UI 项 id 为 `streaming`，并非可删除的持久化消息；发送态及
  `MessageStatus.LOADING` 门禁会排除它。
- RP “撤回”继续调用现有 `recallMessage`，保留行并改写为撤回事件；删除不复用该
  变换，也不改变撤回菜单可见性。
- 离线面板当前没有传递引用/撤回回调，但删除需求必须显式贯通离线和在线两条页面
  路径，不能只在已有在线菜单链路生效。

## 7. 并发、失败与恢复

- Chat 和 RP 在发送期间都会忽略 Room 的迟到观察帧
  （`ChatObservationSupport.kt:44-49`、`RoleplayObservationSupport.kt:200-212`），
  因此删除在发送/流式期间必须被 UI 和 ViewModel 双重拒绝。
- 事务提交前 UI 不移除消息；失败时 Room 自动回滚，原消息及关联数据继续由 Flow
  提供。
- 后台部件更新必须基于当前数据库消息查找目标；目标不存在时 no-op，不能从旧快照
  重新插入。相关回归测试纳入 AC7。
- `NotFound` 视为幂等完成，不显示数据库错误；真正异常使用安全中文提示。

## 8. 兼容性与回滚

- 只新增 DAO 查询和 Kotlin 契约，不变更 Entity/schema，因此不增加
  `ChatDatabase.CURRENT_VERSION`，不生成迁移。
- `ConversationStore` 接口变化需要同步更新 `RoomConversationStore`、
  `FakeConversationStore`、`VoiceSynthesisCoordinatorTest` 的内存实现，以及
  `RoleplayViewModelTest` 的 yielding 实现。
- UI 入口可独立隐藏回滚；底层物理删除一旦成功不可恢复，因此必须先通过原子事务、
  确认门禁和设备测试再开放入口。
