# 会话消息变更契约

本规范约束单条消息物理删除，以及发送、重试、编辑、撤回、取消清理等
“读取消息列表后再写回快照”的流程。目标是避免多表部分成功、已删除消息复活、
旧任务覆盖新任务和永久 Loading。

## 1. Scope / Trigger

以下任一场景必须按本规范实现：

- 物理删除一条 `ChatMessage`，且需要同步清理引用、记忆或摘要。
- 基于 `List<ChatMessage>` 执行 read → transform → replace/upsert。
- 发送 run 在取消、失活或切换会话后清理 Loading。
- 模型正文提交后继续执行协议、脚本、观察、转账或其他副作用。

## 2. Signatures

单条删除使用现有统一契约：

```kotlin
sealed interface MessageDeletionResult {
    data class Deleted(
        val updatedMessages: List<ChatMessage>,
    ) : MessageDeletionResult

    data object NotFound : MessageDeletionResult
}

fun ConversationMessageTransforms.deleteMessageAndClearReferences(
    messages: List<ChatMessage>,
    targetMessageId: String,
): MessageDeletionResult

suspend fun ConversationStore.deleteMessageWithAssociations(
    conversation: Conversation,
    conversationId: String,
    messageId: String,
): MessageDeletionResult

suspend fun ConversationRepository.deleteMessage(
    conversationId: String,
    messageId: String,
): MessageDeletionResult
```

消息快照写入没有新的公共 API，但必须由单一所有者保护：

- ViewModel 用户动作：同步设置 `isMessageDeletionInProgress`，并在 `finally` 释放。
- 同一发送执行器内的持久化与取消清理：使用同一 `Mutex`，且在锁内重新读取
  最新持久化消息。

## 3. Contracts

### 单条物理删除

`RoomConversationStore.deleteMessageWithAssociations` 必须在一个
`database.withTransaction` 中完成：

1. 读取目标会话的最新消息。
2. 目标不存在时返回 `NotFound`，不执行任何写入。
3. 移除目标，并清空其他消息顶层及 `parts` 内指向目标的全部 `replyTo*`。
4. 删除 `messages` 目标行。
5. 删除全部 `memory_entries.sourceMessageId == messageId`。
6. 删除该会话的总摘要和全部摘要分段。
7. 只刷新会话 `updatedAt`，保留标题、模型和其他元数据。

删除消息行只移除附件 URI/元数据引用，不删除 URI 指向的本地文件、相册或外部
文档。本契约不要求 Room schema 变化。

### 快照并发与发送提交边界

- 删除进行中不得启动发送、重试、编辑、撤回、POKE、特殊玩法或自动补偿等会基于
  旧快照写回的动作；UI 门禁之外，ViewModel 必须再次校验。
- 取消/失活清理只能移除当前 run 自己追踪的 Loading id，不能用旧内存快照整体覆盖
  数据库。
- 需要 read → transform → replace 时，读取必须发生在互斥区内；禁止先读、等待、
  再拿旧列表写回。
- 模型正文成功持久化即为生成提交点。之后的协议、脚本、观察等失败只能记录脱敏日志
  或非阻断提示，不得把正文改回 `ERROR`，也不得重新生成正文。
- 有顺序依赖的副作用只能在前置状态成功落库后继续。例如转账状态写回失败时，必须
  跳过结算。

## 4. Validation & Error Matrix

- `conversationId` 或 `messageId` 为空 → `NotFound`，零写入。
- 会话或目标消息不存在 → `NotFound`，零写入，不显示数据库错误。
- 发送中、流式中、目标为 `LOADING` 或已有删除任务 → 拒绝删除。
- 删除进行中触发发送/重试/编辑/撤回 → 忽略新动作，不启动写入。
- 事务任一步异常 → Room 整体回滚；ViewModel 保留原消息并显示脱敏中文错误。
- `CancellationException` → 保持取消语义；必要的 Loading 清理使用
  `NonCancellable`，但不得更新已经失活的新会话 UI。
- 旧 run 清理时发现同会话新消息 → 只移除旧 run 的 Loading，保留新消息和新
  Loading。
- 正文提交后的后处理失败 → 正文保持 `COMPLETED`，输出非阻断 Notice/日志。

## 5. Good / Base / Bad Cases

- **Good**：删除目标后，引用、来源记忆、摘要和分段同时消失，附件文件仍存在，
  Flow 重观察不再返回目标。
- **Base**：重复删除同一 id 返回 `NotFound`，会话时间和关联表都不再变化。
- **Bad**：先删除消息，再依次调用记忆和摘要 Repository；后一步失败会留下部分成功。
- **Bad**：取消时把启动阶段保存的 `cancelledMessages` 整体 replace 回数据库；新 run
  的消息会被覆盖，旧 Loading 也可能复活。

## 6. Tests Required

- 纯变换：目标存在/不存在、顶层引用、parts 引用、非目标引用和顺序保持。
- Repository/Fake：参数传递、`NotFound`、异常传播和元数据保留。
- 真实 Room androidTest：提交后的重观察、所有来源记忆、摘要/分段、附件文件保留、
  `NotFound` 零写入；使用 SQLite trigger 或等价故障注入验证全事务回滚。
- Chat/RP ViewModel：删除未提交期间发送、重试、编辑、撤回等入口无写入，释放后删除
  完成；异常文案不泄漏 SQL、路径、正文或密钥。
- Compose：菜单入口、二次确认、取消零回调、确认一次回调；RP 验证撤回并存、
  `sourceMessageId` 和 POKE。
- 群聊：旧 run 取消不得删除新 run Loading；后处理失败保持已完成正文、继续后续阶段，
  日志不包含聊天正文和密钥。

## 7. Wrong vs Correct

### Wrong

```kotlin
// 多仓储顺序写入会产生部分成功。
messageDao.deleteMessage(conversationId, messageId)
memoryRepository.deleteBySourceMessageId(messageId)
summaryRepository.clear(conversationId)

// 使用等待前读取的旧快照，可能复活已删除消息或覆盖新 run。
val staleMessages = repository.listMessages(conversationId)
waitForNetworkOrCancellation()
repository.replaceConversationSnapshot(conversationId, staleMessages)
```

### Correct

```kotlin
database.withTransaction {
    val latestMessages = conversationDao.listMessages(conversationId)
    val result = ConversationMessageTransforms.deleteMessageAndClearReferences(
        messages = latestMessages.map { it.toDomain() },
        targetMessageId = messageId,
    )
    if (result is MessageDeletionResult.Deleted) {
        val changedMessages = resolveMessagesToUpsert(
            existingMessages = latestMessages.map { it.toDomain() },
            nextMessages = result.updatedMessages,
        )
        if (changedMessages.isNotEmpty()) {
            conversationDao.insertMessages(changedMessages.map { it.toEntity() })
        }
        check(conversationDao.deleteMessage(conversationId, messageId) == 1)
        memoryDao.deleteMemoryEntriesBySourceMessageId(messageId)
        memoryDao.deleteConversationSummary(conversationId)
        memoryDao.deleteConversationSummarySegments(conversationId)
        conversationDao.upsertConversationEntity(conversation.toEntity())
    }
}

messageSnapshotMutationMutex.withLock {
    val latestMessages = repository.listMessages(conversationId)
    val cleaned = latestMessages.filterNot { it.id == trackedLoadingMessageId }
    repository.replaceConversationSnapshot(
        conversationId = conversationId,
        messages = cleaned,
        selectedModel = selectedModel,
    )
}
```
