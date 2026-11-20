# 记忆资料的导入导出

## 1. 适用范围

修改 ContextDataBundle 的记忆子包、资料预览/目标/确认、来源恢复或向量缓存时阅读。
以完整档案图恢复，而非只复制正文列表。运行和管理规则分别见
[记忆系统](./memory-system-guidelines.md) 与 [档案管理](./memory-management-guidelines.md)。

## 2. 接口与数据库

领域类型位于 `model/MemoryArchiveTransfer.kt`；实现使用
`RoomMemoryArchiveTransferRepository`、`MemoryTransferWriter`、
`MemoryArchiveTransferValidation`、`MemoryTransferJson` 与 `MemoryTransferDao`。

```kotlin
suspend fun exportArchive(includeVectors: Boolean = true): MemoryArchiveTransferData
suspend fun prepareImport(bundle: ContextDataBundle, targets: Map<String, MemoryImportTarget>): MemoryImportPlan
suspend fun importArchive(bundle: ContextDataBundle, plan: MemoryImportPlan): MemoryArchiveImportResult
suspend fun notifyImportCommitted(plan: MemoryImportPlan)
```

`MemoryImportPlan` 保存本机 localPlanId、bundleFingerprint、targets、stateConflicts 与
replaceExistingStates。53→54 的 `memory_transfer_origins` 保存带 FK 的无正文出处，
`memory_transfer_receipts` 保存本机计划及无正文映射/计数；不存第二份可复活正文。
控制与激活字段随同一版迁移，不能各自再加版本或修改固定 53 schema。

## 3. 可执行契约

### 完整图与兼容

- 生产 ContextDataBundle 外壳为 version=3，记忆图继续沿用 v2.4 合同，旧 v1/v2 可读取。
  v3 的可空 companions 只扩已有角色偏好/装扮域，缺域保目标、v4+拒绝；不扩成全聊天、
  音频或后台任务备份。build/filter/isEmpty/count/preview/confirm/transaction 必须都
  识别承载域，不能只加 DTO。陪伴域细节见
  [历史与资料恢复](./companion-history-transfer-guidelines.md)。
- 图包含空间、记录与全部已存版本/每版来源、事件/成员/确切覆盖、认知/状态及
  直接知识来源/历史 supportingRecords、当前 derived links、替代关系、出处及
  可选向量 manifest。只有认知/状态的 source-only 图也有效。
- v2 图优先，和 memoryEntries 按原 ID 去重；未投影旧行仍保留，不能恢复双份或
  因 facade 切换漏旧资料。v1 保留原 scope、置顶、重要性和时间，作为导入资料，
  不凭缺失原文宣称是真实聊天证明。
- 旧摘要/分段作为 LEGACY_UNCLASSIFIED 档案材料，保留 typed 原指针/覆盖元信息。
  不覆盖本机恰好同 ID 的当前摘要，不伪造会话来满足 FK，也不授权裁剪原文。
- 不恢复外来运行绑定、可执行 job/lease/ingress/cursor、待批准操作、真实请求回执、
  引擎状态或凭据。导入回执与真实 HTTP 回执是不同概念。

### 目标、来源与删除

- 每个原空间/persona 分组由用户明确选择本机角色、空间和身份目标；分组 key 使用
  `MemoryArchiveIdentity.id` 的无歧义组合，不能以外来 owner/ID 决定本机写入。
  NEW_DAILY 与真实未来对话共用日常 identity；NEW_ARCHIVE 不伪造活跃剧情运行。
- 所有导入对象重映射 ID/关联；同槽状态替换才使用明确选中的本机 ID 和 CAS。
  导入 source 的数据库 messageId 为 NULL、conversationId 为空，旧 legacy 运行
  指针也清空；原 ID、角色、空间、身份与来源状态只存独立出处。
- IMPORTED+AVAILABLE 表示资料语义快照可读，不证明本机原聊天存在。长度、SHA、
  已知格式及 UTF-16 半开 span 在导入边界验证；正文不 trim、截尾或改换行后继续
  使用旧指纹/向量。原未知/变更/缺失来源不洗成可信。
- 真实召回和 HTTP 材料必须保留 imported 属性与资料标签；不是只在管理页显示
  IMPORTED 而把正文当作本机共同经历。最终出口按当前来源复查此标志。
- DELETED 来源不能携带可复活的记录/知识/事件缓存。事件的标题、摘要、进展须
  检查成员当前与确切覆盖版的删除来源；不能仅降为 STALE 后保留正文。不要把该
  拒绝规则扩大为一律删除 UNKNOWN/CHANGED 历史。合法无正文墓碑仍可保留出处。
- 共享 IMPORTED/MANUAL 来源的回收要检查所有 record_sources 与 knowledge_sources，
  包括历史版本；删最后一条经历不能擦掉仍被认知或其它历史引用的快照。

### 状态冲突与事务

- 状态槽为 `(spaceId, stateKey, personaKey)`；重映射主键不解决冲突。预览现有状态，
  默认冲突整笔不写；显式替换保留目标历史，按计划映射导入版本/来源并复查 CAS。
  多个原空间映到同目标形成同槽冲突时不能静默选最后一条。
- 本机 localPlanId 才是重试凭据，不信任源包批次。回执必须绑定包、全部目标/身份、
  状态冲突与替换方案；同计划重试复用已提交映射，新 prepare 才表达再次复制。
- 记忆、出处和计划 receipt 在同一事务。ALL 资料的记忆与其它 Room 资源继续置于
  外层 dataImportTransaction；后续世界书/预设失败时全部回滚。生产 D6 的完整资料
  恢复增加 Room/文件/每设置目标的独立回执；无 memory 包也可持久重开。
  设置按目标在同次 DataStore edit 做 CAS/幂等，重试不覆盖用户后改，
  不重做已完成 Room/文件阶段。旧调用的默认兼容路径保留。
- importArchive 不调用通知、不猜测线程局部 inTransaction。持有最外层 Room
  事务的调用者在实际提交后调用 notifyImportCommitted，只做本地通知；回滚不通知。
  不用它触发自动提取、事件整理或收费向量生成。

### 向量与恢复

- 核对 provider 身份、正规化 endpoint、模型、预处理版本、维度、有限非零 Float32、
  全部 chunk、UTF-16 位置、正文 revision/指纹，以及映射后的 manifest/本机代次。
  使用 C3 的共享配置指纹，不能只比较模型名或在 codec 复制另一套算法。
- 不兼容缓存保留正文并显示需重建，不照抄外来 active/PLANNED/requestToken。
  完整合法现成缓存可无 INDEX 请求恢复；没有当前 runtime 不伪造激活权限。
- `automaticIndexSelection` 区分普通记忆、合法缓存、此前明确 FULL 的冻结范围和
  新导入待确认正文。后续普通维护/启动也不悄悄 INDEX 待确认导入；显示部分就绪，
  不冒充全库 READY。`forceRebuild=true` 表达用户明确重建，未知费用另行确认。
- 已授权 FULL 工作中断后，原 manifest 与成功段仍属于同一次工作；默认恢复不因
  调用参数回默认而丢失授权或重付成功批次。新导入 ID 不继承旧 FULL 授权；未决
  批次保持 NeedsReview，不借新 key 绕过。

## 4. 校验与错误矩阵

| 条件 | 行为 |
| --- | --- |
| 非法 JSON、未知版本/枚举、重复字段/ID、缺必填或坏引用 | 导入前拒绝，不留下部分图 |
| 包/目标/身份/替换方案与已确认计划不符 | 重新预览，不把原计划成功冒充新目标成功 |
| 目标状态在预览后变化或多个头争同槽 | CAS/冲突，保留待导入包 |
| 来源/版本/空间跨界或缓存引用 DELETED 来源 | 拒绝，不降级保存可复活正文 |
| 合法旧未知/变更来源或缺原聊天 | 保留真实状态与资料出处，不制造本机消息认证 |
| 向量不兼容/不完整或费用不明 | 正文保留、缓存不激活，明确待重建/待审 |
| Room 写失败/取消 | 实际外层事务回滚，回执不提前成功，取消传播 |

## 5. 正常、旧资料与异常样例

正常：原包和本机存在同名 messageId，恢复后的来源无本机消息指针；编辑本机消息
不会误伤导入快照。旧资料：没有原对话的 v1 摘要作为明确导入材料查看。异常：
事件声称来源已删除却仍带旧进展，应拒绝而不是只标 STALE。

## 6. 必需验证

`MemoryArchiveTransferCodecTest`、`MemoryArchiveTransferRepositoryTest`、
`MemoryArchiveTransferFlowTest`、`MemoryArchiveVectorTransferTest` 用两个隔离 Room 库
执行编码/导入/关闭重开/正式读取和真实 MockWebServer 请求。覆盖 source-only、
同 ID 本机消息隔离、历史来源/删除闭包、状态 CAS、内外事务/取消/幂等、v1/摘要、
兼容/坏向量、普通维护不收费及 FULL 恢复。VM/资料页还验证目标选择、后置设置
失败重试、200% 实际窗口；仅 DTO equals 或 Fake 默认成功不构成恢复证明。

## 7. 错误与正确用法

错误：把外来 source.kind 改成 IMPORTED 却保留本机消息指针，或在内层事务返回后
立即当作最外层已提交。正确：隔离运行指针、保存独立出处与完整引用图；由实际
外层事务持有者通知提交，回执与数据共同提交/回滚。
