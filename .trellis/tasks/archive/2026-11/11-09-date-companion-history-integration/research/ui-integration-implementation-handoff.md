# D6 UI 与生产入口实施交接

2026-11-19，原生 `trellis-implement /root/d6_ui`。功能归属 v2.4.1，沿用已批准任务；本记录是实施与验证输入，**不是已运行测试的通过报告**。

## 已实现入口与接口

- 生产 `RoleplayNavGraph` 的陪伴准备页“查看记录”进入新历史；Stage 保留原连续阅读，Reader 增加“历史与导出”入口。历史可返回 Reader、定位原文、进入独立声音库和本地装扮包。退出仍由既有 D2 场次/origin 路径恢复原线上会话。
- `AppRoutes.roleplayCompanionHistory(scenarioId, assistantId)` 与 `roleplayCompanionAppearanceTransfer` 配对编码。`roleplayCompanionReading` 尾加可空 `messageId/archiveSpaceId/archiveRunId`，旧两参数仍产生原路径。
- `CompanionReadingViewModel` 旧默认保持；显式旧档调用同一 D4 `authorizeArchive`，定位调用 `locate` 并建立 `followingBottom=false` 的真实消息/item/cursor 锚点。不重绑原运行或创建模型历史授权。
- `CompanionHistoryScreen` 消费新历史 VM：年月/日期/场次及未分场查询、旧档选择、50 条窗口与全量计数、加载更早、多选/单条编辑、删除确认及覆盖层 Back。TXT launcher 冻结实际 `exportRequest.id`，取消传 null；旧 SAF 结果不能套入新请求。
- 管理页对 `!record.readerEligible` 显示“回合待完成，原文可管理/导出”并禁单条 Reader 定位；`binding.readOnly` 与全旧档 scope 禁写，混合选择含只读绑定时禁批量改删。真实仓储仍负责最终资格/CAS；UI 没有扩大 Reader/声音/模型范围。
- `CompanionAppearanceTransferScreen` 绑定既有新 transfer VM 的预览/接受/取消/恢复。`CompanionAppearanceDocumentStore` 是 ContentResolver 文档适配：仅 content URI、16 MiB 上限、关流后才成功。launcher 取消不调用 VM/IO，不显示导出成功。
- 私人资料真实装配位于 `NavigationViewModelOwners.rememberContextTransferViewModel`；`SettingsDataNavRoutes` 接 `resumePendingImport`。`ContextTransferScreen` 仅在新域/未完成操作非空时增加计数、预览说明与恢复区，旧默认空值视觉保持。
- `AppGraphStorageOverrides` 只替换所提供存储；未提供时沿用生产 Room/AppSettingsStore/合规仓储。`@JvmOverloads` 保留原一参数构造。没有模型、合规、收费或网络安全绕过开关。
- F01 在既有 `NewChatSheet` 的角色选择区增加名称/标签本地筛选。空筛选可清除并返回，通常不可达的真实零角色仍有返回。内建 RP 角色始终存在的适配范围须在矩阵单列，不能把空会话当无角色。

## 独占文件与最小共享适配

源码根为 `app/src/main/java/com/example/myapplication/`。

- 新：`di/AppGraphStorageOverrides.kt`、`data/repository/roleplay/CompanionAppearanceDocumentStore.kt`、`ui/navigation/CompanionHistoryRoutes.kt`、`ui/screen/roleplay/CompanionHistoryScreen.kt`、`CompanionAppearanceTransferScreen.kt`、`ui/screen/settings/ContextTransferRecoveryList.kt`。
- 适配：`di/AppGraph.kt`、`ui/navigation/AppRoutes.kt`、`CompanionPresentationRoutes.kt`、`RoleplayNavGraph.kt`、`NavigationViewModelOwners.kt`、`SettingsDataNavRoutes.kt`、`viewmodel/CompanionReadingViewModel.kt`、`ui/screen/roleplay/CompanionReadingScreen.kt`、`ui/screen/settings/ContextTransferScreen.kt`。
- 敏感共享 UI：`ui/screen/immersive/ImmersivePhoneShell.kt` **仅 `NewChatSheet`** 的声明/本地筛选/空态/过滤列表。修改前原字节副本为 `research/ui-before/ImmersivePhoneShell.kt`；主会话已明确授权，未改变全壳布局、旧皮肤、基准或 inventory。
- JVM：原 `viewmodel/CompanionReadingViewModelTest.kt` 仅新增两条定位/不完整旧档凭证回归。
- AndroidTest 新：`ui/navigation/CompanionIntegrationStoryTest.kt`、`ui/screen/roleplay/CompanionHistoryScreenTest.kt`、`CompanionImeWindowAssertions.kt`。

历史新模型/VM/仓储/导出是 history 代理所有权，资料/设置/schema 是 transfer 代理所有权；此代理未写这些文件。check 代理后来调整 pending canonical 与只读绑定合同，已接 UI gates。

## 主会话的最小验证输入

唯一 Gradle、IDE formatter、截图和设备执行者为主会话；本代理没有运行它们。静态符号/调用方/路径查询已做，实际编译和结果待主会话提供。

- JVM selector：`com.example.myapplication.viewmodel.CompanionReadingViewModelTest`；本次新增 `requestedOldMessage_isLocatedOutsideInitialWindowAndOwnsReadingAnchor`、`incompleteArchiveIdentity_isRejectedWithoutFallingBackToCurrentWindow`。
- 设备 selector：`com.example.myapplication.ui.navigation.CompanionIntegrationStoryTest`（2 方法）与 `com.example.myapplication.ui.screen.roleplay.CompanionHistoryScreenTest`（2 方法）。不能仅因编译或 XML 文件存在写成通过。
- 完整故事用真实 AppGraph/AppNavHost/ROLEPLAY 手机主壳及生产 Screen/VM/仓储/网关，文件型独立 Room/DataStore、Fake secure store、已明确合成的合规记录、MockWebServer；预置 121 条线上来源与另一角色哨兵。覆盖预览不入 canonical→接受→输入→typed 继续→原 ID/时间安全重生成→Reader→历史 50/全量 121+→编辑→真实 SAF 取消→删除→声音/装扮→准确回原线上→第 5 个真实 HTTP。检查无跨角色正文、旧删除正文或运行 ID 改变。
- F01 故事真实打开新建会话 Sheet，执行空筛选→清除→再空筛选→返回，断言模型 HTTP 数为零。
- 编辑实际请求软键盘，`CompanionImeWindowAssertions` 从独立 Dialog 原生 `WindowInspector/rootWindowInsets` 验证 IME 可见、保存按钮高于可见区域下缘及触控至少 48dp；不能回退底层 Activity。当键盘关闭后仍保编辑层，再保存/取消/多选/离场。
- 只读页在实际 320dp 内容宽/200% LayoutInput 字体测试禁写及 Back 先清多选再离场。这是内容布局测试，不冒充独立 Dialog 已验证 200% 字体或物理设备体验。
- 完整故事先断言运行时 NetworkSecurityPolicy 对 localhost/127.0.0.1 允许、example.invalid 拒绝，并将 MWS 绑定至 127.0.0.1。仅主会话新增的 Debug 精确 loopback overlay 用于本次测试；main/baseline、权限、信任证书与全局配置保持。

测试每次运行的真实窗口 PNG/TXT 候选保留在隔离 dev 应用 `files/d6-integration-evidence/d6-story-<token>/`（全合成），主会话可用 run-as 读取；没有 record、基准替换或视觉接纳。原 D3/D4 34 项历史差异仍由主会话独立核对。

## 限制与状态

所有测试/截图当前尚待主会话实际运行、定位失败及验证绑定。真实模型文风、MiMo、物理设备音频/蓝牙与视觉接纳未测试。本代理释放上述源码给主会话与原生检查代理；未修改版本参数/更新 JSON，未提交、推送、发布、归档或操作真实用户数据库。
