# v2.4.1 陪伴执行计划

状态：2026-11-09方案获批；2026-11-20 D1–D6工程责任与原生集成完成。用户确认207项UI候选，222/222目标宿主截图、完整云端JVM/Lint/debug及Actions→R2→Pages→公开清单全部通过，2.4.1-dev/20401已发布；工程按本轮授权归档。原工程2956/9方法证据和模型/物理音效缺口保留，见D6 research/release-handoff-2026-11-20.md。

## 1. 启动门槛与当前依赖

- [x] 用户已审阅最新完整方案并明确“可以，没问题，开始吧”，批准D1–D6实施。
- [x] 已记录research/execution-baseline.json：当前HEAD、2124项展开工作区状态、1349源码哈希及273保护项；保护v2.4与用户改动。
- [x] 核对当前最新schema和v2.4的空间/来源/提取接口；D1从54递增至55并经固定旧库验证，不重做C1–C6。
- [x] 每个子任务的PRD/design/implement与真实JSONL上下文齐全；2026-11-20六子任务及父任务task.py validate通过，D6 implement23/check29。
- [x] 首次实施激活D1；父任务只协调，不把父任务当默认代码实现目标。后续激活先检查本文的依赖证据。

v2.4尚有原视觉/提交归档收尾，与本轮计划分别记账；本轮可以基于已验证工作区接口规划。发布参数仍2.3.0-dev/20300，未经发版请求不改版本/更新JSON，不推送或上传。

## 2. 子任务顺序

| 顺序 | 任务 | 硬前置 | 交接验收 |
| --- | --- | --- | --- |
| D1 | [场次与可靠回合](../11-09-date-companion-foundation/implement.md) | 最终规划批准 | 附属表/消息link、资格全入口、产物补保存、CAS、完成证据及旧路径兼容通过 |
| D2 | [三开场与上下文](../11-09-date-companion-entry-context/implement.md) | D1通过 | 真实入口/preview接受/继续/返回、授权聚合/记忆/时间和HTTP矩阵通过 |
| D3 | [正文文风与观测](../11-09-date-companion-style-observation/implement.md) | D2通过 | XML/权威原文/投影、配置CAS、观测字段快照和模板/风格通过 |
| D4 | [立绘与阅读](../11-09-date-companion-stage-reading/implement.md) | D3通过 | 纯播放器、角色素材、跨场阅读/锚点和实际UI/候选通过 |
| D5 | [声音与朗读](../11-09-date-companion-voice/implement.md) | D4通过 | MiMo/翻译/缓存/取消/选段/独立收藏与资源恢复通过 |
| D6 | [历史与集成](../11-09-date-companion-history-integration/implement.md) | D1–D5全部通过 | 记录修订/导出/资料、旧功能、44+8矩阵和全范围交付通过 |

本轮采用串行实现，共享Room/网关/设置/导航不能由多个子任务同时修改。纯研究或只读复核可并行；构建执行者在每次交接时唯一。每个实施/检查代理均需知道代码库中有其他人，不能回滚、重置或覆盖不属于自己范围的变更。

## 3. 文件所有权与风险点

| 文件/模块 | 主负责块 | 约束 |
| --- | --- | --- |
| ChatMessage/MessageEntity、RoomConversationStore、data/local/companion、迁移/schema注册 | D1；D5新增声音表时显式接手 | 完整mapper/默认值/唯一键/固定旧库；不将linked未知数据降级legacy |
| MemorySourceStorage、RoomMemoryArchiveRepository、摘要/兼容提取资格 | D1 | waiting/control资格、CONTROL_NOOP覆盖及首次persona；不得绕开v2.4失效 |
| AssistantReply/主流事件、PromptExtrasCore结束原因、AiGateway适配 | D1，D2消费 | 旧调用默认兼容；新陪伴必须有完成证据，不全局改停止语义 |
| RoleplayNavGraph、ImmersivePhoneShell、RoleplaySend/ScenarioActionSupport | D2；D4/D6后续明确接手 | 原路返回、不恢复旧Chat图、不把新场等同重开 |
| PromptContextAssembler、原文聚合、RoleplayPromptDecorator/转译 | D2；D3仅增表现协议 | 真实输入触发、授权边界、request-local recall、无技术指令污染 |
| AppSettingsStore/AppSettings、CompanionCharacterSettings | D2建时间/基础契约；D3完整配置；D4/D5消费和字段扩展 | 小映射按assistantId+CAS；旧整角色保存不可覆盖；字段追加串行 |
| RoleplayOutputParser/MessageFormatSupport、统一raw/beat/observation | D3 | 旧XML/JSON/长文兼容；投影不写回canonical |
| RoleplayScreenContent/DialoguePanel/ReadingMode、素材/播放器 | D4 | 原消息列表/主题保留；假Replay/Flow Observation不可充产品 |
| MimoTtsClient/AiTranslationService/AudioFileStorage、VoiceController/收藏 | D5 | 只复用已选供应商，统一音频所有权，文件和Room分阶段 |
| 记录查询/编辑/SAF/装扮codec/ContextDataBundle及传输协调 | D6 | 完整仓储导出、白名单、幂等回执与Room/DataStore边界 |
| 版本记录/规范/任务树/提交收尾 | 主会话 | 仅记录真实完成与限制，保护旧版本段和用户manifest |

原角色/聊天/线下开关、GROUP、长文、v2.4记忆及旧JSON序列化是兼容门禁，不以“新功能用不到”为由删除。

## 4. 每块实施与检查

1. 从task.py current确定本次子任务。派发prompt必须以Active task:路径开始；Codex原生注入优先，缺注入时按角色fallback读取指定上下文。
2. trellis-implement按该块计划实现与最小充分验证，输出真实文件范围和问题；本轮只读研究不作为实施完成。
3. trellis-check独立检查本块全范围，修复已证实失败后定向复验；合格交接必须有实际命令、报告及测试数量，不能累加多轮相同用例冒充独立覆盖。
4. 主会话核对证据与对应源码，更新需求/矩阵/版本记录和新学到的专题规范。依赖块通过后继续下一块，不重复询问已确认产品范围。
5. 无新变更/无失败不反复跑全量；最后D6承担整合的完整门禁。

新增测试名称由实现发现/确定，以下命令中的Companion通配仅用于新测试真正存在之后；必须核对非零测试与真实类名。

## 5. 验证命令与实际入口

各块先检查测试文件，再运行匹配的旧回归与新测试。下面为计划命令，不是本轮已执行结果。

```powershell
rg --files app/src/test app/src/androidTest | rg 'Companion|Roleplay|Memory|Voice|Translation'
./gradlew.bat :app:testDebugUnitTest --tests "*Companion*Test" --console=plain
./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.roleplay.RoleplayRoundTripSupportTest" --tests "com.example.myapplication.viewmodel.RoleplayViewModelTest" --tests "com.example.myapplication.viewmodel.RoleplayDurableMemoryFlowTest" --console=plain
./gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --console=plain
./gradlew.bat lintDebug --console=plain
./gradlew.bat assembleDebug --console=plain
```

主风险的旧入口见data-memory-map、storage-contract-refinement和voice-assets-history-map；各子计划列出更窄的选择器。D1迁移使用固定旧schema真实Room与文件库重开，不以纯内存测试替代恢复保证。

D4等改动已有线下/阅读/侧栏/手机入口时，按真实受影响类选择Roborazzi verify，不提前record：
```powershell
./gradlew.bat :app:verifyRoborazziDebug --tests "com.example.myapplication.ui.screenshot.OfflineRoleplayScreenshotE04Test" --console=plain
```

最终D6运行完整JVM、受影响截图、Lint、dev/AndroidTest构建和相关设备故事；设备可用时定向instrumentation，不能无差别操作用户库：
```powershell
./gradlew.bat :app:testDebugUnitTest --console=plain
./gradlew.bat :app:compileDebugAndroidTestKotlin lintDebug assembleDebug :app:assembleDebugAndroidTest --console=plain
```

设备只使用合成数据/隔离库/Fake或MockWebServer。真实模型/TTS质量、计费和物理设备体验在专项报告中与自动验证分开，未经用户明确选择实际服务不因测试方便调用真实付费接口。

## 6. 关键失败注入与跨模块故事

- **费用/保存**：网络前存储失败不发请求；响应已落盘但canonical失败，关库重开免费补存；首次产物落盘失败只内存保留；REQUESTED崩溃进入不确定且不自动重收费；成功回执丢失仍唯一。
- **来源**：pending INPUT显示已存但所有自动/手动/旧引擎/摘要入口拒绝；CONTINUE有空操作覆盖凭据；资格提升保留首次persona；人工修订/删除与迟到模型CAS。
- **开场**：多次预览离开零共同经历；连点接受一个开场；直接开始没有旧greeting偷seed；A离场开B后A不得污染B。
- **连续性**：同conversation线上→线下→线上；同日两次/同场跨午夜；日常跨conversation未提取原文立即可用；独立/角色/persona/群私有隔离；断点空范围与depth0世界书。
- **呈现**：完整raw→XML投影→HUD/演出/阅读/播音/导出一致；未知属性/坏块不吞正文，改观测标签历史仍正确；源改后旧播放队列失效。
- **声音**：语言/模型/音色/克隆版本矩阵；翻译/合成/prepared迟到；长文分段停止；跨场选段含旁白；生成收藏/清缓存/删源/取消收藏的不同生命周期。
- **资料与UI**：50条窗口导出121+全部；旧单图/样式/字段保留；并发DataStore修订；Room后设置失败重试；320dp/200%/48dp/IME/返回与原快捷项。

## 7. 收尾与回退

- [x] 六块工程范围和52个ID均有实际结果/明确适配边界，D6最新去重JVM2956/原生9及源码/APK绑定可追踪。
- [x] D6独立全范围检查确认无遗留工程问题，模型/实机效果与视觉接纳明确单列。
- [x] 规范按真实接口更新、2.4.1版本记录同步；原始失败/源码/包证据保留。
- [x] 用户接纳207项目标宿主候选，保留14原图；云端222/222、221图零差异，工作与基准分别提交。
- [x] 按已验收源码清单提交2.4依赖、D1–D6和用户确认的UI，定向修复空闲通知竞态后回归及完整CI通过。
- [x] 本轮用户明确批准工程归档、GitHub推送及Actions/R2/内置可选更新；发布已完成，D1–D6与父工程归档及journal收尾，未实测效果继续单列。

回退以停用新调用入口/修复前向迁移为主，保留数据与旧消息读取；禁止降schema、删库或丢companion link强装legacy。参考资料保持指纹，不因它链接到原仓库而改写其源码节录。
