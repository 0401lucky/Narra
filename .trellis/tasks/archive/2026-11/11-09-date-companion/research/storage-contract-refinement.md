# Research: 陪伴场次、回合、尝试与来源资格的最小存储契约

- Query: 在既有单角色线下入口上新增可靠回合时，如何保留全部原文、免费补保存、CAS 修订并复用 v2.4 记忆，而不把未完成回合/继续占位错误提取。
- Scope: internal；只读研究，不涉及 UI/声音设计，不运行测试。
- Date: 2026-11-09；用户已确认升级现有线下扮演及完整规划。以下结构为主会话设计参考，尚未实现。

## Findings

### 1. 当前约束与源码证据

下述代码路径以 `app/src/main/java/com/example/myapplication/` 为根。

| 证据 | 对设计的直接限制 |
| --- | --- |
| `model/ChatMessage.kt:21`、`data/local/chat/MessageEntity.kt:26` | 消息只有 COMPLETED/LOADING/ERROR、memoryRevision 与 recall 请求 ID；没有场次/逻辑回合/attempt。不能把现有 memoryRecallRequestId 改作幂等 turnId。 |
| `data/local/RoomConversationStore.kt:81` / `:98` / `:187`；映射 `:236` / `:293` | 快照、局部变更与 upsert 均可复用 Room 事务和统一来源钩子；新字段必须完整贯穿模型、Entity、映射及导入导出。 |
| `data/local/memory/MemorySourceStorage.kt:28` / `:84` / `:154` | 首次写入任何 COMPLETED 非空消息便冻结身份、生成 AVAILABLE 来源并 enqueue；只延后回合 posthook 阻止不了后台/手动提取已保存的用户输入。 |
| 同文件 `:175` | 来源 revision 只随 sameSource 比较的字段变化；仅把 turn 表从 pending 改 committed，不改消息字段，当前 writeMessages 不会重跑 capture。 |
| `data/repository/context/RoomMemoryArchiveRepository.kt:593` / `:642` / `:1534` | 自动 ingress、手动 captureSelectedMessages、validSource 是独立路径；只改一个入口会留下旁路。 |
| `conversation/DurableMemoryExtractionCoordinator.kt:65`；`ConversationSummaryCoverageSupport.kt:36` / `:243` | 摘要资格与“完整原文补充”也读取消息；控制占位被简单删去后可能由 completeInput 重新补入，不能只改 formatter。 |
| `conversation/ConversationAssistantRoundTripRunner.kt:76` / `:96` | 现有即时请求→upsert 没有持久产物阶段；一次 IO 失败进入一般失败流程，不能等同于可跨进程补保存。 |
| `model/AssistantReply.kt:47` / `:72`；`model/PromptCompletionResult.kt:8` | 流 Completed 没有结束证据；辅助请求已定义 COMPLETED/UNKNOWN/TRUNCATED/FILTERED/INCOMPLETE/FAILED/CANCELLED，可提炼复用类型。 |

### 2. 建议的最小结构

建议 3 张附属表；generated artifact 与 attempt 同一行，先不另造“事件总线表”或新的正文库。权威原文仍在 messages，待确认/待提交产物只在 attempt。

| 实体 | 必要字段/约束；命名可调整 |
| --- | --- |
| `companion_encounters` | `id, conversationId, scenarioId, runId, characterId, personaKey, createdAt, endedAt?, openingMode, state, revision`；显式绑定现有运行，模式切换不改变 memoryPurpose。索引 `(conversationId, createdAt, id)`；可按产品定义限制每运行最多一个活动场次。 |
| `companion_turns` | `id, encounterId, ordinal, kind(INPUT/CONTINUE/OPENING), inputMessageId?, replyMessageId?, mutationRevision, committedRevision, selectedAttemptId?, activeAttemptId?, createdAt`；`UNIQUE(encounterId, ordinal)`。原答是否有效由已提交版本决定，不能由最新 attempt 是否正在生成决定。 |
| `companion_attempts` | `id, turnId, expectedTurnRevision, expectedInputRevision?, expectedReplyRevision?, frozenRuntimeJson, requestDescriptorJson, requestFingerprint, state, claimToken?, leaseUntil?, requestedAt?, receivedAt?, rawReply?, partsJson, completionStatus, rawFinishReason?, rawStatus?, artifactHash?`；状态区分 `NOT_STARTED/REQUESTED/RESULT_READY/COMMITTED/FAILED/UNCERTAIN/CANCELLED`。request 描述保存输入/配置版本/提示词版本，不复制 API Key 或 ProviderSettings。 |
| `messages` 的 2 个增量字段 | `companionTurnId TEXT NOT NULL DEFAULT ''`；`companionMemoryState TEXT NOT NULL DEFAULT 'legacy'`，取值 `legacy/waiting_turn/ready/control`。这是来源资格，不是 UI 状态；turnId 与资格由仓储联合写入，不能由 Composable 任意标 ready。encounter 可通过 turn 关联查询，不必重复存每条消息。 |

turn 与 message 采用受控双向逻辑引用，避免双向 CASCADE 误删整场；仓储事务负责清理与一致性校验。turn 所属 encounter 必须和消息 conversation/角色/运行吻合。新字段未知值或非空 turnId 指向缺失实体时**禁止自动来源资格**，不能回退 legacy。

旧行使用空 turnId + legacy，原 ID、时间、正文、模式与记忆行为不改；迁移不自动为旧历史创建场次/回合。新 schema 从实施时最新版本递增（当前 54），保留固定旧 schema、迁移/S3 测试。新格式导入若缺 turn 图，应明确不完整并保守处理，不能丢掉 link 后伪装成旧普通文本。

### 3. INPUT、CONTINUE、开场与提取资格

1. **INPUT 起始**：同事务建 turn/attempt、保存完整用户原文与 parts。消息可以仍为 UI 的 COMPLETED，但资格必须 `waiting_turn`；MemorySourceStorage 冻结当前 persona/可见角色的 UNKNOWN 空正文来源，不能 enqueue。原文仍完整可见、可恢复、可导出。
2. **CONTINUE 起始**：turn.kind 持久保存 CONTINUE，消息只存本地短显示标记，资格为 `control`；技术调度长文在请求时由同一语义转换器恢复，不写入消息/提取材料。不可仅凭文本恰好等于“继续”判类型。
3. **完成输入回合**：artifact 合法且 canonical 提交事务成功时，将该 INPUT 与新 assistant 一起提升为 `ready`；assistant 保存完整原文/parts。CONTINUE 控制消息永不变 ready，但其合法助手答可成为 ready。
4. **开场**：预览只保存 attempt/草稿；用户接受时同事务创建 canonical 开场消息、确认场次与 turn 的已提交版本。废弃预览、部分答、错误答与未保存结果均无自动来源资格。
5. **提升不能靠 no-op upsert**：把 `companionTurnId/companionMemoryState` 纳入 MemorySourceStorage.sameSource 的语义比较。`waiting_turn→ready` 产生新的 memoryRevision，使用已有 previousSources 冻结的身份创建可用新来源，不按提交时可能已变更的 persona 重绑。模型请求前冻结的输入 revision 与提交后来源 revision应分别记入 attempt/turn，原文字节不变。
6. **资格必须可复核**：统一 `sourceEligibility(message, canonicalTurn)` 的三值结果：`TEXT_ALLOWED / CONTROL_NOOP / UNAVAILABLE`。有 link 的 ready 还须与 turn 当前 canonical 消息 ID/revision 一致；activeAttempt 失败或生成中不撤销旧 selectedAttempt 的合法原答。修改/删除会增加 turn mutationRevision 并执行既有源失效。
7. **所有消费方一致**：capture/enqueue、getSourceSegments/validSource、手动选区、兼容引擎提取、摘要与模型历史各自调用相同语义投影；模型本轮可以显式读取自己 pending INPUT，但不可让其它回合/后台来源读取越权。memoryEngine=1 也不得借旧列表适配把 control/pending 重新当普通原文。
8. **摘要连续覆盖**：CONTINUE 使用绑定 ID+revision+kind 的显式 `CONTROL_NOOP` 覆盖证据，不计作用户言行或需要提取的 span。不能作为一般“缺来源”跳过空洞，也不能被 completeInput 的“完整来源补充”重新拼回。waiting_turn/截断答仍保留真实未完成缺口，不允许用条数推进覆盖。TXT/原文历史仍保留控制行及类型。

上述资格只延后新陪伴回合的机器处理，不删失用户输入。用户撤销/编辑 pending input、来源资格提升、canonical 修订都由一个仓储事务负责，不能单靠 ViewModel 的 isSending 防护。

### 4. CAS 与 generated artifact 的提交协议

- **CAS 单位**：`turnId + mutationRevision + activeAttemptId/claimToken + expected input/reply memoryRevision + frozen role/run/persona/generation`。控制权、来源和目标任一变化拒绝提交。新回复还以 `(turnId, canonical slot)` 保证唯一；不是按“上一条同文本”去重。
- **首次生成**：持久 claim 成功后先把 attempt 标 REQUESTED，再发网络；返回完整响应后，先单独提交 attempt.rawReply/结束证据/hash 为 RESULT_READY；之后在第二个事务写 canonical 消息、turn selectedAttempt/committedRevision、来源资格和 ingress。第二个事务失败保留第一阶段产物。
- **重生成**：只新增 attempt，旧 selectedAttempt/原答保持可见可读；失败只标该 attempt，不把 turn canonical 清空。成功 CAS 更换答内容并递增 memoryRevision/committedRevision，保留 messageId、逻辑顺序、原 createdAt，另外记录本次 generatedAt/editedAt。旧版本派生记忆/观测/声音由来源版本失效。
- **重试提交**：RESULT_READY 重试只复查 CAS、artifact hash 与绑定，不调用模型；已 COMMITTED 再调用返回同一回执。第一次 canonical 事务已成功但 UI 未收到时，重启仍识别 COMMITTED，不重复写来源/ingress。
- **人工编辑/删除**：和在途 attempt 共用上述 CAS；增加 mutationRevision、更新当前 canonical 引用并失效受影响来源。删除旧答不可被迟到响应复活；不要把整份 conversation 快照替换用于单条修订。
- **费用不确定**：REQUESTED 后崩溃/超时且没有可靠 artifact 时标 UNCERTAIN，不自动再请求；NOT_STARTED 才允许首次 claim。失效 lease 只恢复保存/提交的控制权，不能自动认定远端没收费。

### 5. IO 失败与进程恢复的真实保证

| 断点 | 能保证什么 | 不可宣称什么 |
| --- | --- | --- |
| 网络前，claim/REQUESTED 写失败 | 不发请求，原文和旧答保留。 | 不能在记录失败后照常发请求。 |
| 响应已到内存，artifact 第一次落盘失败 | 当前进程可保留有界内存产物并让用户重试保存；旧 canonical 不变。 | **没有任何持久副本时，进程被杀后无法保证恢复正文**。此时只能依靠 REQUESTED 证据避免静默再次付费，不能伪装为 RESULT_READY。 |
| RESULT_READY 已持久化，canonical 事务失败 | 重启校验 hash/绑定后补保存，模型调用数不增加。 | 不能因 SQLite 暂不可用就将它当普通生成失败重发。 |
| canonical 已提交，UI/posthook 失败 | 查询 turn 回执得到唯一结果；持久 ingress 后续恢复。 | 页面没刷新不等于结果未保存。 |
| artifact 截断/过滤/纯推理 | 可保留未完成证据并给出明确状态，不发布 ready 来源。 | 字符串非空、流 close 或 HTTP200 不证明完整。 |

若产品要进一步承诺“artifact 数据库写不进去也尽量跨进程保留”，需另外设计私有文件的原子临时写/校验/孤儿恢复及配额；它不能消除断电前没有任何成功写入的窗口，不应作为无条件保证。当前最小方案使用 Room artifact 行即可兑现“**已落盘产物**免费补保存”。

### 6. 流结束证据与新旧路径隔离

- 最小网关增量可增加 `ChatStreamEvent.CompletionEvidence(status, rawReason, rawStatus)`，保留原无参 Completed 终止信号；所有 exhaustive when 显式接入。新 companion collector 记录证据，旧调用方可忽略新事件但保持原回复行为。缺证据只能 UNKNOWN；不得从流关闭反推 COMPLETED。
- 提炼 `PromptExtrasCore.kt:351` 的归一化规则供主流复用，不另写认证/提供商选择。UNKNOWN 的接受规则在生成契约中统一：普通自然语言“结构完整”不能证明远端没截断，建议保留为未确认结果供回看/处理，默认不自动当完整来源；不能沿用辅助结构化产物的接受规则而不校验。
- 路径选择以**新 companion contract + 非空 turn link + 当前单角色线下场次**为准，不用 `interactionMode != ONLINE_PHONE` 泛化接管所有旧消息。没有 link 的旧线上、群聊、长文、旧线下行继续 legacy；切回线上保留 link 与语义投影，不能把新控制行重新解释成用户台词。
- 如果完整范围最终也升级单角色 OFFLINE_LONGFORM，应让其明确加入同一 contract，并保持原 `<char>/<thought>` 协议；多人及线上 JSON 动作/逐角色回复继续旧策略。共享 Runner 的旧“回退历史再重试”不直接全局改写。

### 7. 必须新增与可复用接口

- 新增：场次/turn/attempt 仓储的 `beginTurn / claimAttempt / saveGeneratedArtifact / commitAttempt / retryCommit / cancelAttempt`（等价命名可）；受控单消息 CAS；统一资格投影、CONTROL_NOOP 摘要证据；finish evidence 采集。共用一个数据库/仓储写入口。
- 复用：RoomConversationStore 的事务与映射、MemorySourceStorage 的首次身份/版本/失效、v2.4 的 recall/提取/任务恢复、现有 AiGateway 三协议与错误脱敏；**MemoryProcessingJob 仅用于记忆加工**，不把陪伴主回合强行塞入其 purpose/type。
- 后续验证必须覆盖：COMPLETED 但 waiting 的 INPUT 被后台/手动/兼容引擎/摘要拒绝；CONTINUE 不出现在提取或完整原文补充里且不堵连续覆盖；提升保留初始 persona；两 owner claim、旧 revision 重生成、artifact 写失败、重开数据库免费补保存、提交成功回执丢失、REQUESTED 不自动重发、旧线上/群/长文模式回归。

## Related specs / Caveats

- 相关规范：`.trellis/spec/backend/database-guidelines.md`、`memory-system-guidelines.md`、`memory-management-guidelines.md`、`network-guidelines.md`、`error-handling.md`；原功能参考为手册 §6、§13、§17–18。
- 本文件只细化数据/状态契约，不决定 UI、供应商、声音或收费策略。主会话落最终设计时需统一 enum 名称、UNKNOWN 接受规则和 child 所有权，避免把两个提案各实现一遍。
