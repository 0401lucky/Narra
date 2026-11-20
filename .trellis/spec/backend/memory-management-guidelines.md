# 记忆档案管理与处理控制

## 1. 适用范围

修改 RP 记忆管理、原文阅读、人工维护、暂停/继续、用途选择或重开/清空时阅读。
基础来源/事务/召回规则见 [记忆系统](./memory-system-guidelines.md)，资料恢复见
[记忆资料](./memory-transfer-guidelines.md)。UI 不直接访问 DAO。

## 2. 入口与存储

生产路由由 `MemoryArchiveNavRoutes` 接入原角色高级/简洁入口，状态由
`MemoryArchiveViewModel` 协调；原文使用独立 `MemoryOriginalViewModel`。
公共领域入口为 `MemoryArchiveRepository`，关键签名：

```kotlin
suspend fun ensureDailySpace(characterId: String, personaKey: String): MemoryRuntimeContext
suspend fun getManagementContext(characterId: String, spaceId: String, personaKey: String): MemoryRuntimeContext?
suspend fun queryArchive(context: MemoryRuntimeContext, query: MemoryArchiveQuery): MemoryArchivePage<MemoryArchiveItem>
suspend fun getLatestArchiveAssistantMessage(context: MemoryRuntimeContext, conversationId: String): MemoryArchiveMessage?
suspend fun getArchivedRecallReceipt(context: MemoryRuntimeContext, conversationId: String, messageId: String, requestId: String): MemoryRecallReceipt?
suspend fun setProcessingPaused(context: MemoryRuntimeContext, expectedControlVersion: Long, paused: Boolean, jobId: String): MemoryProcessingControlResult
```

53→54 增加 `memory_processing_controls`，以空间/任务保存用户暂停与控制版本；
`memory_processing_jobs.controlJobId` 让未请求任务重规划后仍继承原暂停控制。
`memory_engine_state.activationVersion` 区分首次启用与显式兼容状态。迁移还注册
资料出处/回执两张表，固定 53 schema 保持原字节；S3 相邻 fixture 为 53→54。

## 3. 可执行契约

### 管理与当前运行

- 管理 context 使用空间自身的 scenario/run/generation、明确 persona 和空
  conversation；自动/工具任务仍使用原运行守卫，不拿管理态作为失败回退。
- 角色与身份从真实档案枚举，覆盖已移除角色、只有认知/状态而无经历的身份。
  没有空间的新角色可通过 `ensureDailySpace` 明确建立日常档案；与未来真实对话
  绑定共用同一日常 identity，不造第二份空间或伪运行。
- 类别、搜索、状态和身份在分页前过滤。事件待更新取 `summaryStatus`；任务历史
  在 DAO 中按当前 persona 分页，不能让其它身份占页后再由 UI 丢弃。
- 最新回合从整个档案对话定位真实最新助手消息，再查其 requestId。它无回执或
  不属于所选角色/身份时显示无记录；不先过滤身份/空 requestId 后倒退到旧轮。
  用户显式选择旧轮与原文选区使用各自分页，不能混入默认最新回合。
- 原文读取必须验证对话属于该档案。不得调用当前 `ROLEPLAY_READING` 的
  `enterScenario`，也不拿当前面具给旧消息重新署名；缺失时不借当前对话顶替。

### 版本、编辑与 UI 状态

- 人工编辑保持 ID 与所见 expectedRevision，正文作为完整多段内容保存。
  旧 facade 的 `MemoryEntry.archiveRevision` 也是 CAS 凭证；确认弹窗打开时冻结
  条目，不能在确认时从新列表取 revision 删除用户没看到的版本。
- `ArchiveMemoryRepository`、`MemoryRecordStorage` 以档案为主，同事务单向更新
  旧 `memory_entries`。已激活后即便显式兼容读取，仍保留档案 CAS 与安全投影；
  不恢复旧跨范围物理 prune，不通过观察者双向同步。
- 旧认知/状态可能只有当前头而无历史行。仅在请求确切当前版本且历史未记录时
  展示当前头并标明缺失，保留 CAS/编辑/结束/删除；不能虚构早期版本或来源。
- 历史状态展示该修订的状态、期限和结束时间；没有历史观察时间不借当前值。
  直接来源与 supportingRecords 都要查对应修订，不能给旧正文贴新来源。事件
  成员来源与摘要覆盖不同，LIVE 成员不自动算已覆盖。
- 查询、数量、关联候选、任务与索引刷新使用独立请求序号/Job。相同空间内的
  迟到成功/错误也须拒绝；详情关闭后旧候选不回写。保存冲突保留草稿。
- 窄屏/200% 下长 ID、版本、来源及窗口操作必须可达。编辑 Scaffold 避让 IME，
  正文可滚动，保存保持在键盘上方；验证实际触控范围，不只看节点存在。

### 暂停与恢复

- PAUSED 阶段不是持久用户暂停。空间与任务控制都要检查 controlVersion；暂停
  不增加空间/event generation，不清产物、请求证据或成功游标，不偷有效 lease。
- 规划、claim、新请求、提交/激活与启动恢复遵守暂停。已收到的 completion 仍由
  原有效 lease 在来源合法时保存，再停止后续批次；不要把暂停塞进通用 validContext。
- 无请求的 C5 旧产物消费、事件关联消费和活动池自动归档同样遵守暂停，且在事务
  内复查。显式人工 CRUD、必要来源失效、只读召回和已有检索仍可执行。
- 选区先检查完整历史重叠，不能限于前 1000 个任务。USER_SELECTION 只处理用户
  明确范围，不推进自动游标；旧独立归档可走该显式入口，不放宽自动运行。
- “继续”用提取 `resumeJob` / 事件 `resumeEventJob` 恢复原 job/context/manifest；
  已保存批次不重付，未请求批次仍可能产生费用。INVALIDATED 或未知请求保持待审，
  不能换 key、改绑 context 或调用新的 FULL 重整来冒充恢复。
- 向量显示当前配置的持久状态。只有所选空间/身份存在合法活跃 runtime 时才执行
  明确重建；纯旧档案不为索引伪造运行。导入索引授权见资料规范。

### 启用与生命周期

- `MemoryLegacyInstallation.activate` 同事务迁入未投影旧行并首次写
  activationVersion=2，不创建历史 ingress/job；新安装和旧 engineVersion=1 均可
  启用。之后 `keepCompatibilityEngine` 的 1 不被每次读取反复升级。
- 私人/群聊三条创建链都传用途：NewChatSheet、ContactCardSheet、NewGroupChatSheet。
  默认日常，新独立场景默认独立，旧 LEGACY_COMPAT 保留；不从线上/线下推断用途。
- 重开既要在 Room 保档，也要去掉 `RoleplayScenarioActionSupport` 后续旧清理；
  原日记/摘要/记忆不再被上层另删。明确清空按原文依赖失效，保留独立人工版本。
- 清空/重开 IO 完成后的 UI 成功与错误只更新原场景/会话；重开允许本次 Room 返回
  的新会话，但不能覆盖用户已切到的另一场景。提示文案必须与真实清理范围一致。

## 4. 校验与错误矩阵

| 条件 | 行为 |
| --- | --- |
| 角色/空间/身份、generation 或所见 revision 已变化 | 拒绝旧操作，刷新上下文；不借最新 revision 绕过 |
| 当前回合无回执、消息/requestId 不符 | 无记录，不回退上一轮或最新日志 |
| 旧知识缺历史行 | 仅显示确切当前头，明确历史未记录，不伪造证明 |
| 用户暂停 / 控制版本冲突 | 不开始新处理；原已收结果可安全保存 |
| 已请求未知结果 / 旧失效任务 | 保留证据与待审，显式重新请求另行确认 |
| 取消或切换选择 | 取消传播；迟到结果/错误不改新页面 |

## 5. 正常、旧资料与异常样例

正常：日常角色没有对话时先手动记住，之后新对话使用同一空间。旧资料：历史认知
只有当前 v3，显示 v3 与“历史未记录”，编辑产生新版本而不补造 v1/v2。异常：删除
确认打开后后台把 v3 更新到 v4，仍提交所见 v3 并得到冲突，不删除 v4。

## 6. 必需验证

管理/暂停/兼容用 `MemoryArchiveManagementRepositoryTest`、`MemoryProcessingControlTest`、
`ArchiveMemoryRepositoryTest`；真实上层生命周期用 `RoleplayMemoryLifecycleTest`。
UI 用 `MemoryArchiveViewModelTest`、`MemoryArchiveUiTest`、`MemoryArchiveNavigationTest`、
`MemoryPurposeUiTest`、`MemoryLifecycleConfirmationTest` 与旧管理 CAS 回归。
原生使用 `MemoryManagementMigrationTest`、`MemoryOriginalScreenTest`、
`MemoryArchiveEditorImeTest` 和 `RoleplayScenarioEditScreenTest`；真实键盘须可见并测量
保存按钮高于 IME。使用合成数据/隔离库，未运行不能写通过。

## 7. 错误与正确用法

错误：`messages.filter { it.requestId.isNotBlank() }.lastOrNull()` 充当本轮，或在
确认时重新查询最新 revision。正确：先固定真实消息和所见版本，再执行严格关联
读取/CAS；没有本轮证据就明确缺失。重建、继续与解除暂停也不能混成同一个动作。
