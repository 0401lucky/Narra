# Research: P06 真实入口、世界书列表与书目详情

- Query: 世界书的实际可达入口、列表/书目详情动作和窗口边界、共享所有者、迁移风险、现有测试及最小验收是什么？
- Scope: internal；静态源码研究。列表/书目详情及其直接组件为本文件主体；编辑页实现与保存细节由另一研究文件负责。用户后续已决定同时迁移共享数据页，完整资料库研究见 [context-transfer.md](context-transfer.md)。
- Date: 2026-11-05

## Findings

### 文件与阅读范围

以下生产路径以 `app/src/main/java/com/example/myapplication/` 为根；测试路径以 `app/src/test/java/com/example/myapplication/` 为根，设备测试另注明。所有行号来自本次读取的工作区源码。

| 文件 | 作用 |
| --- | --- |
| `ui/screen/settings/worldbook/WorldBookListScreen.kt` | 列表、两种卡片、分组投影及三个空态；主函数 :76，书卡 :380，条目卡 :463，分组 :574 |
| `ui/screen/settings/worldbook/WorldBookListStyle.kt` | 纯函数：关键词预览、相对时间、筛选、历史书脊色 |
| `ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt` | 书名、页内管理、重命名和整书删除确认；主函数 :47 |
| `ui/navigation/SettingsDataNavRoutes.kt` | 列表 :28、详情 :65、编辑 :124、资料库 :163 的装配及回调 |
| `ui/navigation/NavigationViewModelOwners.kt:147` | 世界书 VM 由 SETTINGS_GRAPH 的 back stack entry 持有 |
| `ui/navigation/AppRoutes.kt:29`、`:92` | 世界书三个路由及参数构造 |
| `ui/screen/immersive/ImmersivePhoneShell.kt`、`ui/navigation/RoleplayNavGraph.kt` | 日常主壳入口及角色/会话到世界书的真实路径 |
| `ui/screen/settings/AssistantExtensionsScreen.kt:364` | 角色挂载配置中的“管理世界书条目”入口 |
| `ui/screen/settings/SettingsComponents.kt` | 共享标题、帮助窗口、标签、按钮、字段色、消息消费 |
| `ui/component/NarraAlertDialog.kt:58`、`AppSnackbarHost.kt:55` | 删除窗口与页内通知承载 |
| `model/WorldBookEntry.kt:63` | resolvedBookId 优先显式 bookId，否则按 sourceBookName 派生 |

### 1. 实际入口与调用链

1. 日常入口：`ui/AppRoot.kt:166` 以 ROLEPLAY_GRAPH 启动；`ui/navigation/AppNavHost.kt:59` 注册角色图，:73 注册设置图，普通 Chat 仅在 :65 的 benchmark 条件下注册。`RoleplayNavGraph.kt:69` / :175 进入真实 ImmersivePhoneShell。
2. 直接路径：“我”页签（`ImmersivePhoneShell.kt:235`，:379 真实页签更新）→ :446 的 ImmersiveProfilePage → :1684 的“资料库 / 世界书”PhoneDestinationRow → :453 的 callbacks.onOpenWorldBookSettings → `RoleplayNavGraph.kt:245` 导航 SETTINGS_WORLD_BOOKS → `SettingsNavGraph.kt:438` 注册的 `SettingsDataNavRoutes.kt:28`。
3. 角色路径：通讯录 :424 的 onOpenContact 设置 selectedContact → :504 的真实 ContactCardSheet → :2224 的“世界书”ContactActionChip → :529 先关闭联系人 Sheet 再发 assistantId → `RoleplayNavGraph.kt:240` 导航 settingsAssistantExtensions → `SettingsAssistantNavRoutes.kt:203` / :217 → `AssistantExtensionsScreen.kt:364` 的“管理世界书条目”→ 同一列表。角色详情的“扩展管理”（`AssistantDetailScreen.kt:161`；`SettingsAssistantNavRoutes.kt:102`）也可进入此角色扩展页。
4. 会话路径：线上内容的设置动作 `RoleplayOnlinePhoneContent.kt:544`、线下 `RoleplayScreenContent.kt:478`，经 `RoleplayNavGraph.kt:580` 导航会话设置；设置主面板“高级选项” :1018 展开后，`RoleplaySettingsSidebarPanel.kt:1061` 的世界书行调用 onOpenWorldBookSettings。`RoleplayNavGraph.kt:749` 有角色时先进入角色扩展，无角色时直接进入全局列表。此处“已挂载”摘要及角色挂载选择属于上游页面，不是列表的选择模式。
5. **不可认作真实入口的签名**：`SettingsNavGraph.kt:146` 虽传 onOpenWorldBookSettings 给 SettingsScreen，但 `SettingsScreen.kt:100` 是该符号在此文件的唯一出现；没有消费它的可见控件。不能把“设置首页 → 世界书”列为已接通路径，更不能为本轮迁移恢复一个新入口。
6. 列表 → 书卡传 `book.id`，条目卡传 `entry.id`；目的地均 launchSingleTop（`SettingsDataNavRoutes.kt:37`、:42）。详情按 Uri.decode(bookId) 筛选所有 entries（:72），所以从搜索后的书卡进入，详情显示该书全量条目，不继承列表筛选。
7. `rememberWorldBookViewModel` 使用 SETTINGS_GRAPH owner（`NavigationViewModelOwners.kt:152`），列表、详情、编辑共享 VM；不在每个截图或每个目的地创建生产数据库。`settingsWorldBookBook` 对 bookId 编码（`AppRoutes.kt:100`）；编辑路由当前只编码 bookName（:92），本次不顺带改既有参数协议。

### 2. 列表：现有动作与状态

| 动作/状态 | 源码锚点与契约 |
| --- | --- |
| 新增 | `WorldBookListScreen.kt:140` 顶栏“新增”→ onAddEntry；`SettingsDataNavRoutes.kt:47` 打开 entryId=new，不创建空书对象 |
| 导入 | `WorldBookListScreen.kt:163` “导入世界书”→ onOpenImport；`SettingsDataNavRoutes.kt:52` 只导航共享资料库，无 section 参数、无自动打开选择器 |
| 搜索/清空 | :101 rememberSaveable(searchQuery)，:183 单行 OutlinedTextField，:196 清空；搜索变化经 :111 派生结果，:224 帮助说明搜索字段 |
| 展开/收起筛选 | :109 showFilters，:216 TextButton 翻转；:228 为 LazyColumn 内普通 item，不是 Sheet/Popup |
| 三维互斥单选 | :231 作用域、:237 状态、:243 书；每一维单选、维度间 AND。选项横向 LazyRow（:351）；只有 allBookOptions.size>1 时出现“书”维度 |
| 清除筛选 | :250 重置 scope/status/bookId，**保留 searchQuery**；计数不含搜索（`WorldBookListStyle.kt:139`） |
| 分组与打开 | :286 / :308 为“导入的世界书”和“独立条目”两个 stickyHeader；不是可折叠书组。:300 的书卡打开书目，:322 独立条目直接打开编辑 |
| 空态 | :261 优先 entries.isEmpty，然后非空搜索，再激活筛选；:626 文案为“还没有世界书 / 当前筛选下没有条目 / 没有匹配结果”。搜索空态只清搜索，筛选空态只清三维筛选 |
| 状态显示 | 书卡 :438 显示条目数；部分停用才显示“启用 n”，另有相对时间和含正则。条目卡 :525 常显作用域，:531 关键词，:539 状态优先 停用 > 常驻 > 正则；这些标签不触发启用/禁用 |
| 通知 | :88 仅显示包含“删除”的 uiMessage；其他消息 :94 延迟 3 秒兜底 consume。`TopAppSnackbarHost` 是页内组合，非独立窗口 |

纯函数契约：

- `WorldBookListStyle.kt:110` 的 filterEntries 按 scope、status、resolvedBookId 与搜索同时筛选，保留输入顺序；搜索将 title/content/sourceBookName/keywords/aliases/secondaryKeywords 合并，用 Locale.getDefault() 小写匹配。
- `WorldBookListScreen.kt:574` 按 resolvedBookId 分组，不按书名简单去重；空 bookId 但有书名可走派生 ID，bookId 非空但书名空仍入书组，显示“未命名世界书”。组内排序 insertionOrder ↑、createdAt ↑、updatedAt ↓；组间取**排序后第一条的 createdAt**再按小写书名。独立条目同样三键排序（:124），无用户切换排序控件。
- 先筛条目再 buildWorldBookBooks（:120），因此搜索后的书卡计数/预览/相对时间只反映命中条目；所有书筛选选项独立从未筛选 entries 生成（:131）。
- `WorldBookListStyle.kt:38` 去空白、按关键词/别名/次关键词顺序去重取前 3 个，不拆正则；:60 相对时间默认读取系统时间，老日期受 Locale/时区影响。截图需固定这些输入和环境。
- `bookSpineColor` 只有定义和 3 个纯测试，**没有生产调用**；当前书卡实际为 :405 的 BookOpen 图标，不能称为“有彩色书脊需迁移”，也不应为了清单修改死代码或新增品牌色例外。

**本列表/详情没有**：批量选择、书组折叠、启用 toggle、复制书/条目、用户排序菜单、列表内删除、列表内导出。相应需求不得因动作清单而被新增；筛选的“启用”仅筛数据。挂载选择在角色扩展，条目启用/优先级等在编辑边界。

### 3. 书目详情：现有动作与状态

| 动作/状态 | 源码锚点与契约 |
| --- | --- |
| 书名/列表 | `WorldBookBookDetailScreen.kt:114` SettingsPageIntro，:175 按 entry.id 列卡片；:178 onOpenEntryEdit(entry.id) |
| 管理展开 | :64 rememberSaveable(showManagement)，:122 “管理世界书/收起管理”；:128 页内 Column，无新窗口 |
| 重命名草稿 | :62 rememberSaveable(bookName)，:81 上游 bookName 变化时同步 renameText；折叠管理本身不提交 |
| 重命名提交 | :85 trim；!isSaving、非空、与现书名不同才 canRename。:142 IME Done 与 :148 保存按钮提交相同 (bookId, trimmedRename)；上游 :98 调 VM，保持当前页 |
| 删除入口 | :154 仅 !isSaving 且 entries 非空可点；打开 showDeleteDialog；红色说明 :160，不直接删除 |
| 删除确认 | :185 NarraAlertDialog；取消/平台 dismiss 只清 showDeleteDialog；:193 确认先关窗口再 onDeleteBook(bookId) |
| 删除后返回 | `SettingsDataNavRoutes.kt:86` 等 entries 真正变空且 bookId 非空才 pop，不在点击确认瞬间 pop；失败后仍有条目则保留页面 |
| 新增 | `SettingsDataNavRoutes.kt:106` 进入 new 并传 bookName；**当前不传 bookId**。P06 保留此入口，ID/保存归属细节交编辑研究，不把 UI 改造成新的书目事务 |
| 通知 | `WorldBookBookDetailScreen.kt:67` 仅显示包含“重命名”的 message，其他 :72 延迟 3 秒 consume；不因迁移增加所有消息面板 |
| 空条目 | :165 存在 SettingsHintCard 的纯 Composable 分支；真实非空 bookId 路由由上述 effect 自动返回，所以不能把它登记为可长期停留的正常路由空态 |

**消息边界必须写入验收**：条目“已保存”消息不在列表/书目详情展示。详情也不会显示缺少“重命名”关键字的其他消息，列表不会显示缺少“删除”关键字的消息。现有过滤不是本轮新错误恢复策略；不要承诺“保存/删除失败都会在当前页出现错误提示”。VM 的成功/失败消息测试不能替代 UI 展示事实。

### 4. 窗口边界与捕获所有权

| 所属 | 实际打开条件 / 回调 | 类型与验收边界 |
| --- | --- | --- |
| 列表帮助 | `WorldBookListScreen.kt:224` → `SettingsComponents.kt:226`，点击“世界书：查看说明”令 showDescription=true（按 title/description saveable） | :250 的私有 SettingsDescriptionDialog 使用 Material AlertDialog（:256）；“知道了”/onDismissRequest 只关闭。P06 必须捕获真实独立 root；不是底页 StageTag |
| 整书删除 | `WorldBookBookDetailScreen.kt:185`，管理展开后点击删除 | NarraAlertDialog → 平台 Dialog（`NarraAlertDialog.kt:77`）。scroll 正文 :110 与固定 FlowRow 动作 :133；平台返回/外部点击 :81 仍应只 dismiss |
| 列表筛选/详情管理/通知 | 列表 :228、详情 :128、TopAppSnackbarHost | 都在页面内容内，没有 ModalBottomSheet、DropdownMenu 或 Popup；以页内截图/行为验收 |
| 上游联系人 | `ImmersivePhoneShell.kt:504` ContactCardSheet | 实际上游 Sheet，导航前关闭；属于入口兼容回归，不把它算作世界书列表窗口 |
| 共享资料库来源 | `ContextTransferScreen.kt:302` ImportSourceChooserDialog → :325 Material AlertDialog | 后续新增的资料库范围负责；“相册 / 文件 / 取消”为本 App 窗口，宿主可按真实 Dialog root 捕获 |
| 系统选择器 | `ContextTransferScreen.kt:68` CreateDocument、:90 OpenDocument、:110 PickVisualMedia | 系统 Activity / 文档提供者 / 照片选择器，不是本页 Compose root；宿主断言 launch/结果/取消，真实平台效果单列设备验收 |
| 导入预览 | `ContextTransferScreen.kt:281` ImportPreviewCard | LazyColumn 内普通卡片，**不是弹窗**；确认/取消及资源类型覆盖见独立资料库研究 |

列表“导入世界书”不会预选 WORLD_BOOK：共享资料页 pendingImportSection/pendingExportSection 初值 ALL（`ContextTransferScreen.kt:60`），用户在“世界书”组点击相应按钮后才设置 WORLD_BOOK（:240 / :244）。本轮已获“同时迁移共享数据页”的范围决定，仍保留这一导航/选择行为。

### 5. 共享 UI 所有者与局部 opt-in

| 所有者 | 现状 | 建议与兼容面 |
| --- | --- | --- |
| `SettingsComponents.kt:122` SettingsTopBar | 已有 horizontalPadding、titleMaxLines=1、titleIsHeading=false | P06 直接显式传 NarraUi 间距及可换行 heading；不改旧默认或顶栏回调 |
| :198 SettingsPageIntro | 4/8dp padding、标题最多 2 行 | 书目标题为实际书名；建议仅补默认兼容的标题行数/间距/语义参数，P06 显式消费，避免复制整个共享标题 |
| :278 SettingsSectionHeader | 4/2/6dp，标题 primary 且最多 2 行 | 增加必要的局部内容色/间距参数或使用小型世界书组合；保持其他消费者默认。本列表 description 为空，不隐式再开帮助窗 |
| :226 → :250 SettingsHelpButton / DescriptionDialog | 固定 48dp 按钮，旧 8dp shape、正文有界滚动，动作默认 Material primary | P06 需真正传到内部窗口的新 shape/动作色；保留旧默认及 title/description saveable key、平台关闭语义。按钮参数不能只加在外层未透传 |
| :472 SettingsStatusPill | shape=999dp、12/6dp；内容色由调用方给定 | 新可选 shape/padding 保留旧值，P06 传 NarraShapes.pill/NarraUi。primaryContainer 上的文字显式使用 onPrimaryContainer |
| :493 SettingsHintCard | 传入 containerColor 只画左侧条，contentColor 只画圆点；真正正文面是 palette.surface，文字 title/body | 若迁移空分支，测实际 surface/onSurface，不能用参数名认定正文在 accentSoft 上 |
| :640 AnimatedSettingButton | 13 个直接消费者文件；无样式参数，内部旧 8dp、20/14dp、min52dp，分支用 NarraButton/OutlinedButton | 优先仅新增 shape/contentPadding 与必要内容色 opt-in，主/次两个分支都透传；维持默认尺寸、动画与禁用语义。若参数过多，可世界书局部组合现有 Narra primitives，但列表与编辑应共用同一小组件 |
| :715 rememberSettingsOutlineColors | 焦点 label/cursor/border=primary，字段实际面=surfaceVariant | P06 局部覆盖可读字段色，不能整体重定义 SettingsPalette.accent 或全局 scheme |
| `NarraAlertDialog.kt:58` | 已有 shape/actionContentColor 可选；默认旧 8dp/primary，破坏性确认优先 error | 删除整书及编辑删除都显式传 NarraShapes.large/实际可读 action 色；保留共享滚动/Insets/动画/返回及 error 优先级 |
| `WorldBookListScreen.kt:463` WorldBookEntryCard | 只有列表 :323 与详情 :176 消费；内部 EntryChipRow 私有 | 一处修改覆盖两个目标页面；两处都要回归。WorldBookBookCard 只有列表 :301 调用 |
| :574 buildWorldBookBooks | 列表 :120/:134 与 `SettingsAssistantNavRoutes.kt:77` 角色详情挂载计数共享 | 纯分组/排序不要因 UI 拆文件被改写；AssistantExtensions 自有选项构造，未直接调用该函数 |
| `ui/theme/UiTokens.kt:7` / :34；`ui/component/NarraButton.kt` | 48dp 和 NarraUi/NarraShapes；按钮可接受 shape/colors/padding | 消费已有令牌，不改全局默认；基础按钮本次无需改动，真实 48dp 仍需测量 |
| `AppSnackbarHost.kt:55` / `SettingsComponents.kt:698` | Top host 11 个直接文件；消息协程展示完成后 consume | 保持消息过滤、延时及 host 默认；通知不是独立窗口 |

建议由**一个明确所有者**维护 SettingsComponents、世界书局部组合及其兼容测试；列表/详情与编辑执行者不能分别加一套参数或覆盖对方改动。完整直接消费者清单见[验证与消费者补充](entry-list-detail-validation.md)。底层 Narra primitives、Palette/历史 SettingsScreenPadding 不作全局迁移。

源码守卫当前 `ui/theme/UiTokenSourceInventory.kt:71` 仅扫描 SettingsTopBar/Group/Divider/ListRow；PageIntro/Help/SectionHeader/StatusPill/AnimatedSettingButton 等仍在 legacyDeclarations（:74）。P06 新登记必须覆盖实际局部声明；若新增共享 opt-in，旧默认例外精确到声明/表达式/次数。不能把整个 SettingsComponents 文件豁免，也不能用仅添加 requiredDeclarations 冒充缩小扫描范围。

### 6. 有源码依据的风险（均未复现）

| 静态依据 | 迁移/验收要防止的变化 |
| --- | --- |
| 列表 :188/:393/:474 的旧 8dp、:155 的 20/4/36dp 与 14dp item gap；共享按钮 :647 | 迁移应改变实际绘制的 shape/padding，不能只改令牌定义；边框 0.5dp、图标尺寸等不能机械当间距替换 |
| 列表选中 chip :359、书卡 :442、常驻标签 :547 为 primaryContainer/primary；共享字段 :723 在 surfaceVariant 上用 primary | 固定动态深色等组合可能不足对比；用生产解析的 8 组 scheme、实际 TextLayout 前景及实际承载面验证 ≥4.5:1，不能据此声称当前已失败 |
| 顶栏 default maxLines=1，详情 PageIntro maxLines=2；卡片名称/预览也有显式省略 | 320×640dp / 200% 可能省略关键标题；详情完整书名应可读取。列表预览本来就是摘要，不把“完整长正文显示”当兼容要求 |
| 搜索/计数 sticky header + 多行筛选 item（列表 :175/:228） | 200% 下需证明真实可读条目区域与首末卡片可达；不能只断言 LazyColumn 高度>0。筛选行末端书名需横向滚到并能选中 |
| 组件 .heightIn(min=48dp) 或 NarraButton defaultMinSize | 不足以证明实际点击 node 的 touchBoundsInRoot≥48dp；要在相邻控件中点击并验证仅一个预期回调 |
| 书卡过滤后计数，详情按全量 entries，ID 派生与稳定 LazyColumn keys | 不要让新布局按标题去重、重排、把一个筛选命中误作为整书数据 |
| renameText 按 bookName key、isSaving gate、IME Done；删除确认后由上游空条目再 pop | 折叠、重命名失败、取消/返回及等候删除期间不能额外提交/丢草稿/提早返回；entry 保存消息未展示的原行为也不改 |
| formatRelativeTime 的系统时钟/Locale 与 TopAppSnackbarHost 的定时动画 | fixture 固定日期/时区/Locale、捕获时间和动画阶段；不从用户真实条目/通知建立基准 |


## 验证与补充资料

后续测试、截图建议、相关spec及消费者清单完整保存在[列表/详情验证与共享消费者](entry-list-detail-validation.md)；本文件与补充文件都纳入上下文，不丢失原有锚点或契约。
