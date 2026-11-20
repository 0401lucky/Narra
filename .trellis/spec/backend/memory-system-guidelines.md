# RP 记忆空间与持久任务

## 1. 适用范围

修改记忆保存、来源、剧情运行、自动提取、召回或管理时阅读本规范。
当前基础实现位于 `model/MemoryArchive.kt`、
`data/repository/context/MemoryArchiveRepository.kt`、
`RoomMemoryArchiveRepository.kt` 与 `data/local/memory/`。
统一由 `AppGraph.memoryArchiveRepository` 装配，UI 不访问 Entity/DAO。

C1 建立存储基础；当前 C6 已接通完整引擎，并由一次 activationVersion 控制首次
启用 2。`getEngineVersion()` 返回显式兼容版本 1 时仍应使用兼容分支，不能当作
新引擎已启用。管理/暂停和资料契约分别见
[档案管理](./memory-management-guidelines.md) 与 [资料恢复](./memory-transfer-guidelines.md)。

带 `companionTurnId` 的新陪伴消息还须遵守
[陪伴场次与可靠回合](./companion-foundation-guidelines.md)：COMPLETED 显示状态不等于
可提取，waiting/缺失关联拒绝机器消费，CONTROL_NOOP 只提供连续覆盖证据。自动、
手动、兼容引擎和摘要均使用统一资格投影，不能按 ready 字符串单独放行。

## 2. 接口与数据库

主库 48→49 增加记忆空间、绑定、来源、版本、持久任务及派生数据表；
`messages.memoryRevision`、`roleplay_scenarios.memoryPurpose` 和
`roleplay_sessions.runId` 同步迁移。schema 由 KSP 导出，不手工改身份哈希。

49→50 增加来源格式版本、任务 purpose / 冻结 requestJson / 独立 requestState、
完整审批凭据及摘要的来源证明和运行缓存。旧 49 schema 保持原字节；旧任务默认
请求状态 UNKNOWN，只有空产物的 PLANNED 任务可以确定为 NOT_STARTED。
新任务显式 NOT_STARTED，请求前只允许从该状态原子改为 STARTED 一次，取得 lease 后
也要复查最新请求状态。失效原因可以变化，但请求状态
在归档、generation 改变及明确删除正文时都保留，不能靠 failureCode 推断未收费。

主要接口：

```kotlin
suspend fun bindConversation(
    scenario: RoleplayScenario,
    conversationId: String,
    characterIds: List<String>,
    runId: String,
): List<MemoryRuntimeContext>

suspend fun getRuntimeContext(conversationId: String, characterId: String): MemoryRuntimeContext?
suspend fun planJob(
    context: MemoryRuntimeContext,
    idempotencyKey: String,
    sources: List<MemorySourceRef>,
    targetRevisions: Map<String, Long> = emptyMap(),
    requestJson: String = "",
): MemoryProcessingJob
suspend fun claimJob(jobId: String, owner: String, leaseMillis: Long = 120_000L): MemoryProcessingJob?
suspend fun saveExtraction(jobId: String, owner: String, artifact: String): Boolean
suspend fun commitJob(jobId: String, owner: String, batch: MemoryCommitBatch): MemoryWriteResult
```

人工修改走 `editRecord(context, recordId, expectedRevision, content)`，删除走
`deleteRecord` / `deleteSpace` / `deleteScenarioArchives`。
不要直接写表来绕过版本、墓碑或派生材料清理。

## 3. 数据与生命周期契约

- `MemoryRuntimeContext` 同时约束角色、空间、对话、场景、运行、身份和
  generation。它是快照；绑定、身份或代次变化后重新获取，不缓存为永久凭证。
- 日常空间按角色连续积累，独立空间按角色和运行隔离；用途不由线上/线下模式
  推断。旧场景默认 `LEGACY_COMPAT`，未知历史归属不由模型补造。
- 同一日常空间可以包含不同 persona。自动读取、来源和纠正目标必须匹配运行
  身份；明确的档案管理可以使用空 conversation 的管理上下文与最新空间代次，
  不能把该路径当成自动任务的权限回退。AUTOMATIC_EXTRACTION/TOOL_WRITE 的规划、
  领取、保存和提交都必须检查非空 conversation 的实际运行绑定；无绑定的自动/工具
  任务不能恢复执行。显式 USER_SELECTION 与 EVENT_REBUILD 可使用经过校验的管理
  上下文，但还要求角色存在及当前条款/记忆开关；不得把自动任务改成该 purpose 绕过
  运行守卫。档案管理仍可查看和清理已移除角色的旧档案。
- 角色存在于 AppSettings，不能依赖 Room 角色外键。生产仓储从 AppGraph 接收
  当前角色有效性守卫及角色可用性 Flow；失效角色不能继续自动读写，已订阅的
  运行列表在设置删除角色后也要清空，不能等待下一次 Room 写入才生效。
- `runId` 使用独立 UUID。重开可以复用 session ID，但不能复用运行 ID；
  归档旧绑定后创建新对话/运行。日常仍绑定原日常空间，独立剧情创建新空间。
- 场景、对话、session、空间绑定与开场消息在同一事务中更新，先绑定再写开场；
  重开保留旧原文/记忆/日记，明确删除才按所示范围清理。
- `MemorySourceRef` 指向来源版本及 UTF-16 半开区间 `[start, end)`，不拆代理对。
  `MemorySourceStorage` 负责源消息保存/编辑/删除的同步；提交还会复查原消息版本
  和正文指纹，不能仅信任来源缓存中的数字。来源 span 必须非空。修改 `createdAt`
  同样递增来源版本，提交复查时间锚点；仅改时间不能沿用旧“明天”等相对日期解释。
- 新消息首次保存即冻结 persona 与可见角色；未完成/正文为空时只保存无正文的
  `UNKNOWN` 来源凭据，不创建 ingress。完成后沿用原归属并保存可用的新 revision，
  中途切换身份/群成员不能改挂最终来源。编辑已捕获消息同样保留该归属。
  没有首次凭据的旧历史不因编辑而自动入队，仍等待用户明确选择回填。
  C2 的 `RoleplayTranscriptFormatter.memorySemanticSegments` 筛选卡片/媒体语义，
  不能把非空正文判定当成完整语义验证。
- 旧来源提示使用 `LEGACY_HINT`：有旧指针也仅为 `UNKNOWN`，缺失为 `MISSING`；
  不伪造可用原文、角色可见性或消息版本。升级不自动建立整库待提取事件。
- 同一幂等键只能复用同一冻结上下文/来源/目标版本。产物先持久保存，再原子提交
  记录、版本、来源、关联意图与游标；合法空批次和失败必须区分。提交复查所有
  输入目标版本，即使模型只输出新增记录，也不能使用已经被人工改写的旧目标快照。
  同一冻结请求再次规划时先复用已有任务，已成功纠正导致的目标 revision 变化
  不能使自己的幂等重试失败；重复提交返回原回执，不再写一次修订。
- 处理游标按真实来源 span 的连续覆盖推进，不能用已处理数量之和跳过空洞。
  网络调用在事务外；长请求需要维护 lease，并检查保存产物的布尔结果。
- 人工编辑保留 ID 并增加 revision。删除清理正文版本和派生正文，保留不含正文
  的墓碑；来源或记录变化后，旧摘要、认知和向量不能继续代表新版本。
- 原消息先编辑再删除时，`CHANGED` 的旧来源也必须清理，不能遗留失效记录正文。
  清理对话只失效消息/旧提示来源；人工修订的独立 `MANUAL` 来源与当前版本保留。
  删除的派生正文包括关系意图的事件名；迁移记录的旧 `memory_entries` 兼容副本
  与新档案在同一事务删除，不能从兼容读取重新出现。
- 建议的 `sourceRefsJson` 使用 `List<MemorySourceRef>`。来源修改/删除时，同角色、
  同空间内引用它的待审批材料也须擦除正文并置为 `INVALIDATED`，不能留下可再次
  批准的旧事实；其它来源的建议保留。

## 4. 校验与错误

| 情况 | 行为 |
| --- | --- |
| 运行绑定归档、角色失效、身份或 generation 改变 | 旧运行读取失效；新提交拒绝，调用方刷新上下文 |
| 来源修改、删除或原消息指纹不符 | 不接受旧来源，不能以空产物推进处理位置 |
| 目标不属于当前角色/空间/身份或 revision 不符 | 拒绝纠正，不修改其他身份或人工新版本 |
| lease 被占用/过期、job 阶段不符 | claim 返回 null、产物保存返回 false，或提交返回 JOB_CHANGED |
| 非法批次、重复 local ID、越界来源/目标 | 拒绝整批，不部分写入 |
| SQLite 写失败 | 事务整体回滚；错误传播，不把失败伪装成已提交 |
| 协程取消 | 清理后继续抛出取消，不转换成普通业务失败 |

提交的预期冲突通过 `MemoryWriteResult.Conflict` 返回；reason 包含
`SPACE_CHANGED`、`SOURCE_CHANGED`、`RECORD_CHANGED`、`JOB_CHANGED`、
`INVALID_INPUT`。更前置的 job 失效可能先返回 `JOB_CHANGED`，测试应明确
自己实际驱动的边界。数据库异常与这些可预期冲突分开处理。

## 5. 正常、兼容与异常样例

- 正常：同一已保存产物在提交失败后恢复，不再次请求模型；完整提交一次，重试
  返回已提交结果，不新增重复记录。
- 兼容：旧库升级仍保留原 memory_entries 及完整字段，新记录保持旧 ID、
  `LEGACY_UNCLASSIFIED` 和来源未知标记；专用旧心境另投影到当前状态。
- 异常：重开后拿旧 runtime 查询返回不可用，不能据此断言旧档案已删除；
  应用明确的档案上下文检查旧内容是否仍在。

## 6. 必须验证的行为

- `MemoryFoundationRepositoryTest`：事务真实回滚、幂等、分 span 连续游标、
  重启/lease、人工编辑竞争、跨身份读写、角色失效、派生失效及消息 ID 复用。
- `RoleplayRunRepositoryTest` / `RoleplayRunStorageTest`：先绑定后开场、日常与
  独立重开、旧数据保留、身份/成员变化、并发启动、失败回滚和删除范围。
- `MemoryFoundationMigrationTest`：固定 48.json 创建真实隔离旧库，再用 Room
  执行 ALL_MIGRATIONS；逐字段检查原数据、来源、运行及禁止自动历史回填。
- `ChatDatabaseMigrationRegistryTest` 与既有旧库/原文删除测试继续回归。
- S3 升级性能 fixture 必须匹配实际相邻版本及固定源 schema/SHA；保留版本守卫，
  不提前执行迁移来伪造被测源库。对应契约为 `LegacyUpgradeFixtureContractTest`。

用例存在不等于通过。实际命令、结果与设备限制记录在任务 research 中；
只使用合成数据、隔离数据库和 Fake/Test API。

## 7. 错误与正确用法

错误：重开后继续用旧 runtime 查询，把空列表当作数据已清理的证据；
或为了让测试通过而放宽运行权限。

正确：分别验证旧运行拒绝访问和显式档案仍可读取；明确删除则另外检查
实际墓碑、正文版本和派生正文被清理。

错误：模型返回正文后直接插入记忆并提前增加处理计数。

正确：冻结源/目标快照，持有有效 lease，检查产物持久保存成功，再调用统一
`commitJob`；仅提交成功的连续来源范围推进游标。

## 8. C2 提取、恢复与摘要

- `DurableMemoryExtractionCoordinator` 从持久 ingress 规划完整来源；单空间串行，
  最多两个空间并发。连续线上助手气泡按一次逻辑回复计数，保留既有自动频率。
  被保留等待人工处理的旧来源不累计为新请求频率，恢复旧任务也不降低新来源的触发阈值。
- 格式 2 的 UTF-16 span 相对 `MemorySourceEvidence.content` 的完整语义快照，
  不是原 `ChatMessage.content` 的字节/字符偏移。格式 1 仍按旧正文算法验证。
  内联 Base64 必须先在完整来源中定位，再拆 span；不能只逐段匹配 `data:`，否则
  长负载的尾段会失去前缀而漏入请求。正文中的普通 http(s) 网址继续保留。
- v2 解析必须接受完整对象并严格检查字段、枚举、M/O/N 引用及目标 revision。
  截断、过滤、reasoning-only、空正文与非法结构不是合法空记忆。完成状态 UNKNOWN
  只能在整个结构通过校验后接受。旧自定义模板显式使用 LEGACY_LIST，不回退混解析。
- requestJson 冻结完整 span、目标、模板、名称和时区，不包含 ProviderSettings 或密钥。
  原响应和完成状态先持久保存；state_updates / resident_candidates 留在 artifact，
  C4/C5 通过 `listCommittedExtractionJobs`、`getJobRecordIds` 获取并复查当前版本。
- 前批纠正会改变后批冻结的目标。`replanUnrequestedJob` 仅允许 NOT_STARTED、
  没有产物/不确定请求标记且没有有效 lease 的任务原子建立新快照；原任务保留历史，
  来源、模板和时间锚点保持冻结。已经请求或收到产物不能借重规划自动补发模型请求。
- `resumePlannedOnStartup` 在应用协程内等待已存产物的旧 lease，到期后免费提交。
  等待按空间去重、可取消，并持续检查条款、开关和运行；不新增后台周期任务。
  续租持续到模型工作完成，提交/释放之前先停止心跳，防止把自己的正常结束误报为丢 lease。
- 同一来源仍有旧运行产物或 STARTED / UNKNOWN 请求证据时，新的自动上下文不得重复
  请求这些范围。返回 REQUIRES_MANUAL_REVIEW，保留 ingress 缺口；新来源仍可处理。
  该范围不算成功覆盖。只有用户明确选区/重试才可另行决定处理，不自动改绑旧 job。
- USER_SELECTION 和 TOOL_WRITE 共享来源、版本及事务守卫，但不计入自动已覆盖 span
  或推进自动 cursor。工具建议通过 Room 持久审批，重复确认不重复建记录；执行和可用性
  都核对请求角色与冻结 context，不能借同群另一角色的合法 context 读取私有摘要。
- 摘要保存完整 ID/版本/指纹和 runtime，ERROR、LOADING、空消息与未授权私有混合消息
  都保留真实位置上的缺口。后段可缓存和复用，但不得用数量求和或先筛 completed 越过缺口。
  UNKNOWN 产物不自动重发，却不授权裁剪，也不能被一次 COMPLETED 合并洗成可靠覆盖。
- 摘要段及总摘要缓存按角色/空间/persona/run/generation 区分，A→B→A 复用各自产物。
  bound RP 的无 scope `getSummary/observeSummary` 返回空；旧正文仍保留。
  群请求投影同时处理正文、parts 和引用预览等实际 DTO 字段，不改写持久原文。
- 摘要未达到触发阈值时沿用已有消息快照，不为每次普通发送/重试重新读取全历史。
  真正生成时再读取完整持久来源。TITLE_SUMMARY 的完整 ProviderSettings 必须与其
  baseUrl、key、model 和协议来自同一提供商，不能误传当前聊天提供商的 Responses/path 配置。

对应回归：`DurableMemoryExtractionCoordinatorTest`、`MemoryExtractionBatcherTest`、
`MemoryExtractionParserTest`、`PromptExtrasCompletionResultTest`、
`MemoryArchiveToolFlowTest`、摘要 Coverage/Coordinator/PrivacyFlow/Room 测试及
`RoleplayDurableMemoryFlowTest`。原生 `MemoryExtractionMigrationTest` 使用固定 49，
`MemoryFoundationMigrationTest` 使用固定 48 并升级到 CURRENT_VERSION；不能把历史
目标版本断言改成跳过实际字段和数据保留验证。

## 9. C3 召回、可选向量与发送回执

- 50→51 增加消息 `memoryRecallRequestId`、回执的完整 runtime/锚点/传输记录，
  以及向量索引的 persona/configuration、完整重建标志、批次进度和未决请求 token。
  固定 50 schema 保留原字节，S3 相邻源为固定 50；旧索引/回执原行不删除，缺失新配置
  的索引不能被当作当前空间已就绪。完整引擎仍由 `getEngineVersion()` 控制，C3 通过
  不代表 C4/C5 的维护或 C6 的完整产品接线已完成。
- `MemoryRecallEngine` 使用 `getRecallSnapshot` 的合法来源，当前 user burst 分别查询，
  邻近背景低权重补充；纯寒暄不借角色长回复触发经历。`MemoryLocalIndex` 在 Default
  dispatcher 按角色/空间/persona/空间 generation 缓存，先过滤再排本地/实体/日期名次。
  归档经历仍参与检索；本地索引错误重建本地路径，向量软超时或失败不丢本地结果。
- 旧资料仅通过 `legacyRecords` 专用兼容分支在 LEGACY_COMPAT 下按原角色和 scope 读取，
  `useGlobalMemory` 保持原筛选。UNKNOWN/MISSING 来源继续是旧未验证材料，不升级为
  确认事实，也不进入向量候选。CONTINUOUS/INDEPENDENT 不自动借用此分支。
- 常驻认知、有效置顶、经历/事件和当前状态共享默认 2000 估算 token；配置范围
  256–16000，目标份额 600/200/1000/200 可借用。标签、范围标记和记忆标题计入预算。
  条数只限制经历；活动容量只归档当前角色/身份的超额 EPISODE，不裁剪正文/历史版本。
  长记录片段保留真实 UTF-16 范围与必要命中，不能缩片段时剪掉命中词；置顶同 ID
  去重时保留最佳命中范围，全文放不下仍可提供准确片段，无命中的置顶不任取前缀。
- 预设中的本轮记忆是数据。`MemoryRecallAssemblySupport.renderPreset` 在模板展开前
  暂存当前 section，展开后原样还原并重算日志段；正文里的 `{{char}}`、`<user>`、
  `{{phone_context}}`、`{{context}}` 不递归展开。其它预设/角色卡/世界书变量保持原契约。
  `reconcile` 对本轮 key 的改写残留拒绝发送；重复完整 section 连同标题去重，并记录
  省略原因，不能丢掉 plan 项却留下已改正文。RP 继续不应用普通 CHAT 的
  `memoryInjectionPrompt`，该既有分支由 `PromptContextAssemblerTest` 验证。
- EMBEDDING 默认 DISABLED，只接受显式 Provider 分配、OpenAI compatible、CUSTOM
  非空模型及凭据。旧/null/非法 FOLLOW_DEFAULT 不能借聊天模型；删除模型/Provider
  不自动回落。认证复用 SecureValueStore，索引配置哈希不含 API key。
- `RoomMemoryVectorRepository` 的缓存属于 space/persona/configuration；索引 generation
  与 runtime 的空间 generation 分开。同一 daily/persona 换会话不重复全库付费。长文
  按所有段索引，响应数量、唯一整数序号、维度、有限值和非零范数严格验证；元数据保存
  正文 revision/指纹及范围，旧向量不代表新正文。新完整代次激活前旧合法索引仍可用。
- 每批请求前原子保存 `pendingBatchJson/requestToken`，网络在事务外，成功段先保存，
  完整校验后再激活。普通恢复不补发不确定请求，返回 NeedsReview；FULL 重启只复用
  本次重建已成功段，不能借旧 active 冒充完成。新 active 用 generation CAS，清理旧
  派生向量时仍保留未决请求证据。条款、角色开关、配置和来源在请求前后复查，取消传播。
- RP 冻结 runtime 并给本轮消息绑定唯一 requestId。该元数据不增加 `memoryRevision`
  或重新入提取队列；预览不创建发送回执。Gateway 只验证本计划的随机 renderKey，
  再按 requestId 绑定受控 system/envelope 载体；用户/助手历史里的旧块不成为控制信息。
  首次合法 payload 保存应用材料所在的文本路径，工具循环随后追加的模型/工具引用即使
  带当前 key 仍只作数据；不能误报重复，也不能在应用载体缺失时冒充新的注入。原应用
  载体内的正文改写/重复仍拒绝，省略仍记录。
  实际 Chat/Responses/Anthropic 出口复查来源和最终材料，并使用真实序列化正文的 SHA。
  Retrofit 与流式请求统一 `AppJson.gson`，验证必须比较 MockWebServer 原始 body 字节。
- 每次 HTTP 单独记录 PREPARED/SENT，服务器回应才确认发送。失败/取消保留已确认及
  尚未确认的传输，不能让新一次未确认的发送沿用上次 sent 材料。回执 Room 提交后才发布
  给按 requestId 关联的日志；关日志/重启不丢持久回执，无当前回执不回退上轮。C6
  从消息锚点接管理入口，不把内存日志当持久真相。

对应回归：`MemoryRecallEngineTest`、`MemoryRecallBudgetTest`、`MemoryRecallRepositoryTest`、
`GatewayMemoryRecallTest`、`RoleplayDurableMemoryFlowTest`、向量分段/数学/客户端/Room 测试、
`EmbeddingProviderSettingsTest`、`SettingsMemoryRecallTest`、`MemoryRecallSettingsUiTest` 与
既有设置/预设回归。`MemoryRecallMigrationTest` 使用固定 50 真实升级到最新版本；提供商
外观复用 E11 32 项 verify，不 record 或改用户既有 manifest。

## 10. C4 事件连续性与完整重整

- 51→52 只追加事件盒 `personaKey`、持久任务 `eventId` 和 eventId/status 索引。
  固定 51 schema 保留原字节，S3 相邻来源更新为固定 51。旧盒仅在非空且全体成员
  同角色/空间、同一非空白且非 unknown 身份时回填；其它旧行、正文和成员均保留。
  默认未配置 persona 的合法新运行本身也使用 `unknown`，因此列表、详情与召回还要
  核对非空及全部成员归属，不能仅凭盒子字段匹配就暴露旧 mixed/跨空间盒的元数据。
- `MemoryEventStore` 是主库事务内部实现，公共操作继续走 `MemoryArchiveRepository`。
  只消费受控 `same_event`，核对两端 revision、角色/空间/persona 和当前有效来源。
  同主题或同名不自动合并；已分属两个盒子返回 `DIFFERENT_EVENTS`，重复关联不改
  标题、时间、代次或成员。显式 `unlinkEventMember` 保留原记录。
- 新成员为 LIVE/无 coveredRevision，不增加事件 generation；已冻结工作只归档自身
  覆盖的版本。编辑、删除、解绑、归档切换和人工重整提高事件 generation，阻止迟到
  提交。成员编辑/删除/解绑使旧摘要和全部旧覆盖失效，但不清理其它盒或无关来源。
- `MemoryEventCoordinator` 由提交/人工编辑增量、显式重整或应用恢复触发，复用 MEMORY
  的完整 ProviderSettings；不新增默认周期收费任务。自动阈值为四个未覆盖成员或
  6000 字符原记录，完整成员及其来源按 12000 字符软目标分组。每批输入在网络前
  校验，不截尾；极端单成员完整输入超过 1,000,000 字符时保留原料并拒绝发送。
- `MemoryEventSummaryWork` 一次冻结所有来源与版本。FULL 首批不携带旧摘要；后批
  只用本次已完成中间产物和下一组完整原料，`previousSummaryFromCurrentWork=true`，
  真实已发布基线可以为 0，不能虚造 revision。普通 INCREMENTAL 才复用合法 READY
  旧摘要及其确切覆盖；所有批均完成后才原子发布摘要、进展、revision 和成员归档。
- `EVENT_SUMMARY/EVENT_REBUILD` 复用 processing_jobs。每批请求前持久保存
  `pendingBatch`，原 `PromptCompletionResult` 先落盘再解析；只有 COMPLETED 和严格
  完整结构授权下一批/最终发布。UNKNOWN、截断、非法结构和未决网络请求保留原证据，
  不自动重发，不能用后批的成功洗成可靠覆盖。删除正文仍保留独立 requestState。
  普通 `commitJob/markRequestStarted/saveExtraction` 不接受事件任务。
- 工作幂等键包括完整 runtime。旧运行失效后，仅确定 NOT_STARTED 且无产物的任务
  可以按新上下文重规划，旧历史不改绑；同事件的已请求/未知/已收产物先进入 NeedsReview，
  不能借新 key 绕过费用保护。另一次收费重整需要显式 manualRebuild；存在未决请求
  时还须明确 retryUncertain。
  自动整理拒绝空 conversation 管理上下文；人工重整仍检查当前角色、条款和记忆开关。
- 启动恢复按每个安全可恢复任务及空间活跃 lease 独立决定等待，不根据单个汇总错误
  决定是否恢复其它事件。待审事件不能阻止另一已存产物在 lease 到期后免费提交。
  不偷活跃 lease，心跳在提交/释放前停止，协程取消继续传播。
- 摘要 supportingRecords 仅表示实际覆盖版本；associatedRecords 包含合法新 LIVE。
  `eventMemberships/eventGenerations` 独立提供合法关联，EMPTY/STALE 也能展开当前
  原记录，不伪造 EVENT 摘要或 revision。旧进展标为“摘要覆盖时的进展”，存在未覆盖
  成员时明确提示；即使新进展因预算省略，也不能将旧状态称为最新结果。C5 消费
  `listEventConclusions` 时继续以 coveredRecords/sourceRefs 为依据。
- 关联展开的 RECORD ref 携带 `eventContext(eventId, generation, anchor版本)`，
  最终 HTTP 出口复查关联及代次；直接命中只使用自身来源。新增合法成员不破坏原有
  关联证明，解绑/编辑后的旧“同事件”标签不能继续发送。回执沿用原 JSON 存储。
- Engine 先登记全部直接命中，再展开事件；经历条数按 priority 裁决，置顶继续保留
  独立额度与最佳命中范围。不能让关联候选覆盖同 ID 的长尾命中或挤掉另一当前主题。
  LocalHit/VectorHit 没有 24 字符最小窗口契约；短范围先补周边原文，预算不足时只
  收缩到完整 required 命中，不能因固定字符/token 下限丢弃本可发送的有效短范围。
  所有边界仍为合法 UTF-16，正文/历史不因片段选择而被改写。

对应回归：`MemoryEventRepositoryTest`、`MemoryEventCoordinatorTest`、
`MemoryEventSummaryFormatterTest`、`MemoryEventSummaryParserTest`、
`MemoryEventPromptServiceTest`、`MemoryEventRecallStateTest`、`MemoryEventRecallFlowTest`，
并继续执行 C1–C3 来源/运行/预算/向量/实际 Gateway 回执测试。`MemoryEventMigrationTest`
从固定 51 真实升级，既有固定 48/49/50 测试继续升到最新版本；不通过修改旧 schema
或删除断言来适应新归属字段。完整引擎切换和管理 UI 见 C6 档案管理规范。

## 11. C5 长期认知与时效状态

### 11.1 适用范围

修改认知/状态的提取、持久恢复、人工维护或召回时，使用 `MemoryKnowledgeStore`、
`MemoryKnowledgeStorage`、`MemoryKnowledgeEditing` 及统一 `MemoryArchiveRepository`。
C5 阶段不自动启用完整引擎；当前 C6 已通过一次本地激活接入版本 2，显式兼容
选择仍保持版本 1，详见档案管理规范。

### 11.2 接口与数据库

52→53 增加 `memory_knowledge_revisions`、`memory_knowledge_sources`、
`memory_knowledge_consumptions`，状态增加 `editedByUser/observedAt`。版本按
`(ownerKind, ownerId, revision)` 标识，来源关联精确版本与 UTF-16 span；多态 owner
由领域事务验证和清理。消费凭据与派生写入同事务，不包含可复活的正文。
固定 52 schema 保留原字节，S3 使用固定 52→53。

```kotlin
suspend fun consumeCommittedKnowledge(context: MemoryRuntimeContext): MemoryKnowledgeProgress
suspend fun editResidentEntry(
    context: MemoryRuntimeContext, id: String, expectedRevision: Long,
    content: String, subjective: Boolean,
): MemoryKnowledgeWriteResult
suspend fun endCurrentState(
    context: MemoryRuntimeContext, id: String, expectedRevision: Long,
): MemoryKnowledgeWriteResult
```

### 11.3 动作、恢复与生命周期

- `knowledgeTargets.version=1` 冻结 R 目标及状态 revision；认知动作 add/replace/
  reinforce/retire，状态 set/end，字段名为 `target_ref`、`expected_revision`、
  `durability=stable|temporary` 和可选正整数 `expires_at`。未输出动作不清空；
  无目标的旧 C2 快照不能替代任意现存条目。
- 新提取在 `commitJob` 原子写入认知/状态及消费凭据，只有知识、没有经历的结果
  同样可追溯。旧产物消费读取实际 job/localItemId 输出映射，复查来源、输出版本、
  空间代次和身份；不重算旧 ID，也不补发模型请求来取得字段。
- `MemoryResidentLimits` 默认 12/10/10/8，每类 0–100；通过
  `AppSettings.memoryResidentLimits`、`saveMemoryResidentLimits` 保存。仅限制常驻
  读取并继续受 C3 token 预算约束，不物理删除存档或来源历史。
- THOUGHT/DESCRIPTION 来源保留主观标记；temporary 或与状态正文重复的候选不
  固化为稳定认知。背景明确既有角色卡、设定、世界书优先，不要求复述或催办。
- mood/location/task/appointment 默认寿命分别为 6 小时/24 小时/30 天/7 天，
  按原来源观察时间计算。恢复旧产物不续期；有效读取及目标快照识别 EXPIRED/ENDED，
  HTTP 出口继续复查来源和状态，不能发送准备后已过期或已结束的材料。
- 人工编辑保留 ID、递增 revision，建立独立 MANUAL 来源；原消息删除只清理依赖
  原消息的旧修订，用户的新修订保持。历史 UNKNOWN/CHANGED 来源仍保留真实引用，
  不因不可召回而丢失历史，也不因此升级为可信。
- 归档只改变可用状态，必须保留原正文来源和支持版本。RETIRE 保留旧正文时须
  同时关联原来源与结束声明；删除原消息时清理当前归档正文及相关历史。不能把
  “可用于本轮召回”的过滤结果直接用作“必须保留的历史来源”集合。

### 11.4 校验与错误

| 条件 | 行为 |
| --- | --- |
| 空间/角色/身份不匹配或旧 generation | 拒绝维护与自动消费，不借管理上下文绕过 |
| 来源或 expectedRevision 已变、目标经人工编辑 | 旧动作无权覆盖；返回冲突或记录跳过原因 |
| 旧 artifact 重复消费 | 读取持久消费凭据，不重复写入或请求 |
| 任一事务写入失败 | 消费记录、知识、来源、历史全部回滚 |
| 来源 UNKNOWN/CHANGED | 不自动注入；保留同归属的历史引用以供查看/清理 |
| 已结束/过期状态或非法主观/临时候选 | 不作为当前有效事实注入 |
| 取消 | 继续抛出 CancellationException，不伪装成功 |

### 11.5 正常、旧资料与异常样例

正常：地址更正保持同一认知 ID 并递增版本，真实请求仅包含新地址；寒暄仍可提供
合法背景。旧资料：只有 typed 候选的 C2 已存产物在重启后免费消费一次，旧未知
出处继续标未知。异常：人工编辑后旧模型结果返回，必须保留新版本并拒绝旧覆盖。

### 11.6 必需验证

`MemoryKnowledgeParserTest`、`MemoryKnowledgeCoordinatorTest`、
`MemoryKnowledgeRepositoryTest`、`MemoryKnowledgeLifecycleTest` 覆盖来源、动作、
CAS、事务/重启和时效；`MemoryKnowledgeRecallFlowTest` 核对真实 HTTP 的 ID/版本/
正文与回执，包括全部省略/部分保留。`MemoryKnowledgeProvenanceTest` 复现归档与
退役丢来源后的删除遗漏。`MemoryKnowledgeMigrationTest` 从固定 52 真实升级，
验证旧数据、默认值、FK/唯一键与合法 unknown，不用读操作补造历史证明。

### 11.7 错误与正确用法

错误：保留归档正文但只写 `sources.filter(::isCurrentlyRecallable)`，或将退役正文
仅关联结束声明。正确：保留同归属的真实历史引用，将当前可用性与出处保留分别
处理；用户删除原来源时按引用关系清理所有依赖正文。
