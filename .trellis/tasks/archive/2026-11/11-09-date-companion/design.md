# v2.4.1 陪伴技术设计

状态：2026-11-09最新完整方案已获用户批准，从D1开始实施。需求权威为 [PRD](prd.md)，参考项落点为 [验收矩阵](acceptance-matrix.md)；本文件中的新类型/表/接口仍是设计，实际完成以子任务证据为准。

## 1. 架构边界与复用

```text
ImmersivePhoneShell / RoleplayNavGraph（现有入口）
  → 准备/预览 + 原线下 Screen 的演出/阅读/记录
  → 场次 ViewModel / Coordinator（状态与取消）
  → CompanionRepository（回合/产物/提交/查询） + 既有设置服务
  → 既有 RoomConversationStore / MemorySourceStorage + 新附属表
  → PromptContextAssembler / AiGateway / DurableMemoryExtractionCoordinator
  → 同源 ParsedCompanionReply → BeatPlayer / Reading / VoiceController
```

装配仍集中在AppGraph，导航负责StateFlow与回调。新增模型/协调器/解析器/数据层按现有包职责拆分，不继续扩大RoleplayViewModel或AiGateway，不引入另一个聊天数据库或状态单例。建议文件簇为model/Companion*、data/local/companion/、data/repository/roleplay/Companion*、roleplay/companion/、viewmodel/RoleplayCompanion*，最终名称沿用已有可复用类型。

### 路径与旧行为

- 新契约适用于显式创建的单角色线下陪伴场次：SINGLE + OFFLINE_DIALOGUE（含心声展示）；消息带非空companionTurnId及协议版本。不能用“不是ONLINE”直接接管所有旧线下/长文/群聊。
- 原线上JSON动作、GROUP逐角色回复、剧情OFFLINE_LONGFORM及无link旧消息仍使用原协议与兼容路径。新共享接口须有显式旧调用适配并通过原回归。
- 新建线下或显式“开始新一场”进准备页；点击旧会话仍恢复它。新场只增加encounter，不自动重开run/conversation或改memoryPurpose。
- “重开剧情”继续调用v2.4保档机制；它与新场是不同操作。
- 从线上开始见面时，返回标记保存原scenario/conversation及原mode；退出恢复原mode而非另找同名会话。切换/返回不重复开场，已生成开场只有接受后入经历。

## 2. 持久模型、身份与迁移（D1）

采用同一Room主库的三张附属表，不另造长期原文库：

| 结构 | 必要内容与约束 |
| --- | --- |
| companion_encounters | id、scenario/conversation/run、characterId、personaKey、openingMode、createdAt/endedAt、state、revision；绑定既有运行，索引conversation+时间+id；同一运行/角色/身份最多一个active场次。 |
| companion_turns | id、encounterId、ordinal、kind(INPUT/CONTINUE/OPENING)、input/replyMessageId、mutationRevision、committedRevision、selectedAttemptId、activeAttemptId、createdAt；UNIQUE(encounterId,ordinal)。 |
| companion_attempts | id、turnId、expectedTurn/Input/ReplyRevision、运行/身份/代次快照、请求和设置/提示版本、允许历史的ID/修订清单、claim/lease、请求与响应时间、状态、rawReply/parts、completion evidence、artifactHash。 |
| messages增量 | companionTurnId默认空；companionMemoryState默认legacy，新增waiting_turn/ready/control；协议版本由关联attempt/turn快照提供。模型、Entity、所有mapper、复制及支持的资料出口同步。 |

attempt不得保存Key或整份ProviderSettings；保存无密钥的Provider/模型引用、实际采样参数、有效配置/提示/历史版本等可复现描述。原文权威仍是messages，attempt只是待接受/待提交产物；旧attempt不进入正常模型历史或记忆。

turn与message双向引用由事务校验和清理，避免循环CASCADE。未知资格值、非空link但关联图缺失、跨conversation/run/身份的link均保守拒绝机器来源资格，不能回退legacy。selectedAttemptId非空不是提交证明：其实际attempt必须存在、属于该turn且状态为COMMITTED；缺失或错配时保持UNAVAILABLE。

旧消息保持空link+legacy，原ID/时间/模式/正文不改；迁移不伪造旧见面场次。当前基线schema54，实施时重新核对最新版本再+1。后续D5若加声音实体按当时最新版本再追加，禁止改写已验证的旧schema；迁移/注册/固定旧库/S3证据共同更新。

## 3. 来源资格与覆盖：与UI状态分离

现有COMPLETED消息会立即capture+enqueue，延后posthook不够。统一新增语义投影 sourceEligibility(message, canonicalTurn)：

| 结果 | 机器处理 | 用户原文 |
| --- | --- | --- |
| TEXT_ALLOWED | 已提交canonical、来源/运行有效，可供提取/摘要/其它回合历史 | 完整可读 |
| CONTROL_NOOP | 继续控制行不成为用户言行；产生ID+revision+kind的显式空操作覆盖凭据 | 显示/导出短“继续”标记及类型 |
| UNAVAILABLE | pending、部分、失败、关联图缺失或失效版本不可自动消费 | 已保存输入/未完成结果仍可在相应记录或恢复面板查看 |

INPUT开始时可保持UI COMPLETED，但机器资格为waiting_turn；首次写入冻结原角色/身份的UNKNOWN空正文来源，不enqueue。CONTINUE一直为control，只有合法助手答成为ready。accepted开场与完成输入回合的canonical提交事务提升相应资格并写ingress。

资格字段纳入MemorySourceStorage.sameSource；waiting→ready的提升会生成新的memoryRevision，并继承首次冻结身份，不能取提交时已经切换的persona。请求前输入revision与提交后来源revision分别保存在回执中。activeAttempt失败或生成中不影响旧selectedAttempt原答的TEXT_ALLOWED资格。

自动/手动capture、getSourceSegments、validSource、兼容引擎1、摘要sourceAllowed/completeInput、跨模式历史均消费同一投影。模型当前turn可显式读取自己pending INPUT，不能因此向其它回合开放。CONTROL_NOOP用于连续覆盖，不是跳过所有未知空洞；不能被completeInput再拼回技术占位。持久提取、事件/认知尾段全部复用v2.4。

原文编辑/删除同步增加turn mutationRevision并走现有来源失效闭包；手动独立记忆仍保留。已发送到上游的材料无法撤回，未来请求/迟到提交必须重新验证来源版本。

## 4. 请求状态机与失败保证

attempt状态固定为NOT_STARTED → REQUESTED → RESULT_READY → COMMITTED，另有FAILED、UNCERTAIN、CANCELLED；completionStatus独立记录COMPLETED/UNKNOWN/TRUNCATED/FILTERED/INCOMPLETE/FAILED/CANCELLED。

1. 同事务创建turn、待处理输入/控制行和attempt；持久claim成功后，先保存REQUESTED再发网络，防止记录失败仍请求。
2. 冻结角色/run/persona/generation、source revisions、设置和本轮语义；网络在事务外。新的attempt使用新requestId，逻辑turn不变。
3. 接收响应并记录结束证据；完整正文才可供canonical提交。先把raw产物/hash与证据独立保存到attempt，再尝试canonical事务。
4. canonical事务比较turn mutationRevision、activeAttempt/claim、输入/旧答修订及冻结运行/来源；保存/替换消息、更新selectedAttempt/committedRevision、提升来源和写ingress后统一提交。
5. UI按encounter+generation发布，不把UI是否还在前台当作唯一存储授权；离场取消网络与自动操作，已持久RESULT_READY仍可在恢复入口由用户补保存。
6. RESULT_READY的“补保存”只做hash/绑定/CAS复核，不调模型。canonical仍对应该attempt的原提交版本时，重复COMMITTED调用返回相同回执，不重复生成/提取。若后来已重生成、人工修订或删除，原回执对应版本失效，返回TURN_CHANGED；不能把当前新答作为旧attempt的成功回执，也不能为重试复活被删正文。
7. REQUESTED崩溃/超时而无可靠产物时置UNCERTAIN，不自动重发；lease到期只可恢复本地控制权，不证明上游未计费。
8. artifact首次落盘也失败时，当前进程保留有界内存结果供重试保存；进程丢失后仅剩REQUESTED证据，不能宣称正文仍可恢复。无无限缓存或未经用户操作的重收费重试。

安全重生成只允许当前场次最新canonical助手答。新attempt期间旧答、ID、原createdAt和逻辑顺序保持；成功后CAS换正文，另记editedAt/generatedAt。源答编辑/删除、输入改变或出现新的有效回合使旧attempt失效。旧“回退到该楼层”继续是独立明确动作。

### 完成证据

提炼现有PromptCompletionResult/PromptExtrasCore的结束原因规则，为主流事件增加CompletionEvidence，保留原Completed终止语义及旧调用默认适配。三协议各自映射明确终止、截断、过滤、取消和未知；不能从HTTP200、字符串非空、流关闭或推理文本推定完整。

新陪伴缺乏可靠完成证据时是UNKNOWN，保留可查看结果并显示未确认完整，不自动发布成功来源。纯推理不作为剧情；HTML错误仍是错误。旧RP主动停止保留片段的契约不全局重写，新路径用attempt/资格区分未完成状态。新已提交消息统一保留权威raw accessor，避免parts.toPlainText优先吞掉协议。

## 5. 开场、继续、上下文与时间（D2）

### 入口状态

```text
旧会话 → 原样恢复
显式新场 → 准备
  ├ 我靠近 / 角色靠近 → 请求预览 → 预览/重试/退出
  │                                 └ 接受保存 → ACTIVE
  └ 直接开始 → ACTIVE（无开场文本/模型调用）
ACTIVE → 输入/继续 → 回合状态机
ACTIVE → 离开 → 原入口；新场/重开由各自明确动作触发
```

预览产物可持久保存在draft attempt，但不作为messages/来源；多次预览只有选中的完整结果能接受一次。新companion创建路径显式跳过旧greeting自动seed；不删除旧会话已经存在的greeting。预览保存失败留在预览，后续互动等接受事务成功。

D2续接时将入口恢复细化为已有Room附属记录中的显式关联：准备入口先保存无请求DRAFT/OPENING标记，其初始attempt描述保留entryOrigin和entrySelectionId。切换开场在同一场景事务中比较expectedEncounterId，再创建并记录实际选择；DIRECT自身仍不创建OPENING回合或模型请求。元数据读取校验scenario/conversation/run/character/persona与选中场次一致，不按时间戳或随机ID猜测最新选择，也不改写已请求或已有产物的attempt描述。内部准备标记不成为消息、经历或模型原文，随原会话级联清理；历史功能不能把标记当作真实见面。普通旧路由恢复到DRAFT/PREVIEW时，真实导航同样显示准备页并处理返回，而非只恢复ViewModel状态。此细化须由D2恢复/并发/导航回归验收。

Continue显示和持久化为typed控制行，统一转换器在请求阶段产生场景留白任务。重试使用原快照，重生成新attempt可改变已记录的写法提示但仍保持原continue语义；不把“继续”普通用户输入按字符串猜成控制。

### AllowedCompanionHistoryScope

- 每请求冻结character/persona/space/generation、允许的conversation/run、现有上下文窗口/断点、当前turn及历史截止。
- 同conversation复用现有媒介转译；CONTINUOUS空间可聚合同角色/同身份、允许单聊绑定的已提交新鲜原文，遵守每个源会话排除/断点与总窗口。INDEPENDENT只读当前独立run；不因同角色扫描所有历史。
- 分页历史可包含只读旧档，但模型授权集合不从这些UI行构造；空授权返回空，不回退UI或全库。GROUP私有原文不加入单聊聚合。
- 重生成截到原触发输入的冻结边界，不把更晚线上聊天当本轮事实；待发送前复核引用的ID/修订及权限，提交时复核冻结上下文中已变/删来源。
- 先选真实语义输入做检索/世界书触发，再加场景/风格/玩法指令。复用PromptContextAssembler及GatewayRequestMessageBuilder；用真实user对象/ID加本轮提示，不能假定数组末项就是user。
- 开场同样准备新鲜request-local recall；无输入时读取合法当前背景/状态，不借旧共享对象注入。最终发送回执沿用v2.4材料ID/版本/预算体系。
- 多媒体和旧线上卡片使用现有语义转译；背景与立绘不自动进入识图。

时间配置沿用scenario.enableTimeAwareness；新增陪伴角色可选ZoneId只用于该功能，未设取设备时区。绝对时间用于保存/超时，设备时间用于记录分组，场景叙事时间保留文本。关闭实时语义时统一移除额外当前时间/现实间隔/日程及系统日期包装，但不改写原文或改变期限判断和记忆归属。

## 6. 原生XML扩展、设置与观测（D3）

沿用线下narration/thought/dialogue结构，在陪伴协议版本1下扩展可选sprite、voice_emotion及独立observe块；保留ONLINE JSON和长文char/thought parser。示意：

```xml
<observe version="1"><field id="time">傍晚</field><field id="obs_c42">蓝色外套</field></observe>
<narration sprite="normal">他把椅子拉近，留出空位。</narration>
<dialogue speaker="角色" emotion="放松" sprite="happy" voice_emotion="calm">想坐一会儿吗？</dialogue>
```

- 受限observe adapter先分离可选块，再调用既有正文parser；不塞进当前会被忽略的STATUS parts，不引入DTD/外部实体/HTML执行。
- ParsedCompanionReply绑定messageId、memoryRevision、protocolVersion、parserVersion；内容为带raw span的typed beats、observation与diagnostics。
- beatId由源ID/修订/parserVersion/稳定segmentIndex或span产生。一次完整回复仍是一条canonical消息；投影不回写TEXT parts或增加来源修订。
- content与持久TEXT保持同一权威raw；统一accessor明确companion优先级，display/speech/export分别取自己的投影，不能先去标签再当raw读取。
- 观测内建ID为time/place/state/detail；自定义ID程序生成、最多6项、显示名不能当解析键。请求快照保留旧字段定义，改名后历史仍可解释；未知ID/坏块保留原文、诊断且本轮observation=null。
- 未知属性/无标签/旧XML/纯文仍可阅读；表情键统一校验，不删除普通用户方括号；可选声线与视觉表情不互相推断。
- 五文风、三人称、默认不指定、自定义补充/模板、可复现细节hint进入生成层；人物事实/授权优先于文风。

2026-11-11 D3接线细化：`CompanionRequestDescriptor.settingsRevision`仍表示当前attempt创建时的实际设置修订，用于D2发送前守卫；新增`presentation`保存本次实际使用的文风、人称、补充、单次细节hint、观测字段ID/label及`sourceSettingsRevision`。显式失败重试复用原presentation，用户主动重新生成建立新presentation；两者分别保留D2来源重新冻结、timePolicy与最后HTTP守卫。不能仅凭`allowUncertainRetry`区分产品动作，也不能为重试旧表现而放松发送资格。旧descriptor缺presentation时使用默认无观测兼容投影，不从当前角色设置补造历史标签。

### 配置存储

在现有AppSettingsStore增加按assistantId索引的小型companionCharacterSettingsJson，schemaVersion+revision；不复制Assistant或改旧整列表保存语义。字段含风格/人称/观测/模板、图集引用、阅读/演出、声音与时区偏好。Key仍在现有安全存储，图片/音频只存稳定引用。

updateCompanionSettings(assistantId, expectedRevision, patch)在DataStore edit内读最新值并字段级更新；角色删除在同一设置编辑移除此映射，资源清理由提交后引用扫描处理。旧角色编辑不会覆盖新key。未知更高schema保留原数据并拒绝降级保存，旧资料缺字段不等于清空。

优先级：本轮明确的场景外观选择 > 角色陪伴偏好中的明确选择 > 原全局默认；历史遗留单图不是对新套装的永久覆盖。未作新外观选择时沿用旧scenario图；用户明确选择套装后先解析套装，缺图再回退旧图。13种气泡主题和全局无障碍原值保留。新页面草稿保存/取消一致，图像IO完成仍校验所见revision。Room与DataStore无跨库事务，跨资料导入用版本化计划/回执与幂等补存，不能声称一起原子提交。

## 7. 立绘、阅读与资源生命周期（D4）

- BeatPlayer是纯状态机：IDLE/TYPING/COMPLETE/END，输入为固定源版本；补全先取消timer，下一点击推进，最后一拍后本地重播。按grapheme推进，长按/滚动取消点击，覆盖层消费自己的事件。
- 新回复提交后才生成正式演出队列；流式未完成预览不作为新的已发生节拍。切阅读或查看旧记录不改变当前encounter或操作目标。
- 未设演出偏好的旧用户维持原列表/阅读；选择演出后按角色记住偏好。无素材也可通过稳定头像/姓名和文本使用，不能成为阻塞。
- 图集表达键共用schema，套装/素材有稳定ID与PORTRAIT/DECOR用途。确定回退为当前套目标表情→套默认→旧场景图→角色头像→姓名；不把上一拍表情当新匹配成功。
- 资产沿用LocalImageStore私有文件，失败留草稿，导入替换按引用关系清理，不能删除仍被其它场景/套装引用的文件；无base64进播放快照。
- 阅读按权限范围keyset分页，key至少有稳定排序值和messageId，不能以“游标那条必须仍存在”查询下一页；刷新保留已展开页并按ID去重。
- 滚动保存首个可见messageId+偏移，上翻暂停追底，接近底部才跟随；图片/字体/IME/删除后按锚点修正。设置、编辑、HUD、多选有统一返回层级。
- 主题/行高/背景/原生语义沿现有令牌；320dp/200%/48dp、键盘、系统栏与图片Fit都进入实际UI验收，截图只在真实宿主verify并单独接纳。

## 8. MiMo声音、翻译与收藏（D5）

- 复用MimoTtsClient、角色音色和AiTranslationService；新增companion VoiceController统一拥有会话内翻译/合成/排队/播放，不能复用每卡片各自独立的控制方式。
- 语音/自动播默认关闭；开关与现有后台VOICE_MESSAGE合成不同。只在用户请求点播/启用自动播/生成收藏后调用；缺配置始终保留文字。
- 统一SpeechUnit绑定源ID/revision/beatId、种类、原文、实际朗读文本/语言与音色配置。默认自动播只台词；手动选段可含旁白并跨场次按所选顺序，不能缩成当前场次。
- 翻译走独立用途和缓存，失败可重试或显式改读原文；不更改canonical。相同文本但目标语言/音色/克隆内容不同不会命中旧键。
- SpeechRequestKey覆盖文本hash、目标语种/翻译版本、端点标识/模型、角色音色及克隆样本版本、声学参数；不含明文Key，所有内存/磁盘缓存同一键。
- 每次选句/换模式/换角色/退出增加generation并取消网络与播放器；保存成功也不越过generation抢播。中止/错误输出不成为成功缓存。
- MiMo完整音频成功后校验文件头并用临时→最终文件发布；数据库引用与文件不可共享Room事务，使用PENDING/READY资源状态、hash/长度、引用校验和孤儿清理。已持久音频补挂接不重新合成。
- 独立收藏保存音频快照及原/朗读文本、语言、源修订、来源状态。未有音频时动作明确“生成并收藏”；取消收藏只减本资源引用，清缓存跳过收藏，删原文保留用户收藏并标出处失效。
- 缓存提供用量与手动清理；避免无限增长，按无收藏引用的LRU/可配置上限回收。自动回收不删原文/收藏。
- 长内容按句/段分批读取完整音频再串行播放；本版无PCM实时合成、逐字音频时钟或假字幕进度，UI仅显示当前实际朗读单元。

2026-11-19 D5已按此范围完成工程验收；实际 `CompanionVoiceSession`/Snapshot翻译、私有文件与Room回执、CANCELLED tombstone、独立收藏及UI接口、107 JVM/8设备和明确恢复限制见 [D5交接](../11-09-date-companion-voice/research/handoff.md)。新旧接口、55→56/固定55/S3保持，真实MiMo/物理设备及视觉接纳单列；D6集成结果见下一节。

## 9. 历史、修订、导出与资料（D6）

- 历史查询显式scope = 角色+persona+允许space/run+陪伴来源，选只读旧档时仍有边界。scene与date分组不同；旧无encounter记录按原会话/已知日期显示“旧记录，未分场”，不伪造开场。
- canonical编辑/单条及多选删除委托D1事务，增加turn mutationRevision，拒绝迟到结果、使旧投影/音频选择过期；v2.4来源闭包与独立人工版本保留规则继续生效。
- TXT重新从仓储遍历完整授权集合，以UTF-8 BOM、合法文件名和原生SAF输出；本场/日期/全部操作均不使用UI窗口作全量。保留原文/时间/说话人/控制类型，IO失败/用户取消不提示成功。
- 陪伴装扮本地包只白名单包含套装/表达键、布局、允许外观及素材内容映射；不含原文、记忆、prompt补充、角色API、Key、绝对私有路径、音色样本或定位配置。未支持的更高版本只预览错误，不部分导入。
- 私人ContextDataBundle按既有目标映射/幂等扩展陪伴偏好；继续保留v2.4已授权记忆及来源快照，不套用公开分享的“无原文”规则。旧包未带陪伴域保留目标现值。它仍不备份整个会话/声音库，不将TXT称可恢复备份。
- Room阶段与设置阶段有各自回执、CAS与可重试计划；避免先改旧整角色列表再覆盖新映射。资源导入先校验、私有暂存，再挂接，失败可清除未引用临时文件。

2026-11-20 D6已按本节完成工程/原生集成，实际接口见 [D6交接](../11-09-date-companion-history-integration/research/handoff.md) 和历史/资料专题规范。公开装扮v1与私人v3分开，PNG严格验证/单份有界冻结、完整仓储TXT/批次CAS与最终owner复核已接线。Android采用完整非空临时目录改名发布，保旧单文件恢复；Room/文件/逐设置回执分别提交。schema56→57只新增操作回执表，旧58表不变。JVM最新2956/2956、原生9/9、52工程项齐全；视觉/真实模型/物理音效和未发布状态按交接保留。

## 10. 交付与回退

D1→D2→D3→D4→D5→D6串行验收共享接口；每块输出相关测试/失败注入及实际文件清单。完整跨模块审查由D6汇总，不能把某一块成功当整轮通过。

回退是关闭新入口/新契约调用或修复前向迁移，保留已存原文与附属表；不降schema、不删用户库、不把linked消息丢字段后伪装legacy。已有资料/截图/2.4改动独立保护，具体提交范围在实施结束后再整理，当前不批准发布。

技术未知已通过 [存储细化](research/storage-contract-refinement.md) 和 [设置/协议细化](research/settings-protocol-refinement.md) 收敛。实际模型质量、设备音频/性能、目标宿主视觉及版本发包不由规划假定通过。
