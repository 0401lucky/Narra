# Research: P10 设置首页与 P11 提供商配置扩面

- Query: 调查实际设置入口、提供商配置链、共享所有者、源码风险与既有覆盖，提出可独立验收的 v2.3 批次。
- Scope: internal；只研究与规划。P11 限于提供商列表、详情 Config/Model tabs 及其实际可达弹层。
- Date: 2026-10-21
- Inputs: 本任务 `prd.md`；`.trellis/tasks/archive/2026-10/10-21-ui-token-accessibility-pilot/research/expansion-list.md`；`.trellis/spec/backend/ui-token-contract.md`。

## Findings

### 结论与批次边界

1. P10 与 P11 应为两个独立实现 / 检查 / 人工视觉验收批次。P10 管设置首页与三类外观面板，并唯一持有 `SettingsComponents.kt` 的增量共享接口；P11 在该接口稳定后迁移提供商链。不要把两者合成全设置迁移。
2. P11 包含 `ProviderSettingsScreen`、`ProviderDetailScreen`、`ProviderDetailChrome`、`ProviderConfigTab`、`ProviderModelTab`、`ProviderModelDialogs`、`ProviderTemplateDialog`。独立 `SettingsModelScreen` 不是本批整页迁移对象；其共享模型选择器只做必要回归。
3. 设置共享函数的当前搜索命中分布在 45 个 UI 文件（含定义文件，按下列符号集合计）。它不是“45 个页面已迁移 / 已验收”的证据。默认值若直接改动会波及 P02、P05、语音、翻译、世界书、记忆、Moments、Phone 等。
4. 有既有设备行为测试，无 P10/P11 页面截图登记。已有“160% 字体 / 320dp”不能升级成“320×640dp / 200% 字体已通过”，源码研究也不能替代运行结果。
5. 当前版本仍为 2.2.12-dev，本任务只归属 v2.3 规划；不调整版本、更新源、存储 / 协议或发布流程。

### 实际入口与回调链

| 入口 / 层 | 源码锚点与实际行为 |
| --- | --- |
| 手机主壳“我的 / Profile” | `app/src/main/java/com/example/myapplication/ui/screen/immersive/ImmersivePhoneShell.kt:446` 创建 Profile，`:458` 传入设置回调，`:1692` 的“设置”行调用它。不是“查手机”PhoneCheck 页入口。 |
| 主壳 → 设置首页 | `app/src/main/java/com/example/myapplication/ui/navigation/RoleplayNavGraph.kt:202` 导向 `AppRoutes.SETTINGS`，`launchSingleTop=true`。 |
| 设置图 | `app/src/main/java/com/example/myapplication/ui/navigation/SettingsNavGraph.kt:66` 注册图，`:71` 收集共享 SettingsViewModel 状态并创建 `SettingsScreen`；`:101` 提供商入口导航到 SETTINGS_PROVIDERS。 |
| P10 可见入口 | `app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsScreen.kt:177` 模型服务组实际显示默认模型、提供商、MiMo TTS、语音识别；`:210` 为主题色调 / 颜色模式 / 显示；`:235` 为更新 / 条款 / 诊断 / 脚本。虽然函数仍接收角色、世界书、记忆等回调，但首页没有对应行，不能据签名扩面。 |
| 提供商路由注册 | `app/src/main/java/com/example/myapplication/ui/navigation/SettingsNavGraph.kt:424` 调用 `registerSettingsProviderRoutes`；`app/src/main/java/com/example/myapplication/ui/navigation/SettingsProviderNavRoutes.kt:51` 是列表目的地，`:73` 是详情目的地。所有回调绑定同一 SettingsViewModel。 |
| 直接进入 P11 的会话设置入口 | `app/src/main/java/com/example/myapplication/ui/navigation/RoleplayNavGraph.kt:727` 打开指定 provider 详情，`:732` 打开提供商列表；因此 P11 验收还需覆盖由会话设置进入的路径，不只从 P10 进入。 |
| 路由 ID 兼容 | `app/src/main/java/com/example/myapplication/ui/navigation/AppRoutes.kt:15` / `:20` 为列表 / 详情常量，`:54` 当前直接拼 providerId；`app/src/main/java/com/example/myapplication/ui/navigation/SettingsProviderNavRoutes.kt:75` 直接取参数。视觉任务保留现状，不顺带重写编码或 ID。 |
| 全局模型页是独立目的地 | `app/src/main/java/com/example/myapplication/ui/navigation/SettingsNavGraph.kt:136` 由默认模型行进入 SETTINGS_MODEL；`app/src/main/java/com/example/myapplication/ui/navigation/SettingsProviderNavRoutes.kt:106` 单独注册 `SettingsModelScreen`，不是 ProviderDetail 的 Model tab。 |

P10 `SettingsScreen.kt:138` 的顶栏行为必须保留：有草稿且不在保存时为“保存”，否则按已保存配置显示会话 / 欢迎动作；保存中禁用。`SettingsNavGraph.kt:82` 的显式保存回调通过 `saveSettings { ... }` 成功回调决定去角色主壳或 HOME；本轮不恢复普通 Chat 日常入口。

### P11 文件与可达弹层

| 文件 | 负责的 UI / 行为锚点 |
| --- | --- |
| `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderSettingsScreen.kt` | `:83` 空草稿 ensure；`:95` 等待新 ID 真正出现在状态中才导航；`:136` 对已有凭据且无健康结果的提供商自动检测；`:172` 自适应 Grid 与搜索；`:250` 模板弹窗；`:287` 提供商卡。 |
| `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderTemplateDialog.kt` | `:44` 真实 Dialog；`:50` 返回 / 外部点击关闭；`:86` 模板 LazyColumn；`:100` 固定取消操作；`:117` 模板卡。 |
| `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderDetailScreen.kt` | `:75` 按 providerId 查找，不存在时 `:77` 明确提示；`:94` 按 providerId 保存 tab；`:101` 底栏；`:104` Model tab 才显示抓取 / 手工添加浮动操作；`:203` Config、`:218` Model 内容；`:238` 手工添加弹窗。 |
| `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderDetailChrome.kt` | `:45` 含返回、品牌图标与长标题的头部；`:113` Config/Model 底栏；`:152` 自定义 tab 项。 |
| `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderConfigTab.kt` | `:74` 密钥显隐按 provider.id 保存；`:101` 启用；`:109` 名称；`:121` API Key；`:142` 协议；`:162` 文本接口模式；`:185` URL；`:198` 有条件的自定义路径；`:224` 删除（最后一个不可删）；`:244` 保存禁重。协议选择 `:274` 已为窄屏 / 大字体切换纵向单选。 |
| `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderModelTab.kt` | `:90` 模型按生产排序；`:93` pending fetch 必须属于当前 provider；`:102` 空态；`:135` 模型列表；`:149` 清空确认；`:164` 能力覆盖；`:175` 抓取后选择 Sheet。 |
| `app/src/main/java/com/example/myapplication/ui/screen/settings/ProviderModelDialogs.kt` | `:65` 带独立能力编辑 / 移除的选择卡；`:154` 能力覆盖；`:198` 能力标签；`:242` 抓取选择 Sheet；`:469` checkbox 行；`:578` 手工添加及空 ID 错误。 |

P11 不应改为“新增模板后立即按返回 ID 导航”：现有 pending 等待防止新草稿尚未注入时进入“未找到”。列表自动健康检测会触发网络副作用，测试与截图必须隔离，不能为了截图改产品触发条件。

### 共享所有权与 opt-in 方案

`SettingsComponents` 搜索集合为 `Settings(Group|GroupDivider|ListRow|TopBar|Palette|PageIntro|SectionHeader|HelpButton|StatusPill|LetterAvatar|ModelAvatar|ModelOptionRow|NoticeCard|HintCard)`、`AnimatedSettingButton`、`rememberSettingsOutlineColors`。具体消费者包含：

- `app/src/main/java/com/example/myapplication/ui/screen/settings/AssistantListScreen.kt`（已验收 P02）；`ui/screen/roleplay/RoleplaySettingsContent.kt`、`RoleplaySettingsSidebarPanel.kt`、`RoleplaySettingsSections.kt`（P05）。
- `app/src/main/java/com/example/myapplication/ui/screen/settings/VoiceSynthesisSettingsScreen.kt`、`VoiceRecognitionSettingsScreen.kt`、`VoiceRecognitionApiScreen.kt`、`ScreenTranslationSettingsScreen.kt`、`SearchToolSettingsScreen.kt`。
- `app/src/main/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookListScreen.kt`、`ui/screen/settings/memory/MemoryManagementScreen.kt`、`ui/screen/moments/MomentsScreen.kt`、`ui/screen/phone/PhoneCheckScreen.kt`、`ui/screen/settings/AppUpdateScreen.kt` 等；不因此纳入这些页面的整页迁移。

建议确定以下写入边界，顺序为 P05 结束后再由 P10 修改共享文件，再进入 P11；若主计划另设共享准备步骤，也必须只有一个实现所有者。

| 文件 / 契约 | 唯一所有者与约束 |
| --- | --- |
| `ui/screen/settings/SettingsComponents.kt` | P10 所有者。新增按组件需要的可选 `shape`、`contentPadding`、行 / 组间距、标题换行参数，默认严格等于当前旧值；P10/P11 显式传入 NarraUi / NarraShapes。优先小参数接口，不新增全局迁移布尔开关，不用全局 CompositionLocal 自动改变所有消费者。P11 如需要新增共享参数，回到同一共享所有者处理，禁止两个实现同时写此文件。 |
| `SettingsScreenPadding` 与私有 legacy 常量 | `SettingsComponents.kt:70` 为 20dp、`:72` 组间距 8dp、`:73` 组圆角 8dp、`:74` 头像 16dp；保留原值与未迁移调用。P10 自己的 LazyColumn 改为显式 NarraUi spacing，不把 20dp 全局改成 16/24dp。 |
| 组 / 行 / 顶栏 opt-in | `SettingsComponents.kt:300` 两个 SettingsGroup 重载都要透传可选参数；`:347` SettingsListRow 保持旧形状 / padding / 两行上限默认，仅已迁移页选择新配置。`:118` SettingsTopBar 已有 `horizontalPadding`，P10 使用它；若增加标题换行选项，默认仍为当前行为，不能让 P02 被隐式改动。 |
| Palette | `SettingsComponents.kt:96` 已 `remember(colorScheme)`，角色对应 primary/onPrimary、secondaryContainer/onSecondaryContainer。先复用此解析，不引入第二套主题来源。补 primaryContainer 内容色等语义参数若必要，保持已有字段兼容，只由迁移调用显式消费。 |
| `ui/component/NarraButton.kt` | 默认不编辑；`:108` 起各文字按钮已用 `defaultMinSize(minHeight=48dp)`，`:268` 起 icon 全族已提供宽高 48dp。不能用再修改全局按钮默认 shape 的办法替代局部迁移。 |
| `ui/component/NarraAlertDialog.kt` | 默认不编辑。`:70` 窗口约束、`:110` 有界正文滚动、`:130` FlowRow 独立操作区、`:137` / `:145` 操作最小 48dp 已存在。P11 优先复用；如确有 P11 必需的新外观参数，单独通过共享所有者 opt-in，保留平台外点 / 返回关闭。 |
| `ui/theme/UiTokens.kt` | 沿用现有六档 spacing、8/16/24dp + pill、48dp，不动 `:21` 起历史别名数值。 |
| 源码守卫 / 截图登记公共文件 | 每批串行更新，单一集成所有者维护。`app/src/test/java/com/example/myapplication/ui/theme/UiTokenSourceInventory.kt:43` 当前只扫描 SettingsTopBar，`:45` 起其余 helpers 为 legacy；P10 仅提升已迁移声明，兼容默认值精准登记，不加“全部 settings / brand”白名单。 |

P10 接口提交应证明旧调用默认参数仍等价（编译 + 定向受影响行为 + P02 截图 verify）。P11 依赖 P10 的共享接口，不依赖 P10 成为所有设置页的全局默认。

### 源码风险候选（未宣称已复现的运行缺陷）

| 候选 | 证据、实际状态与计划检查 |
| --- | --- |
| 几何不统一 | `SettingsScreen.kt:169` 页面 20dp / 20dp 节奏；`:337`、`:367`、`:431`、`:565` 等 Sheet 的 20dp / 8dp shape；`SettingsComponents.kt:388` / `:390` / `:397` 行内 16/12、14、5dp；ProviderConfig `:115` 等 12dp shape；ProviderModelDialogs `:482` 等 14dp shape。逐个迁移默认布局到令牌；图标、边框、装饰偏移不等于 spacing，不机械取整。 |
| 顶栏和行长文 | `SettingsComponents.kt:159` 顶栏单行省略，`:403` / `:411` 行标题与说明两行上限；`ProviderDetailChrome.kt:105` 提供商标题最多两行。验证 200% 长标题与顶栏操作可达，再决定 opt-in 换行，不能仅因 maxLines 存在便断言裁切。 |
| 选中语义与容器配对 | `SettingsScreen.kt:368` / `:587` 选中面为半透明 primaryContainer，正文仍 title/body；`ProviderSettingsScreen.kt:375` 启用 pill 为 primaryContainer + primary；`ProviderDetailChrome.kt:160` / `:161` tab 同样使用 primaryContainer + primary。应计算实际合成表面的文字对比与角色配对，不假定 primary 是 primaryContainer 的通用内容色。 |
| 已正确的语义色 | `ProviderModelDialogs.kt:219` 能力颜色已有成对容器 / onContainer；`SettingsComponents.kt:96` 依实际 scheme 解析；`ProviderSettingsScreen.kt:293` 品牌暗色判断依实际 surface luminance。保留这些路径，不退回系统深浅主题判定。 |
| 健康标识 | `ProviderSettingsScreen.kt:394` 绿 / 黄 / 红硬编码圆点；`:354` 同时有检查 / 正常 / 失败文字，不是只靠颜色。圆点是品牌 / 装饰还是状态语义要精确约定；不要把装饰 9dp 圆点强制变为 48dp 视觉组件。 |
| 自定义 tab | `ProviderDetailChrome.kt:165` 已至少 48dp 高，但 `:167` 只有 clickable，未显式提供 tab role / selected 状态。检查实际语义树，补选中与分组语义时保留切换回调及 providerId 键控 tab。 |
| 提供商启用开关 | `ProviderConfigTab.kt:323` 的 title 与 Switch 是分离节点，只有 Switch 回调；与 P10 `SettingsScreen.kt:503` 的整行 Role.Switch toggleable + child null callback 不同。检查读屏名称和整行操作，避免新增双重触发。 |
| 抓取中操作与 FAB | `ProviderDetailScreen.kt:127` 抓取操作至少 48×48，默认图标有标签；`:138` 抓取中替换为 progress。确认 loading 语义、禁用状态、200% 两操作宽度、底栏与末项不遮挡。Config / Model 列表 `bottom=120dp` 是现有浮层留白，先测量再决定是否调整，不能直接当任意 spacing 消除。 |
| 手工输入与搜索 | `ProviderModelDialogs.kt:619` / `:653` BasicTextField 的视觉标签为相邻 Text；`:348` 抓取搜索同样为 BasicTextField。需要检验实际可访问名称、错误与最小触控范围；不能仅以外部文字可见宣称读屏已关联。 |
| Modal / Dialog 大字 | 模板 `ProviderTemplateDialog.kt:56` 全屏 Box 内 Surface + LazyColumn，声明允许外点关闭；需要真实外点 / 返回测试确认窗口命中，不把属性值当结论。Fetched Sheet `ProviderModelDialogs.kt:276` 为头 / weight 列表 / 固定底操作，在 200% 与 IME 下检查关键操作、搜索、首末模型。 |

### 必须保留的状态、ID、草稿和失败契约

- `app/src/main/java/com/example/myapplication/viewmodel/SettingsViewModel.kt:213` 名称按 ID 改草稿；`:216` / `:219` URL / key 修改清健康；`:222` 协议切换有现有 URL / mode 分支。v2.3 只改这些字段的呈现，不改行为与协议分流。
- `app/src/main/java/com/example/myapplication/viewmodel/SettingsDraftStateSupport.kt:27` 有未保存变更时保留本地草稿；`:162` 模板创建保留已有列表并选择新 provider。`SettingsViewModel.kt:305` 仍自动持久化新增 provider，UI 不新增 Store 写入。
- `SettingsViewModel.kt:375` 保存获取当前草稿，成功调用 `onSaved`，失败调用 applySaveFailure 并保留草稿；`app/src/main/java/com/example/myapplication/viewmodel/SettingsUiMutationSupport.kt:72` / `:77` 成功 / 失败分支显式复位 isSaving，没有 finally 释放的实现。取消 `CancellationException` 继续抛出。本轮不修改这些状态实现。
- **现状边界**：P10 `SettingsNavGraph.kt:180` 与 P11 `SettingsProviderNavRoutes.kt:66` / `:99` 返回时调用 `saveSettings {}` 后立即 pop，不是保存成功才返回。不要把 VM 的“不调用 onSaved”误写成 P10/P11 已保证“保存失败留在原页面”。本批不顺带重写返回导航；显式保存失败测试可断言草稿仍在、消息展示、未执行成功回调。
- `ProviderModelDialogs.kt:154` 取消覆盖只 dismiss；`:188` “恢复自动”显式 onSave(null)；`:591` 手工添加仅非空 ID 才提交；`ProviderModelTab.kt:93` pending fetch 归属当前 provider；`:157` 清空仅确认时触发。保留选择 / 编辑 / 移除隔离、已有能力 override、手动 ID / 显示名、清空 / 删除边界。
- API Key 不写入截图文本或研究材料。配置截图只用合成占位 key 并保持 PasswordVisualTransformation；不切成显示密钥状态录图。保留 `ProviderConfigTab.kt:127` 密码模式以及 Keystore / SecureValueStore 路径，不触及 schema / 凭据。
- `app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsModelScreen.kt:269` 复用 ModelPickerSheet；其实际物理文件 `app/src/main/java/com/example/myapplication/ui/screen/chat/ChatModelSheets.kt:1` 包名为 `ui.component.modelpicker`，`:213` 是定义。调用还在 `RoleplaySettingsContent.kt:216`、`ChatScreenOverlays.kt:148`。P11 默认不改该文件；必要共享回归不意味着接手普通 Chat，也不迁移该文件的 ReasoningBudgetSheet。

### 已有测试与覆盖缺口

以下是已存在的测试定义；本研究没有执行它们，不重复声称此前试点的全测覆盖了新的页面目标。

| 已有定义 | 覆盖与实际限制 |
| --- | --- |
| `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/SettingsScreenTest.kt:27` / `:79` | 有草稿动作走保存、诊断入口专属导航。未覆盖主壳 → P10 → P11 全路由或 200%。 |
| `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/SettingsAppearanceScreenTest.kt:35` / `:49` / `:60` | 主题 / mode 选中语义与回调，160% 显示 Sheet 最后一项可滚到并切换；没有强制 320×640 宿主。 |
| `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/SettingsComponentsTest.kt:30` | 320dp、140%、dark，长帮助关闭可达、帮助不触发行回调。不是 200% 全页验证。 |
| `app/src/androidTest/java/com/example/myapplication/ui/screen/settings/ProviderSettingsUiTest.kt:52` | 320dp / 160%，协议帮助默认隐藏、48dp、真实协议 / mode 回调不串线。 |
| 同文件 `:100` / `:191` | 模型卡三主题×浅深、长名称、160%、48dp 编辑/移除互不串选；能力取消 / 显式恢复自动；保存中禁重复。测试直接渲染 ConfigTabContent / PremiumModelCard，不是 ProviderSettingsScreen / ProviderDetailScreen 整页。 |
| `app/src/androidTest/java/com/example/myapplication/ui/component/NarraAlertDialogTest.kt:59` / `:124` / `:158` / `:188` | 长文大字末项与固定动作、禁用确认、返回仅 dismiss、外点仅 dismiss。复用既有基础能力，不代表 ProviderTemplateDialog 同样已受测。 |
| `app/src/test/java/com/example/myapplication/viewmodel/SettingsViewModelTest.kt:115` / `:216` / `:255` / `:337` / `:421` / `:452` / `:493` / `:896` | provider 初始化、新模板 / 旧草稿保留、立即选择保存、功能模型不切主会话 provider、能力 override、改 key 清健康。`:689` 失败保稿与不调用成功回调可复用模式。 |
| `SettingsDraftStateSupportTest.kt:63` / `:104` / `:115` / `:139`（同 JVM viewmodel 目录） | 草稿 hydrate、空列表注入、新模板追加与保存尚未同步时保留旧项。 |
| `SettingsProviderDraftSupportTest.kt:57` / `:109` / `:124`（同目录） | 抓取模型合并保留 override、删除 provider 选中回退、删模型回退。 |
| `SettingsModelLoadCoordinatorTest.kt:24` / `:50`、`SettingsHealthCoordinatorTest.kt:14` / `:37` / `:92`、`SettingsPersistenceCoordinatorTest.kt:33` / `:165`（同目录） | pending / 持久化抓取、健康成功 / 失败 / 脱敏、保存 / 旧 flow 与新草稿选择优先级。视觉改动通常不新增这些领域逻辑，只定向回归所绑行为。 |

本次搜索 `app/src/androidTest` 未发现对 ProviderSettingsScreen、ProviderDetailScreen、SettingsModelScreen、ProviderTemplateDialog 或 ModelPickerSheet 的完整直接渲染行为测试；P11 整页与实际 overlay 流程存在缺口。此结论是按 Composable 名称 / 测试源码搜索，不是形式化全覆盖分析。

截图事实：

- `app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotCases.kt:60` 起页面矩阵只登记 P01/P02/P03；`:181` / `:185` 当前首批 families 和计数无 P10/P11。`ScreenshotBaselineGuardTest.kt:21` 校验登记 / 类 / PNG。不得把 Bubble B04 线下或全局 C01 色板当设置页基准。
- `app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotFixture.kt:33` 常规 360×800dp / density2 / font1；`:36` 窄屏 320×640dp / font2，全部固定合成数据。
- `app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotStageTest.kt:105` 普通主题禁动态系统值，`:126` 支持固定 ColorScheme，`:142` 支持主题状态切换，`:91` 可冻结动画时钟；`ThemeScreenshotC01Test.kt:38` / `:60` 复用 resolveAppColorScheme 生成固定动态 scheme，不读系统壁纸。
- **弹层截图基础设施风险**：`ScreenshotStageTest.kt:186` 只捕获 StageTag 节点。真正 Dialog / ModalBottomSheet 可能使用独立窗口，新用例必须证明图像捕获了实际弹层并断言其标题、列表、操作区真实可见；不能仅截被遮挡底页，也不能用假弹层 Composable 代替生产窗口。必要时仅扩测试宿主捕获入口，不改产品窗口语义。

### 可复用 Fake 与最小行为验收

- P10/P11 都是纯 UiState + 回调参数页面，可先用固定 `SettingsUiState` 和调用计数 / 最后参数驱动真实 Composable；无需装配 AppGraph、用户数据库、用户 key 或启动网络。
- JVM / Robolectric 要贯通 SettingsViewModel 时复用 `app/src/test/java/com/example/myapplication/testutil/TestAiServices.kt:42` / `:52`、`FakeSettingsStore.kt:24`、`TestOpenAiCompatibleApi.kt:14`、既有 MainDispatcherRule。**TestAiServices 默认 `:74` 会创建真实 API factory**，Provider 列表又会自动 health check，因此必须注入 Test API，或给纯参数页面注入本地 callback + 固定健康 map；不能假定调用 helper 就已隔绝网络。
- 失败注入沿用 `SettingsViewModelTest.kt:695` 的 `AiSettingsEditor by services.settingsEditor` 包装，仅 override 本批相关 saveProviderSettings 抛错；验证改前 / 改后 provider IDs、草稿、选中模型和 override 保留，isSaving 复位、message、成功 callback=0。不要为视觉任务重写通用 Fake。
- androidTest 不直接引用 `src/test` 的 Fake；复用现有测试本地 `ProviderTestTheme` / `ProviderConfig` 的组合方式（`ProviderSettingsUiTest.kt:220` / `:235`），必要时把**测试专用**固定 UiState builder 放在对应 source set，不移动生产依赖。

P10 最小行为：手机 Profile 的设置回调 → P10；P10 提供商 / 模型行的独立回调；保存 / 会话 / 欢迎动作分支；三 Sheet 选中、关闭、真实更新回调；320×640 / 200% 下最后一项可达，Switch 标签单击一次触发一次；深浅 / 三主题 / 固定动态 scheme 内容配对、主要动作 48dp。若共享 opt-in 更改，P02 与 P05 原参数默认视觉 / 回调要保留。

P11 最小行为：列表搜索（含无结果）、新增模板取消 / 返回 / 外点不新增、模板确认新 ID 出现后只导航一次、按 ID 进入 / 缺失提示；Config 启用 / 协议 / mode / URL / key 显隐 / 路径字段值与 ID 不串；单 provider 不可删、多个可删；保存中禁重复、失败保稿；tab role / selected、切换后 provider.id 状态不串；Model tab 首末项 / 空态 / 抓取 disabled / loading / 成功 / 失败回调；已选模型、编辑 / 移除隔离；能力取消只 dismiss、恢复自动仅显式；抓取 Sheet 搜索 / 多选 / 全选 / 取消 / 确认保持既有 override；手工空 ID 错误不提交、合法 ID / displayName 正确回传；清空只有显式确认触发。200% / 48dp 以实际页面测量与触控验证，不单测常量存在。

### 每批最小截图矩阵建议

为控制新增基准量，三主题全部做主页面静态截图，其他状态在代表主题取图；其余三主题×深浅×动态组合通过参数化语义色 / 内容对比与行为检查覆盖，不把所有状态做笛卡尔积。以下是**拟新增候选数量，不是已有或通过数量**。

| P10 场景 | 配置 | 候选张数 |
| --- | --- | ---: |
| 设置首页，固定长 provider / model 摘要 | MATCHA / VANILLA / OCEAN × light / dark，360×800 / 100% | 6 |
| 设置首页固定动态 scheme | dynamic light / dark | 2 |
| 设置首页顶栏、服务组主要操作 | MATCHA light / dark，320×640 / 200% | 2 |
| 主题色调、颜色模式、显示 Sheet（显示滚到末项） | 每种 Sheet 的 light / dark，320×640 / 200% | 6 |
| **P10 合计** | 不包含未修改其他设置页 | **16** |

| P11 场景 | 配置 | 候选张数 |
| --- | --- | ---: |
| Provider 列表，长名称 / 选定模型 / 健康正常失败与停用 | 三主题 × light / dark，常规宿主 | 6 |
| 详情 Config 与有选中项的 Model tab | 每个 tab 的 MATCHA light / dark，常规宿主 | 4 |
| Config 实际表单 / 选择内容配对 | 固定 dynamic light / dark | 2 |
| 列表、Config 滚到保存、Model 末项与浮动操作 | 每场景 MATCHA light / dark，320×640 / 200% | 6 |
| 模板、抓取选择、手工添加错误、能力覆盖、清空确认、协议说明 | 每种实际弹层的 light / dark，320×640 / 200%，固定内容与打开步骤 | 12 |
| 搜索无结果、Model 空态 | 代表 light 与 dark 各一个场景 | 2 |
| **P11 合计** | 不新增 SettingsModelScreen 整页基准 | **32** |

P11“未找到 provider”由行为检查覆盖；若本次改动其 notice 几何 / 配色，再新增对应图。CHECKING 圆点有无限脉冲，常规图使用固定最终健康状态；确需 loading 图时冻结 Compose clock 到明确帧，不删除生产动画。

每张图先断言真实目标节点、主要文字 / 操作、宿主尺寸、fontScale、非空内容，再截图。新增 family / class / registry / expectedFamilyCounts / manifest 与 PNG 清单必须一致；先 compare 产出 actual / diff 人工审阅，未批准不接纳。当前原有 79/79 只作试点回归；新页候选、目标宿主 verify 与远端 CI 分别记录，不因本机通过宣称远端通过。

建议验收顺序：P10 opt-in 兼容检查 → P10 行为与 16 候选人工确认 → P11 真实列表 / 详情 / overlay 行为与 32 候选人工确认。任何批失败只收回该批局部调用 / opt-in 参数，不改全局令牌、回滚用户配置或重新录掉旧图；不要用一次全 App 批量替换“修复”失败。

## Related Specs

- `.trellis/workflow.md`：当前保持 planning，完成 design / implement / JSONL 后交主会话汇总，获后续批准才开始实现。
- `.trellis/spec/backend/ui-token-contract.md`：六档 spacing、NarraShapes、旧别名兼容、实际合成配对、用户几何优先、200% / 48dp、截图先审阅再接纳。
- `.trellis/spec/backend/ui-state-guidelines.md:93`：SettingsHelpButton、当前模型 / 健康信息不得藏入帮助、依实际 Material 主题、保存中禁重、能力取消 / 恢复自动边界。
- `.trellis/spec/backend/quality-guidelines.md:67`：Roborazzi verify、不在验证前 record、宿主隔离、基准人工确认；设备编译不等于运行。
- `.trellis/spec/backend/network-guidelines.md:17` / `:23` / `:44`：协议层归属、URL / 认证 / 分流保留，不在 UI 重建网络。
- `.trellis/spec/backend/database-guidelines.md:95`：敏感值与 Keystore，不触碰真实 key / 存储 schema。
- `.trellis/spec/guides/index.md`：共享修改先检查所有消费者，不用通用建议覆盖仓库实际行为。

## External References / Versions

- 本题无需外部方案比较，未使用在线文档或网络检索。
- 仓库依赖版本来源 `gradle/libs.versions.toml:9` / `:20` / `:31` / `:32`：Compose BOM 2026.02.01、coroutines 1.10.2、Roborazzi 1.76.0、Robolectric 4.17；`app/build.gradle:648` 起为测试依赖。不是建议升级依赖。

## Caveats / Not Found

- 全部风险条目为源码调查候选，未运行 App / 测试 / build / 设备，不声称复现裁切、对比失败、读屏错误或窗口点击问题。
- 没有 P10/P11 页面基准不等于没有设备测试；已有设备测试主要为组件 / 直接回调，且最大已见字体为 160%，需补整页 200% 与真实导航链。
- 共享消费者计数是按所列符号搜索到的 UI 文件数，含定义和辅助文件，不是完整传递依赖图或精确用户页面数量。
- 独立 Dialog / Sheet 能否由现有宿主正确捕获需在新增测试时证明；若截图捕获需要额外宿主工作，作为测试基础设施步骤，不改变产品 UI 状态 / 网络副作用。
- 保留 P10/P11 返回立即 pop 的现状。本任务只保障显式保存回调和草稿契约，不擅自承诺失败后仍停在当前 destination。
- P13 语音、翻译、全局模型页、ReasoningBudgetSheet、普通 Chat 以及 v2.4–v2.6 不在本轮实现范围。设置入口仍保留其真实导航，但不迁移被指向页面。
- D01、TalkBack 实际体验、物理设备 / IME / 系统栏与性能结论、远端新视觉 CI 均不能从本研究或宿主截图推导。
