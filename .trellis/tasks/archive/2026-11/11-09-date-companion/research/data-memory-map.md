# Research: 陪伴回合、持久化与 v2.4 记忆衔接

- Query: 将指定陪伴手册映射到 Narra 当前 RP 主链，辨别场次/回合可靠性、授权上下文与记忆的复用点和差距。
- Scope: internal；手册作为外部项目静态参考，不访问其源码、服务或用户数据。
- Date: 2026-11-09。
- 状态：仅规划研究，未执行测试、构建或产品修改；下列测试是后续验证入口，不是本次通过结果。
- 产品决定更新：用户已选择“升级现有线下扮演”与完整规划；父/子设计已落定六块主体验。本报告保留静态证据，最终范围和合同以父PRD/design为准。

## Findings

### 1. 主要结论

1. **v2.4 的空间、运行、来源版本、提取、召回与失效机制可直接作为基础。** 不需要另一套陪伴记忆库；`CONTINUOUS` 与 `INDEPENDENT` 才决定归属，线上/线下只决定互动与表现。
2. **同一 RP 会话的线上/线下转换已经存在。** 消息保存自己的 `roleplayInteractionMode` 与输出格式，入模时按当前模式转译旧消息，有实际跨模式回归。
3. **跨场次“关系不断档”不能只靠共享记忆宣称完成。** 当前主回复的原文材料来自当前 conversation；日常空间共享已提取记忆，但尚未发现供新陪伴入口使用的、跨 conversation 未提取原文授权聚合器。
4. **现有重试不是手册的安全重生成。** 目标旧答会先清空为 LOADING，目标之后的消息会被裁掉，随后请求前保存整份快照；不能直接复用为“旧答保留、成功后版本化替换”。
5. **主回复还没有持久逻辑回合与 generated 待补保存阶段。** v2.4 的 `MemoryProcessingJob` 可靠性属于记忆加工，不能据此声称聊天主回复已支持产物免费恢复。
6. **主回复完整性和用户停止语义要明确。** 现有流结束事件不携带结束原因；停止的部分正文按已有产品语义保存为 COMPLETED。新陪伴不能让这类片段被误认为完整回合。

### 2. 文件与实际调用链

以下代码路径以 `app/src/main/java/com/example/myapplication/` 为根；测试路径以 `app/src/test/java/com/example/myapplication/` 为根，除非另行标注。

| 文件与锚点 | 当前职责与可复用模式 |
| --- | --- |
| `model/ChatMessage.kt:21` | 共享消息：ID、conversation、正文/parts、状态、时间、RP 模式/格式、memoryRevision、memoryRecallRequestId；没有 encounterId、语义 turn kind 或生成阶段。 |
| `model/RoleplaySession.kt:5`；`model/RoleplayScenario.kt:29` | session 包含 scenario/conversation/runId；scenario 已有时间感知开关与 memoryPurpose。session 不等于每次临时“见面”场次。 |
| `data/repository/roleplay/RoleplayRepository.kt:279` | startScenario 默认复用已有 session；缺 session 才创建新运行。`createNewRun:337` 归档旧绑定、新建 conversation/runId、绑定记忆后写开场；`withScenarioTransaction:404` 在生产 Room 事务中执行。 |
| `data/repository/roleplay/RoleplayRepository.kt:379` | 用途变化创建新运行，单纯互动模式变化沿用原运行并重新校验绑定。 |
| `data/repository/roleplay/RoleplayRepository.kt:799`；`viewmodel/RoleplayScenarioActionSupport.kt:86` | 当前开场是场景 openingNarration，空时可填角色 greeting；进入后直接保存，不是请求生成→预览→用户接受的三种开场状态机。 |
| `viewmodel/RoleplaySendActionSupport.kt:697` | 防同界面重复发送、启动会话、冻结局部 state、保存输入和 LOADING、执行单人或群回复；新陪伴应复用共享服务而独立限制单角色行为。 |
| `roleplay/RoleplayRoundTripSupport.kt:45` | 构建用户/助手消息，模型历史先选 COMPLETED 且有内容的消息；`currentConversationMessages:37` 只筛当前 conversation。 |
| `viewmodel/RoleplayRoundTripExecutor.kt:123` | 局部发送代次与会话检查；`:156` 冻结记忆 runtime/请求 ID；`:171` 初始消息持久化；`:244` 组装上下文；`:744` 完成后触发记忆尾段。 |
| `conversation/ConversationAssistantRoundTripRunner.kt:76` | stream→解析→回调构造消息→upsert；将后置副作用失败与主消息成功区分，但还没有持久生成产物/补保存用例。 |
| `data/local/RoomConversationStore.kt:81` | 快照替换和 `updateConversationMessages:98` 均走 Room 事务；消息变更统一经过 MemorySourceStorage。可在此模式上增加受控单条 revision 比较，不在 UI 直接写 DAO。 |
| `data/local/memory/MemorySourceStorage.kt:28` | 自动递增来源 revision，改删原文同步使来源及派生材料失效；`:84` 首次来源冻结；完成正文才成为 AVAILABLE，LOADING/ERROR 保存 UNKNOWN 凭据。 |
| `context/PromptContextAssembler.kt:86` | 角色、persona、世界书、摘要、记忆共用组装；`:110` 校验 runtime；`:122` 每次局部准备 MemoryRecallPlan，不依赖共享角色字段残留。 |
| `context/MemoryRecallEngine.kt:35`；`data/repository/context/MemoryRecallStore.kt:372` | 召回按角色、空间、persona 校验资料；长期认知/状态、事件、经历与向量沿用 C3–C5 实现。 |
| `data/repository/ai/GatewayRequestMessageBuilder.kt:39` | 统一三协议前的消息构造；`:104` 处理实际历史深度/顺序/role 注入，`:170` 处理媒体与 parts。 |
| `roleplay/RoleplayOnlineReferenceSupport.kt:128` | 当前角色可见性与旧模式正文转译，线上返回时不继续携带旧线下协议。 |
| `viewmodel/RoleplayContextUpdateSupport.kt:187`；`di/AppGraph.kt:575` | 完成后优先 `updatePendingRoleplayMemories`，读取持久来源；已存在摘要/提取协调与启动恢复，不重新发明尾段服务。 |

主链为 `RoleplaySendActionSupport → RoleplayRoundTripExecutor → PromptContextAssembler + RoleplayPromptDecorator → AiGateway → ConversationAssistantRoundTripRunner → RoomConversationStore/MemorySourceStorage → 记忆协调器`。

本研究以 RP 线上/线下实际生产链为准；主会话/UI 研究已确认旧 ChatNavGraph 只在 benchmark 注册，不能把旧 ChatViewModel 的能力直接写成陪伴入口已具备。

### 3. 关键差距与设计建议

| 项目 | 当前证据 | 陪伴规划需要的契约 |
| --- | --- | --- |
| 场次边界 | startScenario 复用 session；restart 改 runId/conversation；消息无 encounter 标识。 | 区分记忆运行与见面 encounter。按次回看不得用日期或当前 session 替代 encounter；同场跨午夜与同日多场可验证。既然升级现有线下入口，优先评估在原 conversation/run 内标记 encounter，保留已有明确“重开”动作的归档语义。 |
| 开场接受 | 进入场景即写 greeting/openingNarration，`ensureOpeningNarrationSeeded:799`。 | 预览和废稿不进入共享消息/来源/记忆；接受后一次事务写场次、开场与选中模式，再允许下一回合。直接开始不得触发开场请求。 |
| 语义继续 | `ChatMessage`、当前 SendActionSupport 无专用 continue kind；普通输入要有可发送内容。 | `turn.kind=INPUT/CONTINUE/OPENING` 或等价类型；展示短占位，发送/重试/重生成共用转换器。技术调度说明不落原文或长期记忆。不要将 preset 中的 continue 提示片段当作已接通用户动作。 |
| 安全重生成 | `RoleplayRoundTripSupport.kt:144` 接受 COMPLETED/ERROR，清空旧答、裁到目标之前；`RoleplaySendActionSupport.kt:128` 请求前 ReplaceSnapshot。 | 保留目标 ID、逻辑序号、原 createdAt 与旧正文；冻结期望 revision，成功后同事务 CAS 替换并失效派生版本。失败/保存失败/并发编辑不得丢旧答。不能把旧“回退到选中楼层再生成”语义偷偷改成另一产品行为。 |
| 失败输入复用 | Retry 复用原 assistant ID 与前面的 user，不重复插入输入；仅有局部 isSending/run gate。 | 显式逻辑 turnId 与 attempt/requestId；存储唯一约束/状态抢占避免第二 ViewModel 或进程恢复重复提交，同文连续两次仍是两个真实输入。 |
| 模型成功、存储失败 | Runner `:96` upsert 失败进入同一 onFailed；无持久产物/回合状态；RP 重入 `RoleplayRepository.kt:546` 清除孤立 LOADING。 | 区分生成失败与结果待保存，保存可恢复产物并提供补保存；进程死亡后未确定远端结果不能静默再请求。仅内存副本不足以宣称持久恢复。 |
| 迟到与取消 | Executor 的 canApplyUiUpdate 同时用于 canPersistResult；VM `cancelSending:1635` 失效发送代次；未活动时不把结果写入新场景。 | UI 归属检查与旧场次结果保存策略分开；若允许后台补保存，绑定原 encounter/run/persona，不能以“仍显示当前页”作为唯一存储授权。冻结后源版本/角色/绑定变化要拒绝提交。 |
| 主回复完整性 | `model/AssistantReply.kt:47` 的 Completed 无 finish reason；`model/PromptCompletionResult.kt:8` 仅现有辅助请求有详细结束证据；`RoleplayRoundTripExecutor.kt:521` 主动停止的部分正文保存 COMPLETED。 | 扩展或适配已有网关返回结束证据，保留正文/推理分离；空/纯推理、截断、过滤、取消分别处理。部分文本可供回看但不可冒充完整回合入库/提取。兼容当前 RP 停止语义需定向设计。 |
| 提示/工具边界 | Decorator `:217` 已有角色与用户边界，`:238` 仍要求每轮有效新变化；GatewayRequestMessageBuilder `:145` 对所有 ROLEPLAY 追加 special-play 提示。Executor 还会组装经济/脚本内容。 | 陪伴使用已有提供商/网关，但应明确允许的上下文与工具，不整套带入转账/手机动作；允许简单日常与沉默，不强制每次升级情绪。 |
| 时间与归属 | `RoleplayTimeAwarenessSupport.kt:65` 用设备时区格式化；scenario 只有当前模式的 enableTimeAwareness；recall 调用无 omitDates 参数。 | 增加陪伴时间语义时区分保存时间/显示时间/叙事时间。关现实时间不能悄悄改变记忆用途，也不能只关一条 prompt 就宣称所有记忆日期已去除。 |

### 4. 原文范围与跨入口衔接

- **已经成立**：同 conversation 切模式，`RoleplayViewModel.kt:1362` 仅更新 scenario 模式/格式，不变 memoryPurpose；`RoleplayRunRepositoryTest.kt:76` 验证模式变化保留运行。线上返回对旧线下正文用 `RoleplayOnlineReferenceSupport.kt:153` 转译，而不是丢弃历史。
- **已经成立**：日常重开仍共享空间。`RoleplayRunRepositoryTest.kt:173` 断言新 runId、相同 spaceId、旧记忆与原 conversation 保留、其它角色隔离。
- **尚未成立**：独立新 encounter/conversation 能立即看到上一次尚未提取的原文。`RoleplayRoundTripSupport.kt:37`、SendActionSupport `:764`、Executor `:1160` 都从当前会话窗口出发，后者只用同 runtime 的已验证摘要裁剪，没有跨会话原文聚合。
- **建议**：按已确认的“升级现有线下”方案，优先在原 conversation 内标记 encounter，直接复用已验证的双向模式转译；不要仅因切成线下就新建空间或重开剧情。若仍要求跨 conversation 承接未提取原文，则增加受控聚合：冻结角色/空间/persona、允许的运行集合、来源版本、稳定排序与显式截止；不得把全部同角色会话、旧独立剧情、群聊私有消息混入。
- 用户 UI 的分页/回看窗口、当前场次的重生成窗口、模型获准的历史必须是三套独立范围。空授权范围不能回退 currentRawMessages 或全库。
- 复用 `ConversationSummaryCoverageSupport.kt:175` 的可靠连续覆盖裁剪、`RoleplayTranscriptFormatter.kt:25` 的完整语义来源与 `:166` 的角色可见性。新“继续”消息需适配提取语义，不把占位词当用户确实说过的话。
- 复用 `GatewayRequestMessageBuilder.kt:104` 的世界书深度注入；`RoleplayFormatReminderSupport.kt:58` 已按最新 user 定位而非最终数组最后一项。但现有 Executor 在格式提醒后才把材料交给组装器，陪伴应使用未加技术提示的真实输入作为召回/世界书检索材料，避免新玩法说明污染匹配。

### 5. 建议回合契约与拆分边界

以下是适配建议，未获产品批准，不要求采用这些类名。

1. **场次/来源基础**：encounter、语义 turn、逻辑顺序、来源模式、导航 origin、运行/persona 快照；资产和播放器书签另存，不复制完整历史。当前库为 54，实施时以最新 schema 为准递增一版，保留旧 schema 与既有 v2.4 数据。
2. **回合仓储**：冻结输入及源 revision；持久化 `PENDING/GENERATING/GENERATED/COMMITTED/FAILED/CANCELLED` 或等价状态，记录请求是否已发；模型请求在事务外，确认/补保存/CAS 在事务内。
3. **请求协调**：生成每次 request-local 记忆/上下文，复用 provider 整体配置与三协议适配；不把 memoryRecallRequestId 当 turnId。错误信息走现有脱敏出口。
4. **来源提交**：只有接受的开场/真正提交的回合触发 MemorySourceStorage；人工编辑或 CAS 替换继续走其 revision/失效逻辑，用户独立人工修订按 v2.4 语义保留。
5. **回合后处理**：调用现有 durable 提取/事件/认知尾段；取消、失败、未接受预览、未保存结果不推进成功水位。开场是否触发自动提取应与普通已接受正文使用同一策略。
6. **回放/UI交付**：解析与声音绑定 messageId+revision；重生成或编辑后旧观测/播放/缓存引用过期。声音/收藏详细语义交另一研究主题。

数据库/模型字段、共享消息仓储、网关事件和记忆接口不可由多个子任务同时写；可把“场次与可靠回合”“上下文与生成语义”“整体验收”作为串行依赖。UI/声音可以研究并行，集成以这些契约为先。

### 6. 手册 F07–F13 / G01–G11 映射

| ID | 当前状态与证据 | 规划所需可观察验收 / 现有入口 |
| --- | --- | --- |
| F07 同日多次/跨午夜 | 部分：runId 存在，encounter 缺失。 | 两次见面分组不同；同场跨午夜仍一场；不改变记忆空间。复用 RoleplayRunRepositoryTest，再补场次查询测试。 |
| F08 A 迟到、已进入 B | 部分：发送代次/当前会话 guard 已有，持久场次提交策略缺失。 | A 不污染 B 的 UI/消息/来源；按已选策略保留 A 待保存结果。复用 RoleplayViewModelTest 的切换/取消、RoleplayMemoryLifecycleTest。 |
| F09 已存 user，生成失败 | 部分：重试不重复 user，未有独立 turn 幂等。 | 同一 turn 重试一次输入、一次成功回复；新发同文是另一 turn；多 owner 并发仍唯一。 |
| F10 成功生成、保存失败 | 缺失主回合阶段。 | 注入数据库失败，旧答/输入保留；补保存模型调用数不增加；重启仍可恢复；未确定请求状态不自动重发。 |
| F11 重生成失败/超时/事务中断 | 当前行为不满足旧答保留。 | 三种失败原 ID、正文、顺序、时间和来源版本都保持；成功才统一替换。现有 retry 测试只作兼容对照。 |
| F12 生成中编辑/删除源答 | 部分：来源版本系统已有，主回复没有源 revision CAS。 | 编辑后迟到生成拒绝覆盖；删除后不能复活旧答/派生材料；真实 Room 事务验证。 |
| F13 同时重试 | 部分：当前 UI isSending、本实例锁，不是持久请求唯一性。 | 两个独立 owner/重建 ViewModel 抢同一 turn；最多一次允许发起与一次提交，失败不得偷换新 turn 绕过。 |
| G01 不分析，只相处 | 部分：现有互动原则，但仍有每轮变化硬规则。 | 明确允许坐一会儿/沉默；请求中不夹带强制诊断/任务推进；真实模型语义另列人工场景评估。 |
| G02 拒绝、纠正、换话题 | 部分：已有不代说/不代决定原则。 | 拒绝不是“欲拒还迎”，纠正改变后续场景；单靠 prompt 断言不能声称真实模型效果已验收。 |
| G03 同文风不同角色 | 部分：角色/场景/preset 分层已有。 | 风格只改变描写，不覆盖角色事实/身份/授权；相反性格合成情境评估；原文/请求 fixture 验证身份不串。 |
| G04 无新动作点继续 | 缺少语义回合。 | 原文仅记录继续语义，角色可自然回应；不代写用户动作、内心、身体反应或接受。 |
| G05 继续重试/重生成 | 缺少统一转换。 | 所有路径冻结相同 continue 语义；调度长文不进入 DB、TXT、来源提取或下一轮原文。 |
| G06 空范围/用户断点 | 部分：摘要覆盖可验证，没有陪伴授权聚合边界。 | 空范围、群聊私有、旧独立剧情和断点外原文在最终 HTTP 均不可见；不由 UI 已加载内容回填。 |
| G07 相同深度/深度0 | 共用注入实现已具备。 | 保持顺序/role；提示落真实 user，不落 depth=0 世界书；GatewayRequestMessageBuilderTest:16 + 新陪伴实际 HTTP。 |
| G08 卡片/媒体/长正文 | 部分：parts 构造与完整语义来源已有。 | 控制标记/资源 URI 不冒充语言，长文末尾事实仍按明确范围保留；RoleplayTranscriptFormatterTest + GatewayRequestMessageBuilderTest。 |
| G09 关时间/时区/跨年 | 部分：场景开关与设备格式，陪伴独立语义未实现。 | 最终D2纳入可选角色ZoneId；系统/角色/界面时间各自明确，关闭时不附加现实时间推进，原文日期不改写。 |
| G10 纯推理/HTML/截断 | 部分：AiGateway:763 拒绝无可见正文；主流 Completed 无结束证据，停止片段是 COMPLETED。 | 非完整正文不假作完整剧情/来源；可选观测坏块不吞合法正文；三协议 MockWebServer + PromptExtrasCompletionResultTest 参考。 |
| G11 关记忆/召回失败 | 已有 request-local RecallPlan 与 runtime 校验，开场尚未接入。 | 每次开场/普通/重生成独立准备；关开关或失败不复用上轮、他人材料，保留现有失败回执语义；MemoryRecallRepositoryTest / 实际 RP HTTP 测试。 |

### 7. 可复用验证入口

- `roleplay/RoleplayRoundTripSupportTest.kt`；`viewmodel/RoleplayViewModelTest.kt:1037`（旧 retry 语义）、`:1129`（编辑后回退）、`:2690`（空取消）、`:2847`（停止片段）、`:4609` / `:4729` / `:4822` / `:4931` / `:5040`（跨模式请求）。
- `conversation/ConversationAssistantRoundTripRunnerTest.kt:190`：主消息提交后副作用失败不改成失败消息。
- `data/repository/roleplay/RoleplayRunRepositoryTest.kt:76` / `:132` / `:173` / `:302` / `:359` / `:380`：绑定、旧档保留、独立/日常、事务回滚与并发启动。
- `viewmodel/RoleplayDurableMemoryFlowTest.kt:64` / `:105` / `:124` / `:293`：完整来源、失败取消、群角色边界、实际 HTTP 回执与下一次失败。
- `data/repository/context/MemoryRecallRepositoryTest.kt:118` / `:146` / `:184`：改删源/身份冲突、重开持久回执、事务后发布。
- `context/PromptContextAssemblerTest.kt`、`context/MemoryRecallEngineTest.kt`、`context/PresetPromptRendererTest.kt`、`data/repository/ai/GatewayRequestMessageBuilderTest.kt`、`data/repository/ai/PromptExtrasCompletionResultTest.kt`。
- 有 schema 改动时补 `app/src/androidTest/.../MemoryFoundationMigrationTest.kt` 所用固定旧 schema 方法、最新迁移测试及 S3 fixture；只用合成数据与隔离库。

## Related specs / References

- `.trellis/workflow.md` Phase 1.2：研究文件持久化；不运行实现阶段。
- `.trellis/spec/backend/memory-system-guidelines.md`：运行/persona、来源、任务、请求费用未知状态、提取与召回契约。
- `.trellis/spec/backend/memory-management-guidelines.md`：一次激活、暂停/继续、历史档案与上层生命周期。
- `.trellis/spec/backend/database-guidelines.md`：当前 schema54、事务、迁移/固定旧库、共享仓储。
- `.trellis/spec/backend/network-guidelines.md`、`error-handling.md`：三协议、取消、POST 重试、副作用与错误脱敏。
- `docs/agent-prompt-reference/date-companion-agent-handbook.md` §6–8、§13–14、§16–18、§20：产品/验收参考。其 TypeScript/IndexedDB/源码路径不属于 Narra；未引入新依赖或服务。

## Caveats / Not Found

- **已确认产品边界**：升级现有线下扮演，不新建独立陪伴入口。需要在后续设计明确“切入线下”“新一场见面”“重新打开现有场景”“重开剧情”各自含义；不得借升级把旧数据搬到另一套记忆库。
- **共享实现兼容边界**：可靠回合适配可能触及共用 Runner、Gateway 流事件与 Room 消息。先让新线下路径显式使用回合/CAS 契约，保留线上 JSON 动作、群回复逐角色处理、OFFLINE_LONGFORM 原协议和旧消息转译；不能因为参考为单角色便把现有多人或长文入口删掉。若统一升级共用接口，须让这些旧路径走等价兼容分支并扩充回归。
- **需要范围确定**：是否新增角色时区/陪伴专属时间设置；原生现有开关语义不能由参考默认值自动替换。
- 现有“编辑用户消息并回退后文”和“选中楼层重试”是明确旧行为，须保持兼容或通过新入口区分，不能作为低风险重命名处理。
- 本研究静态核对主调用链，不把可复用测试的存在当作通过，不宣称真实模型语义、费用、设备性能或退出后的后台完成已验证。
- v2.4 已有代码与验证成果仍待主会话完成其原收尾，当前未发布；2.4.1 规划以工作区接口为基线，不改变那些文件。
