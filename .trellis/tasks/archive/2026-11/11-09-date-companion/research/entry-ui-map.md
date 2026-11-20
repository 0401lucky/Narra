# Research: 2.4.1 陪伴入口、线下界面与交互映射

- Query: 将 `date-companion-agent-handbook.md` 的单角色见面、三开场、演出/阅读、观测与设置映射到 Narra；比较升级现有线下入口与新增见面入口的兼容影响。
- Scope: internal；参考资料来自工作区内的 SullyOS 手册，当前实现事实来自 Narra 源码与测试。
- Date: 2026-11-09。
- Status: 规划研究；未修改产品代码、未运行构建/测试、未调用模型或使用私人数据。2026-11-09 用户已明确选择“升级现有线下扮演”（父 PRD R5）。

## Findings

### 1. 首先分清真实产品入口

| 事实 | 当前源码证据 | 规划意义 |
| --- | --- | --- |
| 产品默认进入角色手机主壳 | `app/src/main/java/com/example/myapplication/ui/AppRoot.kt:167`；`ui/navigation/RoleplayNavGraph.kt:69` 的 startDestination 为 ROLEPLAY | 陪伴入口应沿真实手机主壳/角色卡/现有 RP 会话设计 |
| 普通 Chat 仅在 benchmark 会话注册 | `app/src/main/java/com/example/myapplication/ui/navigation/AppNavHost.kt:65` | 不能因存在 ChatScreen.kt 而把它当作生产聊天入口，也不应为本功能恢复旧 Chat 图 |
| 主壳入口实际传入完整角色与会话列表 | `app/src/main/java/com/example/myapplication/ui/navigation/RoleplayNavGraph.kt:175` | 可复用既有角色身份与单聊选择，不需要新增角色资料系统 |
| 新建会话有五种 UI 选项 | `app/src/main/java/com/example/myapplication/ui/screen/immersive/ImmersivePhoneShell.kt:280` | “线上 / 线上-心声 / 线下 / 线下-心声 / 剧情模式”已经形成用户习惯；不能当作全空白产品重做 |
| 角色卡与全局新建都支持模式选择 | `app/src/main/java/com/example/myapplication/ui/screen/immersive/ImmersivePhoneShell.kt:1900`、同文件 `:2138`、`:2292` | 新三开场适合挂接明确的新场操作，已有“打开会话”另保留 |
| 点击现有会话直接到 play(scenarioId) | `app/src/main/java/com/example/myapplication/ui/navigation/RoleplayNavGraph.kt:146`、`:355` | 目前无独立准备/开场预览目的地 |
| 设置中切模式更新同一场景 | `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplaySettingsSections.kt:292`；`viewmodel/RoleplayViewModel.kt:1368` | 显示/生成模式改变，不代表应改变记忆用途、角色或重新建一场 |
| 读设置页返回不重进已加载会话 | `app/src/main/java/com/example/myapplication/ui/navigation/RoleplayNavGraph.kt:451` | 现有保留消息窗口的行为需继续保持 |

本节缩短的 `ui/...`、`viewmodel/...` 路径均相对 `app/src/main/java/com/example/myapplication/`。

### 2. “打开旧会话”与“开始新一场”是不同产品语义

当前模型是 `RoleplayScenario → RoleplaySession → conversationId + runId`，并有显式 `MemoryPurpose`：

- `app/src/main/java/com/example/myapplication/model/RoleplayScenario.kt:5`：场景含 assistantId、用户身份、背景、人物图片、静态 openingNarration、模式、SINGLE/GROUP 与 memoryPurpose。
- `app/src/main/java/com/example/myapplication/model/RoleplaySession.kt:5`：会话关联 scenarioId/conversationId/runId。
- `app/src/main/java/com/example/myapplication/data/repository/roleplay/RoleplayRepository.kt:279`：`startScenario` 优先复用已有会话与 runId、恢复其消息；不存在才创建。它不是“每打开一次就是新 encounter”。
- `app/src/main/java/com/example/myapplication/viewmodel/RoleplayViewModel.kt:539`：创建角色会话时将角色 greeting 写入 openingNarration。
- `app/src/main/java/com/example/myapplication/viewmodel/RoleplayScenarioActionSupport.kt:37`、`:86`：进入场景时可补角色 greeting，然后直接 startScenario；没有先生成、预览、用户接受的三开场状态机。
- `app/src/main/java/com/example/myapplication/data/repository/roleplay/RoleplayRepository.kt:806`：静态开场的 seed 逻辑存在；需要保留旧记录语义，不能把这种 seed 冒充用户接受过的动态开场。
- `app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayScenarioListScreen.kt:400`：按钮已区分“继续会话 / 开始会话”。
- `app/src/main/java/com/example/myapplication/viewmodel/RoleplayViewModel.kt:453`、`:472`：进出场景会取消当前生成与帮写；新入口应复用这类生命周期边界。

**已选方向的兼容建议**：在现有线下入口内增加显式“开始新一场”，再进入三开场；普通打开旧会话仍恢复原会话。用户已选择增强现有线下体验；“开始新一场”的具体呈现仍是设计建议，不能将普通打开自动改成新场。

### 3. 手册能力与当前 UI 的差距

| 能力 | 状态 | 当前证据及缺口 |
| --- | --- | --- |
| 从现有角色进入 | 已有基础 | 角色卡与模式选择已接真实回调；见主壳 `:2244`、`:2307` 与导航 `:271` |
| 三种开场 | 缺失 | 当前是静态 greeting/手填旁白；缺“我靠近 / 角色靠近 / 直接开始”的选择与预览状态 |
| 开场接受前不成共同经历 | 需新增 | 现有直接 startScenario 不能用于预览废稿；新流程必须区分 preview 与 accepted |
| 输入与留白“继续” | 部分 | 普通输入已有；`viewmodel/RoleplaySendActionSupport.kt:457` 对空 userParts 报“请输入剧情内容或添加图片”，没有独立 continue 回合入口；“继续会话”仅表示恢复旧会话 |
| 背景、双方形象、情绪色 | 已有基础 | `ui/screen/roleplay/RoleplayScreenContent.kt:130`、`:185`、`:205`；背景、头像/人物图和情绪色已消费 |
| 每拍表情立绘、打字机与循环重播 | 缺失 | 当前 `ui/component/roleplay/RoleplayDialoguePanel.kt:320` 为 LazyColumn 消息流；头像层 `RoleplayPortraitLayer.kt:57` 是双方人物卡，不是表情图集播放器 |
| 普通阅读视图 | 已有基础 | `ui/screen/roleplay/RoleplayReadingMode.kt:70`、`:173`、`:231` 提供分类型全文阅读 |
| 同角色跨场次阅读/日期整理 | 缺失 | `ui/navigation/RoleplayNavGraph.kt:835` 仅传当前 `roleplayState.messages`；阅读器未实现按角色/来源的分页、按场次/年月查询 |
| 手动滚动时暂停跟随 | 部分 | `RoleplayDialoguePanel.kt:134`、`:233` 使用有用户滚动意图的自动跟随；可复用决策，跨页 prepend/键盘重排锚点仍要新增验收 |
| 正文解析与宽容显示 | 已有基础 | `roleplay/RoleplayOutputParser.kt:19`、`:58` 解析 narration/thought/dialogue 与 emotion，有纯文本和混写兜底；不是手册的方括号 VN 协议 |
| 时间/地点/状态/细节观测 HUD | 缺失 | `viewmodel/RoleplayObservationSupport.kt:31` 是观察 Flow 的组织代码，不是 OBSERVE。现有 STATUS 分段在 `RoleplayMessageBubbles.kt:600` 和阅读器 `:275` 不展示 |
| 观测旧值问题 | 新协议必须规避 | `roleplay/RoleplaySceneMood.kt:19` 会取消息列表最近非空 emotion，适合作为已有氛围投影，不能直接当“本轮有观测”的凭据 |
| 五种文风、人称、自定义补充 | 部分 | 现有 Preset、深度沉浸、旁白与长文设置可复用基础；`RoleplayScenario.kt` 未见陪伴专属 style/pov/observe 字段，手册对应配置未实现 |
| 设置与外观 | 已有基础但作用域不同 | 背景/人物图在 scenario；显示令牌、行高、13种气泡皮肤等在 AppSettings，手册不少配置在 character；不能全局覆盖用户原有值 |
| 复制、重试、删除与编辑 | 部分 | `RoleplayMessageBubbles.kt:680` 提供长按菜单、复制、重回回合、单条删除；编辑仅用户对白，标签为“重回并编辑”，并非任意原文原位修订；阅读页无对应多选操作 |
| 来自聊天时原路返回 | 部分 | 当前 play 退出调用 leaveScenario + popBackStack（导航 `:597`），没有“从线上聊天临时进入见面”的一次性 origin/return contract |
| 旧多人/剧情兼容 | 必须保留 | `model/RoleplayScenario.kt:33`、`:90` 有 GROUP；`model/RoleplayInteractionMode.kt:4` 有三种持久模式。新单角色流程不能套到已有群聊、改变其参与人 |

这里的“已有基础”仅表示源码存在可复用能力，不意味着 2.4.1 的完整验收已通过。

### 4. 已选入口与对照方案的影响

| 选项 | 用户体验 | 可复用边界 | 需要明确的兼容行为 |
| --- | --- | --- | --- |
| A：升级现有线下扮演（用户已选） | 用户沿用角色卡/会话→线下；演出与阅读继续挂在既有体验中，显式新场是建议的兼容动作 | 既有 RP 导航、场景/身份、上下文、消息投影、素材、设置骨架 | 旧单聊点击仍续聊；三开场只作用明确新场；长文剧情和群聊保留；普通对白可保留为显示方式或兼容入口 |
| B：增加“见面/陪伴”入口（未选，仅保留对照） | 主壳/角色卡/线上会话增加专属入口，准备→预览→见面；旧线下入口仍可访问 | 同一个 Roleplay 图和领域服务即可复用，不必复制 DB、角色或记忆系统 | 两套入口需额外避免重复会话，另定义入口恢复与返回契约；本轮不按此方向展开 |
| 两方案共通 | 角色与关系不断档，媒介/显示方式可变化 | 复用 v2.4 memoryPurpose/空间/身份规则 | 不以 ONLINE/OFFLINE 推导 CONTINUOUS/INDEPENDENT；不把播放器书签当运行事实 |

用户已明确选择 A，父 PRD R5 已记录；随后又确认完整规划（R6）。最终设计保留打开旧会话并增加显式新场，不引入第二个独立陪伴App；具体六块范围与适配边界见父PRD/design，当前尚未批准实施或视觉基准。

### 5. 可复用的状态和 UI 边界

- 新流程建议具有明确的准备、开场请求中、预览成功/失败、接受保存中、正式见面状态；直接开始跳过开场生成。连点接受只形成一份 accepted 开场，失败留在可恢复页。
- 当前场次身份/消息来源由存储层负责；演出投影独立记录 sourceMessageId/revision、beatIndex、viewMode。既有 XML 分段和新解析结果可归一到统一 typed beat，是否改变输出协议交由模型/协议研究收敛。
- 本地点击补全、下一拍、重播与切阅读只读已有正文；不要复用 send/retry 回调来推进播放器。对 emoji/组合字符按可视字符安全推进，不能照搬 JS 每20ms一个 code unit。
- 新读历史窗口按 assistant/persona/空间/来源筛选，包含旧场次但不能把选中的历史消息设为当前会话。分页 state 与模型的授权原文范围分别建模。
- 设置/菜单/观测/多选有明确覆盖层优先级，系统返回优先关闭最上层；将当前帧状态放在场次所有者，而非依赖整页重新挂载后的默认值。
- 设置作用域应明确为全局默认、角色配置、场次覆盖中的哪一种，并在 UI 呈现保存语义。既有导航 `RoleplayNavGraph.kt:698`、`:795` 会在进入阅读/返回前保存 SettingsViewModel 草稿，而场景开关即时保存；本轮不能显示全局“取消”却保留部分已保存更改。
- `RoleplayViewModel.updateCurrentScenarioVisual`（`:1411`）已按最新场景仅更新视觉字段，避免图片 IO 的旧快照覆写其它设置；图集/布置应沿用字段级更新边界。
- 背景换图不等于更新模型中的地点；观测输出绑定具体源消息/修订，缺失、关闭或解析失败不能继续显示上一轮 HUD 为本轮事实。
- 单角色陪伴可隐藏不属于当面互动的转账/手机类快捷项，但要先确定产品范围；不要为了简化新页面删除原 `RoleplayScreen.kt:386` 的既有快捷入口。

### 6. 本主题验收映射

| 手册项 | 当前可用基础 | 2.4.1 需要验证的可观察行为 |
| --- | --- | --- |
| F01 | 主壳/会话列表有空态 | 无角色/空筛选给创建或返回入口，不发模型请求 |
| F02 | 当前设置/阅读本身不生成文本 | 新准备/布置/记录入口均零文本请求，不因重组、返回或恢复意图自动开场 |
| F03 | 角色 greeting 仅静态 seed | 两生成开场角色动作与用户执笔边界不同；开场模式写入所选候选 |
| F04 | 无对应预览流程 | 多次预览退出后无共同经历/记忆；废稿不可出现在记录页 |
| F05 | 既有保存错误反馈 | 连点进入只接受一次；保存中防重；失败仍可重试接受而非假进入 |
| F06 | 普通输入已有；留白继续缺失 | 直接开始零开场请求；输入或继续可即时开启正式回合 |
| F14 | popBackStack + 同场模式切换 | 从线上会话进入/退出回同一会话，角色/身份/历史保留，后续回复恢复线上格式 |
| U01 | XML/纯文本/混写 parser | 新/旧格式与未知表情降级一致，原文不丢，坏可选观测不吞正文 |
| U02 | 无产品打字机 | 补全当前句后旧计时器不再改写；下一点击才推进；组合字符不破碎 |
| U03 | 阅读投影已有 | 重播、切阅读、展开 HUD 无新消息/文本调用，历史浏览不改当前场次 |
| U04 | 头像/默认名兜底已有 | 新图集缺图、切皮肤、错误引用有稳定兜底；装饰小人不当主体图 |
| U05 | 现有 emotion 氛围不能代替本轮观测 | 新回复无观测/坏格式/观测关闭时，本轮面板明确为空或未提供 |
| U06 | 尚无自定义观测字段 | 稳定字段 ID 支撑改名/删项，历史有版本可解释，重名受控 |
| U07 | 当前仅会话窗口 | 混合线上/线下/群聊与121条以上数据下，陪伴来源分页不重不漏；删游标仍可续读 |
| U08 | 现有暂停跟随决策 | 用户上翻时新答/前插旧页/字体/IME/删除不抢位置，底部用户仍正常跟随 |
| U09 | 原长按菜单 | 滚动取消长按；按钮/菜单点击不推进演出、不误删 |
| U10 | 既有 Dialog/Sheet 与设置子页 | 系统/手势返回按覆盖层优先级关闭，最后才离场；恢复后场次/输入/播放器身份正确 |

F07–F13 的事务/并发细节、G 项模型/记忆边界和 U11–U18 声音/资料由其它研究主题覆盖；本表不将其排除出版本。

### 7. 已有测试与新增验证落点

以下仅阅读源码，未本轮执行；复用断言和 fixture，而非用旧测试名称代替新需求验收。

| 可复用测试 | 已有关注点 |
| --- | --- |
| `app/src/test/java/com/example/myapplication/ui/screen/settings/MemoryPurposeUiTest.kt:19`、`:45` | 200%字体下新建/角色卡用途选择传到真实模式回调 |
| `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplayScenarioEditScreenTest.kt:37`、`:250` | 模式切换保草稿；LEGACY 用途保留到显式选择 |
| `app/src/test/java/com/example/myapplication/model/RoleplayInteractionSpecTest.kt:60`、`:255` | 模式联动与无关字段保持 |
| `app/src/test/java/com/example/myapplication/roleplay/RoleplayMessageUiMapperTest.kt:565`、`:704`、`:1089` | 模式切换仍按消息存储格式显示；静态开场不可重试；消息自身媒介优先 |
| `app/src/test/java/com/example/myapplication/roleplay/RoleplayOutputParserTest.kt:13`、`:52`、`:176` | 分段、纯文本兜底、宽松属性；新协议要加入相同投影契约 |
| `app/src/test/java/com/example/myapplication/ui/component/roleplay/OfflineRoleplayAccessibilityTest.kt:22`、`:47`、`:171` | 消息/输入空间、独立触控回调、长列表；可沿用320dp/200%语义检查 |
| `app/src/test/java/com/example/myapplication/ui/component/roleplay/RoleplayReadingModeAccessibilityTest.kt:20`、`:67` | 长文、混合类型、大标题与始终可达的关闭按钮 |
| `app/src/test/java/com/example/myapplication/ui/component/roleplay/RoleplayStreamingAutoScrollTest.kt:25`、`:39`、`:94` | 近底跟随、上翻暂停、末尾部分可见/不可见边界 |
| `app/src/test/java/com/example/myapplication/ui/screen/roleplay/SessionSidebarPersistenceTest.kt:60`、`:66` | 返回/阅读前保存草稿；写入失败不离场且不丢草稿 |
| `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplayScreenQuickActionsTest.kt:31` | 线下快捷项真实回调；兼容入口不能误删 |
| `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplaySystemBarsTest.kt:32` | 离场页延迟销毁不能改写当前页系统栏 |

新增测试应落在 entry/encounter 状态机、纯 beat player/parser、历史分页/锚点、原生覆盖层与真实导航这几类；名称在设计阶段确定。改造既有界面时按受影响 H/E04/E05 截图 verify 并单独审阅候选，不能先 record 使比较通过。仅规划文档不要求启动模拟器。

### 8. 相关规范与参考

- `.trellis/spec/backend/directory-structure.md`：AppGraph 装配、Screen/VM/Repository 分层、不得继续扩大主类。
- `.trellis/spec/backend/ui-state-guidelines.md:5`：真实产品入口；`:41`：局部状态、键控副作用、资源释放；`:77`：阅读和系统栏生命周期。
- `.trellis/spec/backend/ui-token-contract.md:71`：线下/阅读默认令牌、用户样式原值、200%/48dp与图片加载的真实像素验证。
- `.trellis/spec/backend/quality-guidelines.md:71`：截图 verify 与人工接纳边界。
- `.trellis/spec/backend/memory-system-guidelines.md`、`memory-management-guidelines.md`：由记忆主题核对空间/用途/旧档案契约，本 UI 不另定规则。
- `docs/agent-prompt-reference/date-companion-agent-handbook.md` §3–5、§8–11、§15、§20；参考基线 f93e28ec3d56451ed8f603597da4da7671dd3c64，2026-11-05。
- 资料中的 React/IndexedDB/ResizeObserver/portal/CSS 是参考平台实现；Narra 应使用现有 Compose、Room、导航和主题机制，不需引入 WebView/CSS 分享后端。

## Caveats / Not Found

- 没有发现生产三开场、独立 blank-continue 按钮、逐拍演出、陪伴 OBSERVE HUD 或角色跨场次记录页；这里的结论来自已追踪的真实入口及其依赖，不来自文件名猜测。
- `RoleplayOfflineReplaySupport` 只接受固定 BenchmarkOfflineReplaySpec（`viewmodel/RoleplayOfflineReplaySupport.kt:51`），不作为用户重播播放器的复用实现；`RoleplayObservationSupport` 是 Flow 观察层。
- 多人剧场、角色计时、桌面模型等不属于指定手册的主体；不能由本研究替用户扩大或删减整个2.4.1范围。
- 用户已选择升级现有线下扮演和完整规划；旧会话/新场兼容差异已进入父设计与验收，不能借此改变既有五种模式或用户设置。后续以最终父/子合同为准。
- 所有发现为静态研究；真实交互、模型语义、设备性能和本轮视觉效果均未验证。
