# 陪伴场次与可靠回合

## 1. 适用范围

修改显式单角色线下陪伴的场次、请求、保存恢复、消息资格或生成完成证据时阅读。
新路径由 `companionTurnId` 与场次关联选择，不能以“不是线上”接管群聊、长文和
无关联的旧线下消息。依赖装配仍在 `AppGraph`，复用既有主库与 v2.4 记忆运行。

主要实现位于 `model/CompanionTurn.kt`、`data/local/companion/`、
`data/repository/roleplay/RoomCompanionRepository.kt` 和
`conversation/CompanionTurnCoordinator.kt`。

真实产品入口、持久开场选择、原路返回和最后发送前校验见
[陪伴入口与授权上下文](./companion-entry-context-guidelines.md)。

## 2. 接口与数据库

主库 54→55 新增 `companion_encounters`、`companion_turns`、
`companion_attempts`，以及 `messages.companionTurnId`（空串）、
`messages.companionMemoryState`（`legacy`）。不为旧消息推测场次，不修改旧
ID、时间、原文或旧 schema。场次绑定已有 run；新场不是重开剧情。

- 场次 `activeKey` 可空且唯一；DRAFT/ENDED 不占活动槽。
- `UNIQUE(encounterId, ordinal)` 固定逻辑顺序，输入/回复消息引用各自唯一。
- conversation → encounter → turn → attempt 级联清理；消息双向引用由事务维护，
  不增加循环级联。
- `selectedAttemptId` 表示已提交答案，`activeAttemptId` 表示当前尝试。生成中
  不清空已提交答案或撤销其来源资格。
- 尚无 canonical 的 INPUT 编辑后，可以保留已失效 attempt 的恢复指针；必须结合
  attempt state 判断是否仍在请求，不能把非空 activeAttemptId 当成网络正在执行。
  用户显式重试才创建新 attempt 并冻结最新输入/来源版本；旧 claim/产物不能提交，
  删除输入后也不能借该指针恢复。

核心接口：

```kotlin
// CompanionRepository
suspend fun beginTurn(
    encounterId: String,
    kind: CompanionTurnKind,
    input: ChatMessage? = null,
    descriptor: CompanionRequestDescriptor = CompanionRequestDescriptor(),
    turnId: String = UUID.randomUUID().toString(),
): CompanionPreparedTurn
suspend fun newAttempt(
    turnId: String,
    descriptor: CompanionRequestDescriptor? = null,
    allowUncertainRetry: Boolean = false,
): CompanionAttempt?
suspend fun claimAttempt(id: String, owner: String, leaseMillis: Long = 120_000): CompanionAttempt?
suspend fun saveGeneratedArtifact(id: String, owner: String, artifact: CompanionGeneratedArtifact): Boolean
suspend fun commitAttempt(id: String, acceptOpening: Boolean = false): CompanionCommitResult

// CompanionTurnCoordinator
suspend fun generate(
    attemptId: String,
    stream: suspend (CompanionAttempt) -> Flow<ChatStreamEvent>,
): CompanionGenerationResult
suspend fun retrySave(attemptId: String): CompanionGenerationResult
suspend fun acceptOpening(attemptId: String): CompanionGenerationResult
```

## 3. 数据与生命周期契约

### 请求、产物与提交

- `INPUT`、`CONTINUE`、`OPENING` 是持久类型。普通输入“继续”仍是 INPUT；
  CONTINUE 的短显示文本是 `COMPANION_CONTINUE_DISPLAY_TEXT`，不保存技术提示词。
- attempt 按 NOT_STARTED → REQUESTED → RESULT_READY → COMMITTED 推进；
  FAILED、UNCERTAIN、CANCELLED 与生成完成状态分别记录。
- 请求前持久 claim 成功才调用网络。网络在事务外；租约到期不能证明上游未收费，
  UNCERTAIN 不自动领取或重发，另一次请求需要明确的新 attempt。
- 协调器在 claim/网络前原子预留恢复槽；请求中的占位与待补保存产物合计最多 8 个。
  不能仅限制“已经收到”的缓存数。丢失 lease 必须取消收集作用域，不能只停心跳。
- 完整产物先独立写入 attempt 并计算 hash，再执行 canonical 事务。事务校验
  turn mutation、active attempt、输入/旧答版本、冻结运行与来源引用，统一保存消息、
  selected attempt、来源资格及 ingress。
- 首次产物写入失败返回 `NeedsSave(artifactPersisted = false)`，仅当前进程保留
  有界结果；设备存储完全失败后不承诺跨进程找回正文。
- `retrySave` 只重试存储，不接受网络 lambda。已落盘 RESULT_READY 可关库重开后
  补保存；OPENING 补存只恢复预览，正式接受仍调用 `acceptOpening`。
- DIRECT 创建 ACTIVE 场次，不生成开场；其它开场在 DRAFT 中预览，接受成功才发布
  原文和激活。新选择会持久淘汰旧草稿，旧接受动作不得关闭后来激活的新场。
- 结束场次或返回线上后，有效旧场已收产物可仅补保存，不重新激活或改变当前场次。
  已接受开场的 reroll 也适用；从未接受且已废弃的开场不能借补存成为经历。
- 同场开始新回合、编辑或删除来源后，旧 reroll 不能迟到覆盖。安全替换保留原回复
  ID、createdAt 和逻辑位置；旧回退/重回编辑仍是独立功能。
- canonical 仍对应原 attempt 和原提交版本时，重复 COMMITTED 返回原回执，
  `alreadyCommitted = true`，不再执行 posthook。后续修订使原版本失效时返回
  TURN_CHANGED，不把新答案作为旧请求的回执，也不为重试复活已删正文。

### 来源资格

`ChatMessage.companionSourceEligibility()` 与 Room 的 `CompanionSourceStorage`
统一投影为 TEXT_ALLOWED、CONTROL_NOOP、UNAVAILABLE：

- 无 link 且为 legacy 的已完成、未撤回旧消息按旧路径读取。
- waiting INPUT 即使 UI 为 COMPLETED 也不可供其它回合或机器提取；首次保存冻结
  persona/可见角色。提交提升为 ready 时继承首次身份并生成新来源版本。
- linked 消息必须核对 message/turn/encounter 的 ID、版本和运行关系，且实际
  selected attempt 存在、属于该 turn、为 COMMITTED，冻结身份/运行与场次一致。
  只检查 selectedAttemptId 非空不构成证明。
- CONTROL_NOOP 提供 ID/revision/kind 的连续覆盖证据，不变成用户言行，也不能被
  `completeInput` 或兼容引擎重新拼回正文。未知空洞仍不可被跳过。
- Room 读取后生成瞬时 `CompanionSourceProof`，其指纹覆盖完整消息 JSON。
  `copy` 改正文/状态/link 后旧 proof 失效；JSON 导入不信任或恢复该瞬时 proof。
  非空 link 缺图、未知资格或单边字段缺失一律保守拒绝，不降级成 legacy。
- 自动/手动来源、原文读取、兼容提取及摘要均复用上述投影。原位编辑与删除通过
  Repository 增加 mutation 并走 v2.4 来源失效闭包，不能绕过统一存储入口。

### 完成证据

`ChatStreamEvent.CompletionEvidence(status, rawFinishReason, rawStatus)` 与旧
`Completed` 传输终止事件分开。`AssistantReply.completionEvidence` 默认 UNKNOWN。
三协议的流式、非流式、回退及工具最终答案通过已有网关统一归一化。
HTTP 200、非空正文、EOF、推理或工具中间轮都不是完整剧情证明。

陪伴 collector 要求正确终止及 COMPLETED 正文；未知、截断、过滤、取消、纯推理
或 HTML 错误不能发布 ready 来源。旧消费者仍使用原 Completed 语义，不能全局改变
原 RP 主动停止保留片段的行为。请求描述只存模型/Provider 引用、版本和来源修订，
不得保存 Key 或完整 ProviderSettings。

## 4. 校验与错误矩阵

| 条件 | 可观察结果 |
| --- | --- |
| 恢复槽已满或该 attempt 正在生成/待补存 | Rejected(INVALID_INPUT)，未开始新的网络请求 |
| claim 或 active attempt 已变化 | Rejected(ATTEMPT_CHANGED)；不发送或不覆盖 |
| claim 的持久写入失败 | 向调用方传播保存失败，零模型调用并释放预留恢复槽 |
| lease 丢失或用户取消 | 取消 collector，传播 CancellationException，保留原 canonical |
| artifact 首次落盘抛错 | NeedsSave(false)，当前进程可重试存储 |
| artifact 已存、canonical 事务失败 | NeedsSave(true)，可重启补存且零新模型调用 |
| 开场预览或非完整产物 | Generated(attemptId, status)，不代表 Saved |
| 产物 hash 不符 | INVALID_ARTIFACT，不发布来源 |
| 生成完整性不足 | 提交拒绝 INCOMPLETE_RESULT，原答仍保留 |
| 输入/引用来源变化 | SOURCE_CHANGED 或对应 turn/context 冲突，拒绝迟到提交 |
| 已提交请求后来被重生成/修订/删除 | TURN_CHANGED；缺失整个关联图时按相应缺失冲突处理 |
| selected attempt 缺失、错属、未提交或错运行 | UNAVAILABLE，原文/手动提取/摘要不能旁路放行 |

## 5. 正常、基础与异常案例

- 正常：INPUT 先保存为 waiting，完整结果独立落盘，canonical 事务提升来源；
  回执丢失后重试同一提交不增加消息、修订或提取任务。
- 基础：打开旧无 link 消息不造场次；DIRECT 只创建新场，后续用户动作才生成。
- 异常：旧答生成替代品时，用户已开始下一轮或编辑旧答；迟到产物不得覆盖新状态。
  首次全落盘失败且进程死亡只剩不确定请求证据，不能自动重发并声称免费恢复。

## 6. 必需测试与断言

- `CompanionRepositoryTest`：双 owner、pending 来源、control 覆盖、相同输入不同
  turn、保旧答、源修订/删除、新场与新回合拒绝迟到、线上历史 CRUD。
- `brokenSelectedAttempt_deniesOriginalsManualSourcesAndSummary`：缺失/跨 turn/
  未提交/错 run 的 selected attempt，在原文、手动来源及摘要链共同拒绝。
- `CompanionTurnCoordinatorTest`：文件型 Room 关闭重开补存零模型调用、首次产物
  写入失败的进程内结果、取消与未知结果不发布。
- pending INPUT 编辑恢复须覆盖请求前/请求后、关库重开、最新 source revision、
  显式新 attempt 及删除后不可恢复；请求前持久写失败用真实 Room 断点证明零网络
  调用且不泄漏恢复槽。
- `concurrentRequests_reserveBoundedRecoverySlotsBeforeCallingModel`：并发第 9 个
  请求在网络前拒绝；`lostLease_cancelsCollectorAndDoesNotPublish`：失去 lease
  立即终止流；`committedRetry_afterRerollEditOrDelete_rejectsStaleReceipt`：
  不返回后来新答，不重复 posthook。
- `CompanionSourceProofCompatibilityTest`：旧/缺失/null 字段、伪造导入 proof、
  copy 修改及无歧义指纹；`AiGatewayCompletionEvidenceTest`：三协议真实 HTTP
  和工具/回退，必须实际执行，不能把初始化失败算方法通过。
- `CompanionFoundationMigrationTest`：固定 54 真实 Room 升级、旧数据/默认值、
  三表唯一约束与级联；同时运行受影响的旧迁移与来源回归。

## 7. 错误与正确做法

```kotlin
// 错误：保存失败后又调用文本模型；或用旧回退函数先清空 canonical。
coordinator.generate(attemptId, stream)

// 正确：已收到的产物只补存；开场预览保存成功后仍需单独接受。
val recovered = coordinator.retrySave(attemptId)
val accepted = coordinator.acceptOpening(openingAttemptId)
```

不能用 `message.status == COMPLETED` 或 `selectedAttemptId != null` 判断新消息
是否可进入记忆。读取实际关联图，再使用统一 `companionSourceEligibility()`；
界面显示状态、请求状态和机器来源资格各有明确职责。
