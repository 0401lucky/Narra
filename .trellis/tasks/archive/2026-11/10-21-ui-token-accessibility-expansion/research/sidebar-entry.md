# Research: P05 会话设置侧栏入口、共享边界与验收

- Query: 核对 P05 实际入口与旧 Screen 消费者、独立窗口、字体与滚动风险、皮肤草稿及保存边界，给出 v2.3 的独立批次与最小新增验收矩阵。
- Scope: internal；只读源码 / 已归档验证记录，不执行构建、测试、设备操作或产品修改。
- Date: 2026-10-21

## Findings

### 1. 结论与范围

P05 应作为一个独立子任务，接在 P04 的共享所有者检查之后，先迁移自己的路由面板、局部组件和弹层，再验收。名称中的“侧栏”是视觉布局：**本体是普通导航目的地中的右侧面板，不是 Drawer，也不是独立 Dialog 窗口**。独立窗口只包括下述 BottomSheet / AlertDialog。不能用覆盖底层面板的截图宣称弹窗已覆盖。

当前只有一条生产消费链：`RoleplayNavGraph` → `RoleplaySettingsScreen` → `RoleplaySettingsContent` → `RoleplaySettingsSidebarContent`。未发现另一套实际挂载的旧 `RoleplaySettingsScreen` 消费者。`RoleplaySettingsHero` 只有声明，不能算当前入口或为了扩面顺便启用。

本批保持所有现有回调、保存时机、导航、13 套皮肤、用户几何、尾角与线上玻璃开关；不新增保存流程、状态机或错误重试产品行为，不接手世界书 / 记忆的领域功能。

### 2. Files found

下列生产路径相对 `app/src/main/java/com/example/myapplication/`；测试路径明确标记源集。

| 文件 | 作用 |
| --- | --- |
| `ui/navigation/AppRoutes.kt:43`、`:112` | 会话设置路由常量与带编码 scenarioId 的构造函数 |
| `ui/navigation/RoleplayNavGraph.kt:580`、`:628`、`:667` | 唯一实际会话设置目的地、图级 ViewModel、草稿覆盖与回调绑定 |
| `ui/screen/roleplay/RoleplayScreen.kt:579`、`:650` | P03 / P04 向同一设置导航回调汇合；线下有 benchmark 专用分支 |
| `ui/screen/roleplay/RoleplayOnlinePhoneContent.kt:516`、`:540` | P03 “更多”菜单中的“设置”入口 |
| `ui/screen/roleplay/RoleplayScreenContent.kt:458` | P04 顶部更多图标直接打开设置 |
| `ui/screen/roleplay/RoleplaySettingsScreen.kt:63` | 右侧路由面板、固定标题、内部页、模型 Sheet、重启 / 重置确认 |
| `ui/screen/roleplay/RoleplaySettingsContent.kt:61`、`:126`、`:404`、`:454` | 材质主题适配层、开关行和卡片 |
| `ui/screen/roleplay/RoleplaySettingsSidebarPanel.kt:144` | MAIN / SCENE / IDENTITY / GROUP / THEME / QUICK / VISUAL / REGEX 全部局部页与预设 / 面具 / 成员弹层 |
| `ui/screen/roleplay/RoleplaySettingsSections.kt:444` | 皮肤预览、13 项预设、气泡几何、玻璃 / 尾角、阅读设置与长说明 |
| `ui/component/roleplay/ImmersiveTabRow.kt:44` | 交互模式、沉浸模式、行高三个等宽选项行的唯一所有者 |
| `ui/screen/settings/SettingsComponents.kt:219` | 共享说明按钮与独立说明 Dialog；P05 仅调用，不应连带整体迁移 |
| `ui/screen/chat/ChatModelSheets.kt:213` | P05 / 设置模型页 / Chat 共用 `ModelPickerSheet` |
| `model/RoleplayNoBackgroundSkinSettings.kt:3` | 完整用户皮肤对象、13 个存储枚举与合法范围 |
| `viewmodel/SettingsViewModel.kt:375` | 成功才调用退出回调，失败保留草稿并写入 message |
| `viewmodel/SettingsPreferenceDraftSupport.kt:96`、`SettingsPersistenceCoordinator.kt:40` | 完整主题对象传递至现有保存入口 |
| `data/local/AppSettingsStore.kt:582`、`:1294` | `roleplay_no_background_skin_json` 保存；本批无 schema / JSON 变更 |

### 3. 实际入口、页与窗口

1. P03：`RoleplayOnlinePhoneContent.kt:516` 打开“更多”，`:540` 的“设置”关闭菜单后调用 `onOpenSettings`；`RoleplayScreen.kt:579` 原样传递。P04：`RoleplayScreenContent.kt:458` 的 `NarraIconButton` 触发同一回调，经 `RoleplayScreen.kt:650` 到导航。不要把两种入口合并成新的产品操作。
2. `RoleplayNavGraph.kt:580` 导航到 `AppRoutes.roleplaySettings(scenarioId)`；`:628` 注册普通 `composable`，`:642` 生命周期收集 Settings 草稿，`:648` 收集 Roleplay 状态，`:659` 根据 route scenario 进入场景。`:670` 将皮肤、沉浸、行高等草稿覆盖到 `savedSettings`，不能改成只读 savedSettings，亦不能给 UI 另建 Store 写入路径。
3. `RoleplaySettingsScreen.kt:157` 的 `BoxWithConstraints` 承载右侧 panel：窄屏使用剩余宽的 94%，宽屏 66%，最大 480dp（`:166`）。保留此宽度适配算法；它不是间距令牌字面量。Insets / IME padding 在 `:160`，不应被默认 spacing 替换。
4. 标题独立占高，正文 `Box` 中为内部页面（Screen `:177`、`:251`）；MAIN 和各详情页为 `LazyColumn`，THEME 的一个 item 中再包含完整皮肤 / 阅读 Column（Sidebar `:403`、Sections `:454`）。大字体要从真实 320×640dp panel 测量，不能只渲染 320dp 内容卡。
5. 返回图标的既有行为：MAIN 调用 `onNavigateBack`；详情先回 MAIN（Screen `:193`）。该 Screen 当前未声明 `BackHandler`，所以系统返回不能被测试或规划先验当成与图标完全相同。此次只登记 / 回归实际行为，不趁视觉迁移重定义系统返回或自动保存。

| 覆盖物 | 所有者 / 源码锚点 | 窗口与边界 |
| --- | --- | --- |
| 本会话预设 | Sidebar `:1130`、`:1139` | `ModalBottomSheet`，最大高度为屏高 72%，选项列表有界 LazyColumn，管理按钮留在列表外；选择立即调场景回调并收起 |
| 本角色面具 | Sidebar `:513`、`:519` | 同为独立 Sheet；按 scenario.id 记住展开状态，选择场景面具回调后收起 |
| 模型选择 | Content `:201`、`:216` → ChatModelSheets `:288` | 独立 `ModalBottomSheet`；选择模型后按原行为立即请求保存并收起，进入提供商详情先收起；不改为等待全局保存 |
| 长规则说明 | Content `:435` → SettingsComponents `:243` | `AlertDialog`，正文最大 360dp 可滚动，确认“知道了”在正文外；保留独立于 switch / 行点击的行为 |
| 重启 / 重置 | Content `:237`、`:262`；Screen `:337` | Material `AlertDialog`，确认动作与取消分离；成功回调才走离页保存回调 |
| 成员移除 / 添加 | Sidebar `:1397`、`:1670`、`:1701` | Material `AlertDialog`；添加候选为最大 360dp LazyColumn；回归单击选择与取消，不改群聊功能 |

预设 / 面具 Sheet 的 `.heightIn(max=sheetMaxHeight)`、标题、说明、管理按钮与 `LazyColumn.weight(1f, fill=false)` 是大字体测试重点（Sidebar `:518`、`:541`、`:575`、`:1138`、`:1161`、`:1206`）。当前源码不能证明它们在 200% 下最后一项与管理按钮都可达，需要定向布局 / 交互验证。

### 4. 保存成功 / 失败现状的精确边界

- **全局草稿**：皮肤、气泡参数、玻璃、沉浸、对比度、行高由 `SettingsViewModel` 草稿维护；Sidebar 不直接写 DataStore。`RoleplayNavGraph.kt:794` 的离页回调执行 `settingsViewModel.saveSettings { popBackStack() }`，阅读模式入口 `:696` 同样保存成功再导航。
- **成功**：`SettingsViewModel.kt:381` 持久化，`:387` 应用成功状态，`:393` 才调 `onSaved`。已有 `SettingsViewModelTest.kt:664` 校验完整 skin 保存、草稿收敛和一次回调。
- **失败**：ViewModel `:396` 应用失败状态，不调用 `onSaved`；已有 `SettingsViewModelTest.kt:689` 通过注入失败的 `AiSettingsEditor`，验证玻璃 false 草稿仍在、stored value 未变、`isSaving=false`、错误 message 存在、退出回调为零。`SettingsPersistenceCoordinatorTest.kt:116` 另外覆盖 display 写入失败向上传播。**这些证明保存层和 callback 边界，不证明 P05 当前已经显示错误提示。**
- **当前反馈缺口（源码边界，非本次运行发现）**：P05 Screen 的签名与唯一导航调用没有接入 Settings `message` / `onConsumeMessage` / SnackbarHost，查询 P05、RoleplayNavGraph、AppRoot 未找到该消息展示接线。不能把 ViewModel 的 message 断言写成“侧栏错误提示已显示”，也不要为截图伪造一个生产不存在的保存错误面板。后续本批只回归失败留页 / 草稿 / 回调；新增提示属于另行明确的产品接线范围，不默认新增。
- **场景字段**：预设 / 面具 / 互动模式 / 视觉等直接走 RoleplayViewModel 的既有场景回调（NavGraph `:706`、`:714`、`:716`），与全局草稿不是同一提交事务。VISUAL 的本地草稿随 scenario 字段同步，`persistVisual` 只提交视觉字段（Sidebar `:646`、`:661`）。不能为了令牌改造变成统一“保存 / 取消”流程。
- 重启 / 重置确认先收起 Dialog，再调用 `onRestartSession(onNavigateBack)` / `onResetSession(onNavigateBack)`（Screen `:337`、`:346`）；既有设备测试只验证重启成功回调前不退出。错误 / 取消不应假装成功退出。当前 `saveSettings` 实现自身也没有 `if(isSaving) return` 的入口守卫，本批不可凭规划声称重复保存已被防住，亦不增加新状态流程。

### 5. 个性化保持

- `RoleplayNoBackgroundSkinSettings.kt:12` 的 normalized 只夹取合法范围，原合法值保留。78% / 18dp / 14dp / 10dp 即使等于历史默认值也不推断为“未手调”。皮肤枚举在 `:38` 共 13 项，storageValue 不变。
- `RoleplayNoBackgroundThemeSection` 切换预设时仅 `skin.copy(preset=...)`（Sections `:462`）；气泡重置只恢复该动作原有默认，并显式保留 `onlineBackgroundGlassEnabled`（`:677`）；尾角走 `copy(showBubbleTail=...)`（`:743`）。这些是产品既有行为，不改成一律重置全部值或套 NarraShapes。
- 皮肤预览在 Sections `:487` 使用真实 `rememberRoleplayNoBackgroundSkinSpec` 和 `RoleplayDialogueBubbleStyle`，最大宽、圆角、横纵 padding、尾角都来自用户 skin（`:488`）。默认设置卡外壳可以令牌化，消息预览内用户几何继续原值优先。
- Content `:126` 将设置控件 palette 配对到实际 Material scheme，场景背景和气泡预览保留原配置。这是已有职责分界；不要因为 P05 默认控件令牌化强制把品牌预览全部换成 Narra。
- 本批应引用现有 Store / draft / semantic / bubble policy 回归，不复制一套 JSON 解码、玻璃规则或品牌例外。

### 6. 共享所有权与所有消费者

建议 P05 只修改 P05 的四个生产文件及其局部专用 `ImmersiveTabRow.kt`；不新增全 App helper。与 P04/P03 相交时先顺序集成，再验收。共享默认值不得借本批全局调整。

| 符号 / 文件 | 已核实生产消费者 | 本批所有权建议 |
| --- | --- | --- |
| `RoleplaySettingsScreen` → `Content` → `SidebarContent` | 分别只有 NavGraph `:667`、Screen `:252`、Content `:138` | P05 单一所有者；导航仅必要接线 / 测试锚点，不重构大图 |
| `ImmersiveSettingsCard`（Content `:454`） | `RoleplaySettingsSections.kt`、`RoleplaySettingsSidebarPanel.kt`；定义同文件的其他局部 UI | P05 所有；虽然“Immersive”命名，未发现 P04/P03 直接调用 |
| `RoleplaySettingSwitchRow`（Content `:404`） | 只有 `RoleplaySettingsSections.kt` 的开关 sections | P05 所有；说明按钮仍调用共享 SettingsHelpButton |
| `RoleplaySettingsPanelTitleColor/BodyColor/AccentColor`（Sidebar `:109`） | P05 Screen / Content / Sidebar / Sections 四文件 | P05 所有；已有实际 Material 色，不需要新颜色解析层 |
| `SummaryLinkRow` / `SummarySwitchRow` / `ScenarioPresetPickRow` / `ExpandableGroupHeader` | 全为 Sidebar 私有，所有调用均在本文件：MAIN / shortcut / 群成员 / 面具与预设 Sheet | P05 所有；可在原 helper 内补角色、状态及内容测量，无需外提通用 Settings 行 |
| `ImmersiveTabRow`（组件 `:44`） | **只有** Sections `:287` 互动模式、`:826` 沉浸模式、`:865` 行高；无 P03/P04 其他调用 | P05 所有，三个调用同时回归；保留已有动效，不进入 v2.5 |
| `SettingsScreenPadding`（SettingsComponents `:70`） | 多个 settings / 其他页面，roleplay 中还包括 ScenarioList / ScenarioEdit | 不改这个 20dp 兼容值；P05 调用点显式消费 NarraUi，避免全局范围扩散 |
| `SettingsHelpButton`（SettingsComponents `:219`） | 见下方完整直接消费表；`SettingsPageIntro` / `SettingsSectionHeader` 还间接传播到大量页面 | 所有者留给设置共享组件批次；P05 复用现行能力。确需改动则先单独评审共享影响并由同一批单一持有，不并行改 |
| `ModelPickerSheet`（ChatModelSheets `:213`） | Content `:216`、`SettingsModelScreen.kt:269`、`ChatScreenOverlays.kt:148` | P05 只验收调用界面。若需改共享布局，明确将这一个文件分配给 P05 或 P11 中的一方，另两处同步回归；不恢复日常 Chat 路由 |
| `ImmersiveGlass.kt` / `RoleplaySceneBackground` / skin resolver | P04、P03、P05，以及 reading / diary / video / 气泡 / 输入等 | 原有所有者保持。P05 显式传默认 shape / padding，禁止调整全局默认影响整个 App |
| `NarraUi` / `NarraShapes` / `RoleplayGlassTokens` | 全局 / 多组件，已由试点固定 | 消费令牌；本批不重新定义原值，不将用户几何计入默认布局 |

`SettingsHelpButton` 的完整**直接调用文件**（2026-10-21 搜索 `app/src/main`）：

- `ui/screen/settings/SettingsComponents.kt:213`、`:294`（PageIntro / SectionHeader）。
- `ui/screen/roleplay/RoleplaySettingsContent.kt:435`。
- `ui/component/ContextGovernanceSheet.kt:211`、`:274`。
- `ui/screen/diagnostics/DiagnosticsScreen.kt:156`、`:200`。
- `ui/screen/moments/MomentsScreen.kt:594`、`:1343`；`ui/screen/phone/PhoneCheckScreen.kt:299`。
- `ui/screen/settings/AssistantDetailScreen.kt:492`、`AssistantPromptScreen.kt:429`、`ContextTransferScreen.kt:366`。
- `ui/screen/settings/memory/MemoryManagementScreen.kt:1058`、`:1090`、`:1141`、`:1199`、`:1278`。
- `ui/screen/settings/RoleplayScriptLabScreen.kt:736`、`ScreenTranslationSettingsScreen.kt:384`。
- `ui/screen/settings/VoiceRecognitionApiScreen.kt:134`、`:212`、`VoiceSynthesisSettingsScreen.kt:224`。
- `ui/screen/settings/worldbook/WorldBookEditScreen.kt:611`、`WorldBookEditSections.kt:114`、`WorldBookListScreen.kt:224`。

这份表用于判定共享变更影响，不表示上述页面进入 P05 迁移。若选择新建 P05 专用 helper，所有消费者必须限制在上述 P05 四文件 / 三个 tab 调用；新增消费者或移动到共享组件层需重审所有权。

### 7. 待验证的源码风险，不能写成已证实运行缺陷

- Screen 标题 / 场景名分别 `maxLines=1 + Ellipsis`（`:236`、`:244`），真实 panel 比 320dp 更窄；应证明关键操作可达、标题保留读屏全名，不要求改变产品信息层级。
- `ImmersiveTabRow.kt:57` 等宽 Row + 各项 weight，文本 `maxLines=1`（`:130` 附近）；三组长标签在 200% 字体是否能完整识别需实际量测。可以内容驱动换行 / 最小命中 / 语义，不增加新模式或改选择逻辑。
- `RoleplaySettingSwitchRow` 中图标、文本、48dp 说明与 switch 同行（Content `:421`）；目前只显式给 switch 最小高度，不能据此断言整行所有可点节点都 48×48dp 或全部有独立读屏名称。
- `ScenarioPresetPickRow` 仅 clickable + “已选中”图标（Sidebar `:1232`、`:1256`），`ExpandableGroupHeader` 仅 clickable + 展开 / 收起文字（`:1965`）；选项角色 / selected / expanded 等需检查实际语义树，补足可观测状态而不改回调。
- `SummarySwitchRow`（`:2056`）的 Switch 与文本为兄弟节点；需要读屏关联标签 / 状态和 callback 次数检查。`FooterActionButton`（`:2144`）同理校验明确名称、角色与命中，不将 14dp padding 当作充分证据。
- 成员候选 / 长昵称 / 视觉选择清除按钮 / 在线回复数字输入在较窄 panel 下的测量与键盘遮挡需定向验收，不展开为群聊 / 图片存储修复专项。

### 8. 已有覆盖与可复用 fixture

| 现有文件（相对 `app/src/`） | 实际覆盖 / 证据层级 |
| --- | --- |
| `test/.../viewmodel/SettingsViewModelTest.kt:664`、`:689` | 完整 skin 保存成功与写失败保留草稿 / 不退出，JVM，已包含试点整套通过记录 |
| `test/.../viewmodel/SettingsPersistenceCoordinatorTest.kt:33`、`:116` | display 参数传递与失败传播；可复用现成 editor Fake，不另造保存协议 |
| `test/.../data/local/AppSettingsStoreTest.kt:67`、`:127` | 旧 JSON / 非法类型 / false 保存回读，JVM |
| `test/.../model/RoleplayNoBackgroundSkinSettingsTest.kt:8`、`:42`、`:57` | 默认 / normalized / 13 存储值，JVM |
| `test/.../ui/component/roleplay/RoleplaySemanticColorsTest.kt:22`、`:54`、`:80` | 三主题 / 固定动态色、品牌阅读配对和用户几何不取整；不是 P05 整页截图 |
| `test/.../ui/component/roleplay/RoleplayThemeSwitchUiTest.kt:113`、`:131` | 真实 P03 同组合换主题 / 固定动态色；不证明 P05 控件 |
| `test/.../ui/component/roleplay/RoleplayGlassBubblePolicyTest.kt` | 模式 × 图片 × 开关规则；保留，不复制 |
| `test/.../ui/screen/chat/ChatModelSheetsSearchTest.kt`（类 `ModelPickerSearchTest`） | 只有 provider / model 搜索纯函数，不能当作 Sheet 交互或保存通过 |
| `androidTest/.../ui/screen/roleplay/RoleplaySettingsContentTest.kt:57` | THEME 纯参数界面：沉浸 / 对比 / 行高切换；不是完整 route 导航 |
| 同文件 `:160`、`:234` | 实际预览节点、玻璃开关、选 iMessage、设置 slider、重置保留玻璃；使用真实 draft support，但不是 Store 成功返回链 |
| 同文件 `:280`、`:332` | 280dp / 160% 局部开关帮助独立、系统高对比度提示；未达到 320×640dp / 200% 整页验收 |
| `androidTest/.../ui/screen/roleplay/RoleplaySettingsScreenTest.kt:25` | Screen 的重启确认等待成功 callback 后才离页；回调为测试手工控制，非真实 VM / route 集成 |
| `androidTest/.../ui/screen/settings/SettingsComponentsTest.kt:29` | 320dp / 140% 共享长说明与行点击隔离；不是 P05 200% 窗口 |
| `androidTest/.../ui/component/NarraAlertDialogTest.kt:59`、`:158`、`:188` | 长内容 / 系统返回 / 窗外点击仅关闭；可复用事件方法。P05 当前用的是 Material AlertDialog，不能把这些测试当成 P05 已覆盖 |
| `test/.../ui/screenshot/ScreenshotCases.kt:33`、`:213` | 76 项登记矩阵 + 2 项显式兼容 PNG；原试点 verify 报告 79 项通过，测试数量与 PNG 数量口径不同。**没有 P05 登记项** |
| `test/.../ui/theme/UiTokenSourceInventory.kt:9`、`:43` | 当前不扫描 P05 四文件 / ImmersiveTabRow；SettingsComponents 只扫描 SettingsTopBar。后续需精确新增声明范围，不整体白名单 |

验证诚实边界以归档 `verification.md:23`、`:51`、`:55`、`:68` 为准：1642 JVM 已通过，AndroidTest 只编译，当前无设备；79 次本机截图通过只属于原试点。不能说 P05 设备、TalkBack、键盘、玻璃或新视觉 CI 已通过。本研究未重跑这些验证。

可复用资产：

- `ScreenshotFixture.kt:33` 的 360×800dp / 320×640dp、density 2、fontScale 1 / 2、固定时间、角色与本地 Canvas 背景；新增合成长 preset / mask / 群成员 / provider 数据应沿用这个确定性源，不读用户库、网络或图片文件。
- `ScreenshotStageTest.kt:105` 的三主题宿主，以及 `:126` / `:142` 的固定动态 scheme / 同组合切换宿主。不要依赖测试机真实壁纸动态色。
- `ScreenshotRenderTest.kt:75` 使用真实 SettingsViewModel + `createTestAiServices` / FakeStore 的做法；可用于 P05 保存 callback 测试，默认 no-op 的截图回调不能当保存集成证据。
- `androidTest/.../RoleplayUiTestFixtures.kt:8` 的无图 backdrop 可复用局部行为；整页主题 / 玻璃视觉应使用按实际 scheme 派生的 `ScreenshotFixture.backdropState`，避免用旧 fixture 的固定白字证明语义色正确。

### 9. 建议批次与最小新增验收矩阵

P05 保持一个可独立收口的子任务，内部依次完成，不与 P04 / P10 / P11 同时编辑共享文件：

1. **面板与局部默认组件**：四个 P05 文件 + P05 独占消费的 ImmersiveTabRow；迁移默认间距 / 外壳 / 内容色，补操作角色、选中 / 展开、48dp 命中与内容测量。先固定所有权，再消费现有令牌；不修改用户几何 / Tab 动效 / 状态逻辑。
2. **独立弹窗与兼容交互**：预设、面具、长说明、成员、模型 Sheet、重启 / 重置确认，使用实际入口打开。模型 picker / SettingsHelpButton 共享实现能复用就保持；确需共享改动时只交给一位所有者，并补所有现存消费者的定向回归。
3. **保存 / 导航 / 个性化回归与视觉接纳**：用真实 VM + Fake 验证保存与留页 callback，保留场景即时写入；新增 screenshot fixture / case / root guard，先 verify / 产出候选，用户确认后才接纳。P03 和 P04 作为设置返回后的可见回归面，不把它们的 PNG 自动改写。

建议**新增 18 张**作为 P05 的最小截图候选矩阵，避免 8 页 × 13 皮肤 × 每种窗口的笛卡尔扩张；未截图的页面状态由下方行为矩阵覆盖。编号仅建议，最终登记由截图共享文件单一所有者分配，不能复用 / 删除既有 H01–C01 ID。

| 建议组 | 数量 | 具体条件 / 捕获目标 |
| --- | ---: | --- |
| P05 MAIN | 6 | MATCHA / VANILLA / OCEAN × 浅 / 深，360×800dp / 100%，长会话标题，默认折叠；捕获实际路由面板 |
| P05 MAIN 固定动态色 | 2 | 固定动态浅 / 深 scheme，实际 Material 控件承载面；不以原 C01 色板替代 |
| P05 MAIN 大字体 | 2 | 320×640dp / 200% × 浅 / 深，已展开高级区并滚到最下方，重启 / 重置可见；标题 / 首屏可达由行为断言覆盖 |
| P05 THEME 大字体 | 2 | 320×640dp / 200% × 浅 / 深，滚到气泡或阅读设置尾部，保存的 18dp / 14dp / 10dp / 78%、尾角 true、玻璃 false；13 项可达 / 选择由行为循环覆盖 |
| 预设 / 面具 Sheet | 2 | 320×640dp / 200%，一个浅、一个深，长名称 / 规则、多项，滚到最后；**捕获各自窗口 root**，管理按钮在内 |
| 说明 Dialog | 1 | 深色 / 200%，真实长规则滚到尾，关闭可见；独立窗口 root |
| 模型 Sheet | 1 | 浅色 / 200%，多 provider + 长 model 名，实际 P05 打开；独立窗口 root |
| 重置确认 | 1 | 深色 / 200%，明确危险内容色，取消 / 确认同窗可达；独立窗口 root，重启另做行为 |
| GROUP | 1 | 320×640dp / 200%，长成员名 / 禁言状态 / 回复模式；实际路由面板，成员弹窗另做行为 |
| **合计** | **18** | 12 张面板 + 5 张 Sheet / Dialog + 1 张 GROUP；实际总计以登记表为准 |

截图根与计数注意：`ScreenshotStageTest.kt:186` 当前只捕获带 `StageTag` 的父舞台；独立 Dialog / ModalBottomSheet 不在该节点内。新增窗口截图必须明确定位生产弹层的 root（或其窗口内稳定 tag），先断言窗口存在、标题 / 最后项 / 操作，再捕获；其尺寸守卫需要按该窗口记录，不能机械沿用父舞台 720×1600 或伪造合成弹层。成员与重启不一定新增基准，仍须真实窗口行为断言。扩充 `ScreenshotCases`、计数守卫与 manifest 时追加新 family / 条目，原有 79 次验证和旧 PNG 不减少、不自动 record。

| 必需行为组 | 最小可观察断言 |
| --- | --- |
| 实际入口 | P03 更多→设置 / P04 更多→设置调用导航各一次；route scenario 与当前设置一致；详情返回先 MAIN，MAIN 成功保存才离页 |
| 字体 / 滚动 | 真正 Screen 在 320×640dp / 200% 下标题、内容、操作不重叠；八页逐页进入、滚动到底；THEME 最后一项、长内容 Sheet 最后一项及管理按钮可见可点；VISUAL IME 下保留当前输入 / 操作可达 |
| 无障碍 | 所有主要操作有非空名称及合理 role；选项 selected / switch state / disabled 状态可观测；高级区展开状态可读；48×48dp 命中范围并检查相邻控件不会误点；帮助按钮不触发行 / switch，成员操作名称不混淆 |
| 主题与个性化 | 三主题浅深 + 固定动态 scheme 同组合切换后实际文本 / 承载面重算；13 皮肤逐个选择、保留未改的几何 / 尾角 / 玻璃草稿；原合法历史默认值不取整，重置仅按原动作恢复 |
| 保存 / 失败 | 真实 VM + Fake 成功后完整 skin 回读且 callback=1；注入现成失败 editor 后 callback=0、留页、draft 原值、saved 不冒充更新；不为不存在的 UI 错误视图加截图；不另建自动保存 / 重试流程 |
| 弹窗边界 | Sheet 选择回调参数精确且只一次；取消 / 系统返回 / 外部点击只 dismiss 不确认；重启 / 重置未成功不离页；模型选择与 provider 切换保持原回调和收起时机 |
| 共享回归 | 不改共享实现时只跑实际 P03/P04 设置入口及返回面；若改 ModelPickerSheet 则补 SettingsModelScreen / Chat overlays 的现有调用；若改 Help 则先按完整直接消费表审查，不能只编译 P05 |

TalkBack 实际播报、系统返回 / 外部点击窗口事件、D01 IME / 系统栏与真实玻璃仍需设备证据；无设备可交付宿主行为及候选，但不得将这部分标为设备通过或性能无回归。相关命令选择按子任务真实改动，不在规划阶段重跑完整 1642 项。

### 10. Related specs / prior research / external references

- `.trellis/workflow.md`：Phase 1 保持 planning，研究写盘；创建任务不等于实施批准。
- `.trellis/spec/backend/index.md`、`directory-structure.md`：UI / navigation / VM 依赖边界。
- `.trellis/spec/backend/ui-token-contract.md`：默认令牌实际消费、用户几何优先、实际 ColorScheme、精确例外、人工接纳与宿主区分。
- `.trellis/spec/backend/roleplay-theme-settings.md`：完整 skin 保存、独立玻璃开关、线上 / 线下差别与失败边界。
- `.trellis/spec/backend/ui-state-guidelines.md`：实际导航、长说明、设置主题和窗口关闭语义。
- `.trellis/spec/backend/quality-guidelines.md`：截图登记 / verify / 接纳、编译不等于设备运行。
- `.trellis/spec/guides/index.md`、`code-reuse-thinking-guide.md`：变更共享值前枚举所有消费者。
- `.trellis/tasks/archive/2026-10/10-21-ui-token-accessibility-pilot/research/expansion-list.md`：P05 排在 P04 后，SettingsComponents / 旧别名未整体迁移。
- 同归档任务 `verification.md`：原试点已通过项与仍待设备 / 新 CI 的边界。
- External references: 未调用外部资料；此为当前仓库消费者与覆盖研究，不引入新的平台依赖或版本方案。

## Caveats / Not Found

- 本研究未运行构建 / JVM / 截图 / AndroidTest，所有“已有通过”仅引用归档证据；源码风险不等于运行时已复现缺陷。
- 未发现 P05 独立截图、完整 route 保存失败 UI 测试、八页 200% 字体行为或窗口 root 截图。现有 AndroidTest 在原试点只编译通过，设备缺口仍在。
- 未发现 P05 对 Settings message 的展示接线；只能确定现有 ViewModel 失败状态与不退出回调边界。该缺口不授权新增产品错误流程。
- 普通 Chat / Translator 不恢复为日常入口；共享 ModelPickerSheet 的 Chat 消费者只参与变更影响审查。
- v2.3 扩面仍待用户批准实施；构建仍为 2.2.12-dev，不改版本 / dev.json、不发包，不进入 v2.4–v2.6。
