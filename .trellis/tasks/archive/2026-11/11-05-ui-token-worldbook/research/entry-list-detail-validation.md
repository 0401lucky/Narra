# Research: 列表/详情验证与共享消费者

- Query：延续[entry-list-detail.md](entry-list-detail.md)的验证、Fake、截图与消费者研究。
- Scope：internal，仅静态规划；完整路径缩写与主契约见该研究入口。
- Date：2026-11-05

### 7. 现有测试与缺口

- `ui/screen/settings/worldbook/WorldBookListScreenGroupingTest.kt`：3 个纯 JUnit 测试（:10/:57/:95），覆盖显式 ID 分组及条目顺序、空名称但有 ID 仍分组、ID/名称都空不分组。名称 groupsByBookName 是历史测试名，不代表实现按书名分组。未直接渲染列表。
- `WorldBookListStyleTest.kt`：24 个纯 JUnit 测试；书脊色 3 个（无生产可见消费者）、关键词 6 个（:45 起）、相对时间 7 个（:87 起）、筛选及 count 8 个（:161 起）。筛选测试包含空筛选、标题/关键词匹配、GLOBAL、启用/常驻、aliases 正则、派生 bookId 与 AND；测试名称所称“扫描全部字段”不等于逐字段隔离覆盖，当前也没有实际筛选 chip 点击、状态恢复或截图。
- `viewmodel/WorldBookViewModelTest.kt:20` / :62 / :103 / :122 / :141 / :161 / :176 / :196：8 个 VM 测试覆盖整书重命名/删除、失败消息、仓库流与消息关键词；不能证明页面显示对应消息、isSaving 下控件命中或导航何时 pop。VM 实现细节另由编辑研究处理。
- 领域兼容测试存在 `data/repository/context/WorldBookRepositoryTest.kt`、`TavernWorldBookAdapterTest.kt`、`context/WorldBookMatcherTest.kt`；外观改造不需借机重写其算法或增加镜像实现测试。
- `ui/screen/settings/SettingsHomeEntryTest.kt:14` 实际点击的是手机“设置”；worldbooks 在 :23 只是防串回调配置，**没有验证点击世界书**。可在相同纯 UI host 模式补真实 Profile 世界书入口断言。
- 设备 `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/SettingsComponentsTest.kt:30` 仅用 320dp 宽/140% 字体，测帮助入口高度与关闭、不串行点击；不是 P06 200%/真实触控宽高/滚尾证据。设备 `ui/component/NarraAlertDialogTest.kt:59`、:124/:158/:188 覆盖长内容、禁用、返回、外点；默认兼容可复用。
- 宿主 `ui/screen/settings/ProviderSharedDialogCompatibilityTest.kt:21` / :53 已覆盖共享默认/显式动作色、破坏性色优先级、形状/取消；`SettingsHomeSharedCompatibilityTest.kt:27` / :68 / :96 覆盖 Group 透传、旧行限行和顶栏显式换行，适合补 P06 新 opt-in 的默认兼容断言。
- 本次搜 `WorldBookListScreen(`、`WorldBookBookDetailScreen(`、worldbook_list/worldbook_detail、P06/E06：未找到直接页面 UI/导航/截图测试，也未在截图登记表或源码守卫找到世界书目标声明。不能把 3+24 个纯函数测试称为 UI/a11y 覆盖。

### 8. 列表/详情候选截图建议（等待实施前冻结）

不做所有维度笛卡尔积。一个可控的起点是 **27 张列表/详情候选**，编辑与共享资料库另外登记，不能用此数量概括整项 P06。

| 维度 | 候选数 | 场景与必须证明 |
| --- | --- | --- |
| 列表常规三主题 × 浅深 | 6 | MATCHA/VANILLA/OCEAN，混合书目与独立条目、启用/停用/正则/常驻标签 |
| 详情常规浅深 | 2 | 书名、计数、管理入口、共享条目卡；三主题全面颜色检测由行为/像素测试补足，不宣称这 2 张覆盖六种截图 |
| 固定动态浅深：列表及详情 | 4 | 复用生产 resolveAppColorScheme 路径；包含选中 chip/字段 focus 等实际强调色组合 |
| 320×640dp / 200%，浅深 | 8 | 列表筛选展开首项、列表末项、详情管理展开含保存/删除、详情最后条目，各 2 张 |
| 三类列表空态 | 3 | 全局空、搜索无结果、筛选无结果各 1 张，主题/明暗分散；清理动作差异走行为 |
| 两类真实窗口，320×640dp / 200%，浅深 | 4 | 列表帮助；长真实书名整书删除确认；断言独立 Dialog root 和关闭/取消/确认完整可见 |

- 若最终要求详情也有完整三主题浅深图片，则增加 4 张为 31；这是截图预算选择，不增加产品行为。固定动态方案不能直接用系统动态色或随机输入。
- 宿主既有规范：`ui/screenshot/ScreenshotCases.kt:35` 常规 360×800dp、窄屏 :37 为 320×640dp；`ScreenshotFixture.kt:34` 中文环境、density=2、fontScale=1/2。复用这些参数及当前 API/graphics 配置。
- 正文/关键词/书名/ID/时间用固定虚构 fixture；实体默认时间不要意外取到系统当前值。窗口标题/书名使用生产数据拼接文案，不能换成测试专用窗口。
- `ProviderSettingsRenderTest.kt:178` 的 windowMatcher/windowRoot 可借鉴：必须匹配独立 root、真实标题、isDialog 且不含 StageTag；未打开窗口时应失败。直接捕获方式 :284；列表帮助文案若本来不够长，不声称已滚尾，合成长文在兼容行为测试单列。
- 末项必须绑定具体 bookId/entryId，测 unclippedBounds 与 touchBounds 全部在实际列表视口内，不能仅滚到同名 Text。参考同文件 :195 的 scroll value/max、:208 的完整边界和 :223 的真实 48dp；长卡可高于视口，关键操作区应单独可达，不以强行缩字体换取“整卡可见”。
- 源码修改前先补真实捕获 fixture 并取得 before；after 保持相同窗口/滚动/数据策略。旧图哈希冻结，新增 missing baseline/计数守卫保持失败，人工接纳前不 record。现有 158 PNG / 159 截图 / 1826 JVM 是前序结果，不能折算为本轮证据。

### 9. 最小行为验收

1. 真实“我 → 世界书”只发该回调；列表书卡/独立条目/顶栏新增/导入/返回分别只发正确参数。至少用同名不同 ID、空书名但有 ID、独立条目 fixture 验证不会串路由。
2. 搜索覆盖标题、正文、书名和三类关键词；三维 AND、横向滚动末项、清筛选保留搜索、搜索空态只清搜索；展开/收起和 Saveable 恢复不改数据。状态筛选不触发启用切换。
3. 从只命中部分条目的书卡进入详情，列表计数按命中，详情恢复全书；保留既有排序/keys。不要把纯函数断言当点击回归。
4. 详情管理展开/收起、空白/同名/trim 后书名、IME Done/按钮同参数提交、isSaving 禁用保存和删除，角色/其他书条目不受影响；书名上游回传后 UI 同步。
5. 删除：真实Screen回调与Fake验证入口不调用delete、确认只提交目标bookId、取消仅关闭；静态核对生产路由在entries变空后pop、失败且数据未变时不pop的接线。默认不为本批构造用户AppGraph或重写导航DI，不把仿写NavHost称生产装配测试；系统返回/外点实际效果另留设备证据。
6. 列表只显示删除类消息，详情只显示重命名类，其他包括 entry 保存消息在原延时消费；验证迁移没有新增错误提示、重复消费或重放提交。
7. 三主题浅深与固定动态浅深共 8 组实际 scheme 测颜色；320×640dp/200% 实测标题、清空/筛选/chip/帮助/卡片/管理/保存/删除/窗口动作真实触控宽高≥48dp、相邻命中、首末操作可达。只读状态标签无需强行扩大为 48dp 按钮。
8. 共享新增参数的默认/显式分支与转发均验证；已有共享截图保持零像素一致。资料库入口不预选 WORLD_BOOK；资料库全页行为/系统选择器由独立研究矩阵补齐。

上述是实施后建议验证；本研究未执行任何测试或 Gradle。

### 10. 检查点与范围建议

可以继续一个 P06 任务，分可审阅检查点：先冻结 fixture/before 与共享 opt-in 接口，再列表/详情，再编辑，再共享资料库，最后整合共享默认回归、导航/Fake 行为、截图接纳。共享 SettingsComponents 由一人持有，页面可按文件分工，列表分组纯函数/导航/存储协议默认只读。

用户已补充选择“同时迁移共享数据页”，所以资料库全部可见分组/预览/来源弹窗的影响不能只按 WORLD_BOOK 部分验收；其额外矩阵和所有权见单独 `context-transfer.md`。仍未请求角色扩展页或主壳联系人 Sheet 的视觉重做；这些只保入口和共享默认兼容。新增复制/批选/排序操作、修复历史消息策略、变更书 ID 归属或导入格式均属于新产品/数据范围，不包含在外观迁移中。

## Related Specs

- `.trellis/workflow.md:293` / :308：planning 研究落盘，任务创建批准不等于实施批准；本轮没有 start。
- `.trellis/spec/backend/index.md`、`directory-structure.md`：Screen → Navigation/VM → Repository 边界。
- `.trellis/spec/backend/ui-token-contract.md`：局部 opt-in、历史默认兼容、实际配色、48dp、首末项、窗口与 before/after 接纳。
- `.trellis/spec/backend/ui-state-guidelines.md`：真实路由、生命周期收集、Saveable 状态与 Dialog 取消契约。
- `.trellis/spec/backend/quality-guidelines.md`：Fake、截图登记/缺图/计数/零像素、宿主与设备/CI 区分。
- `.trellis/spec/guides/index.md`、`code-reuse-thinking-guide.md`：共享入口/所有消费者与小范围组件复用。
- `.trellis/tasks/archive/2026-11/10-21-ui-token-accessibility-expansion/research/coverage-and-queue.md`：P06 队列来源；历史数量仅为当时记录。

## External References / Versions

本研究未访问外网。版本依据本仓库 `gradle/libs.versions.toml`：AGP 9.1.0 (:2)、Compose BOM 2026.02.01 (:9)、Navigation Compose 2.9.7 (:11)、Lifecycle 2.9.1 (:12)、Roborazzi 1.76.0 (:31)、Robolectric 4.17 (:32)。平台组件行为仅按当前调用和项目既有测试研究，没有把外部最新文档当已验证事实。

## Caveats / Not Found

- 本轮只做源码阅读与研究文档；没有运行 App、截图、测试、Gradle、git、真实数据或网络操作。所有布局/对比度“风险”均为待测静态依据。
- 未找到 P06 真实页面/窗口视觉基准；现有纯函数、VM 和共享组件测试不能代表页面已符合 200% / 48dp。
- OS 文档/照片选择器、IME、物理系统栏/设备表现不能用 Compose 宿主截图替代。
- 仅世界书编辑入口签名/路由在此文件中划界；不重复另一研究的表单字段与保存实现。
- 以下完整调用清单是 app/src/main 下显式直接调用；剔除声明、import、注释，保留共享包装器。通过 SettingsGroup/SettingsPageIntro 等包装器间接消费的页面沿表中包装器追踪；没有把生产签名或未使用 import 算为消费者。

## 共享消费者完整清单

路径相对 `app/src/main/java/com/example/myapplication/`；相同行内逗号表示多个调用行。此清单保留本次研究时的源码行号；接口调整后由实施者重扫。

- **SettingsTopBar**：29 个文件 / 30 处。`ui/screen/diagnostics/DiagnosticsScreen.kt:92`；`ui/screen/roleplay/RoleplayScenarioEditScreen.kt:199`；`ui/screen/roleplay/RoleplayScenarioListScreen.kt:93`；`ui/screen/settings/AppUpdateScreen.kt:72`；`ui/screen/settings/AssistantBasicScreen.kt:93`；`ui/screen/settings/AssistantDetailScreen.kt:94`；`ui/screen/settings/AssistantExtensionsScreen.kt:112`；`ui/screen/settings/AssistantListScreen.kt:62`；`ui/screen/settings/AssistantMemoryScreen.kt:138`；`ui/screen/settings/AssistantPromptScreen.kt:160`；`ui/screen/settings/CharacterArtStudioScreen.kt:150`；`ui/screen/settings/ContextTransferScreen.kt:132`；`ui/screen/settings/memory/MemoryManagementScreen.kt:216`；`ui/screen/settings/memory/SimpleMemoryEditorScreen.kt:81`；`ui/screen/settings/PresetManagerScreens.kt:177,414`；`ui/screen/settings/ProviderDetailScreen.kt:72`；`ui/screen/settings/ProviderSettingsScreen.kt:162`；`ui/screen/settings/RoleplayScriptLabScreen.kt:93`；`ui/screen/settings/ScreenTranslationSettingsScreen.kt:103`；`ui/screen/settings/SearchToolSettingsScreen.kt:54`；`ui/screen/settings/SettingsModelScreen.kt:104`；`ui/screen/settings/SettingsScreen.kt:155`；`ui/screen/settings/UserPersonaMasksScreen.kt:76`；`ui/screen/settings/VoiceRecognitionApiScreen.kt:80`；`ui/screen/settings/VoiceRecognitionSettingsScreen.kt:56`；`ui/screen/settings/VoiceSynthesisSettingsScreen.kt:129`；`ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt:90`；`ui/screen/settings/worldbook/WorldBookEditScreen.kt:176`；`ui/screen/settings/worldbook/WorldBookListScreen.kt:140`
- **SettingsPageIntro**：9 个文件 / 10 处。`ui/screen/diagnostics/DiagnosticsScreen.kt:115`；`ui/screen/settings/ContextTransferScreen.kt:155`；`ui/screen/settings/memory/MemoryManagementScreen.kt:241`；`ui/screen/settings/PresetManagerScreens.kt:198,444`；`ui/screen/settings/RoleplayScriptLabScreen.kt:120`；`ui/screen/settings/ScreenTranslationSettingsScreen.kt:124`；`ui/screen/settings/SearchToolSettingsScreen.kt:77`；`ui/screen/settings/SettingsModelScreen.kt:123`；`ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt:114`
- **SettingsHelpButton**：17 个文件 / 26 处。`ui/component/ContextGovernanceSheet.kt:211,274`；`ui/screen/diagnostics/DiagnosticsScreen.kt:156,200`；`ui/screen/moments/MomentsScreen.kt:594,1343`；`ui/screen/phone/PhoneCheckScreen.kt:299`；`ui/screen/roleplay/RoleplaySettingsContent.kt:435`；`ui/screen/settings/AssistantDetailScreen.kt:492`；`ui/screen/settings/AssistantPromptScreen.kt:429`；`ui/screen/settings/ContextTransferScreen.kt:366`；`ui/screen/settings/memory/MemoryManagementScreen.kt:1058,1090,1141,1199,1278`；`ui/screen/settings/RoleplayScriptLabScreen.kt:736`；`ui/screen/settings/ScreenTranslationSettingsScreen.kt:384`；`ui/screen/settings/SettingsComponents.kt:220,301`；`ui/screen/settings/VoiceRecognitionApiScreen.kt:134,212`；`ui/screen/settings/VoiceSynthesisSettingsScreen.kt:224`；`ui/screen/settings/worldbook/WorldBookEditScreen.kt:611`；`ui/screen/settings/worldbook/WorldBookEditSections.kt:114`；`ui/screen/settings/worldbook/WorldBookListScreen.kt:224`
- **SettingsSectionHeader**：18 个文件 / 41 处。`ui/screen/diagnostics/DiagnosticsScreen.kt:120`；`ui/screen/roleplay/RoleplayScenarioEditScreen.kt:222,276,343,383,428,458,528,560`；`ui/screen/settings/AppUpdateScreen.kt:226,249`；`ui/screen/settings/AssistantDetailScreen.kt:176`；`ui/screen/settings/AssistantExtensionsScreen.kt:257,305`；`ui/screen/settings/AssistantPageComponents.kt:68`；`ui/screen/settings/contextlog/ContextLogListContent.kt:89`；`ui/screen/settings/ContextTransferScreen.kt:162,203,283`；`ui/screen/settings/memory/SimpleMemoryEditorScreen.kt:130,158`；`ui/screen/settings/PresetManagerScreens.kt:683,783`；`ui/screen/settings/RoleplayScriptLabScreen.kt:126,149,176,198`；`ui/screen/settings/ScreenTranslationSettingsScreen.kt:131,192,230,244,279`；`ui/screen/settings/SearchToolSettingsScreen.kt:83,144`；`ui/screen/settings/SettingsComponents.kt:315`；`ui/screen/settings/UserPersonaMasksScreen.kt:109`；`ui/screen/settings/VoiceSynthesisSettingsScreen.kt:370`；`ui/screen/settings/worldbook/WorldBookEditScreen.kt:280,471`；`ui/screen/settings/worldbook/WorldBookListScreen.kt:294,316`
- **SettingsStatusPill**：11 个文件 / 49 处。`ui/screen/roleplay/RoleplayScenarioListScreen.kt:163,309,359,367,374,381`；`ui/screen/roleplay/RoleplaySettingsSections.kt:914,923`；`ui/screen/settings/AssistantDetailScreen.kt:266,285,421,523`；`ui/screen/settings/AssistantListScreen.kt:210,217`；`ui/screen/settings/AssistantMemoryScreen.kt:534,539,544,549,554,559,687,693,762,768,773,783`；`ui/screen/settings/memory/MemoryManagementScreen.kt:742,748,754,761,767,773,865,871,878`；`ui/screen/settings/PresetManagerScreens.kt:474`；`ui/screen/settings/RoleplayScriptLabScreen.kt:244,497,619,721`；`ui/screen/settings/ScreenTranslationSettingsScreen.kt:348`；`ui/screen/settings/UserPersonaMasksScreen.kt:230,237`；`ui/screen/settings/worldbook/WorldBookListScreen.kt:440,450,525,540,545,550`
- **SettingsHintCard**：6 个文件 / 10 处。`ui/screen/settings/AssistantMemoryScreen.kt:314,342,370,389`；`ui/screen/settings/ContextTransferScreen.kt:415,463`；`ui/screen/settings/memory/MemoryManagementScreen.kt:248`；`ui/screen/settings/ScreenTranslationSettingsScreen.kt:250`；`ui/screen/settings/SettingsComponents.kt:556`；`ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt:167`
- **AnimatedSettingButton**：13 个文件 / 29 处。`ui/screen/diagnostics/DiagnosticsScreen.kt:167`；`ui/screen/roleplay/RoleplayScenarioEditScreen.kt:610,661,1121`；`ui/screen/roleplay/RoleplaySettingsSections.kt:1019,1029`；`ui/screen/settings/AppUpdateScreen.kt:192,216`；`ui/screen/settings/AssistantBasicScreen.kt:328,353`；`ui/screen/settings/AssistantExtensionsScreen.kt:364,370`；`ui/screen/settings/AssistantMemoryScreen.kt:242,260`；`ui/screen/settings/ContextTransferScreen.kt:373,382,470,476`；`ui/screen/settings/ScreenTranslationSettingsScreen.kt:261,269,289,295,301`；`ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt:148,154`；`ui/screen/settings/worldbook/WorldBookContentFullscreenSheet.kt:81`；`ui/screen/settings/worldbook/WorldBookEditScreen.kt:523,556`；`ui/screen/settings/worldbook/WorldBookListScreen.kt:164`
- **rememberSettingsPalette**：37 个文件 / 113 处。`ui/component/worldbook/KeywordChipInput.kt:130`；`ui/screen/diagnostics/DiagnosticsScreen.kt:76`；`ui/screen/roleplay/RoleplayScenarioEditScreen.kt:105,848,902,962,1035`；`ui/screen/roleplay/RoleplayScenarioListScreen.kt:74,135,208`；`ui/screen/settings/AppUpdateScreen.kt:47`；`ui/screen/settings/AssistantBasicScreen.kt:62`；`ui/screen/settings/AssistantDetailScreen.kt:74,315,392,449,471,507`；`ui/screen/settings/AssistantExtensionsScreen.kt:42`；`ui/screen/settings/AssistantListScreen.kt:57`；`ui/screen/settings/AssistantMemoryScreen.kt:89,508,573,590,630,658,709,738`；`ui/screen/settings/AssistantPageComponents.kt:25,73`；`ui/screen/settings/AssistantPromptScreen.kt:77`；`ui/screen/settings/CharacterArtStudioScreen.kt:95,359,498,579,681,713`；`ui/screen/settings/ContextTransferScreen.kt:59,402`；`ui/screen/settings/memory/MemoryManagementScreen.kt:137,633,711,835,1024,1118,1258`；`ui/screen/settings/memory/SimpleMemoryEditorScreen.kt:75`；`ui/screen/settings/PresetManagerScreens.kt:115,324,398,627,778`；`ui/screen/settings/ProviderConfigTab.kt:83,368`；`ui/screen/settings/ProviderDetailChrome.kt:58,138,180`；`ui/screen/settings/ProviderDetailScreen.kt:63`；`ui/screen/settings/ProviderModelDialogs.kt:74,313,547`；`ui/screen/settings/ProviderModelTab.kt:52`；`ui/screen/settings/ProviderSettingsScreen.kt:123,278,303,467`；`ui/screen/settings/RoleplayScriptLabScreen.kt:85,230,265,301,423,464,517,604,659,702,753`；`ui/screen/settings/ScreenTranslationSettingsScreen.kt:77`；`ui/screen/settings/SearchToolSettingsScreen.kt:42`；`ui/screen/settings/SettingsComponents.kt:133,202,255,282,326,351,377,452,499,595,716`；`ui/screen/settings/SettingsModelScreen.kt:70,346`；`ui/screen/settings/SettingsScreen.kt:116,395,494,654`；`ui/screen/settings/UserPersonaMasksScreen.kt:68,188,293`；`ui/screen/settings/VoiceRecognitionApiScreen.kt:69`；`ui/screen/settings/VoiceRecognitionSettingsScreen.kt:46`；`ui/screen/settings/VoiceSynthesisSettingsScreen.kt:108`；`ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt:60`；`ui/screen/settings/worldbook/WorldBookEditScreen.kt:93,601,689,771`；`ui/screen/settings/worldbook/WorldBookEditSections.kt:89`；`ui/screen/settings/worldbook/WorldBookListScreen.kt:86,344,384,467,625`
- **rememberSettingsOutlineColors**：18 个文件 / 35 处。`ui/component/worldbook/KeywordChipInput.kt:131`；`ui/screen/roleplay/RoleplayScenarioEditScreen.kt:106`；`ui/screen/settings/AssistantBasicScreen.kt:63`；`ui/screen/settings/AssistantExtensionsScreen.kt:41`；`ui/screen/settings/AssistantListScreen.kt:82`；`ui/screen/settings/AssistantMemoryScreen.kt:90`；`ui/screen/settings/AssistantPromptScreen.kt:78`；`ui/screen/settings/memory/MemoryManagementScreen.kt:349`；`ui/screen/settings/memory/SimpleMemoryEditorScreen.kt:151,172`；`ui/screen/settings/PresetManagerScreens.kt:228,499,610,680,716`；`ui/screen/settings/RoleplayScriptLabScreen.kt:336,359,453,539,547,555,563`；`ui/screen/settings/UserPersonaMasksScreen.kt:294`；`ui/screen/settings/VoiceRecognitionApiScreen.kt:141,180,251,267`；`ui/screen/settings/VoiceSynthesisSettingsScreen.kt:207,255,311,1308`；`ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt:61`；`ui/screen/settings/worldbook/WorldBookContentFullscreenSheet.kt:44`；`ui/screen/settings/worldbook/WorldBookEditScreen.kt:94`；`ui/screen/settings/worldbook/WorldBookListScreen.kt:204`
- **rememberSettingsSnackbarHostState**：12 个文件 / 12 处。`ui/screen/diagnostics/DiagnosticsScreen.kt:77`；`ui/screen/settings/AppUpdateScreen.kt:48`；`ui/screen/settings/ContextTransferScreen.kt:63`；`ui/screen/settings/memory/MemoryManagementScreen.kt:138`；`ui/screen/settings/memory/SimpleMemoryEditorScreen.kt:76`；`ui/screen/settings/ProviderDetailScreen.kt:64`；`ui/screen/settings/ProviderSettingsScreen.kt:124`；`ui/screen/settings/RoleplayScriptLabScreen.kt:86`；`ui/screen/settings/SettingsModelScreen.kt:71`；`ui/screen/settings/SettingsScreen.kt:117`；`ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt:67`；`ui/screen/settings/worldbook/WorldBookListScreen.kt:88`
- **TopAppSnackbarHost**：11 个文件 / 11 处。`ui/screen/diagnostics/DiagnosticsScreen.kt:211`；`ui/screen/settings/AppUpdateScreen.kt:266`；`ui/screen/settings/ContextTransferScreen.kt:294`；`ui/screen/settings/memory/MemoryManagementScreen.kt:553`；`ui/screen/settings/ProviderDetailScreen.kt:248`；`ui/screen/settings/ProviderSettingsScreen.kt:253`；`ui/screen/settings/RoleplayScriptLabScreen.kt:215`；`ui/screen/settings/SettingsModelScreen.kt:241`；`ui/screen/settings/SettingsScreen.kt:276`；`ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt:97`；`ui/screen/settings/worldbook/WorldBookListScreen.kt:147`
- **NarraAlertDialog**：16 个文件 / 24 处。`ui/component/NarraAlertDialog.kt:181`；`ui/screen/chat/ChatScreenOverlays.kt:321`；`ui/screen/immersive/ImmersivePhoneShell.kt:1697,1707`；`ui/screen/moments/MomentsScreen.kt:1042,1058,1074,1091`；`ui/screen/phone/PhoneComponents.kt:469`；`ui/screen/roleplay/RoleplayScreen.kt:832`；`ui/screen/roleplay/RoleplaySettingsContent.kt:250,268`；`ui/screen/settings/AssistantDeleteConfirmDialog.kt:18`；`ui/screen/settings/contextlog/ContextLogScreen.kt:204`；`ui/screen/settings/PresetManagerScreens.kt:262,579,762`；`ui/screen/settings/ProviderConfigTab.kt:421`；`ui/screen/settings/ProviderModelDialogs.kt:189,682`；`ui/screen/settings/ProviderModelTab.kt:118`；`ui/screen/settings/VoiceSynthesisSettingsScreen.kt:1421`；`ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt:186`；`ui/screen/settings/worldbook/WorldBookEditScreen.kt:570`
- **NarraInfoDialog**：0 个文件 / 0 处。没有生产调用；NarraInfoDialog 包装内部调用 NarraAlertDialog 已计入后者。

