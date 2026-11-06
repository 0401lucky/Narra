# Research: 共享资料库验证与消费者

- Query：延续[context-transfer.md](context-transfer.md)的验证、Fake、截图与消费者研究。
- Scope：internal，仅静态规划；完整路径缩写与主契约见该研究入口。
- Date：2026-11-05

### 8. 可行的测试隔离与耦合处理

**截图/普通行为优先直接渲染真实 Screen**。它已有完整 UiState+回调边界，无需新增领域接口或用户 AppGraph：

1. 使用既有 `ui/screenshot/ScreenshotStageTest.kt:55` 的普通 Application、API35/native graphics 与 :63 的 ComponentActivity。它不启动生产 ChatApplication/AppGraph。固定虚构 UiState / ContextImportPreview + 记录回调列表，图像本身不需要 VM。
2. rememberLauncherForActivityResult 是真实初始化依赖。建议测试专用 ActivityResultRegistryOwner，经 LocalActivityResultRegistryOwner 覆盖；onLaunch 记录真实 contract/input/requestCode，按需要 dispatch 合成 Uri/null。保持生产按钮→真实 launcher→真实 callback，不把 UI 改成跳过 chooser 的假回调来宣称文件流程通过。
3. 第一条 before 捕获先做单例宿主探针：证明来源窗口真实打开且没有外部 Activity、用户文件/网络副作用。本仓库 app/src/test 与 androidTest **未找到** ActivityResultRegistry/ShadowContentResolver/测试 ContentProvider 现成 fixture，此 test-only 设施需在实施阶段验证，不能现在标成已可运行。
4. 需要穿过 :498/:486 私有 IO helper 时，以 test-only ContentProvider/Resolver 内存 stream 给合成 content:// URI、MIME、DISPLAY_NAME 与空/异常流。这样可保持生产 IO 函数私有，无需为测试公开或挪到领域层。Robolectric 注册内存流或 provider 的具体方式需按本机依赖做小探针；不能回退打开用户 Downloads。
5. 本页没有 Clipboard 耦合，不沿用编辑页的复制/粘贴替身，也不读取系统剪贴板。仅有照片/文件 Uri；PNG fixture 沿既有测试构造字节，无需实际照片或图片 API。
6. VM 级接线可直接构造 `ContextTransferViewModel:90`：FakeWorldBookRepository/FakeMemoryRepository/FakeConversationSummaryRepository/FakePresetRepository + FakeSettingsStore 支撑的现有 services；注入 avatarSaver 记录参数后返回合成 URI、dataImportTransaction 记录/门控 block、existingConversationIdsProvider 固定集合。需要失败时可让 transaction 在 block 前抛合成异常，不假造跨DB/Settings/媒体的回滚保证。
7. `testutil/TestAiServices.kt:52` 默认 apiServiceProvider 为空会回落真实 ApiServiceFactory (:74)，stream factory (:81) 也不是隔离保障。新测试显式传不会联网的 TestOpenAiCompatibleApi 或 fail-fast providers，并断言调用次数=0；不要把“函数名含Test”当网络隔离。`ProviderPersistenceTest.kt:26` 的 gate/计数/委托编辑器模式可复用，资料库无需 SettingsViewModel 或全量生产 AppGraph。
8. 原导航完全未改时，最小回归由真实入口 Screen 的 callback/section 合同 + 静态 registerSettingsDataRoutes 接线核对组成；不能用手抄 NavHost 称为生产 AppGraph 集成测试。如另有导航改动，才为该 UI 路由提供局部 test seam，不扩建领域 DI 体系。

### 9. 现有测试与缺口

- `viewmodel/ContextTransferViewModelTest.kt`：16 个 JVM 测试，使用 MainDispatcherRule、Fake 仓库与本地 PNG 构造。
- :58 合并角色/世界书/记忆/摘要；:144 缺会话分段跳过；:205 原生包规范化。
- :296 / :332 / :384 为角色/世界书/记忆导出筛选；:430 自定义预设来回转换；:492 SillyTavern 预设。
- :543 冲突和 Tavern 来源；:598 / :633 普通及 bracket-wrapped PNG；:671 Tavern 角色包附世界书；:736 头像与世界书；:799 / :851 独立世界书及 attachable；:900 WORLD_BOOK 从 PNG 抽取。
- 私有测试工厂 :949 直接 new VM，支持 Fake repo/avatarSaver/existingConversationIdsProvider；不调用 AppGraph；默认 transaction 只是运行 block，不是生产数据库事务证明。
- 已有底层测试：`data/repository/context/ContextTransferCodecTest.kt:18` 编解码；TavernCharacterAdapterTest 4 项、TavernCharacterImageAdapterTest 2 项、TavernPresetAdapterTest 3 项、TavernWorldBookAdapterTest 8 项。可原样作为格式/媒体/预设回归，而非 UI 证据。
- 未找到直接渲染 ContextTransferScreen 的 UI/截图测试，未找到其 ActivityResult/ContentResolver helper 测试。上述 VM 测试未覆盖本页真正点击来源、系统取消、预览取消、busy UI 触控、URI 权限失败或 200% 布局。
- 初始数据读取/导出生成异常未有完整恢复实现；最小视觉验收不应新增要求它满足一个新状态机。预览解析失败与确认合并失败的现有分支可用 Fake 验证。
- 共享帮助/Group/顶栏/AlertDialog 回归见 entry-list-detail 的测试清单；140% 设备帮助测试不能移称为此次 200% 通过。

### 10. 最小验收矩阵（合并/媒体/预设协议不改）

| 类别 | 最小行为与判定 |
| --- | --- |
| 真实入口 | “我→资料导入导出”和列表“导入世界书”各只发一次正确导航回调；进入页面不自动 launch chooser、不预选 WORLD_BOOK |
| 所有 section | 5 个导出均启动 CreateDocument 且文件名匹配 enum；3 组导入先来源窗，MEMORY/PRESETS 直接 OpenDocument；动作必须绑定具体组，不能用全页第一个“导入”代替 |
| 来源窗口 | 三个 section 的相册/文件路线保持 pendingImportSection；相册为 ImageOnly，文件 MIME=application/json,text/plain；窗口先关闭再 launch；取消/返回/外点没有 import/export/仓库/媒体调用 |
| 系统 null 结果 | 三类 launcher 返回 null 均无 IO/preview/export；旧预览不因 chooser 取消被清空 |
| 一条真实 Uri 接线 | JSON/BOM 和本地构造 PNG 各一次，通过 test-only URI 走生产 helper，得到正确 text/binary payload 与 section；导出只写受控 output stream，成功/失败文案和回调分层不串 |
| 权限/IO错误 | 至少一条读/写权限或空流失败使用合成 exception，安全提示可读且无真实数据写入；不把错误详细路径/完整 payload 放入截图或日志。若 IO helper 没改，不强制重写全部16MiB/BOM算法测试 |
| 预览不写 | 真 VM + Fake 调 preview，仓库 upsert/avatarSaver 均0；计数、来源/范围、notice、总冲突数与前5项正确，零类目不出现 |
| 取消与确认 | 取消清preview/pending，随后confirm是no-op；确认成功按现有协议写相应类型并刷新计数。仅对头像应用/merge范围内的被捕获失败断言busy=false、preview/pending保留；成功分支refreshCounts异常不在该恢复保证内 |
| busy | 用已有 transaction/saver gate 挂起确认：5组导出/导入与预览确认/取消均禁用，重复真实触控无第二提交；不承诺 busy 遮住系统 chooser/页面读写全程，也不把返回当取消 |
| 格式与媒体 | 保留现有16项VM及adapter回归；重点守 ASSISTANTS Tavern 附世界书、WORLD_BOOK PNG只提取世界书、头像仅确认时保存、MEMORY包含分段、PRESETS不覆盖内置身份 |
| 六个帮助入口 | intro与5组逐个真实打开并校验正确标题/正文、独立root和关闭；全程没有导入/导出/preview消费副作用 |
| a11y | 8组真实scheme查实际文字/承载面≥4.5:1；320×640dp/200% 顶部计数、首个及末个资源组、预览首段/末端确认取消、三按钮来源窗都可到达；真实点击node宽高≥48dp、相邻操作不串。只读信息行无OnClick，不伪装为禁用按钮 |

对“头像应用/合并受捕获失败保预览”的新增用例是验证已存在路径；对成功合并后refreshCounts异常或任意导出生成失败的自动恢复，源码没有统一保证，不能混写。

### 11. 截图代表态建议

为配合一个P06任务收敛总预算，资料库建议以 **16 张候选起点**，再由统一矩阵合并同容器代表态；不是本研究已运行数量。

| 维度 | 候选 | 内容 |
| --- | --- | --- |
| 三主题浅深 | 6 | 常规概览，五类非零计数与真实页面顶区；固定虚构数据 |
| 固定动态浅深 | 2 | 真实预览状态，信息行/notice/冲突容器的实际配色 |
| 320×640dp/200% 代表 | 4 | 首个资源组动作（浅）、末个资源组动作（深）、预览首段notice（浅）、预览末端冲突+确认取消（深） |
| 来源 Dialog 浅深 | 2 | 200%，同时验证相册/文件/取消的完整布局和真实root |
| 共用帮助代表浅深 | 2 | 200%，选择 intro 长标题与真实说明；其他五个帮助入口走逐个行为回归 |

全部页面/窗口的八种 scheme 颜色与两种深浅200%行为需覆盖，不能把代表PNG说成逐页全笛卡尔积。busy/零计数/无冲突/仅单一资源类型优先行为验证；若测出独特布局差异，再有依据补候选。

末端截图要使最后资源组**两个按钮完整触控区**可见，预览最后确认/取消完整可见；同时记录真实滚动位置/max、unclipped 与 touch bounds。不能只 assertIsDisplayed 某段“预设”文字。首段与尾段不能用更短 fixture 偷换数据。

使用普通Application宿主、无用户文件/数据库/网络、固定时间/Locale/时区。App 窗口先证明独立 root，OS chooser 单独说明未被宿主捕获。先真实 before，再同参数 after；计数/缺图/零像素与人工接纳规则保持，不向旧基准目录 record。

### 12. 实施所有权与回退形状

- 保留一个 P06，按共享接入→列表/详情→编辑→共享资料库的顺序检查点；由统一所有者维护 SettingsComponents、源码清单、ScreenshotCases 和基准接纳记录。
- 资料库 UI 所有者负责 ContextTransferScreen 及必要的小型 UI 组合；VM、Codec/adapter、normalization、AppGraph/媒体保存/系统权限默认只读。test-only registry/provider/Fake 局部放测试源集。
- 回退为撤回本页 opt-in/布局及对应候选，不动用户资源包、数据格式、权限或合并记录；不要把“回退 UI”承诺成“撤销已发生的导入”。
- 若真实验证发现只能改变读写、合并或取消策略才能满足额外目标，记录具体触发与新范围；不要借此P06外观任务隐式进入领域修复。

## Related Specs

- `.trellis/workflow.md`：仍处于 planning，未 start。
- `.trellis/spec/backend/ui-token-contract.md`：实际 Material 配对、局部 opt-in、48dp、首末项、真实独立窗口和接纳。
- `.trellis/spec/backend/ui-state-guidelines.md`：Screen 回调边界、图级 VM、一次性消息/取消。
- `.trellis/spec/backend/database-guidelines.md` / `error-handling.md`：仓储写入/规范化边界、URI/文件失败、异常脱敏；记录旧差异，不能从规范推断代码已有保证。
- `.trellis/spec/backend/quality-guidelines.md`、`guides/code-reuse-thinking-guide.md`：Fake隔离、最小定向测试、不扩全局默认或重复领域接口。
- [列表与详情研究](entry-list-detail.md)：入口链、共用 UI 所有者与完整消费者。

## External References / Versions

未访问外网。版本同 entry-list-detail：本地 Compose BOM 2026.02.01 / Navigation 2.9.7 / Lifecycle 2.9.1 / Robolectric 4.17 / Roborazzi 1.76.0。ActivityResultRegistry/测试ContentProvider方案是根据现有AndroidX调用提出的测试宿主方案，尚未运行探针；不声称平台版本表现已验证。

## Caveats / Not Found

- 只读研究与本文件落盘；未运行测试、Gradle、App、真实chooser、git或网络，没有接触用户文件/数据库/剪贴板。
- 16个现有VM测试不是文件选择器/权限/视觉通过证据；本次没有当前运行结果。
- 没有找到现成ActivityResult/ContentResolver测试隔离设施；实施前捕获探针若不能可靠拦截，需先完成test-only设施，不能构造真实用户AppGraph作为捷径。
- 既有isBusy、导出异常与返回取消范围如上，不承诺未实现的事务回滚、全流程防重或新错误恢复。
- 系统文档/相册picker与IME/系统栏真实效果仍需单列设备证据，宿主PNG不替代。

## 补充：Group / Divider / ListRow 全部直接消费者

路径相对 `app/src/main/java/com/example/myapplication/`；其余共享API完整清单见 entry-list-detail.md。范围为 app/src/main 显式调用，剔除声明/import/注释；含无括号的 SettingsGroup trailing-lambda 调用。

- **SettingsGroup**：19 个文件 / 62 处。`ui/screen/diagnostics/DiagnosticsScreen.kt:122,151,188`；`ui/screen/roleplay/RoleplayScenarioEditScreen.kt:228,282,319,349,389,434,464,534,566,605`；`ui/screen/settings/AssistantBasicScreen.kt:119,271,323`；`ui/screen/settings/AssistantDetailScreen.kt:122,180,255`；`ui/screen/settings/AssistantExtensionsScreen.kt:146,178,252,300,359`；`ui/screen/settings/AssistantMemoryScreen.kt:177,237,271`；`ui/screen/settings/CharacterArtStudioScreen.kt:244,581,714`；`ui/screen/settings/contextlog/ContextLogListContent.kt:95,118`；`ui/screen/settings/ContextTransferScreen.kt:164,353,403`；`ui/screen/settings/RoleplayScriptLabScreen.kt:155,258,302,518`；`ui/screen/settings/ScreenTranslationSettingsScreen.kt:133,194,232`；`ui/screen/settings/SearchToolSettingsScreen.kt:86,114,214`；`ui/screen/settings/SettingsComponents.kt:316`；`ui/screen/settings/SettingsModelScreen.kt:129`；`ui/screen/settings/SettingsScreen.kt:337`；`ui/screen/settings/UserPersonaMasksScreen.kt:115`；`ui/screen/settings/VoiceRecognitionApiScreen.kt:110,186,222,292`；`ui/screen/settings/VoiceRecognitionSettingsScreen.kt:85,102,156`；`ui/screen/settings/VoiceSynthesisSettingsScreen.kt:158,318,381,1156,1193,1219`
- **SettingsGroupDivider**：13 个文件 / 54 处。`ui/screen/diagnostics/DiagnosticsScreen.kt:129,141`；`ui/screen/roleplay/RoleplayScenarioEditScreen.kt:368,372,578,593,700,740,780,796`；`ui/screen/settings/AssistantDetailScreen.kt:128,136,146,158,165,190,200,211,223,271,290`；`ui/screen/settings/AssistantMemoryScreen.kt:183,189,204`；`ui/screen/settings/contextlog/ContextLogListContent.kt:137`；`ui/screen/settings/ContextTransferScreen.kt:171,178,185,192`；`ui/screen/settings/RoleplayScriptLabScreen.kt:169,280,362,387,575`；`ui/screen/settings/ScreenTranslationSettingsScreen.kt:145,162,176,202,210,218`；`ui/screen/settings/SettingsScreen.kt:385`；`ui/screen/settings/UserPersonaMasksScreen.kt:132`；`ui/screen/settings/VoiceRecognitionApiScreen.kt:143,253`；`ui/screen/settings/VoiceRecognitionSettingsScreen.kt:136`；`ui/screen/settings/VoiceSynthesisSettingsScreen.kt:170,209,257,274,290,324,1188,1230,1238`
- **SettingsListRow**：14 个文件 / 46 处。`ui/screen/diagnostics/DiagnosticsScreen.kt:123,130,142,152,189`；`ui/screen/roleplay/RoleplayScenarioEditScreen.kt:320,374,435`；`ui/screen/roleplay/RoleplaySettingsSections.kt:962,975,988`；`ui/screen/settings/AssistantDetailScreen.kt:256,275`；`ui/screen/settings/contextlog/ContextLogListContent.kt:124`；`ui/screen/settings/ContextTransferScreen.kt:165,172,179,186,193,408,423,431,439,447,455`；`ui/screen/settings/PresetManagerScreens.kt:332,469`；`ui/screen/settings/RoleplayScriptLabScreen.kt:231,260`；`ui/screen/settings/ScreenTranslationSettingsScreen.kt:233,372`；`ui/screen/settings/SettingsComponents.kt:623`；`ui/screen/settings/SettingsScreen.kt:367`；`ui/screen/settings/VoiceRecognitionApiScreen.kt:355`；`ui/screen/settings/VoiceRecognitionSettingsScreen.kt:137,157`；`ui/screen/settings/VoiceSynthesisSettingsScreen.kt:258,275,319,382,687,741,748,1158,1194,1220`

