# 单条消息删除：仓库证据

## 结论摘要

- 普通聊天和 RP 共用 `ChatMessage`、`messages` 表及 `ConversationRepository`，可以且
  应该共用同一底层物理删除契约。
- 当前 Store 的列表变换能力不能持久化“少一条消息”，必须新增明确的单条删除 DAO
  与 Store 事务。
- 关联清理必须覆盖顶层和 parts 内引用、所有来源记忆、总摘要和摘要分段；这些数据
  都在同一个 `ChatDatabase`，可实现原子事务且无需 schema 迁移。
- RP 撤回是改写消息而非删除，应继续作为独立菜单项保留。

## 1. UI 入口现状

### 普通聊天

- `ChatMessageWorkUnitSheets.kt:79-94` 定义消息操作面板回调，没有删除回调。
- `ChatMessageWorkUnitSheets.kt:112-229` 依次提供复制、翻译、记忆、预览、保存、
  搜索、导出、分享、编辑和重新生成，没有删除项。
- `ChatScreenCallbacks.kt:17-29` 的 `ChatMessageCallbacks` 没有删除动作。
- `ChatNavGraph.kt:69-82` 绑定现有消息回调，没有删除绑定。

### 角色扮演

- `RoleplayMessageBubbles.kt:645-653` 的长按菜单接收重试、编辑、引用和撤回回调。
- `RoleplayMessageBubbles.kt:687-742` 提供复制、编辑、引用、撤回和重回回合，没有删除。
- `RoleplayScreenCallbacks.kt:23-39` 与 `RoleplayNavGraph.kt:395-412` 没有删除回调。
- 在线路径已把引用/撤回传到消息项（`RoleplayOnlinePhoneContent.kt:600-620`）；离线
  `RoleplayDialoguePanel.kt:90-119,247-257` 没有这些可选回调。删除接入必须明确贯通
  两条路径，不能只验证在线聊天。

## 2. 共用底层可行性

- `ConversationStore.kt:7-48` 是普通聊天与 RP 共用的消息存储边界，目前仅支持观察、
  upsert、快照替换、清空和删除会话，没有单条删除。
- `ChatDatabase.kt:40-47,72-76` 显示消息、记忆、总摘要和摘要分段属于同一个 Room
  数据库，并同时暴露 `ConversationDao` 与 `MemoryDao`，具备单事务清理的基础。
- `ConversationRepository.kt:326-356` 的 RP 撤回已通过共用 Conversation Store 修改消息，
  证明两套 UI 可共享消息级 Repository 用例。
- `RoleplayMessageUiMapper.kt:230-280` 显示一个底层消息的多个 parts 会映射为多个
  `RoleplayMessageUiModel`，且都使用同一 `sourceMessageId`。因此 Repository 的删除
  单位应是底层消息 id。

## 3. 为什么不能复用现有 updateConversationMessages 删除

- `RoomConversationStore.kt:86-105` 读取旧列表、执行 transform 后，只计算并 insert
  变化消息。
- `RoomConversationStore.kt:249-256` 的 `resolveMessagesToUpsert` 仅遍历新列表；旧列表中
  被过滤掉的消息不会进入 upsert，也没有 DELETE SQL，所以仍留在 Room。
- `ConversationDao.kt:28-41` 只有列出消息、insert、清空全会话和删除会话，没有按消息
  id 删除查询。

因此需要新增明确的 `deleteMessageWithAssociations` Store 契约，并在
`RoomConversationStore` 的 `withTransaction` 中调用单条 DELETE。

## 4. 引用不变量

- `MessageEntity.kt:33-37` 保存 `partsJson` 和顶层三个 `replyTo*` 字段。
- `RoleplayMessageUiMapper.kt:270-272` 会优先读取 part 内的引用，顶层清理不足以消除
  RP 引用卡片。
- 删除目标消息时应清空其他消息中命中的顶层和 part 三元组；不命中的引用、正文、
  附件和顺序保持不变。

## 5. 记忆不变量

- `MemoryRepository.kt:15-28` 只能按 scope + source 查一条并按 entry id 删除，缺少
  “删除所有同来源记忆”的契约。
- `MemoryDao.kt:16-27` 可按 `sourceMessageId` 查询单条 scope 记忆，但
  `MemoryDao.kt:76-77` 只能按 entry id 删除。
- `ChatMessageMemoryCoordinator.kt:61-82` 明确把“记住这条”记录的
  `sourceMessageId` 设为消息 id。
- `ChatViewModel.kt:1257-1274` 从 Memory Flow 的 `sourceMessageId` 计算
  `rememberedMessageIds`；删除来源记忆后 UI 会自动去除记忆标记。

新增 `MemoryDao.deleteMemoryEntriesBySourceMessageId` 可以覆盖所有 scope，无需数据库
字段或索引迁移。

## 6. 摘要不变量

- `ConversationSummaryCoordinator.kt:54-77` 依据已覆盖消息数量决定是否复用旧摘要。
- `ConversationSummaryCoordinator.kt:135-178` 的分段和总摘要保存起止消息 id、消息数和
  覆盖数量；删除任意消息后继续复用可能让上下文包含已删除内容或错误覆盖数。
- `MemoryRepository.kt:163-166` 的 `deleteSummary` 已同时删除总摘要和摘要分段；底层
  DAO 查询位于 `MemoryDao.kt:98-102`。

本任务采用删除整份摘要和全部分段，不尝试局部修补，也不在删除事务中发起网络重算。

## 7. 撤回与删除的差异

- `ConversationRepository.kt:326-356` 的撤回保留原消息 id，只把内容改为撤回提示、
  标记 `isRecalled` 并清空自身引用。
- `RoleplayMessageBubbles.kt:671-731` 仅对符合条件的用户消息显示撤回。

物理删除必须使用独立方法和菜单项，不能调用或改写 `recallMessage`。

## 8. 发送与流式状态

- `ChatObservationSupport.kt:44-49` 在当前会话发送时忽略 Room 观察消息。
- `RoleplayObservationSupport.kt:200-212` 同样在发送时让发送链路独占原始消息状态。
- `ChatViewModel.kt:499-503,604-608` 的重试和编辑已采用 `isSending` 门禁，可复用为
  删除入口模式。

因此 UI 与 ViewModel 都必须拒绝发送中、流式中和 `LOADING` 目标，避免迟到写入和
状态分叉。

## 9. 附件策略

- `MessageEntity.kt:33-34` 仅把附件和 parts 作为 JSON/URI 元数据保存在消息行。
- 共用消息删除链路当前不依赖任何文件删除组件；删除 Room 行自然移除引用，但不会
  删除 URI 指向的文件。

本版本保持这一行为，并用代表性本地文件设备测试证明文件仍存在。

## 10. 实现影响面

- 生产：`ConversationMessageTransforms.kt`、`ConversationStore.kt`、
  `RoomConversationStore.kt`、`ConversationDao.kt`、`MemoryDao.kt`、
  `ConversationRepository.kt`、两个 ViewModel、两套 callbacks/导航/UI 菜单。
- `ConversationStore` 测试实现：`FakeConversationStore.kt:10`、
  `VoiceSynthesisCoordinatorTest.kt:384`、`RoleplayViewModelTest.kt:2197`。
- 重点测试：纯引用变换、原子 Room 删除与回滚、Repository 幂等、两个 ViewModel
  门禁、普通/RP 确认交互、RP 多气泡和附件文件保留。
