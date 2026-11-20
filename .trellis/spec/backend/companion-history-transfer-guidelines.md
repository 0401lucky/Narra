# 陪伴历史、原文导出与资料恢复

## 1. 适用范围

修改陪伴历史查询、原文改删、TXT/SAF、公开装扮或私人资料恢复时阅读。
来源与回合规则见 [可靠回合](./companion-foundation-guidelines.md)，
阅读与资源见 [连续阅读](./companion-stage-reading-guidelines.md)，
独立收藏见 [声音](./companion-voice-guidelines.md)，
私人记忆图见 [记忆资料](./memory-transfer-guidelines.md)。

## 2. 接口与数据库

领域类型为 `CompanionHistoryQuery`、`CompanionReadingScope`、
`CompanionMessageMutation`、`CompanionTextExportResult` 和 `CompanionTransfer.kt`。
生产装配集中在 `AppGraph`，Screen 不直接访问 DAO 或 Room。

```kotlin
suspend fun authorize(scenarioId: String, characterId: String,
    archiveSpaceId: String? = null, archiveRunId: String? = null): CompanionReadingScope?
suspend fun readAll(scope: CompanionReadingScope,
    query: CompanionHistoryQuery): List<CompanionReadingRecord>
suspend fun edit(scope: CompanionReadingScope,
    edits: List<CompanionHistoryEdit>): CompanionHistoryMutationResult
suspend fun delete(scope: CompanionReadingScope,
    sources: List<CompanionMessageRevision>): CompanionHistoryMutationResult
suspend fun export(scope: CompanionReadingScope, query: CompanionHistoryQuery,
    documentUri: String?): CompanionTextExportResult
suspend fun resumeOperation(operationId: String): ContextTransferOperationSummary
suspend fun applyContextTransferTarget(operationId: String, planHash: String,
    target: ContextTransferSettingsTarget): ContextTransferSettingsResult
```

56→57 只新增 `context_transfer_operations`。主键 `id`；索引为
`(kind, stage, createdAt, id)`。保存 planVersion/kind/stage/revision/planHash、
目标计数、已完成设置、发布素材及记忆结果回执和时间，完整冻结计划放私有文件。
`advance` 同时比较 id、expectedRevision、planHash，成功才递增 revision。
维护全部旧 schema、固定 56 升级和 S3 source56→target57，不修改旧定义。

## 3. 可执行合同

### 授权、原文与来源

- 历史以角色/persona/space/run 和单聊绑定授权，回看不创建场次、不重绑旧运行，
  不转成模型 AllowedHistory。显式旧档冻结当前入口 origin，授权失效清旧原文。
- `date`/`month` 使用 ISO 日期/年月，`zoneId` 显式冻结设备显示时区。
  同场跨午夜仍是同 encounter；旧无 encounter 不虚构见面次数。
  `createdAt=0` 显示日期/时间未知，不显示 1970 年。
- 管理和 TXT 通过独立 `readCanonicalAll` 包含合法已存 pending INPUT/CONTINUE。
  `readerEligible=false` 的记录不能定位到 Reader、播音或进入模型/提取。
  DAO 尾参 `includePendingInputs=false` 保持默认 Reader 资格。
- legacy 必须没有已知 turn 反向关联。空 link、不同 link 或带 legacy 标签的
  已关联消息整条拒绝，不能靠剥字段绕过来源/turn CAS。
- 批量改删冻结所见 conversation/message/revision，整批预检后委托 D1 写钩子
  和同一 Room 事务；后续冲突或存储失败整批回滚。迟到生成/声音/提取不能复活旧源，
  独立人工版本和声音收藏保持自身生命周期。
- ViewModel 的读取、改删和 TXT 返回之后都确认 scope；失效或无法确认时清
  原文、选择、草稿和旧操作代次。普通 IO 错误在授权仍有效时保留窗口供重试。
  成功 page/readAll 返回也必须在发布原文前 ensureOwner→requireScope→ensureOwner，
  不能只在异常分支复核；测试要让完整旧 snapshot 成功返回后再撤授权。
  requireScope(scope, owner) 本身在 suspend refreshScope 返回后先核对原 owner，
  再决定清窗或返回；旧 NonCancellable null 回调不能取消新打开/新查询。

### TXT 与公开装扮

- TXT 使用仓储完整授权集合，不用 UI50条窗口或摘要 Formatter。UTF-8 BOM、
  原时间、真实说话人/turn 类型、完整 raw、字面标签/实体/空白均保留。
  TXT 是阅读副本，不是可恢复聊天备份。
- SAF 回调携带打开时的 requestId；取消返回 Cancelled，零仓储读取/IO。
  write/flush/close/权限失败不返回 Saved，协程取消继续传播。
  全量 snapshot 编码后、writer 之前再次 ensureActive→refreshScope→ensureActive；
  授权失效零 writer，不能仅靠写完后清窗口来阻止迟到文档写入。
- `CompanionAppearancePackage(version=1)` 只白名单包含显示选择、套装/表情、
  布局、有限资源元数据和 PNG 内容映射；不含 Assistant、人设补充、记忆/原文、
  Key、声音样本、私有路径或运行 ID。导入重映射本机资源/套装 ID。
- SHA-256 针对实际 PNG 字节；生产导出去附属元数据。单图 4 MiB、图片总量
  12 MiB、资料包/冻结计划 16 MiB；数量及名称复用 `CompanionVisualValidation`。
  预览前核对字段/版本/ID/引用、CRC、白名单块结构/顺序/位深和有界像素流。
  合法 CRC 不能代替完整 deflate/行数/过滤字节验证。

### 私人资料与阶段恢复

- 私人 ContextDataBundle 生产外壳 v3，尾加可空 `companions`，只扩已有角色
  偏好/装扮域。保留 v2.4 授权记忆和来源语义快照，不扩成全聊天、运行、音频、
  收藏或后台任务备份；v1/v2/缺域保目标，v4+明确拒绝。
- 首次 prepare 冻结包、目标 Assistant/companion CAS、Room 前置及素材 hash。
  源偏好/图片只存在一份冻结 bundle，targets 保存本机 CAS 快照，避免重复 Base64
  提前超限；旧本机 v1 冗余 target 字段仍兼容读取。
- 顺序 PLANNED→ROOM_COMMITTED→FILES_COMMITTED→COMPLETE。Room 资源和
  ROOM_COMMITTED 回执同事务，记忆提交后通知由实际外层事务持有者执行。
  无 memory 的包也有独立回执，重试不重做已完成 Room 阶段。
- 文件按 hash 只创建新入口，已有文件必须验字节后复用，changed 文件不覆盖。
  Tavern 头像输入同样在首次准备冻结；取消只清本操作未引用临时内容。
- Android 的 app_data_file 禁止硬链接。新文件先在同父目录的私有临时目录写完、
  sync并核hash，再把整个非空目录改名到固定hash/计划目录；非空目标不能被并发
  目录改名覆盖。发布和复验成功后才返回回执，不用单文件exists前检加rename。
  继续读取/复用/清理旧 `assets/{hash}.png` 与 `plans/{id}.json`，坏旧文件拒绝覆盖。
- 每个设置目标在同次 DataStore edit 完成 Assistant/companion CAS 与幂等回执。
  回执绑定 operationId/planHash/实际动作；免费补 Room ack 不重写用户后改，
  不升 revision、不复活已删除角色、不整列表覆盖未导入角色或 selectedAssistantId。
- 只有 PLANNED 可取消预览。部分完成保留恢复入口，恢复旧 operationId 不清另一份
  新预览。跨 Room/DataStore/文件分别提交，不宣称跨存储原子或无条件进程恢复。

## 4. 校验与错误矩阵

| 条件 | 行为 |
| --- | --- |
| 角色/persona/space/run/origin 失效 | 拒绝读取/写入并清旧窗口与选区 |
| 日期/月/ZoneId 非法 | 安全中文错误，不变当前查询或运行 |
| 只读旧档或混合选择有只读绑定 | ReadOnly，零改删 |
| 所见 revision/link/turn 前置不符 | Conflict，整批不部分提交 |
| SAF取消/迟到旧requestId | 零IO/零成功提示，不套用新查询 |
| 文件或数据库IO失败且scope有效 | 保留原文/草稿供显式重试 |
| 未来版本、坏字段/ID/PNG/hash/超限 | 预览前整包拒绝，不部分导入 |
| 已成功设置但Room ack缺失 | 复用同回执免费补确认，保后改 |
| 剩余设置目标CAS冲突 | 保已完成阶段，重新预览剩余目标 |
| 已取消计划/篡改冻结计划或发布文件 | 拒绝恢复，不覆盖或复活 |

## 5. 正常、旧记录与异常样例

正常：页面只加载50条，TXT从同一授权仓储导出121+全部，另一角色哨兵不出现。
旧记录：无 encounter 以原会话/已知日期显示未分场，零时间显示未知，旧包缺域保值。
异常：已提交消息被剥 link 后仍由 turn 引用，读取及改删均拒绝，旧回合修订不被绕过。
阶段恢复：Room/某角色已完成后用户改设置，继续只补剩余目标，不覆盖已成功角色。

## 6. 必需验证

- `CompanionHistoryManagementRepositoryTest`：真实 Room 的混合121+、场次/午夜、
  pending 原文与严格资格、坏双向关联、批次回滚、迟到源和独立资源。
- `CompanionTextExportTest` / `CompanionHistoryViewModelTest`：全 raw/BOM、取消、
  write/close/权限失败、请求ID、授权失效竞态、普通 IO 保窗口与脱敏。
- `CompanionAppearanceTransferCodecTest`：敏感/未知字段、路径/hash/PNG/像素流、
  合法调色板/Adam7和旧/未来私人域。
- `ContextTransferRecoveryRepositoryTest` / `AppSettingsStoreContextTransferTest`：
  文件型 Room/DataStore 关闭重开、无 memory、外层回滚、逐目标回执/CAS/后改保值、
  旧 v1 本机计划、单份有界图片、头像/文件篡改、同计划ID与取消。
- 原生 `CompanionIntegrationStoryTest` / `CompanionHistoryScreenTest`：真实生产
  AppGraph/Nav/HTTP故事、空筛选、完整原文/授权/返回、改删/SAF取消、Back层次。
  独立 Dialog 的 IME/density/TextLayout/48dp 须来自实际 owner，不能用底层 Activity
  或外层 LocalDensity 推定窗口内200%字体。
  编辑窗关闭发生在仓储窗口刷新前，不能据此认定导出/选择/删除已可用；原生故事
  先等实际按钮enabled，再验证SAF取消、数据库变化及零额外模型请求。
- `ContextTransferMigrationTest` / `CompanionTransferFileInstrumentedTest`：固定56
  全旧表保留、新回执重开、双FileStore竞争、旧单文件兼容及 Android 实际 hash 文件发布。完整 JVM、受影响截图
  verify、Lint/编译/双APK按任务计划执行，真实模型/物理设备/视觉接纳单列。

## 7. 错误与正确用法

错误：把 `uiState.records` 当全部历史，或把 pending 原文管理资格用于模型/Reader；
逐条提交批次、先报 Saved 再关流、重试整角色列表都可能丢原文或覆盖后改。

```kotlin
// 正确：从冻结授权查询完整集合，writer 关流成功后才返回 Saved。
val records = repository.readAll(scope, query)
val content = CompanionTextExportContent.encode(records, query)
coroutineContext.ensureActive()
requireNotNull(repository.refreshScope(scope)) { "导出范围已失效，请重新选择" }
coroutineContext.ensureActive()
writer.write(documentUri, content)
```

正确：分别验证阅读/管理/机器资格；使用同事务批次 CAS、真实 stage/每目标回执，
将未完成阶段与未验证效果如实展示，不用文档勾选代替实际方法/HTTP/原生证据。
