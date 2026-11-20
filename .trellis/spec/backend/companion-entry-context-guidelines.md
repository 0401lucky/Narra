# 陪伴入口、授权上下文与请求快照

## 1. 适用范围

修改单角色陪伴的三开场、旧会话恢复、返回线上、逐请求历史/记忆/世界书或
角色时区时阅读。场次、来源资格、产物补存和安全替换继续遵守
[陪伴场次与可靠回合](./companion-foundation-guidelines.md)。

生产入口为 `RoleplayNavGraph` / `CompanionRouteContent`，状态由
`RoleplayCompanionViewModel` 持有，依赖在 `AppGraph` 装配。旧线上、群聊、
长文和未关联陪伴的消息继续使用原路径。

以下源码路径相对 `app/src/main/java/com/example/myapplication/`。

## 2. 实际接口与存储字段

```kotlin
// RoleplayRepository
suspend fun prepareCompanionScenario(scenarioId: String, entryId: String): CompanionSessionPreparation
suspend fun resumeCompanionScenario(scenarioId: String): CompanionSessionSelection?
suspend fun selectCompanionOpening(
    entryId: String,
    expectedEncounterId: String,
    mode: CompanionOpeningMode,
    encounterId: String,
): CompanionSessionSelection
suspend fun restoreCompanionOrigin(origin: CompanionEntryOrigin, encounterId: String): Boolean

// CompanionHistoryRepository
suspend fun freeze(
    context: MemoryRuntimeContext,
    cutoff: CompanionHistoryCursor,
    selection: CompanionHistorySelection,
    originalScope: AllowedCompanionHistoryScope? = null,
): AllowedCompanionHistoryScope
suspend fun read(scope: AllowedCompanionHistoryScope): List<ChatMessage>
suspend fun validate(scope: AllowedCompanionHistoryScope): Boolean

// CompanionRepository：复用同一Room事务中的请求、来源与运行守卫
suspend fun validateRequest(id: String, owner: String): Boolean
suspend fun canSaveGeneratedArtifact(id: String, owner: String): Boolean
suspend fun listPendingResults(context: MemoryRuntimeContext): List<CompanionPendingResult>

// CompanionRequestCoordinator
suspend fun newAttempt(turnId: String, allowUncertainRetry: Boolean = false): CompanionAttempt?
suspend fun generate(attemptId: String): CompanionGenerationResult
suspend fun retrySave(attemptId: String): CompanionGenerationResult
suspend fun acceptOpening(attemptId: String): CompanionGenerationResult
suspend fun pendingArtifact(attemptId: String): CompanionGeneratedArtifact?
suspend fun pendingResults(scenarioId: String): List<CompanionPendingResult>
suspend fun savePendingResult(scenarioId: String, attemptId: String): CompanionGenerationResult
```

`CompanionRequestDescriptor` 的兼容JSON保存 `historyScope`、`entryOrigin`、
`entrySelectionId`、`replyMessageId`、`timePolicy`、来源修订和提示/设置版本。
不保存凭据或完整ProviderSettings。入口元数据沿用schema 55的
`companion_attempts.requestDescriptorJson`，不另造原文库。

## 3. 跨层合同

### 准备、选择与返回

- 显式新入口先在同一场景事务中保存无请求DRAFT/OPENING准备标记，初始attempt
  保存原scenario/conversation/run/character/persona、媒介和协议开关。
- `RoomCompanionEntryState` 通过准备turn及 `entrySelectionId` 找实际选中场次。
  切换时比较 `expectedEncounterId`，同事务更新选择、结束旧候选并创建新场。
  已发请求或已有产物的attempt描述不可用作可变入口标记。
- DIRECT自身没有OPENING turn、开场请求或静态greeting；返回信息由独立的
  无请求准备标记保留。仅有准备记录不构成共同经历；标记随原会话级联清理。
- 当前选择依赖持久的DRAFT淘汰和唯一ACTIVE槽，不按时间戳/随机UUID猜先后。
  旧页面、旧选择或跨归属标记不能替换新场或恢复另一运行的媒介。
- 返回核对原scenario/conversation/run/character/persona及当前媒介/协议状态；
  新DRAFT或已结束场会拒绝迟到返回。新场不调用重开剧情，也不更改记忆用途。
- 普通旧route没有入口参数时也可能恢复到PREPARE/PREVIEW。实际
  `CompanionRouteContent` 必须显示准备页并处理Back；不能只恢复ViewModel字段。
  显式入口同时匹配scenarioId和entryId，同场景切到新入口时也不能短暂显示旧候选。
  阅读、设置、重组与ON_RESUME只刷新本地状态，ON_PAUSE取消生成，不自动重发。

### 回合与恢复动作

- INPUT、CONTINUE、OPENING由持久类型决定。普通输入“继续”仍是INPUT；
  `CompanionPromptSupport` 在检索完成后才追加本轮互动指令。
- CONTINUE只表示用户本轮没有新增言行；短控制行及技术调度文字不成为原文来源。
  回到线上也不把控制行改成用户说过的“继续”。
- 预览、免费补存和接受分开。`retrySave` 恢复OPENING产物不等于接受成功；
  接受事务失败后的恢复动作仍调用 `acceptOpening`，不得再次请求模型。
- `RoleplayCompanionUiState.recoveryAction` 区分GENERATE/SAVE/ACCEPT。
  忙态防重复，取消和失败保留可恢复信息；迟到结果按当前页面/场次所有权隔离。
- 刷新恢复不能只凭Room的REQUESTED/UNCERTAIN猜动作。通过 `pendingArtifact`
  核对D1当前进程实际持有、且原owner/请求仍允许保存的产物，有产物才保留SAVE。
  无产物的NOT_STARTED/未知请求不能冒充可免费补存；失效缓存应释放，不扩大8槽上限。
- 已持久的完整RESULT_READY通过 `listPendingResults` 的当前角色/persona/run、
  hash和提交守卫筛选。`CompanionPendingResultsBar` 在原线上普通route和ACTIVE新场
  都可达；保存旧A只调用 `savePendingResult`，不切场、不激活旧run、不重新生成。
  未接受DRAFT、空准备标记、截断或失效来源不列为可提交结果；已接受开场的合法
  reroll与INPUT/CONTINUE同样可补存。
- 尚未落盘的进程缓存只在原请求保存权限仍有效时可用；设置/阅读往返不会因此变成
  收费重发。结束场次、所有权/来源失效或进程丢失不能被伪装成持久恢复成功。
- 有效完整主回复优先免费补存。完整RESULT_READY因来源变化已无法提交时，用户
  明确的 `allowUncertainRetry = true` 才可建立新attempt并重冻授权；保留原输入，
  不把新请求藏在刷新/恢复导航中。开场预览的显式重生成继续遵守原候选语义。
- 陪伴图片草稿使用 `RoleplayMessageCallbacks.onSendWithInputSaved(parts, ack)`：
  `beginTurn` 保存成功后才ack；Screen仅清理仍与提交快照相等的附件，迟到ack不能
  清掉新选择。文字同样只清匹配的已提交输入。旧callback为null时保留原发送行为。
  普通线上没有activeCompanion时，补存/刷新busy不能被合并成isSending而清草稿。

### 模型授权历史与逐请求准备

- `RoomCompanionHistoryRepository` 从授权的空间/角色/persona绑定和有效来源读取；
  不接受UI已加载窗口作为兜底。空messages、空允许集合或零窗口就是空授权。
- CONTINUOUS可聚合同角色、同persona且单聊绑定的已提交新鲜原文；INDEPENDENT
  限当前conversation/run。群私有、失效修订、waiting输入和CONTROL_NOOP不混入。
- Scope冻结允许绑定、message ID/revision、稳定时间+ID截止、各会话起点、排除项及
  窗口。首次开场在明确点击生成时冻结，可承接停留准备页期间的新鲜合法历史；
  重生成沿原scope的截止/授权集合重新校验，不加入原截止之后的历史。
- 当前turn可单独读取自己的pending INPUT；这不向其它回合或记忆开放资格。
  输入及引用修订仍由请求和提交事务复核。
- 开场、输入、继续和重生成分别执行 `assembleCompanion`。记忆关闭/召回失败只
  丢弃本请求的注入，不能复用旧结果或兼容记忆列表。
- 陪伴使用 `RoleplayConversationSupport.resolvePromptSettings` 的
  `includeScenarioPersona = true`，将既有解析规则得到的有效身份说明放进persona槽
  且只注入一次。旧调用默认false，由原Decorator注入；不能在跳过旧Decorator后
  继续沿用清空身份槽的设置投影。
- 世界书触发使用真实语义输入，保持原depth/order/role；本轮指令按实际输入对象
  加入，不能贴到depth 0注入后的数组末项。线上卡片使用既有媒介转译，资源URI
  不当成自然语言；不无条件注入手机经济、状态卡或脚本玩法规则。
- `assembleCompanion` 经 `CompanionPromptSupport.adaptBuiltInPreset` 对内置默认/
  Qwen规则包做请求时适配，消除强制每轮推进与允许沉默的冲突。仅匹配builtIn、
  已知preset/entry ID且正文仍等于出厂原文的块；用户改写的块、自定义预设、人设、
  Sampler及上下文槽/插入配置保留。普通RP、线上和长文继续使用原内置规则，
  不能通过全局替换短语或写回持久预设来实现模式适配。

### 最后发送点与回执

- `GatewayCompanionSession` 随协程进入三协议共同的 `GatewayRecallTransport`。
  先校验，再准备召回传输回执；准备中存在挂起点，因此在真正的
  `call.execute()` / Retrofit调用之前必须再校验。
- 守卫包括 `validateRequest(attemptId, owner)` 的持久claim/activeAttempt/输入/
  引用/运行核对，以及历史scope、角色、当前模型、时区版本和记忆开关。
- `GatewayRecallSession.payload` 只准备传输记录；通过最后守卫后才调用
  `sending()`。发送前确定被拦截使用SOURCE_CHANGED，不把零HTTP记成
  TRANSPORT_UNCONFIRMED；已有真实发送的传输回执保留。
- 上游已经收到的内容无法撤回；之后的来源变化通过提交CAS拒绝旧结果，不能宣称
  本地取消能删除上游已收到的数据。

### 角色时区与配置保存

- `CompanionTimePolicy(enabled, zoneId, requestTime)` 冻结实际请求使用的时间。
  enabled沿用场景现实时间开关，未设置角色ZoneId时使用注入的设备时区。
- 发送前设置revision或场景时间开关变化，明确拒绝旧请求；发出后使用已冻结的
  timePolicy记录结果，后来的设置不改写本次提示快照。
- 关闭现实时间抑制额外实时包装，保留原文里的叙事日期、真实保存时间和记忆用途。
  场景说明只展开其自身的人物名字占位，不二次展开已经组装的原文/记忆内容。
- `AppSettingsStore.updateCompanionSettings(assistantId, expectedRevision, patch)`
  在DataStore edit内校验角色和CAS。角色小映射与旧整角色列表的key分开；
  删除角色在同一edit内清理其映射。未知高版本JSON保留并拒绝降级覆盖。
- 后续扩展文风/观测时仍复用此映射，并将实际生成配置和稳定字段定义写入请求
  快照；仅有revision不能用来解释改名后的旧观测。

### 可观察空闲与操作所有权

- `RoleplayCompanionViewModel.runOperation` 用 `CoroutineStart.LAZY` 创建 Job，先登记
  `operation`，再发布忙态并启动；已登记但尚未启动也必须由 `operation != null` 阻止重入。
- `catch` / `finally` 同时校验导航 `generation` 和当前 Job 身份；先释放自己的操作槽，
  再发布 `isBusy=false`。StateFlow 通知可同步执行下一动作，旧 Job 或旧 launch 返回值
  不得覆盖这一动作的新 handle、错误或忙态。`refresh` 使用相同的已登记守卫。
- 不能用“空闲收集者总在 finally 后调度”的隐含假设，也不能让测试读取私有 Job
  或增加 sleep 掩盖该窗口；空闲通知中立即发起的真实操作必须正常执行。

## 4. 校验与错误矩阵

| 条件 | 必须观察到的行为 |
| --- | --- |
| 选择标记/expectedEncounter不匹配 | 场景事务拒绝，当前场和返回媒介不变 |
| 选择存储失败 | 标记、新场及旧候选淘汰一起回滚 |
| 空历史授权/断点排除 | 最终HTTP不出现被排除原文，不从UI补回 |
| 回执准备期间删源/改输入/切运行或设置 | 最后守卫阻止HTTP，记录明确的发送前失败 |
| 预览接受存储失败 | 留在预览，免费重试接受；生成调用数不增加 |
| 设置返回后仍持有有效进程产物 | 保持SAVE和正文，不变成GENERATE |
| 已收完整结果的来源失效 | 拒绝旧补存；只有显式新请求才重冻来源，输入不重复保存 |
| 输入保存失败/保存确认迟到 | 原图片和文字草稿保留；不清除后来新选的附件 |
| 设置CAS冲突/高版本/角色已删除 | Conflict/UnsupportedVersion/CharacterMissing，保留当前数据及可恢复草稿 |
| 离场或切页取消 | 取消当前操作；旧回调不改新场；重入不自动收费重发 |
| 空闲通知中立即选择开场 | 新动作正常执行，旧 Job 不拦截或清理新操作；DIRECT 仍为零 HTTP |

## 5. 正常、基础与异常案例

- 正常：线上明确开始见面，选择DIRECT，关库重开仍从持久关联恢复原线上返回目标；
  整个准备与选择没有文本请求。
- 基础：点击旧无陪伴关联会话，保留原greeting和run，不推测见面场次。
- 异常：A候选后选择B，A的迟到选择/返回被拒绝；在回执保存期间删除引用源，
  即使第一次校验成功也不能发出旧原文。

## 6. 必需测试与断言

- `CompanionEntryRepositoryTest`：乱序ID/同时间的选择、事务回滚、旧返回、跨归属
  标记、旧会话兼容及级联清理。
- `CompanionEntryViewModelTest`：DIRECT重开、PREVIEW恢复、路由重建、接受失败/
  免费补存、未发请求输入和取消；请求计数及实际仓储状态共同断言。
  `idleNotification_allowsImmediateOpeningSelection` 用同步通知调度发起真实 DIRECT，
  核对 UI/Room 的 ACTIVE、无 turn/消息与零 HTTP；保留原开场/待保存恢复断言。
- `CompanionHistoryScopeTest` / `CompanionRequestCoordinatorTest`：真实Room授权，
  三协议最终HTTP、控制语义、首次开场/重生成截止、删源挂起竞态、记忆关闭/失败、
  世界书depth 0与role顺序。不能仅断言中间prompt字符串。
- `CompanionTimePolicyTest`：跨年/跨时区、关闭现实时间、发送前设置变更与发送后
  快照不变；`AppSettingsStoreCompanionTest`：真实文件DataStore、CAS和旧字段兼容。
- `CompanionBuiltinPresetTest`：实际BUILTIN_PRESETS的默认/Qwen INPUT与CONTINUE
  最终HTTP没有强制推进冲突；旧模式、自定义正文、改写过的内置块及Sampler保留。
  角色、世界书及有效persona仍在请求中，场景身份只出现一次；空FakePreset不能
  代替默认请求的语义验证。旧 `RoleplayConversationSupportTest` 保持默认调用兼容。
- `CompanionEntryScreenTest` / `CompanionEntryNavigationTest`：实际生产页面和路由
  wrapper、Back/IME、恢复动作、320dp/200%字体及48dp操作区。原生NavHost测试
  不等于整个AppGraph启动验证；设备结果与JVM结果分别报告。
- `CompanionPendingRecoveryTest`：关库重开、原线上/新B补旧A、来源/身份/预览/
  截断过滤和已接受开场reroll；`CompanionPendingResultsNavigationTest`：实际待保存
  入口、准确attempt回调、忙态、Back和大字。`CompanionInputPersistenceScreenTest`
  用真实RoleplayScreen与合成PhotoPicker验证图片持久化ack、失败保留、迟到确认
  以及旧回调兼容，不用复制草稿清理逻辑的假控件代替。

## 7. 错误与正确做法

```kotlin
// 错误：准备页恢复又生成；或把UI窗口作为无授权原文的兜底。
requests.generate(lastAttemptId)
val history = frozenHistory.ifEmpty { uiState.messages }

// 正确：恢复本地选择；每次明确生成读取冻结授权范围。
val selection = roleplayRepository.resumeCompanionScenario(scenarioId)
val history = companionHistoryRepository.read(scope)
```

返回与读取历史不创建新turn；重新生成、免费补存和接受开场由各自明确的用户动作
进入相应接口。参考场次规范中的D1提交/资格合同，不通过清空旧答或改记忆用途绕过。
