# Research: P06 共享资料库全页（ContextTransfer）

- Query: 用户决定同时迁移共享数据页后，资料库全部分组、预览、真实窗口及系统选择器如何保持原契约？怎样用 Fake 验证而不构造用户 AppGraph 或扩大领域接口？
- Scope: internal；未发布 v2.3 外观/a11y 规划，覆盖共享资料库全页。只研究 UI、直属文件 helpers、VM 与导航的既有边界，不变更格式、合并、媒体保存、预设或 URI 读写策略。
- Date: 2026-11-05

## Findings

### 文件与职责

生产路径相对 `app/src/main/java/com/example/myapplication/`；测试路径相对 `app/src/test/java/com/example/myapplication/`，设备路径另注。

| 文件 | 职责与锚点 |
| --- | --- |
| `ui/screen/settings/ContextTransferScreen.kt:48` | 纯 UiState/回调入口；:68/:90/:110 注册三个 ActivityResult launcher；:130 真实页面 |
| 同文件 :320 / :346 / :396 | 私有 ImportSourceChooserDialog、TransferSectionGroup、ImportPreviewCard；只由本页调用 |
| 同文件 :486 / :498 / :526 / :542 | 写 URI、读 payload、查文件名、16MiB 有界读取；属于现存 Android IO 边界 |
| `viewmodel/ContextTransferViewModel.kt:36` / :53 / :65 / :79 | section 枚举、预览、payload、UiState；:90 构造注入，:123/:156/:208 为导出/预览/确认 |
| `ui/navigation/SettingsDataNavRoutes.kt:163` | 收集 VM state，原样绑定导出/预览/确认/取消/consume/back |
| `ui/navigation/NavigationViewModelOwners.kt:180` | 图级 VM owner；真实头像保存/数据库事务/会话 ID provider 的装配 |
| `model/ContextDataBundle.kt:3`、`ContextImportNormalization.kt` | 本地资源包字段及既有输入上限 |
| `data/repository/context/ContextTransferCodec.kt:8` | AppJson Gson 编解码；数组空值兼容；日志只记原文长度 |
| `TavernCharacterAdapter.kt`、`TavernCharacterImageAdapter.kt`、`TavernWorldBookAdapter.kt`、`TavernPresetAdapter.kt`（同 repository/context 目录） | 既有外部资源格式适配；P06 只回归，不改解析 |
| `ui/screen/settings/SettingsComponents.kt` | 全页共同使用标题、信息行、Group、帮助窗口、按钮和 HintCard；兼容默认所有者 |

### 1. 入口与状态所有权

- 真实日常直接入口：“我 → 资料库 → 资料导入导出” `ImmersivePhoneShell.kt:1687` → :455 的 callback → `RoleplayNavGraph.kt:255` → SETTINGS_CONTEXT_TRANSFER。另一个真实入口是世界书列表“导入世界书” `WorldBookListScreen.kt:163` → `SettingsDataNavRoutes.kt:52`。
- `SettingsScreen.kt:102` 的 onOpenContextTransferSettings 只出现在签名，`SettingsNavGraph.kt:156` 的传参没有对应设置首页可见控件；不能列为已接通入口。
- 世界书列表不传 section，不自动打开来源弹窗。`ContextTransferScreen.kt:60` / :61 的 pendingImportSection / pendingExportSection 初值均为 ALL，用户点击具体资源组才修改；“从世界书入口进入就预选 WORLD_BOOK”会改变原行为。
- 页面本身**不依赖 AppGraph、ViewModel、Clipboard**。只有 LocalContext、ActivityResultRegistry/launcher、ContentResolver 及组合协程是平台接点。未发现本页 Clipboard 操作、BackHandler、运行时权限 launcher 或独立网络请求。
- `NavigationViewModelOwners.kt:186` 使用 SETTINGS_GRAPH owner；:191 的生产 factory 接 AppGraph 五类仓储/Settings editor，:198 的头像保存调用 ImageFileStorage，:205 接数据库事务，:206 查已存在会话 ID。测试不能为截图走这条真实 AppGraph 装配。
- UI 的两个 pending section 与来源弹窗开关用 rememberSaveable；VM 的 pendingImportBundle/pendingAssistantAvatarImport 为普通字段（`ContextTransferViewModel.kt:114`），没有 SavedStateHandle。不能承诺进程死亡恢复导入草稿。
- 顶栏 :132 只有返回，导航回调只是 pop（`SettingsDataNavRoutes.kt:177`）；不调用 dismissImportPreview、不取消 VM job。图存活时 VM 可保留预览；“返回”不等于撤销导入。

### 2. 全页可见内容与各组动作

`ContextTransferScreen.kt:144` 一个 LazyColumn，自上而下：intro + 资料库概览 + 资源包 + 可选导入预览。没有 tab、搜索、排序、全页保存、FAB、选择列表、Sheet 或 Popup。

**概览**（:162–198）：五个只读 SettingsListRow，分别显示角色包 customAssistantCount、世界书条目 worldBookCount、记忆档案 memoryCount、对话摘要 summaryCount、自定义预设 presetCount。所有行 onClick=null、enabled=false、showArrow=false。即使数量是 0，也保留行和全部导入/导出按钮，没有特殊“空资料库”分支。

| 可见资源组 / section | 导出（CreateDocument，application/json） | 导入入口 | 既有数据边界 |
| --- | --- | --- | --- |
| 资料库备份 / ALL (:206) | :210 → narra-library-export.json | :214 → 来源选择 Dialog | 当前包全部六类数组 |
| 角色包 / ASSISTANTS (:221) | :225 → narra-character-pack.json | :229 → 来源选择 Dialog | 原生包只角色；Tavern 角色来源可附角色作用域世界书 |
| 世界书 / WORLD_BOOK (:236) | :240 → narra-worldbook-pack.json | :244 → 来源选择 Dialog | 只保留 worldBookEntries，包括从 Tavern JSON/PNG 抽出的世界书 |
| 记忆档案包 / MEMORY (:251) | :255 → narra-memory-pack.json | :259 → 直接 OpenDocument | memoryEntries + conversationSummaries + conversationSummarySegments |
| 预设包 / PRESETS (:266) | :270 → narra-preset-pack.json | :274 → 直接 OpenDocument | 只保留 presets；无单独摘要导入按钮 |

文件名和标签由 `ContextTransferViewModel.kt:36` 枚举拥有。预览/导出 section 过滤在 :565；不应把五个 UI 按钮归并成一个新的全局选中类型。

每组由私有 `TransferSectionGroup:346` 渲染：标题、固定“导出或导入本地文件”、帮助按钮、两列主/次按钮。isBusy 时两按钮文本都变“处理中…”，enabled=false（:373/:382）。没有进度百分比或停止动作。

### 3. 预览确为页内卡片

- `ContextTransferScreen.kt:281` 只在 uiState.importPreview 非空时追加“导入预览”Header 与 ImportPreviewCard；没有自动滚到预览的 LaunchedEffect 或新路由，不新增自动跳转策略。
- `ImportPreviewCard:408` “来源：sourceLabel / 范围：section.label”为只读行。sourceLabel 是 VM 的有限来源标签（`ContextTransferViewModel.kt:725`）：上下文数据包、Tavern 角色卡、Tavern 图片角色卡、独立世界书、SillyTavern 预设，**不是用户文件名**。
- noticeMessages 非空时显示完整“安全上限提示”HintCard（:414），每条以前缀圆点加入正文；这是决策信息，不收进帮助窗口。
- assistant/worldBook/memory/summary/preset 五类计数仅在 >0 时显示（:422–460）。摘要计数合并全文摘要和分段（VM :430）。
- conflicts 非空时标题给**全部冲突数量**，正文只取前 5 项“typeLabel / title”（:462–468），没有查看全部或逐项解决按钮。不能为长文本迁移添加“替换/跳过”选择器，也不能将 5 项上限改成任意全量。
- :470 “确认导入”与 :476 “取消”都在同一 LazyColumn item 尾部；isBusy 时两者禁用，确认文案变“处理中…”，取消文本仍是“取消”。没有第二道确认 Dialog。
- 冲突标题/安全提示来自真实预览字段，可构造带固定虚构长书名/角色名/冲突的 fixture；不要用不可能的超长 sourceLabel 冒称生产状态。

### 4. 实际窗口清单

| 入口 | 所有者/状态 | 关闭与捕获 |
| --- | --- | --- |
| intro 帮助，标题“管理 Narra 本地资源包” (:155) | SettingsPageIntro 非空 summary → SettingsHelpButton → 私有 SettingsDescriptionDialog | 与以下 5 个帮助共用 Material AlertDialog；showDescription 按 title/description saveable，知道了/返回/外点只关闭 |
| 资料库备份帮助 (:207/:208)、角色包帮助 (:222/:223)、世界书帮助 (:237/:238)、记忆档案包帮助 (:252/:253)、预设包帮助 (:267/:268) | TransferSectionGroup :366 的 SettingsHelpButton；每组现有 description 都非空 | 六个真实帮助入口都做行为覆盖；同一窗口实现的视觉可选长标题 intro 为代表，不必 6×主题全矩阵 |
| 选择导入来源 | :62 showImportSourceDialog；ALL/ASSISTANTS/WORLD_BOOK 三个入口复用 :320 的 ImportSourceChooserDialog | Material AlertDialog :325，标题“选择导入来源”；相册 :334 / 文件 :338 / 取消 :339。来源状态先在页面设好，按钮 :305/:311 先关闭窗口，再 launch |
| 选择/创建文档 | :68 CreateDocument、:90 OpenDocument | Android 系统 Activity / provider，不是 App Compose Dialog root；宿主测合同与返回，设备效果另列 |
| 选择相册图片 | :110 PickVisualMedia，:306 ImageOnly | 系统 Photo Picker 或平台回退；ImageOnly **并不只返回 PNG**，PNG 支持由 VM/adapter 验证，不要擅自过滤/转换选中图片 |

帮助窗口源码为 `SettingsComponents.kt:250`；本页有**两种 App 窗口实现（6 个帮助入口 + 1 个共用来源窗口）**，没有 ModalBottomSheet/DropdownMenu/Popup。入口取消只是关当前窗口，不清除可能已存在的导入预览。

独立 root 捕获借鉴 `ui/screenshot/ProviderSettingsRenderTest.kt:178` / :284，必须断言真实标题、Dialog root、不含 StageTag、关闭/各动作可见；窗口未打开时失败。系统 chooser 的底页 PNG 不能算作 chooser 截图。

### 5. 副作用、确认、失败与权限契约

| 阶段 | 现有事实 |
| --- | --- |
| 导出入口 | 先设置 pendingExportSection 并 launch CreateDocument；用户取消返回 null (:71) 立即 return，不调用 onExportJson/文件写入 |
| 导出成功结果 | :72 才调用 onExportJson(pendingExportSection)；VM :123 读取/过滤当前本地数据、codec.encode，再回调(json, fileName)。页面忽略回调的第二个文件名，因为 CreateDocument 已按 section 文件名启动 |
| 写 URI | :486 的 writeTextToUri 在 Dispatchers.IO 用 openOutputStream→bufferedWriter→use 写出；null 流报“无法写入导出文件”。成功/失败由页面直接向本地 SnackbarHost 发“上下文数据已导出”或经 SensitiveTextRedactor 的错误；不是 VM message |
| 导入入口 | 系统返回 null (:93/:113) 无读入/无 preview 回调。JSON 文件或相册非空 Uri 才在组合 scope 协程中 readImportPayload → onPreviewImportPayload(payload,pendingImportSection) |
| 读取 URI | :498 先 getType / query DISPLAY_NAME，再有界读 bytes；MIME=image/* 或文件名.png 走 binaryContent，否则 UTF-8 并去 BOM 走 textContent。查询无可用名字回退 uri.lastPathSegment (:526)。null 输入流、“>16MiB”、权限/IO异常走页面脱敏 Snackbar |
| VM 预览 | :156 busy=true/message=null；解码→section 过滤→normalizedForContextImport→空包检查→构建计数/冲突/notice。成功保留 pending bundle/avatar 并显示预览；失败清两种 pending、清预览、busy=false、写安全 message (:194) |
| 确认 | :208 无pending bundle时return。否则busy=true，runCatching只包applyImportedAssistantAvatar/mergeImportedData；该范围失败时busy=false + 错误message，保留preview/pending。成功分支先清pending，再调用refreshCounts(:217)并更新UI；refreshCounts异常不在前述失败恢复范围内 |
| 预览取消 | :236 清 pending bundle/avatar 与 importPreview，不写仓库，不同于“取消来源选择”或“系统 chooser 返回 null” |
| VM 通知消费 | 页面 :63 把全部 uiState.message 交 rememberSettingsSnackbarHostState，显示完回调 consumeMessage；不像世界书列表/详情按关键词过滤。页面直接 IO Snackbar 不通过此 VM 消费链 |
| 返回 | 顶栏/系统导航离开页面，没有专用 cancel job 或回滚；不保证停止已开始的 VM 合并/头像写入。禁止把返回描述为导入事务取消 |
| 权限 | 本页未 requestPermissions / RequestPermission / takePersistableUriPermission；使用所选 Uri 的 ContentResolver 能力并在 use 结束关流。`app/src/main/AndroidManifest.xml:6` 虽保留 WRITE_EXTERNAL_STORAGE(maxSdkVersion=28)，此页没有请求它的接线；不新增存储/媒体权限或持久授权 |

**isBusy 的范围不能扩大描述**：VM 的预览解析、导出生成、确认合并会置 busy；打开系统 chooser、页面 URI 读取、导出 callback 中另起的文件写入并未全程归入该状态。导出 VM 在 callback 返回后即 busy=false（:133–138），页面写文件可能仍在进行。此次只保持现有控件绑定，不新增“所有 IO 期间绝不允许操作”的状态机。

**仅记录的旧边界，非已复现 bug**：exportBundleJson (:127) 和 init/refreshCounts (:117/:314) 未有 try/finally 恢复；runCatching 路径也未专门区分 CancellationException。不能在验收中要求目前不存在的“任意生成失败都自动解锁/返回会无副作用取消”。如实际 UI 改动引出失败，先定位；改变这些策略须另行范围决定，不用测试桩掩盖事实。

### 6. 保留的格式、合并、媒体和预设契约

- `ContextDataBundle.kt:3` 仍为 version=1、本地六类数组（角色、世界书、记忆、摘要、摘要分段、预设），exportedAt 默认系统时间；不加入 Provider/API Key/settings 全对象。`ContextTransferCodec.kt:9` 使用 AppJson Gson，数组空值回退保持现状。
- VM 文本识别顺序 :469：非空 Narra 数据包 → SillyTavern 预设 → 独立世界书 → Tavern 角色 JSON；binary 走 TavernCharacterImageAdapter。来源标签来自识别结果；不能为 UI 简化改变顺序或识别格式。
- ASSISTANTS 对 Tavern JSON/PNG 特例附角色作用域世界书与挂载链接（:572/:624），原生备份导出 ASSISTANTS 不附这些世界书；WORLD_BOOK 从角色卡抽取时去掉角色，只保留世界书。不把两者做成相同导入动作。
- 预设导出仅非 builtIn（:254），导入 :617 将 preset 标记 builtIn=false/userModified=true；保留嵌入模板/未知字段的 adapter 行为。
- mergeImportedData (:258) 规范化后按现有仓储 upsert 世界书/记忆/摘要/分段/预设，数据库部分在 injected transaction (:265)；settingsEditor.saveAssistants (:283) 在该事务之后。角色按 ID 合并、忽略导入内置角色 ID（:350）；不宣称头像文件、DB 与 Settings 是一个可整体回滚的原子事务。
- 头像只在确认阶段保存（:332，先 apply 再 merge），预览阶段仅保留字节及 assistant 信息；真实 saver 由 NavigationViewModelOwners :198 指向 ImageFileStorage。测试必须以 lambda 替换真实媒体保存。
- 缺本地 conversation 的摘要分段由 existingConversationIdsProvider 决定过滤（:297），预览以 notice 明示将跳过（:437）；counts 在初始化及成功导入后刷新，不是全仓库实时观察流。
- 16MiB 文件上限、规范化字符/条数上限、ID 派生和 extras/未知字段均保持；当前世界书 500 条 / 标题与书名160字符等来自 `ContextImportNormalization.kt:8`，不是 UI 新阈值。

### 7. 局部视觉迁移与共享影响

- 主页面 :146 仍用 SettingsScreenPadding=20dp、4/32dp contentPadding；:152 item 间距16dp。TransferSectionGroup :355 / ImportPreviewCard :405 用18/16/10dp；改为显式 NarraUi/NarraShapes，不改共享历史默认。
- SettingsGroup 的两个重载已支持 shape/contentPadding（`SettingsComponents.kt:307` / :321），Divider :348、ListRow :360 也有局部间距/行数参数；直接消费这些接口即可，勿重新实现 Group。
- **只读内容颜色是独立风险**：本页 11 个 SettingsListRow 调用全部 enabled=false，共享实现 :383/:387 将文字置 body×0.65/0.6。它们的 onClick 都是 null，false 并没有添加 disabled 语义；可局部恢复 enabled 默认 true，继续 onClick=null/showArrow=false，得到正常阅读色而仍无点击动作。行为验收须断言没有 OnClick，不为修对比修改全局 disabled 颜色。
- SettingsHintCard 真正承载面是 palette.surface，标题/body 用 palette.title/body（:493–542）；传入 accentSoft/accent 只用于色条/圆点。不能以这两个参数作为正文对比度配对。
- SourceChooser :337 的 dismissButton 当前是“文件 + 取消”并排 Row；TransferSectionGroup :368 的导入/导出是两个等宽 Column。200%/窄屏和“处理中…”有拥挤的静态条件，可局部改可换行/纵列布局；不能只放大外层容器然后假定内部按钮达到48dp。
- Intro :155 的共享标题仍最多2行；帮助 :250 为旧8dp，普通 TextButton 默认primary。使用 P06 统一所有者补充的 default-compatible opt-in，传到真正窗口；不为资料库另建第二套帮助行为。
- 共用按钮、Help、PageIntro、SectionHeader、HintCard、Palette、Snackbar消费者见[列表/详情补充](entry-list-detail-validation.md)；SettingsGroup/Divider/ListRow全部直接消费者见[资料库验证补充](context-transfer-validation.md)。三个私有UI helper只在本文件使用。
- 拟登记源码守卫应覆盖 ContextTransferScreen 及这三个 UI helper；现存 IO 常量不是布局令牌，不顺带重构 IO。共有 SettingsComponents 的 legacy 声明/精确例外调整由同一所有者执行。

所有风险目前都来自源码，未运行截图证明裁切或对比度失败。


## 验证与补充资料

后续测试、截图建议、相关spec及消费者清单完整保存在[共享资料库验证与消费者](context-transfer-validation.md)；本文件与补充文件都纳入上下文，不丢失原有锚点或契约。
