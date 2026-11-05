# Research: P04 线下扮演入口与扩面边界

- Query: 线下扮演实际入口、共享所有者、令牌 / 无障碍风险、现有覆盖，以及可独立验收的最小批次。
- Scope: internal；只调查与规划，不修改产品 / spec，不执行测试或构建。
- Date: 2026-10-21
- 版本边界：v2.3 扩面待批准；构建仍为 2.2.12-dev，不涉及 v2.4–v2.6。

## Findings

### 入口与实际调用链

下列源码路径均相对 `app/src/main/java/com/example/myapplication/`，测试另行写完整源集路径。

| 源码锚点 | 事实与规划意义 |
| --- | --- |
| `ui/AppRoot.kt:166`；`ui/navigation/RoleplayNavGraph.kt:69` | 日常起点是 `ROLEPLAY_GRAPH`，图的起点为角色手机主壳；不要为扩面恢复旧 Chat / Translator 入口。 |
| `ui/screen/immersive/ImmersivePhoneShell.kt:276`、`:278`、`:280`、`:1940`、`:2255` | 新建选项含线上、线上心声、线下、线下心声、剧情模式；线下两项为 `OFFLINE_DIALOGUE`，剧情模式为 `OFFLINE_LONGFORM`。主壳创建弹层和角色详情都最终回调 `onCreateChat`。 |
| `ui/screen/immersive/ImmersivePhoneShell.kt:825`、`:2195`、`:2267` | 消息列表、角色主会话和已有会话项通过 `onOpenChat(scenarioId)` 打开既有场景，不能只验收新建入口。 |
| `ui/navigation/RoleplayNavGraph.kt:146`、`:271` | `createChatForAssistant(assistantId, mode, enableNarration)` 完成后或打开既有会话时，均导航至 `AppRoutes.roleplayPlay(scenarioId)`。 |
| `ui/navigation/AppRoutes.kt:42`、`:109`；`ui/navigation/RoleplayNavGraph.kt:414`、`:428`、`:446`、`:464` | 路由为 `roleplay/play/{scenarioId}`，参数编码 / 解码；导航收集图级 ViewModel 状态，并仅在场景 / 会话缺失时 `enterScenario`，避免从设置返回重置列表。 |
| `ui/screen/roleplay/RoleplayScreen.kt:507`、`:582`、`:606` | 同一生产 `RoleplayScreen` 在 `ONLINE_PHONE` 时用 P03；其余模式用 `RoleplaySceneContent`，所以 P04 必须覆盖普通对白和长文剧情两条内容分支。 |
| `ui/navigation/RoleplayNavGraph.kt:580`、`:696`、`:805`、`:832` | P04 更多按钮进入 P05；P05 的阅读入口在 `saveSettings` 成功回调里进入 `roleplay/play/{scenarioId}/reading`，然后由 `RoleplayReadingMode` 展示同一会话。保留保存成功门槛，不新增旁路。 |

`RoleplayScreen.kt:283` 的加载 / 场景不存在分支、`:695` 的角色改绑确认和 `:703` 的删除确认属于线上线下共享页面外壳；本任务只为受影响行为补验，不顺带重做这些业务状态。

### 共享组件与单一所有者

| 文件 / 声明 | P04 用法及其他生产消费者 | 建议所有权 |
| --- | --- | --- |
| `ui/screen/roleplay/RoleplayScreenContent.kt:71` | `RoleplaySceneContent` 只由线下 `RoleplayScreen.kt:606` 调用；组装背景、顶栏、立绘、记忆提议、对话区。 | P04；文件内 `RoleplaySpecialPlayOverlays` 和改绑 Dialog 仍由共享 `RoleplayScreen.kt:744` / `:695` 使用，不能把整文件视为线下独占。 |
| `ui/component/roleplay/RoleplayDialoguePanel.kt:97` | 只由 `RoleplayScreenContent.kt:264` 调用，负责空态、消息 LazyColumn、跳转、线下建议、发送提示与输入。 | P04；保留滚动和发送状态机制。 |
| `ui/component/roleplay/RoleplayDialoguePanelParts.kt:107`、`:180`、`:352` | 建议 / 空态只有线下使用；`rememberImmersiveRoleplayColors` 由线上 `RoleplayOnlinePhoneContent.kt:1104` 复用，颜色模型还被共同气泡 / 输入消费。 | P04 负责线下声明；颜色模型 / 解析若确需改动，由同一 P04 所有者集中改并回归 P03。 |
| `ui/component/roleplay/RoleplayInputBarComponents.kt:100` | 线下 `RoleplayDialoguePanel.kt:460` 与线上 `RoleplayOnlinePhoneContent.kt:781` 共用，已经有令牌、命名与大字体分栏。 | 以复用为先；没有失败证据不重新改造。确需修复时 P04 独占该文件，P05 不并行编辑。 |
| `ui/component/roleplay/RoleplayMessageBubbles.kt:56` / `RoleplayDialogueBubbles.kt:31`、`:111` | 线上 `RoleplayOnlinePhoneContent.kt:617` 和线下 `RoleplayDialoguePanel.kt:314` 使用同一消息项；按 DEFAULT / ONLINE_PHONE 分流。 | 共享气泡所有者归 P04 条件修改；不重设已接纳 P03 的默认值或用户值。 |
| `ui/screen/roleplay/RoleplayReadingMode.kt:63` | 独立路由；接收消息、标题、背景、行高和高对比，不接收 `roleplayNoBackgroundSkin`。内部 LONGFORM 复用 `RoleplayLongformText.kt:32`，该卡也被消息项 `RoleplayMessageBubbles.kt:409` 使用。 | P04 第二串行检查点；维持阅读页既有独立材质和信息层次，不凭扩面给它新加皮肤持久化行为。 |
| `ui/component/roleplay/RoleplaySceneBackground.kt:22` / `ImmersiveGlass.kt:237`、`:317`、`:459` | 背景包装器无自身几何，委托公共 backdrop；除 P03 / P04 / 阅读外，`RoleplaySettingsScreen.kt:151`、`RoleplayDiaryComponents.kt:51` 等也用背景；玻璃还被 P05 / 设置内容 / 动作卡 / 长文卡等使用。 | P04 默认只改调用点；共享玻璃全局外观不是本批替换目标。必要公共修复须列受影响消费者并由一个所有者处理。 |
| `ui/component/roleplay/RoleplayPortraitLayer.kt:54` | 当前生产调用在 `RoleplayScreenContent.kt:522`；共享组件的姓名 / fallback / speaking badge 需要随 P04 验证。 | 仅出现已证实裁切 / 配色 / 语义失败时纳入 P04；不改立绘资产、裁剪意图或动效规则。 |

### 需要实际验证的源码风险

这是待验证库存，不是已复现的运行缺陷。

| 范围与证据 | 验证 / 迁移方向 |
| --- | --- |
| `RoleplayScreenContent.kt:236` 固定立绘区高 120dp；`:248` 记忆提议占上方空间；`:299` 对话区剩余权重。`RoleplayPortraitLayer.kt:220` 姓名区 padding 16dp，`:228` 姓名单行，`:249` speaking badge。 | 320×640dp / 200% 字体下同时启用立绘、长名、提议和建议 / 输入，测量正文 viewport 大于零且关键操作可达；若需要改变测量，保留原默认立绘展示，不把它扩成视觉重设计。 |
| `RoleplayScreenContent.kt:399` 顶栏 12/6 padding、8dp shape；`:408` 整行 clickable 隐藏顶栏；`:435`、`:452` 标题 / 氛围单行；`:489` handle 是普通 clickable。 | 默认布局用 NarraUi / NarraShapes；标题隐藏与恢复具备可识别动作 / 角色，支持读屏与键盘；返回 / 更多 / 标题动作互不误触。单行不是自动缺陷，需确认长名信息和操作仍可理解。 |
| `RoleplayDialoguePanel.kt:268` 外边距 14/10、`:269` 间隔 6；`:400` 发送提示 16dp shape、`:405` 14/9 padding。`RoleplayScreenContent.kt:338` 提议卡 16/14 padding；`:369` 两个操作同行。 | 只迁移登记的默认布局；不把 blur、描边、图标、装饰线、系统 Insets、图片请求尺寸当 spacing 取整。长文案 / 200% 字体下发送提示与提议操作需测量，不只截屏。 |
| `RoleplayDialoguePanelParts.kt:197`、`:236`、`:304` 建议 shape 16/18/14dp；`:203`、`:309` combinedClickable 未指定动作 label / Role；`:244` 标题和刷新 / 收起同行；`:297` 建议列表最大高 280dp。 | 线下折叠、生成中、错误、成功多条建议分别做真实回调与大字体测试；有界列表还需基于整页可用高度验收，不能仅因内部可滚动就判断输入仍可达。 |
| `RoleplayDialoguePanelParts.kt:292` 错误文字直接用 `errorText`，但颜色解析 `:165` 是相对 `errorBackground` 算出的；`:339` 建议正文用 `textPrimary`，解析 `:153` 是相对角色气泡算出的；实际建议面是 `:238` / `:305` panel。空态副文案 `:411` 再降 alpha。 | 针对实际绘制面计算合成对比并取成对角色；已有“颜色字段合格”不证明建议 / 空态实际文字承载面合格。须先复现 / 测量，再决定修复。 |
| `RoleplayScreenContent.kt:176` 场景有按氛围 tint 的 0–0.35 scrim；`ImmersiveGlass.kt:553` 公共对话面采样的是黑色 0/0.14/0.21/0.28 scrim 加黑白 backing。 | P04 要以场景真实 scrim + 玻璃 + sheen 验证正文、名字和提示；P03 白 / 灰 / 黑图测试不能直接当作 P04 的实际页面配色结论。 |
| `RoleplayScreen.kt:385` 线下快捷项固定品牌式 hex（如 `:397`、`:405`、`:421`），线上对应快捷项已在 `RoleplayOnlinePhoneContent.kt:243` 等使用 scheme 角色。 | 只替换线下默认 accent 或登记有明确用途的精确例外；获取实际 `ColorScheme` 后若加入 remember 必须将 scheme 纳入 key，避免切主题复用旧色。 |
| `RoleplayInputBarComponents.kt:195` 已在 fontScale≥1.5 时上下分栏，`:342` 操作行可横滚；`:121` 展开面板默认上限 5×48dp，而线上调用 `RoleplayOnlinePhoneContent.kt:817` 传入页面算出的上限。 | P04 必须实测“建议展开 + 快捷面板展开 + 大字体”组合是否留有正的消息区域；优先在 P04 调用点限定内容空间，保留共享输入现有行为。附件移除 `:572` 虽传 28dp，但 `NarraButton.kt:281` 有 defaultMinSize；需测真实布局与命中，不能凭 28dp 字面量宣称触控已坏。 |
| `RoleplayReadingMode.kt:140`、`:278`、`:354`、`:490` 阅读面仍有 8/20/22/999dp shape；`:146` 22/34 padding；`:277` thought 宽 76%；`:353` 对话宽 82/90%；`:368` 姓名 / 情绪同行。 | 迁移本页默认几何；大字体验证长姓名 / 情绪、多段正文、关闭按钮和到底滚动；阅读页空态 `:201` 固定 100dp padding 需检查。保持系统安全区 `:106` 意图，不借机更改阅读页系统栏生命周期。 |

必须保留的行为：

- `RoleplayNoBackgroundSkin.kt:62`：真实图片加载成功才影响材质；线下 DEFAULT 忽略线上玻璃开关，有图即玻璃、无图即皮肤。不要清空 bitmap 来改变材质。
- `RoleplayNoBackgroundSkin.kt:183`：全部保存的宽度、圆角、padding、尾角直接优先，包括等于历史默认的值；13 个预设不删、不改存储。
- `RoleplayScreen.kt:253`、`:263`：附件经发送回调传出，只有实际进入 sending 才清空待发附件；失败不能假装发送成功。
- `RoleplayDialoguePanel.kt:134`、`:135`、`:137`、`:311`：保留 conversation identity、stable uiItemId / contentType、用户离开底部时的跟随策略与跳转恢复；本轮不重新设计流式和滚动。
- `RoleplayScreenContent.kt:158`：顶栏展开状态按 scenario.id 保存；设置返回不重建会话。P05 前置保存与失败边界保持原样。

### 现有覆盖与复用点

测试路径以下均相对仓库；“覆盖”表示源码存在相应断言，不表示本次执行通过。

| 测试 / fixture | 已覆盖与缺口 |
| --- | --- |
| `app/src/test/java/com/example/myapplication/ui/screenshot/BubbleScreenshotB04Test.kt:58`、`:74`、`:100` | 只有两张线下 DEFAULT 气泡组件图，均 MATCHA 浅色；`renderBubbleStage` 还在 `ScreenshotRenderTest.kt:226` 使用线上 scrim。不是 `RoleplaySceneContent` 整页，不覆盖顶栏 / 立绘 / 建议 / 空态 / 阅读。保留它们作为共享组件回归。 |
| `app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotCases.kt:79`、`:125`；`ScreenshotRenderTest.kt:101` | H01–H04 是 P01/P02/P03；未发现 P04 或阅读页登记及 render helper。当前接纳的 79 项结果不能推导 P04 整页已验收。 |
| `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplaySceneContentTest.kt:24` | 直接渲染生产 scene，检查点击标题出现 handle、点击 handle 恢复；无主题矩阵、长文、建议、多状态、200% 字体或触控范围断言。 |
| `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplayScreenQuickActionsTest.kt:31` | 真 `RoleplayScreen` / OFFLINE_DIALOGUE，检查语音、动态、视频、查手机与 owner 回调；可复用 callback builder。未涵盖全部快捷项、输入发送、错误 / 生成中或窄屏。 |
| `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/RoleplayUiTestFixtures.kt:8` | 可复用无 bitmap backdrop，固定自定义 palette；此 fixture 不随实际 scheme 自动派生，不能用它单独证明主题解析正确。 |
| `app/src/test/java/com/example/myapplication/ui/component/roleplay/PilotAccessibilityTest.kt:106`、`:148`、`:166` | 已有 P03 200% 字体布局 / 操作回调、触控、停止生成、AI 帮写展开的断言方法，可复用测量方法；实际入口仍是线上，不覆盖 P04。 |
| `app/src/test/java/com/example/myapplication/ui/component/roleplay/RoleplaySemanticColorsTest.kt:22`、`:54`、`:80`；`RoleplayThemeSwitchUiTest.kt:163` | 三主题 / 固定动态 scheme、13 皮肤颜色 / 几何与玻璃面计算可复用；需补 P04 真页面、建议面、空态和阅读文字的实际配对。 |
| `app/src/test/java/com/example/myapplication/ui/component/roleplay/RoleplayGlassBubblePolicyTest.kt:16`；`RoleplayStreamingAutoScrollTest.kt:11`、`:39`；`RoleplayDialoguePanelReadyTest.kt:9` | 已有线下玻璃谓词、滚动决策与 benchmark ready 纯逻辑回归。若不改这些算法无需重写测试；它们不能证明整页测量和真机滚动。 |
| `app/src/test/java/com/example/myapplication/ui/screen/roleplay/RoleplayOnlinePhoneMessageListKeyTest.kt:10`；`baselineprofile/.../BaselineProfileGenerator.kt:52`、`FrameBenchmarks.kt:69` | 阅读页有源码 key / contentType 守卫和 baseline 导航路径；未发现直接渲染阅读页的 JVM / AndroidTest UI 或截图用例。baseline 路径存在不代表当前已取得性能数据。 |
| `app/src/test/java/com/example/myapplication/ui/theme/UiTokenSourceInventory.kt:9`、`:16`、`:117`、`:130`、`:149` | P04 screen / panel / reading 尚未纳入整页守卫；线下建议与空态旧几何是精确冻结库存。实现时按声明扩 scope，迁移后删除对应已消费 legacyLayout，不改成整文件 / 目录白名单。 |
| `app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotFixture.kt:33`、`:43`、`:193`、`:199`、`:234`、`:267`、`:282` | 可复用 360×800 / 320×640、density2 / fontScale2、固定时间、长回复、用户 / 角色 / 旁白消息、同源 palette 和本地 syntheticBackdrop。现有 scenario 默认 ONLINE_PHONE（`:107`），P04 helper 必须显式 copy 为两种线下模式；补固定 THOUGHT / LONGFORM / suggestion，不用真实用户数据、网络图片或当前时间。 |

### 建议一个 P04 子任务，两个串行检查点

没有必须为阅读页另建子任务的技术依赖：它是不同路由，但仍用同一图级会话与玻璃能力；P04 内单一所有者、两次定向检查足以隔离反馈。与 P05 / P10 / P11 分开验收即可，不为背景包装器另建全局基础设施任务。

| 检查点 | 文件所有权与完成条件 | 可观察验收 / 失败边界 |
| --- | --- | --- |
| P04.1 场景、对话、建议 | `RoleplayScreen.kt` 仅线下默认 accent / 受影响外壳，`RoleplayScreenContent.kt`，`RoleplayDialoguePanel.kt`，`RoleplayDialoguePanelParts.kt` 的线下声明；对应 tests / screenshot fixture / source inventory。共享 Input / Bubble / Glass 只有测出具体问题才纳入同一所有者。 | 既有会话和新建对白 / 剧情都走原导航；标题收起 / 恢复、返回、设置、建议生成 / 应用 / 刷新 / 收起 / 取消、输入 / 发送 / 停止、快捷入口驱动真实回调。320×640 / 200% 时顶栏、列表、输入不重叠，正文 viewport>0，展开建议 / 快捷面板后关键操作可达；实际独立触控区≥48dp且相邻操作不误触；读屏有名称 / 角色 / 状态。生成中 / 失败保持旧状态语义。若共享 P03 / 材质 / 用户几何回归则本检查点失败，停止进入阅读检查点并修复本批变更。 |
| P04.2 阅读页 | `RoleplayReadingMode.kt` 与阅读专属测试 / 截图；如需改 `RoleplayLongformText.kt` / `ImmersiveGlass.kt`，仍由 P04 同一所有者串行处理并回归两种会话。P05 导航保存由 P05 所有者保留，不并行改导航 / 保存代码。 | 通过现有 P05 保存成功后进入阅读，保存失败仍留 P05；空内容、对白 / 旁白 / 心声 / 长文混排、长标题、200% 字体、滚动到底及关闭回到原会话可观察。真实阅读面文字对比≥4.5:1，关闭触控≥48dp；保持 lineHeight、高对比、系统安全区意图。若公共玻璃变化影响 P03 / P05 / 日记，不能以“P04 页面通过”掩盖，回退本批公共改动再选择调用点修复。 |

两个检查点均验证实际应用浅 / 深主题与固定动态 scheme；13 皮肤与用户几何通过已有纯解析 / 持久化回归，加 P04 实际页面的 Narra、NOIR、LIQUID_GLASS 代表组合。没有必要把全部 13×主题×状态都做截图。

### 最小新增截图矩阵建议（14 张，待规划批准）

所有图用生产 `RoleplaySceneContent` / `RoleplayReadingMode`，不以手工拼接代替整页；建议新登记 P04 专属矩阵，保留 H01–H04 / B01–B04 / C01 原有计数与基准。生成中 / 失败 / 动态色 / 其余皮肤以行为与实际承载面测试补足，不新增笛卡尔积。

| 组 | 固定场景 | 张数 |
| --- | --- | --- |
| 场景常规 | OFFLINE_DIALOGUE、无图、默认 Narra、用户 / 角色 / 旁白；MATCHA / VANILLA / OCEAN × 浅 / 深；360×800、100%。 | 6 |
| 场景空态 | OFFLINE_DIALOGUE、无图、浅色；保留可生成建议入口和输入区。空态动画固定时钟。 | 1 |
| 场景窄屏 | 320×640、200%、MATCHA 浅 / 深：浅色为长名 + 多条建议展开；深色为 OFFLINE_LONGFORM + 发送中 / 取消 + 立绘。共享同一整体测量断言。 | 2 |
| 场景背景 | 固定明暗分区 syntheticBackdrop 加载成功、OFFLINE_DIALOGUE、默认皮肤；实测 scene mood scrim 与正文玻璃，不用线上 scrim。 | 1 |
| 场景建议常规 | 360×800、100%、长建议展开，至少两种 axis / label；与窄屏场景形成可比较的独立建议布局。 | 1 |
| 阅读常规 | MATCHA 浅、固定背景成功，混排对白 / 旁白 / 心声 / 长文，关闭入口可见。 | 1 |
| 阅读空态 | MATCHA 深、无图；空态与关闭入口。 | 1 |
| 阅读窄屏 | 320×640、200%、MATCHA 深、无图、长标题 / 情绪 / 长文；滚动和关闭的实际行为另断言。 | 1 |

先为新增场景取得可信的当前视觉记录，再比较实现候选；人工接纳后才单独更新基准和 manifest。已有 Windows / Ubuntu 宿主约束继续沿用，不把本机接纳当成新远端 CI 已通过。共享文件若改变，至少定向回归 P03 对应页面、B01–B04 / C01 及用户几何；最终截图守卫 / compare 按新登记数检查，不删旧用例以换取通过。

### Related specs / sources

- `.trellis/workflow.md`：本次只完成研究与规划，保持 planning，用户批准后才实施。
- `.trellis/spec/backend/ui-token-contract.md`：令牌、精确例外、实际承载面、用户几何和人工基准接纳。
- `.trellis/spec/backend/roleplay-theme-settings.md`：真实背景 / 有效玻璃分离与线下忽略线上开关。
- `.trellis/spec/backend/ui-state-guidelines.md`：真实入口、导航 / ViewModel 边界、状态与 UI 反馈。
- `.trellis/spec/backend/directory-structure.md`、`quality-guidelines.md`、`.trellis/spec/guides/index.md`：文件 / 测试职责和验证诚实性。
- `.trellis/tasks/archive/2026-10/10-21-ui-token-accessibility-pilot/research/expansion-list.md`：P04 优先、线下历史几何未整体迁移、真机与远端 CI 待补。
- External references: 本次为现有源码 / 测试契约调查，未使用外部资料或引入依赖版本变更。

## Caveats / Not Found

- 本次未运行测试、截图、Lint、构建、设备或性能；没有新的“通过”结论。试点既有 1642 JVM / 79 截图结果由归档 verification 记录负责，不重复执行。
- 未找到 P04 整页或阅读页的截图登记 / 直接 UI 测量测试；现有 B04 只覆盖线下气泡组件，不能当作缺口已填。
- AndroidTest 源码存在与已编译，不等于设备执行；读屏、输入法、系统栏、真实玻璃和物理性能仍需要 D01 / 真机补证。
- 上述风险是源码条件和覆盖缺口，尚未在运行时复现。实现阶段只能针对实际失败收敛修复，不以此清单直接进行全 App 替换。
