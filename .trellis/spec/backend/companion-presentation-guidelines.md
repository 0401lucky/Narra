# 陪伴正文、表现快照与观测

## 1. 适用范围

修改陪伴raw读取、XML投影、文风/人称/模板、观测或角色表现配置时阅读。
场次与来源资格继续遵守 [可靠回合](./companion-foundation-guidelines.md)，
授权与最后发送点遵守 [入口与上下文](./companion-entry-context-guidelines.md)。
普通线上JSON、未关联陪伴的旧消息、群聊和长文保留原协议路径。

后续播放器、素材、阅读与选段合同见
[演出与连续阅读](./companion-stage-reading-guidelines.md)，继续消费本专题的同源投影。

以下源码路径相对 `app/src/main/java/com/example/myapplication/`。本专题描述代码
合同，实际运行结果以D3任务的implementation/check/device报告为准。

## 2. 接口与持久位置

```kotlin
// roleplay/companion/CompanionReplyProjector：纯投影，无存储或模型依赖
fun project(message: ChatMessage, characterName: String = message.speakerName.ifBlank { "角色" }): ParsedCompanionReply?
fun project(messages: List<ChatMessage>): Map<String, ParsedCompanionReply>
fun preview(attempt: CompanionAttempt, raw: String, characterName: String, complete: Boolean): ParsedCompanionReply

// conversation/CompanionRequestCoordinator：产品动作显式区分
suspend fun newAttempt(
    turnId: String,
    allowUncertainRetry: Boolean = false,
    intent: CompanionAttemptIntent = CompanionAttemptIntent.REGENERATE,
): CompanionAttempt?

// 继续使用原角色小映射与DataStore CAS
suspend fun updateCompanionSettings(
    assistantId: String,
    expectedRevision: Long,
    patch: CompanionSettingsPatch,
): CompanionSettingsWriteResult

// conversation/ConversationSummaryCoverageSupport：完整来源补充的消费边界
internal fun completeInput(
    messages: List<ChatMessage>,
    formattedInput: String,
    isSourceAllowed: (ChatMessage) -> Boolean = { true },
): String
```

`CompanionCharacterSettings.writing`承载表现偏好；`CompanionSettingsPatch.writing`
为可选更新，省略时保留现值，时区仍使用同一映射。`CompanionWritingCodec`
处理合法默认值和未知字段，`CompanionDescriptorCodec`解析请求描述中的表现快照。

`CompanionRequestDescriptor.presentation`位于既有attempt的
`requestDescriptorJson`，无需另造正文或观测数据库。快照包含snapshotVersion、
sourceSettingsRevision、文风/人称/补充、单次detailHint以及观测定义。

## 3. 跨层合同

### 权威原文与读取证明

- 非空companionTurnId的原文由 `RoleplayMessageFormatSupport.resolveAssistantRawContent`
  返回message.content，保留空白、CRLF与协议字节。展示parts、去标签文本不能
  优先覆盖raw；旧无link消息继续原有兼容读取。
- 新生成正文的content与持久TEXT保留同一raw。完整一轮仍是一条canonical回复；
  `CompanionBeat`不是另一条消息或记忆来源。
- `RoomConversationStore`经 `CompanionSourceStorage.read`核对真实turn/encounter/
  selected COMMITTED attempt、角色/运行及replyRevision，再附加独立的
  `@Transient CompanionReplyMetadata`。它不授予机器来源资格，不替代原proof。
- metadata绑定messageId、conversationId、turnId、revision、raw hash、speaker、
  格式/媒介与完成状态。copy改正文、关联或角色后必须失效；导入JSON不能恢复
  外部提供的Transient metadata。
- 投影使用当前message.raw，selected attempt仅提供当时的协议与表现定义。
  缺失/失效metadata保留可阅读raw与诊断，观测停用；不能从当前角色配置补标签。
- `CompanionReplySource`保留源ID/修订/角色、protocolVersion/parserVersion、rawHash、
  attempt及配置来源修订。raw span为原始Kotlin字符串的UTF-16半开区间；从一个
  旧式混写XML节点派生多拍时可共享节点范围，不能把规范化后的偏移当raw坐标。
- 重算、切视图、复制展示文本与打开观测均不写canonical、提高memoryRevision或
  触发模型/提取。D4/D5可投影任意合法仓储读取列表，不以当前场次限定纯投影器。
- 记忆/摘要的语义格式化应排除已识别observe展示块；canonical和v2.4原料快照、
  hash及原始span仍保持。裁剪后的偏移不能代替原文偏移，也不能回写去块后的source。
- 摘要的“完整来源补充”由 `ConversationSummaryCoverageSupport.completeInput`
  处理。陪伴补充使用同源 `CompanionReplyProjector.project(message)?.displayText`，
  普通消息继续原文补全；`sourceText`、来源指纹和覆盖核验仍保留raw。不能因为
  格式化输入不包含完整raw，就把已排除的observe重新追加到模型摘要请求。

### 请求快照与设置守卫

- descriptor.settingsRevision是本次attempt创建时的live revision，继续参与D2
  最后HTTP守卫；presentation.sourceSettingsRevision表示实际使用的表现来源。
- `RETRY`保留原presentation、hint及旧观测定义；`REGENERATE`才冻结当前表现。
  两者都重核授权来源并遵守D2时间策略，不能放松权限以重放旧文风。
- `allowUncertainRetry`表示是否允许显式重发未知结果，不代表产品动作分类。
- detailHint由seed确定并保存到快照，失败重试不再次抽取。文风/人称仅进入表现
  装饰，检索与世界书仍用真实输入；角色事实、用户执笔权和上下文权限保持。
- 版本号先按有界整数读取，再转换为Int；溢出、非法或未来版本不能截断成1。
  旧descriptor缺presentation按无观测兼容投影，生成前检查支持的协议/快照版本。

### XML与观测

- 复用narration/thought/dialogue；受限observe块先分离，再用既有正文parser。
  不执行HTML、DTD或外部实体，不把普通方括号文本当协议删除。
- 陪伴的合法短台词和纯文不能被旧变量残留清理吞掉，例如独立的new/old/test
  及其后续段落。正文守卫与展示必须使用相同的保真解析；旧RP默认清理规则保持
  兼容，通过显式陪伴策略处理，不以放松所有完成校验来绕过。
- 正文实体单次解码覆盖五个XML命名实体及十/十六进制字符引用；例如apos与
  `&#x1F642;`应得到实际字符。`&amp;#65;`只变为字面`&#65;`，不能二次展开；
  编码尖括号解码后的文本不能再次当标签删除。raw及span保持原始内容与坐标。
- 内建fieldId固定为time/place/state/detail；自定义ID使用程序生成的
  `obs_[A-Za-z0-9_-]{1,64}`，最多6项。解析键是ID，显示名来自该回复快照。
- 缺块、坏块、未知/重复字段、多块、关闭或不支持版本使本条observation为空；
  未闭合observe遇到有效正文时不能吞掉后面的narration/dialogue。
- 观测开标签属性上限4096字符，块上限8192字符、单值400字符，总字段最多10项。
  开标签先完整识别并排除，再检查属性限额；超限返回
  `OBSERVATION_LIMIT_EXCEEDED`。识别正则不能先限定属性长度而漏掉整个超长块，
  否则field值会退化为普通正文。坏可选块保留raw与诊断，后续合法正文仍可读。
  只有观测、没有有效正文的结果不能发布为完成剧情或正式播放队列。
- sprite使用统一 `CompanionExpressionKeys`，与自由文本emotion和受限
  `CompanionVoiceEmotion`分开。D4素材键与生成/解析遵守同一规则，不能互相猜填。

### 草稿与配置

- 文风和人称允许不指定。写法含五种文风、三显式人称、补充、模板、细节线索与
  默认关闭的观测；旧全局、场景和角色主档案值不被这些偏好覆盖。
- 观测名称去首尾空白后按不区分大小写检查重名；标签上限40字符，补充/模板正文
  上限12000字符，最多20模板、模板名称上限60字符。稳定ID不会随改名变化。
- codec逐字段保留合法内容及同ID项的未知扩展；高schema原JSON保持只读，禁止
  降级覆盖。旧saveAssistants不更新此独立映射，删除角色仍在同次edit清其映射。
- 角色复制使用 `saveAssistantsWithCompanionCopy(assistants, selectedAssistantId,
  sourceAssistantId, targetAssistantId)`，在同次DataStore edit读取当前源与配置并创建
  目标，目标配置revision置0。陈旧整列表不覆盖同期角色；源缺失/高版本或目标已存在
  时整次拒绝，不能留下仅复制角色而丢偏好的半成品。此入口不是资料导入的批量保存。
- `CompanionSettingsViewModel`持有角色绑定的SavedState草稿和base revision；失败、
  冲突、角色删除时保稿。重新读取丢弃草稿必须经真实页面明确选择。
- `CompanionSettingsScreen`的错误/冲突/读取最新动作与保存取消保持可见，不能
  只放在长表单顶部让底部保存没有反馈。IME、系统Back与弹窗各自消费正确层级。
- `CompanionObservationPanel`只展示所给reply的快照观测；新源/修订、无块或禁用
  时旧弹窗不能继续冒充当前观测。
- 当前角色的“显示回复观测”总开关统一作用准备页、会话与阅读宿主。关闭时
  隐藏入口并关闭弹窗；重新开启仍只展示历史真实快照中的观测，不补造旧回复。
  跨场读取按来源角色应用当前显示gate，不能用当前label重新解释历史字段。

## 4. 校验与错误矩阵

| 条件 | 必须观察到的结果 |
| --- | --- |
| live设置或来源在准备后改变 | 最后发送守卫拒绝HTTP，旧表现快照不放大权限 |
| 改设置后显式失败retry | 使用原表现/hint，记录新attempt的live revision |
| 当前观测字段改名/删除 | 历史仍按原ID与原label；新请求才使用新定义 |
| 错误observe或只有observe | raw保留；坏块无HUD，无有效正文不作为成功剧情 |
| observe开标签属性超过4096字符 | 仍识别并排除该块，记录限额诊断；only-observe不能提交，后续合法台词保留 |
| 摘要格式化记录不包含完整raw | 陪伴补充正文投影，观测不重新拼回；raw来源指纹与覆盖不变 |
| metadata被copy到另一消息/角色/关联 | 绑定检查失败，不能显示旧观测或提升来源资格 |
| 未知/溢出版本 | 明确不支持，不伪装当前版本或覆盖原JSON |
| CAS、存储失败、角色已删除 | 草稿保留，保存结果如实反馈，不提示成功 |
| 解析/观测/阅读操作 | 消息数、raw、source revision和模型调用数不变 |

## 5. 正常、旧数据与异常案例

正常：回复记录了time标签“时刻”，角色设置后来改成“时间”；旧回复继续显示
“时刻”，下一次新生成使用“时间”。旧数据：D2描述没有presentation，正文XML
仍可读，观测保持为空。异常：未闭合observe后紧接dialogue，观测无效但台词保留，
raw与原span不被清洗重写。

摘要正常输入：正文加observe只补充可读正文，观察值不进入实际摘要请求。
旧数据仍按原规则补全缺失原文。异常输入：observe的pad属性为5000字符时，
即使整个块未超过8192字符，也必须排除并报告属性超限，不能把field值当台词。

## 6. 必需验证

- `CompanionReplyParserTest`：新旧XML/纯文、未知属性、坏/重复/缺失observe、实体、
  CRLF和源span，sprite/声音emotion独立，保留有效正文；普通短词/多行正文还需
  通过真实collector与Room提交，不能仅让parser测试通过。
- `CompanionProjectionRepositoryTest`：真实Room提交/读取/重开/修订与JSON往返；
  content/TEXT保真，Transient不可导入，stale copy无HUD，投影零存储副作用。
- 超长observe回归同时核对parser诊断/后续台词/raw span，以及真实collector和
  Room拒绝only-observe；不能只断言字符串长度或关闭观测显示。
- `CompanionProjectionRepositoryTest.summaryRequest_excludesObservationAndKeepsRawSourceCoverage`
  抓实际Room→摘要协调器→最终HTTP，断言补充没有观察值，原文、来源指纹和覆盖不变；
  `ConversationSummaryCoverageSupportTest`继续验证普通旧源补全和资格拒绝。
- linked 来源的摘要/仓储夹具沿生产 `AppGraph` 装配
  `RoomMemoryRepository(database.memoryDao(), database.companionDao())`。省略第二个
  DAO 时无法验证 selected COMMITTED attempt，来源会被保守拒绝；不能靠关闭资格
  守卫解决。最终 HTTP 解析 JSON 后核对 role/content，并核对仓储解析的来源快照，
  将装配失败与正文消费问题区分。
- `AppSettingsStoreCompanionWritingTest`及旧Companion设置回归：真实DataStore，
  schema/字段容错、CAS、旧角色保存与未知扩展保留。
- 请求链抓最终三协议HTTP，区分retry/reroll的实际表现与hint、最后发送竞态、
  原型人物/世界书/检索槽保留；只有中间prompt字符串断言不足。
- 真实设置/观测Screen验证失败反馈、重名、模板、稳定ID、窄屏200%/48dp、IME、
  discard/reload与旧HUD清空。AndroidTest编译不是设备通过，截图候选不自动接纳。

## 7. 错误与正确做法

```kotlin
// 错误：展示文本覆盖权威raw，或拿当前label解释历史field。
val raw = message.parts.toPlainText().trim()
val label = currentSettings.writing.observation.fields.first { it.id == fieldId }.label

// 正确：保留权威raw，历史定义只来自当前源的已验证快照。
val raw = RoleplayMessageFormatSupport.resolveAssistantRawContent(message)
val reply = CompanionReplyProjector.project(message)
val observation = reply?.observation

// 正确：产品动作与是否允许重发未知请求分别传入。
requests.newAttempt(turnId, allowUncertainRetry = true, intent = CompanionAttemptIntent.RETRY)

// 错误：格式化摘要缺少raw，就重新补回含observe的原文。
val summarySupplement = ConversationSummaryCoverageSupport.sourceText(message)

// 正确：消费边界使用正文投影，来源证明仍由原raw计算。
val summarySupplement = CompanionReplyProjector.project(message)?.displayText
    ?: ConversationSummaryCoverageSupport.sourceText(message)
```
