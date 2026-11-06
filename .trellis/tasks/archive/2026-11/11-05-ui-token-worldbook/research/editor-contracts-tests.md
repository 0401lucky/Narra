# Research: 世界书条目编辑、全屏正文与测试契约

- Query: P06 世界书条目编辑/全屏正文实际有哪些字段、导航、保存/返回/取消/失败及弹层契约；外观与无障碍迁移应复用哪些测试/Fake，怎样形成最小行为和截图矩阵？
- Scope: internal；未发布 v2.3 / P06 planning，只研究编辑与其弹层。列表/书目详情仅核对编辑入口、身份和消息回流，不重复整页研究。
- Date: 2026-11-05
- Active task: .trellis/tasks/11-05-ui-token-worldbook
- Evidence: 当前源码和测试静态阅读；没有执行 Gradle、设备、截图、网络、git 或真实用户数据操作。PRD 记载的 158 PNG / 159 截图 / 1826 JVM 仅是前序结果，不是本任务验证。

## Findings

### 1. 结论与规划边界

1. 主编辑“保存”按 **onSave(result) → onNavigateBack()** 同步调用，导航绑定异步 WorldBookViewModel.saveEntry 后立即 pop；不存在“保存成功后才返回”的回调。删除确认同样先提交删除再立即返回（WorldBookEditScreen.kt:143、:169、:577；SettingsDataNavRoutes.kt:150）。
2. 编辑页既不接收 isSaving，也不接收 message。ViewModel 不保存编辑草稿。保存失败只产生固定 message；列表仅显示“删除”、书目详情仅显示“重命名”消息，保存成功/失败均被过滤，随后三秒兜底消费。因此 **当前没有保存失败留在编辑页、保留草稿供重试或可见保存错误提示的保证**（WorldBookViewModel.kt:16、:47；WorldBookListScreen.kt:87；WorldBookBookDetailScreen.kt:66）。
3. “全屏正文”实际是 skipPartiallyExpanded 的 ModalBottomSheet。其“保存并返回”只把全文回写主编辑页的 content 草稿，然后关闭 Sheet；它不写 Repository，也不 pop 主编辑页。取消、Sheet dismiss 不回写（WorldBookContentFullscreenSheet.kt:43、:81；WorldBookEditScreen.kt:585）。
4. 编辑器有 5 个折叠段、11 个说明入口、2 种下拉 Popup、1 个正文 Sheet、1 个删除 Dialog。不能只迁移主页面而漏掉说明和 Popup 的真实窗口。
5. 预算、独立的插入位置枚举、扫描深度、扩展 JSON 没有当前编辑控件。可编辑的是 insertionOrder 整数；Tavern position 映射到它。depth / position_override 等未识别导入字段保存在 extrasJson，编辑现有条目必须继续通过 copy 保留。角色级 worldBookMaxEntries / worldBookScanDepth 位于其他页面，不纳入 P06 编辑页扩面。
6. 只计划默认令牌、实际颜色配对、标签/状态语义、触控和有界布局。保存事务、错误路由、匹配、持久化规范化、导入格式和身份生成保持现有契约。下述旧缺口是研究发现，不是本轮已修复的问题。

### 2. Files found 与所有者

下文短文件名的完整路径在此登记；行号以本次静态读取为准。M = app/src/main/java/com/example/myapplication，T = app/src/test/java/com/example/myapplication，A = app/src/androidTest/java/com/example/myapplication。

| 文件 | 责任 / 消费边界 |
| --- | --- |
| M/ui/screen/settings/worldbook/WorldBookEditScreen.kt:81 | 页面草稿、字段、保存/删除、作用域、下拉、试命中；唯一生产装配为 SettingsDataNavRoutes |
| M/ui/screen/settings/worldbook/WorldBookEditSections.kt:32 | 折叠状态 Saver 和分段容器，仅编辑页消费 |
| M/ui/screen/settings/worldbook/WorldBookContentFullscreenSheet.kt:38 | 正文的二级草稿与 ModalBottomSheet，仅编辑页消费 |
| M/ui/component/worldbook/KeywordChipInput.kt:122 | 三组关键词输入、chips、独立未提交文本；生产消费者仅编辑页的主关键词/别名/次级关键词 |
| M/ui/navigation/SettingsDataNavRoutes.kt:124 | 真实编辑路由绑定、bookName 参数、共享 VM、角色/会话数据 |
| M/ui/navigation/AppRoutes.kt:29、:92 | 世界书路由协议与构造器；entryId 直接插入、bookName 编码 |
| M/ui/navigation/NavigationViewModelOwners.kt:147 | 世界书 VM 属于 SETTINGS_GRAPH，列表/详情/编辑共用 |
| M/viewmodel/WorldBookViewModel.kt:16 | entries/isSaving/message；异步写入及固定反馈文案 |
| M/model/WorldBookEntry.kt:22 | 原生字段、稳定身份、resolvedBookId / 派生书 ID |
| M/model/WorldBookMatchMode.kt:3 | CONTAINS / WORD_CJK / REGEX 与存储值 |
| M/model/ContextImportNormalization.kt:88 | 手工保存也经过的字段长度、关键词、概率和 extras 规范化 |
| M/data/repository/context/WorldBookRepository.kt:22 | Repository 接口和 Room 映射；不可从 UI 绕过 |
| M/data/local/worldbook/WorldBookEntryEntity.kt:17；WorldBookDao.kt:16 | Room 列、主键、查询与写入；本任务只读 |
| M/data/repository/context/TavernWorldBookAdapter.kt:110 | Tavern 映射、稳定 ID、未知字段保留 |
| M/context/WorldBookMatcher.kt:144 | 纯本地试命中；非完整上下文注入过程 |
| M/ui/screen/settings/SettingsComponents.kt:100、:122、:226、:640、:715 | 共享 palette、工具栏、帮助弹窗、按钮、TextField 颜色 |
| M/ui/component/NarraAlertDialog.kt:58 | 共享删除确认窗口；支持局部 shape/actionContentColor |
| M/ui/component/NarraButton.kt:268；M/ui/theme/UiTokens.kt:7 | 共享按钮触控下限与令牌；不全局改默认 |
| T/testutil/FakeWorldBookRepository.kt:11 | 内存 StateFlow Fake；整书失败开关，不具备条目写失败/挂起钩子 |
| T/viewmodel/WorldBookViewModelTest.kt:15 | VM 消息/订阅与整书变更测试 |
| T/data/repository/context/WorldBookRepositoryTest.kt:25 | 真实 Repository + 私有 RecordingWorldBookDao 验证规范化/映射 |
| T/data/repository/context/TavernWorldBookAdapterTest.kt:16；ContextTransferCodecTest.kt:18 | 合成 JSON 导入/编码兼容测试 |
| T/ui/component/worldbook/KeywordChipInputTest.kt:8 | 仅纯函数拆词/正则外观识别，未渲染输入控件 |
| T/context/WorldBookMatcherTest.kt:606 | previewHit 空输入、主/次级命中、常驻等纯规则测试 |
| T/ui/screenshot/ScreenshotStageTest.kt:53；ScreenshotFixture.kt:34 | 可复用的确定性 Robolectric 宿主 |
| T/ui/screenshot/ProviderSettingsRenderTest.kt:178 | 独立 Dialog root、触控/未裁切布局/字体测量参考 |
| A/ui/component/NarraAlertDialogTest.kt:59；A/ui/screen/settings/SettingsComponentsTest.kt:30 | 长弹窗、系统返回/外部点击、帮助不触发整行的设备测试模式 |

### 3. 真实导航、草稿生命周期与失败反馈

**路由可达性**

- AppNavHost.kt:73 注册 settings 图；SettingsNavGraph.kt:67、:438 注册 SETTINGS_GRAPH 和 registerSettingsDataRoutes。
- 列表编辑已有 entryId、新建传 "new"；书目详情编辑传 entryId，新建只传 "new" + 当前 bookName（SettingsDataNavRoutes.kt:42、:47、:106、:111）。
- AppRoutes.settingsWorldBookEdit 在 :92 将 bookName 用 Uri.encode 写入查询参数；entryId 不编码。读取端 :135 直接读取 entryId，:136 Uri.decode bookName；仅 entryId == "new" 判新建。正常现有条目 ID 来源为 UUID/稳定 ID，本次不顺带迁移旧路由协议。
- 编辑的 entry 来自 worldBookState.entries.firstOrNull(id)，没有独立 getEntry 或加载/不存在页面（SettingsDataNavRoutes.kt:138）。existingBookNames 为 trim 后去空、distinct、sorted 的名称列表（:146）。
- “可挂载 → 去角色页挂载”只导航 SETTINGS_ASSISTANTS；不先保存、不确认离开，也不自动挂载（WorldBookEditScreen.kt:391；SettingsDataNavRoutes.kt:155）。其目标整页不属本研究扩面。

**草稿**

- 标题、正文、三组关键词、书名、数字文本、四开关、模式、作用域/ID、预览输入、弹层开关用 rememberSaveable；除关键词组件外，页面草稿没有 entry.id 键，也没有对 entry 改变的同步副作用（WorldBookEditScreen.kt:96–129）。
- 五段折叠默认仅基本信息/命中规则展开，作用域/状态/预览折叠；Saver 恢复旧四值列表时 hitPreview 回退 false（WorldBookEditSections.kt:46、:54）。
- rememberSaveable 只提供当前 Composition/导航状态保留机制，不能据此声称保存失败后被 pop 的草稿仍能恢复。VM 内部只有 isSaving/message，没有 draft entry（WorldBookViewModel.kt:22）。
- 静态风险：entry 起初为 null 后到达，已有 rememberSaveable 不随 entry 更新重新初始化；不存在 ID 也会显示默认字段的“编辑条目”，填完后可按新模型生成保存。该路径未运行复现，不新增假想 loading/error 页面，也不在视觉迁移中悄悄改初始化规则。

**动作时序**

| 动作 | 当前精确行为 | 保留要求 |
| --- | --- | --- |
| 主保存，canSave=false | toolbar actionEnabled=false；闭包也检查 canSave | 保留两层门槛，不加新字段/业务校验 |
| 主保存，canSave=true | 组装 entry.copy / 新 WorldBookEntry；onSave；紧接 onNavigateBack | 不变为等待成功、不添加自动重试或未保存确认 |
| 顶部返回 | 直接 onNavigateBack，真实绑定 pop | 不隐式保存 |
| 页面系统返回 | 页面没有 BackHandler，沿既有导航返回；有弹层时先按平台弹层语义 | 不能把系统返回等同于保存 |
| 删除入口 | 仅 !isNew && entry!=null 渲染；先打开确认 | 不把整页删除按钮变为直接删除 |
| 删除确认 | 先关闭 Dialog，再 onDelete(entry.id)，再 onNavigateBack | 只显式确认可写入；文案引用原 entry.title，不是未保存 title |
| 删除取消/返回/外部点击 | onDismiss 只把 showDeleteDialog=false | 不保存、不删除、不离开编辑页 |
| 全屏确认 | onConfirm(draft) → onDismiss；父回调 content=it | 只更新正文草稿，保留其他字段，不触发主保存或导航 |
| 全屏取消/平台 dismiss | 只 onDismiss，父只关 Sheet | 未确认的 Sheet 文本不回写 |

证据：WorldBookEditScreen.kt:135、:143、:176、:550、:569、:585；WorldBookContentFullscreenSheet.kt:43、:47、:67、:81；NarraAlertDialog.kt:77。

**异步状态与消息实际落点**

- saveEntry 开协程，isSaving=true/message=null，upsertEntry 再次更新 updatedAt；成功/失败后 isSaving=false，message 分别为“世界书已保存”/“保存失败，请重试”（WorldBookViewModel.kt:47–59）。无成功回调、无 in-flight 守卫、无被测可注入时钟。
- deleteEntry 空字符串直接返回，否则相同时序，消息“世界书已删除”/“删除失败，请重试”（:62–73）。同一 isSaving 也被 rename/deleteBook 共用，不能视作编辑器专有进度。
- 页面签名完全没有 isSaving/message，Scaffold 没有 Snackbar host；不要创造“编辑页保存中/失败态”截图用例，实际并不存在。
- 列表只把包含“删除”的消息送入 rememberSettingsSnackbarHostState，其余三秒后 consume（WorldBookListScreen.kt:87–99）；详情只显示“重命名”，其余三秒 consume（WorldBookBookDetailScreen.kt:66–78）。保存成功和失败两种消息在两页均不可见。
- 单条删除回列表时可显示删除消息；回到仍有条目的书详情时消息被详情过滤。删掉最后一条后，上层因 bookEntries.isEmpty() 继续 pop 到列表（SettingsDataNavRoutes.kt:86）。
- 固定失败文案不透出原异常。旧 runCatching 也会把 CancellationException 当失败；本次不改 VM 的取消策略，只登记为与 error-handling 规范的既有差距。

### 4. 字段和数据保留契约

| 字段 / UI | 默认、门槛、保存转换 | 证据 |
| --- | --- | --- |
| entry.id / bookId / createdAt | 无编辑控件；旧 entry.copy 保留，新建在点击保存时构造随机 id/时间 | WorldBookEditScreen.kt:146；WorldBookEntry.kt:23 |
| 标题 / 正文 | 默认空；均须 isNotBlank；标题 singleLine，正文 min 160dp / maxLines=12；保存原字符串，不 trim 正文/标题 | WorldBookEditScreen.kt:96、:135、:147、:233 |
| 所属世界书 sourceBookName | 新建取 presetBookName；可输入任意名称或从现有名称 Popup 选择；保存 trim；可留空 | WorldBookEditScreen.kt:107、:149、:632 |
| 三组关键词 | 主 keywords、aliases、secondaryKeywords 默认空；无“至少一个关键词”保存门槛；次级控件仅 selective=true 时可编辑 | WorldBookEditScreen.kt:98、:302、:330 |
| 匹配模式 | 默认 WORD_CJK，选项包含/整词/正则，存储 contains/word_cjk/regex | WorldBookEditScreen.kt:121、:288；WorldBookMatchMode.kt:3 |
| 优先级 priority | 默认文本 "0"；只允许数字和首位 '-'；trim().toIntOrNull() 失败回退 0，允许负数 | WorldBookEditScreen.kt:110、:160、:314、:620 |
| 次级匹配 selective | 默认 false，保留 secondaryKeywords 内容；关掉只禁用控件，不清空 | WorldBookEditScreen.kt:119、:324、:335 |
| 区分大小写 caseSensitive | 默认 false；直接写回 | WorldBookEditScreen.kt:120、:337 |
| 作用域 | GLOBAL / ATTACHABLE 不要求 ID，切到这两项立刻清空 scopeId，保存时也强制空 | WorldBookEditScreen.kt:135、:162、:362；WorldBookEntry.kt:6 |
| 角色作用域 | 角色用 FlowRow chips，保存 assistant.id；没有角色显示“暂无角色” | WorldBookEditScreen.kt:402 |
| 会话作用域 | Popup，按 updatedAt 降序；显示标题/相对时间，空标题显示 DEFAULT_CONVERSATION_TITLE；空列表为只读占位 | WorldBookEditScreen.kt:683 |
| 作用域 ID 验证 | ASSISTANT / CONVERSATION 仅检查非空，不校验存在于候选；二者互切不自动清空旧 ID | WorldBookEditScreen.kt:135、:366 |
| enabled / alwaysActive | 默认 true / false；直接保存，关闭条目仍能编辑保存 | WorldBookEditScreen.kt:117、:451 |
| 高级：insertionOrder | 默认 0；只在“状态与高级 → 高级参数”显示；数字过滤/非法回退 0 | WorldBookEditScreen.kt:111、:156、:463 |
| 高级：probability | 默认 100；数字过滤，非法回退 100，再 coerceIn(0,100)，不作为 canSave 条件 | WorldBookEditScreen.kt:114、:157、:482 |
| extrasJson | 无展示/编辑器；旧 entry.copy 完整传递。存储仍按原规范化规则处理 | WorldBookEntry.kt:43；WorldBookEditScreen.kt:146；WorldBookRepository.kt:201 |
| 待测文本/命中结果 | previewSource 只影响本地预览，不持久化；按钮非空才启用；只有待测文本变化会清空旧预览结果 | WorldBookEditScreen.kt:129、:509、:523 |

**身份与格式不能被外观迁移顺带改写**

- 既有 bookId 非空时，编辑所属世界书名称不会重新派生或替换 bookId；Room 只在 bookId 空时从源书名派生（WorldBookRepository.kt:150–158）。书目详情“新增”只传名称，不传原 bookId。不能把“改显示书名”重写成迁移分组或整书重命名。
- resolvedBookId 首选显式 bookId，否则 trim 书名、规范化空白并 lowercase 后生成 name UUID（WorldBookEntry.kt:63–82）。
- Repository 每次 upsert 都调用 normalizedForContextImport：标题≤160字符、正文≤20000、书名≤160；保留标题/正文末尾换行；源书名、scopeId trim；主/别名最多96项、次级64项，去空/去重/安全过滤；概率0–100（WorldBookRepository.kt:150、:167；ContextImportNormalization.kt:8、:88；WorldBookRepositoryTest.kt:29、:48、:111）。
- extras 空/非法对象回退 "{}"；有效对象按序列化JSON的String.length≤16384处理，代码按字符串长度而非UTF-8字节计数，超限字段整字段处理。不能把“编辑保留extras”扩写成绕过既有规范化的字节级承诺（ContextImportNormalization.kt:136–171）；导入文件16MiB上限是另一层字节限制。
- Tavern order → priority，position → insertionOrder，probability → 独立字段；未知 depth / position_override / role / logic → extrasJson；导入默认 ATTACHABLE（TavernWorldBookAdapter.kt:123、:128、:163、:248；TavernWorldBookAdapterTest.kt:21）。
- 无单条 token budget、独立插入位置或扫描深度控件。AssistantExtensionsScreen.kt:141、:152、:162 所属角色设置的条数上限/扫描深度与单条 priority/order 不是一回事；WorldBookMatcher.kt:42、:50 消费角色的这些配置。本任务不扩入角色设置。

**关键词与预览的细节**

- KeywordChipInput 的 draft 独立于 values；以半角/全角逗号或换行结尾，以及 IME Done 才提交分词/去重；仅失焦或点击主保存不会自动提交尚未形成 chip 的输入（KeywordChipInput.kt:132、:143、:163）。不要为了布局调整添加自动提交。
- 拆词支持正则字面量内的逗号和转义斜线；chip 显示“正则 …”只做外观识别，不等于安全规则验证。点击整个 chip 移除该 token；trailing Close 不是第二个独立回调（:38、:62、:181）。
- 试命中只构造 id/title/content/keywords/aliases/secondary/selective/caseSensitive/matchMode/alwaysActive，调用纯 previewHit；不考虑 scope、enabled、probability、条数预算，也不保存（WorldBookEditScreen.kt:526；WorldBookMatcher.kt:144）。
- 结果目前有“命中：会被自动注入”等文案，但并非完整真实注入证明。修改关键词/模式/开关不自动清掉旧结果是现存行为；本轮不要顺带改预览算法、刷新策略或承诺范围。

### 5. 编辑页全部实际弹层清单

说明框由 SettingsHelpButton → 私有 SettingsDescriptionDialog → Material AlertDialog 实现；不属于 NarraAlertDialog。说明框的“知道了”和平台 dismiss 都只关闭该框（SettingsComponents.kt:226–274）。

| ID | 触发/前置状态 | 真实容器、回调 |
| --- | --- | --- |
| H1 基本信息 | 分段标题右侧“基本信息：查看说明” | AlertDialog，WorldBookEditScreen.kt:223；WorldBookEditSections.kt:114 |
| H2 命中规则 | 分段标题右侧说明 | AlertDialog，WorldBookEditScreen.kt:269 |
| H3 作用域 | 分段标题右侧说明，段可保持折叠 | AlertDialog，WorldBookEditScreen.kt:348 |
| H4 状态与高级 | 分段标题右侧说明 | AlertDialog，WorldBookEditScreen.kt:441 |
| H5 试命中（预览） | 分段标题右侧说明 | AlertDialog，WorldBookEditScreen.kt:499 |
| H6 匹配模式 | 命中规则展开，内部 SettingsSectionHeader 说明 | AlertDialog，WorldBookEditScreen.kt:280 |
| H7 次级匹配 | 命中规则展开，ToggleField 说明 | AlertDialog，WorldBookEditScreen.kt:324；ToggleField :611 |
| H8 区分大小写 | 命中规则展开，ToggleField 说明 | AlertDialog，WorldBookEditScreen.kt:337 |
| H9 启用条目 | 状态与高级展开，ToggleField 说明 | AlertDialog，WorldBookEditScreen.kt:451 |
| H10 常驻注入 | 状态与高级展开，ToggleField 说明 | AlertDialog，WorldBookEditScreen.kt:457 |
| H11 注入控制 | 状态与高级展开且高级参数展开 | AlertDialog，WorldBookEditScreen.kt:470 |
| P1 所属世界书 | 基本信息展开且 existingBookNames 非空 | ExposedDropdownMenu Popup，选名称只写 sourceBookName 并关闭；空候选没有 Popup（:638–677） |
| P2 所属会话 | 作用域展开、选择 CONVERSATION 且会话非空 | ExposedDropdownMenu Popup，选 conversation.id 并关闭；空会话只有只读占位（:697–765） |
| S1 编辑正文 | 基本信息正文 trailing 扩展图标 | ModalBottomSheet，二级草稿、确认回写/取消不回写（:253、:585） |
| D1 删除世界书条目 | 已有 entry、页面末尾点击删除 | NarraAlertDialog，显式确认删除原 entry.id；取消/返回/外部点击仅关闭（:550–582） |

不是弹层：匹配模式/作用域/角色 chips、分段折叠、“高级参数”、试命中结果卡、“去角色页挂载”导航。没有主页面取消按钮、未保存确认框、JSON 编辑框、保存错误 Dialog 或另一个正文路由。

### 6. 外观/无障碍风险与局部迁移建议

这些是源码风险与拟验证点，不是已复现问题。

| 位置 | 当前风险 | 允许的局部处理 / 必须证明 |
| --- | --- | --- |
| 顶部导航/保存 | SettingsTopBar 默认标题 maxLines=1，非 heading；320dp/200% 下返回+标题+保存争宽 | 编辑调用显式启用 titleMaxLines/titleIsHeading 等已有接口；标题可读、保存实际完整触控区≥48dp；保留原 onSave→back 顺序 |
| 长 LazyColumn | 默认前两段展开；大量关键词、长角色名、末尾删除/预览可被挤出；现有 worldbook_editor 是唯一主列表 tag | 使用内容驱动布局/已有滚动，按真实控件或新增局部 tag 找段和末项；测到最末操作全部可见，不能只看最后一个 Text |
| 分段标题 | 行56dp且帮助独立，但折叠点击行未显式 Button role/expanded state；图标含“展开/折叠”文字 | 增强 heading/状态/点击语义；帮助仍单独命中且不触发折叠，不能产生两次回调 |
| ToggleField | Text+帮助+Switch 横排；仅 Switch 接回调，名称 Text 不是 Switch 的明确语义标签 | 可局部合并带名称/状态语义或扩大行操作区；若整行 toggle，子 Switch 只绘制，帮助独立，真实点击只写一次 |
| 关键词输入 | 可编辑框只有 placeholder，标签在外部；每个 chip 整体删除，长正则和中文会换行 | 给输入可读名称；保证长标签可阅读，chip移除动作/禁用态/实际触控≥48dp；保持提交和删除规则 |
| TextField 焦点/selected chip | outline focusedLabelColor=primary，承载面为 surfaceVariant；选中 FilterChip 为 primaryContainer/primary；正则 chip 同样用 primary | 局部使用实际承载面的语义内容色；读取 TextLayout 前景并按实绘颜色合成测≥4.5:1，包含固定动态深色、错误、禁用与焦点状态 |
| 数字/错误 | 必填提示仅顶部 Text；数字非法/溢出静默回退且概率夹紧；没有字段 isError 或错误语义 | 可把已有同一必填条件提供就近/可访问语义；不新增保存门槛、不改数字解析、不宣称 regex 是字段校验失败 |
| 下拉 Popup | 书名、会话长文本/相对时间、many items；是独立窗口 | 真实打开后测末项、selected项、关闭和外部触控；不能在底页定位同名标签冒充 Popup |
| 全屏正文 | Column.fillMaxSize + imePadding + navigationBarsPadding；TextField.weight(1) 没有 label；头部取消/底部保存独立，但字体/键盘可压缩编辑区 | 加正文可访问名称；保留顶部取消与底部保存，在剩余有界空间滚动正文；验证长正文末尾和保存均可达，真实IME另测 |
| 说明框 | 共用 AlertDialog 正文 max360dp/滚动，“知道了”独立；标题和操作大字体可能挤占；硬编码shape8dp | P06显式 opt-in 默认令牌/可读动作色，保持共享旧默认；11入口均驱动真实打开/关闭，长说明需实际滚动到尾后按钮可见 |
| 删除框 | 已有有界正文滚动、FlowRow动作、平台dismiss；当前调用沿旧shape/普通动作 primary | 使用既有 shape/actionContentColor 参数局部传递；破坏性确认 error 优先级、原始 ID、取消闭包不变 |
| 试命中结果 | 结果只是 Text 无状态播报；长命中词可能过长 | 可补 liveRegion/结果语义与换行，不能自动改变匹配/刷新策略 |
| 加载/保存语义 | UI无entry加载态、isSaving接线或防重复保存 | 如实记录缺口，不伪造loading截图；需要新增异步契约时另行批准，不和默认令牌迁移混做 |

源码硬编码分布：WorldBookEditScreen.kt:191、:197、:230、:239、:285、:443、:478、:507、:778；WorldBookEditSections.kt:90、:94、:99；WorldBookContentFullscreenSheet.kt:58、:59、:78；KeywordChipInput.kt:136、:160、:175。图标尺寸、内容编辑最小高度、系统 Insets 与布局间距分开判定；功能尺寸保留时需精确例外，不能整文件豁免源码守卫。

### 7. 共享消费者及建议文件所有权

- **编辑实现所有者（建议同一人）**：WorldBookEditScreen、WorldBookEditSections、WorldBookContentFullscreenSheet、KeywordChipInput 的视觉/语义部分；相关新增 Editor Actions/Accessibility/Windows/Colors 测试。KeywordChipInput 的纯拆词函数不改。
- **主会话/共享集成所有者**：SettingsComponents 的兼容接口、NarraAlertDialog 局部参数消费、NarraUi/NarraShapes、截图登记/源码清单、真实导航接缝。列表/详情所有者与编辑所有者不要同时改共享文件或共同 Fake。
- **契约只读所有者**：WorldBookViewModel、WorldBookEntry/MatchMode、Repository/DAO/Entity、Matcher、Tavern/ContextTransfer 编解码。为测试确需路由接缝时应在计划列为有条件小改，不能直接通过构造真实 AppGraph 取得数据。
- WorldBook VM 还被 SettingsAssistantNavRoutes.kt:57、:205 的角色相关路由读取；数据仓储被 ContextTransferViewModel.kt:250、:267、PromptContextAssembler.kt:90、SearchWorldBookTool.kt:45、ToolAvailabilityResolver.kt:95 消费。不能将编辑草稿写入共享 entries 来“保稿”，这会传播未保存内容。
- SettingsTopBar 为共享接口；当前默认保留，编辑页可显式传现有布局参数。SettingsComponents 中的帮助/section/button 暂无完整局部 shape/actionColor/spacing 参数，若为 P06 增加参数，必须默认兼容并一路透传。
- SettingsHelpButton / SettingsSectionHeader 的直接消费者（合并去重，不含定义文件）：DiagnosticsScreen、RoleplayScenarioEditScreen、RoleplaySettingsContent、PhoneCheckScreen、MomentsScreen、AppUpdateScreen、VoiceSynthesisSettingsScreen、VoiceRecognitionApiScreen、PresetManagerScreens、UserPersonaMasksScreen、SearchToolSettingsScreen、ScreenTranslationSettingsScreen、RoleplayScriptLabScreen、ContextTransferScreen、AssistantPageComponents、AssistantDetailScreen、AssistantPromptScreen、AssistantExtensionsScreen、SimpleMemoryEditorScreen、MemoryManagementScreen、ContextLogListContent、ContextGovernanceSheet，以及 WorldBookListScreen / WorldBookEditScreen / WorldBookEditSections。不能全局替换 SettingsDescriptionDialog 默认shape或文字角色。
- AnimatedSettingButton 的直接消费者（不含定义）：DiagnosticsScreen、AssistantExtensionsScreen、AssistantBasicScreen、AssistantMemoryScreen、AppUpdateScreen、ContextTransferScreen、RoleplaySettingsSections、RoleplayScenarioEditScreen、ScreenTranslationSettingsScreen、WorldBookListScreen、WorldBookBookDetailScreen、WorldBookEditScreen、WorldBookContentFullscreenSheet。P06局部 opt-in，历史默认52dp、8dp形状、20/14dp padding不能顺带全改。
- NarraAlertDialog 的直接消费者（不含定义/NarraInfoDialog）：PhoneComponents、RoleplaySettingsContent、RoleplayScreen、MomentsScreen、VoiceSynthesisSettingsScreen、ImmersivePhoneShell、ProviderModelTab、ProviderModelDialogs、ProviderConfigTab、ContextLogScreen、AssistantDeleteConfirmDialog、PresetManagerScreens、ChatScreenOverlays，以及两处世界书页。现有可选shape/actionContentColor足够，不需改变共享默认。
- FakeWorldBookRepository 的已有测试消费者：WorldBookViewModelTest、ContextTransferViewModelTest、RoleplayViewModelTest、ToolAvailabilityResolverTest、SearchWorldBookToolTest。新增条目失败/延迟场景优先测试局部委托包装，不改变其默认成功行为。


## 验证与补充资料

后续测试、截图建议、相关spec及消费者清单完整保存在[编辑验证、Fake与截图矩阵](editor-validation.md)；本文件与补充文件都纳入上下文，不丢失原有锚点或契约。
